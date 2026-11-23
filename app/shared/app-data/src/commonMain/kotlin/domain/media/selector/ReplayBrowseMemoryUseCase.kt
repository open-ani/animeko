/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.repository.media.ManualBrowseMemory
import me.him188.ani.app.data.repository.media.ManualBrowseMemoryRepository
import me.him188.ani.app.domain.mediasource.instance.MediaSourceInstance
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.BrowseChannel
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

sealed interface ReplayResult {
    /** 在记住的线路里按位置找到目标集, 已 [MediaSelector.select] (正式选择, 写偏好). */
    data object Selected : ReplayResult

    /** 什么都没做, 交给自动匹配; [reason] 说明为什么. */
    data class NotFound(val reason: ReplayMissReason) : ReplayResult

    val isHit: Boolean get() = this is Selected
}

/**
 * 浏览记忆回放未命中的原因.
 */
enum class ReplayMissReason {
    /** 调用时已经有选择. */
    ALREADY_SELECTED,

    /** 该条目没有记忆. */
    NO_MEMORY,

    /** 该记忆不久前刚结构性未命中过, 不再请求. */
    RECENT_STRUCTURAL_MISS,

    /** 记忆的源被禁用、不存在或不支持浏览. */
    SOURCE_UNAVAILABLE,

    /** 条目页请求超时. */
    BROWSE_TIMEOUT,

    /** 条目页没有任何线路. */
    EMPTY_CHANNELS,

    /** 线路列表里按下标和名称都找不到记忆的线路. */
    CHANNEL_NOT_FOUND,

    /** 目标集与记忆的集不是同一类编号, 或差不是整数. */
    NUMBERING_MISMATCH,

    /** 按位置换算出的下标超出线路的剧集列表. */
    INDEX_OUT_OF_RANGE,

    /** 等待条目页期间用户已手动选择. */
    PREEMPTED,

    /** 请求或创建资源时出错. */
    EXCEPTION,
}

/**
 * 切集时先于自动匹配尝试浏览记忆. 调用方保证在同一协程里串行: 返回 [ReplayResult.NotFound] 才启动自动选择.
 * 返回值只由「是否调用了 select」决定; 记忆更新失败不改变返回值.
 *
 * 语义:
 * 1. `mediaSelector.selected.value != null` → NotFound, 无副作用 (不调用 browseSubject).
 * 2. 取 `subjectId` 的记忆; 无 → NotFound.
 *    结构性未命中缓存: 若 `recentStructuralMiss[subjectId]` 记录的记忆与当前记忆相等且距记录不足 [ReplayBrowseMemoryUseCaseImpl.REPLAY_MISS_TTL] → NotFound, 不发请求.
 * 3. 在 `allInstances.first()` 里找 `isEnabled && mediaSourceId == memory.mediaSourceId && source.supportsBrowsing` 的实例; 无 → NotFound (不记入缓存: 源可能只是被临时禁用).
 * 4. `withTimeoutOrNull(REPLAY_TIMEOUT) { source.browseSubject(memory.subject) }`; null (超时) → NotFound; 空列表 → 记入结构性未命中缓存 → NotFound.
 * 5. 线路: `channels.getOrNull(memory.channelIndex)?.takeIf { it.name == memory.channelName }`
 *    ?: `channels.firstOrNull { it.name == memory.channelName }`; 都找不到 → 线路列表非空说明记忆已结构性失效:
 *    `selected == null` 时 `repository.removeIf(subjectId, memory)` (记忆仍等于本次读到的值时才删除: 用户可能在等待期间已重新「播放并记住」) → NotFound. 记录实际下标.
 * 6. 剧集按位置: 上次把第 k 项当作第 p 集播放, 目标第 t 集取第 k + (t − p) 项; t 与 p 须是同一类编号 (都是正片, 或同一种特殊类型)
 *    且差为整数, 否则 → NotFound. 命中下标 i → `media = source.createMedia(memory.subject, channel.name, episodes[i], episodeInfo.sort)` →
 *    再次确认 `selected == null` → `mediaSelector.select(media)` → 更新记忆 → Selected.
 *    不看数据源解析出的集号: 用户「播放并记住」时确认的就是「这一项是第 p 集, 下一话播放下一项」; 站点集号与条目集号的对应五花八门
 *    (续季从 01 重新编号, 整部 OVA 条目列作 OVA01、OVA02 接在正片后面), 拿集号去比会命中别的项.
 * 7. 越界 → NotFound (不记入缓存: 记忆结构有效, 只是这一集不存在, 站点之后可能更新).
 *
 * 记忆更新: `withContext(NonCancellable) { runCatching { repository.setIf(subjectId, expected = memory, memory.copy(channelIndex = 实际下标, channelName = channel.name,
 *   episodeIndex = i, playedAsSort = episodeInfo.sort)) }.onFailure { log } }`,
 *   独立 try/catch, 失败只记日志 (select 已经发生, 返回值必须反映它; 切集也不能把半次写入取消).
 *   比较相等才写 (CAS): 从读记忆到写回之间隔着条目页请求, 用户在这期间「播放并记住」的新记忆必须保留, 不相等只记日志.
 * 所有非 [CancellationException] 的异常 (网络 / BlockedException / UnsupportedOperationException / DataStore) → NotFound; 不弹验证码.
 * 超时用 `withTimeoutOrNull` 而不是 `withTimeout`: `TimeoutCancellationException` 是 CancellationException 子类, 重抛会杀掉扩展任务.
 */
fun interface ReplayBrowseMemoryUseCase : UseCase {
    suspend operator fun invoke(subjectId: Int, episodeInfo: EpisodeInfo, mediaSelector: MediaSelector): ReplayResult
}

/**
 * @param allInstances 传 `MediaSourceManager.allInstances`; 每次调用 `first()` 取当前实例 (实例会被重建并 close, 不长期持有 MediaSource).
 *        测试只需 `flowOf(listOf(createTestMediaSourceInstance(fake)))`.
 * @param clock 结构性未命中缓存的时钟, 测试可注入.
 */
class ReplayBrowseMemoryUseCaseImpl(
    private val repository: ManualBrowseMemoryRepository,
    private val allInstances: Flow<List<MediaSourceInstance>>,
    private val clock: Clock = Clock.System,
) : ReplayBrowseMemoryUseCase {
    /**
     * 结构性未命中 (条目页空) 的记忆与记录时间, 按 subjectId. 只在内存里, 不删持久化记忆.
     * 失效记忆每集都会付一次条目页请求并推迟自动选择, 这个缓存把代价限制在每 [REPLAY_MISS_TTL] 一次.
     */
    private val recentStructuralMiss = mutableMapOf<Int, Pair<ManualBrowseMemory, Instant>>()
    private val recentStructuralMissLock = Mutex()

    override suspend fun invoke(subjectId: Int, episodeInfo: EpisodeInfo, mediaSelector: MediaSelector): ReplayResult {
        try {
            if (mediaSelector.selected.value != null) return ReplayResult.NotFound(ReplayMissReason.ALREADY_SELECTED)
            val memory = repository.get(subjectId) ?: return ReplayResult.NotFound(ReplayMissReason.NO_MEMORY)
            if (isRecentStructuralMiss(subjectId, memory)) return ReplayResult.NotFound(ReplayMissReason.RECENT_STRUCTURAL_MISS)

            val source = allInstances.first().firstOrNull { instance ->
                instance.isEnabled && instance.mediaSourceId == memory.mediaSourceId && instance.source.supportsBrowsing
            }?.source ?: return ReplayResult.NotFound(ReplayMissReason.SOURCE_UNAVAILABLE)

            val channels = withTimeoutOrNull(REPLAY_TIMEOUT) { source.browseSubject(memory.subject) }
                ?: return ReplayResult.NotFound(ReplayMissReason.BROWSE_TIMEOUT)
            if (channels.isEmpty()) {
                recordStructuralMiss(subjectId, memory)
                return ReplayResult.NotFound(ReplayMissReason.EMPTY_CHANNELS)
            }

            val channelIndex = channels.getOrNull(memory.channelIndex)
                ?.takeIf { it.name == memory.channelName }
                ?.let { memory.channelIndex }
                ?: channels.indexOfFirst { it.name == memory.channelName }.takeIf { it >= 0 }
            if (channelIndex == null) {
                // 线路列表非空但按下标和名称都找不到: 记忆已结构性失效, 删除它, 免得这个条目每集都付一次条目页请求.
                // 只删本次读到的那条 (CAS), 且用户已手动选择时不删: 等待条目页期间用户可能已重新「播放并记住」.
                if (mediaSelector.selected.value != null) return ReplayResult.NotFound(ReplayMissReason.PREEMPTED)
                if (!repository.removeIf(subjectId, memory)) {
                    logger.info { "Browse memory for subject $subjectId changed while browsing, keeping the newer memory" }
                }
                return ReplayResult.NotFound(ReplayMissReason.CHANNEL_NOT_FOUND)
            }
            val channel = channels[channelIndex]
            val episodes = channel.episodes

            val index = browseEpisodeIndex(memory.episodeIndex, memory.playedAsSort, episodeInfo.sort)
                ?: return ReplayResult.NotFound(ReplayMissReason.NUMBERING_MISMATCH)
            val episode = episodes.getOrNull(index)
                ?: return ReplayResult.NotFound(ReplayMissReason.INDEX_OUT_OF_RANGE)
            val media = source.createMedia(memory.subject, channel.name, episode, episodeInfo.sort)
            if (mediaSelector.selected.value != null) return ReplayResult.NotFound(ReplayMissReason.PREEMPTED)
            mediaSelector.select(media, SelectTrigger.BROWSE_MEMORY_REPLAY)
            logger.info { "Replayed browse memory for subject $subjectId at index $index as ${episodeInfo.sort}: ${media.mediaId}" }
            updateMemory(subjectId, memory, channelIndex, channel, index, episodeInfo.sort)
            return ReplayResult.Selected
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Failed to replay browse memory for subject $subjectId, falling back to auto select" }
            return ReplayResult.NotFound(ReplayMissReason.EXCEPTION)
        }
    }

    /**
     * select 已经发生, 这里的失败只记日志; 切集取消也不能把半次写入取消.
     * 只在仓库里的记忆仍是本次读到的 [memory] 时写回: 用户在条目页请求期间「播放并记住」的新记忆优先.
     */
    private suspend fun updateMemory(
        subjectId: Int,
        memory: ManualBrowseMemory,
        channelIndex: Int,
        channel: BrowseChannel,
        episodeIndex: Int,
        playedAsSort: EpisodeSort,
    ) {
        withContext(NonCancellable) {
            runCatching {
                val applied = repository.setIf(
                    subjectId,
                    expected = memory,
                    memory = memory.copy(
                        channelIndex = channelIndex,
                        channelName = channel.name,
                        episodeIndex = episodeIndex,
                        playedAsSort = playedAsSort,
                    ),
                )
                if (!applied) {
                    logger.info { "Browse memory for subject $subjectId changed while browsing, keeping the newer memory" }
                }
            }.onFailure { e ->
                logger.warn(e) { "Failed to update browse memory for subject $subjectId after replay" }
            }
        }
    }

    private suspend fun isRecentStructuralMiss(subjectId: Int, memory: ManualBrowseMemory): Boolean =
        recentStructuralMissLock.withLock {
            val (missedMemory, recordedAt) = recentStructuralMiss[subjectId] ?: return false
            missedMemory == memory && clock.now() - recordedAt < REPLAY_MISS_TTL
        }

    private suspend fun recordStructuralMiss(subjectId: Int, memory: ManualBrowseMemory) {
        recentStructuralMissLock.withLock {
            recentStructuralMiss[subjectId] = memory to clock.now()
        }
    }

    companion object {
        val REPLAY_TIMEOUT = 15.seconds
        val REPLAY_MISS_TTL = 10.minutes

        private val logger = logger<ReplayBrowseMemoryUseCaseImpl>()
    }
}
