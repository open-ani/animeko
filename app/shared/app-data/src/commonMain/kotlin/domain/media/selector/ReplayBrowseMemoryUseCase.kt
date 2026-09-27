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
import me.him188.ani.datasources.api.source.BrowseEpisode
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

enum class ReplayResult {
    /** 在记住的线路里按记住的集号对应关系找到目标集, 已 [MediaSelector.select] (正式选择, 写偏好). */
    SELECTED_BY_SORT,

    /** 按位置 (上次第 k 项 → 第 k+1 项, 且目标集 = 上次播放集 + 1) 命中, 已 [MediaSelector.selectTemporarily] (不写偏好). */
    SELECTED_BY_POSITION,

    /** 无记忆 / 源不可用 / 未命中 / 请求失败或超时 / 已有选择: 什么都没做, 交给自动匹配. */
    NOT_FOUND;

    val isHit: Boolean get() = this != NOT_FOUND
}

/**
 * 切集时先于自动匹配尝试浏览记忆. 调用方保证在同一协程里串行: 返回 [ReplayResult.NOT_FOUND] 才启动自动选择.
 * 返回值只由「是否调用了 select / selectTemporarily」决定; 记忆更新失败不改变返回值.
 *
 * 语义:
 * 1. `mediaSelector.selected.value != null` → NOT_FOUND, 无副作用 (不调用 browseSubject).
 * 2. 取 `subjectId` 的记忆; 无 → NOT_FOUND.
 *    结构性未命中缓存: 若 `recentStructuralMiss[subjectId]` 记录的记忆与当前记忆相等且距记录不足 [ReplayBrowseMemoryUseCaseImpl.REPLAY_MISS_TTL] → NOT_FOUND, 不发请求.
 * 3. 在 `allInstances.first()` 里找 `isEnabled && mediaSourceId == memory.mediaSourceId && source.supportsBrowsing` 的实例; 无 → NOT_FOUND (不记入缓存: 源可能只是被临时禁用).
 * 4. `withTimeoutOrNull(REPLAY_TIMEOUT) { source.browseSubject(memory.subject) }`; null (超时) → NOT_FOUND; 空列表 → 记入结构性未命中缓存 → NOT_FOUND.
 * 5. 线路: `channels.getOrNull(memory.channelIndex)?.takeIf { it.name == memory.channelName }`
 *    ?: `channels.firstOrNull { it.name == memory.channelName }`; 都找不到 → 线路列表非空说明记忆已结构性失效:
 *    `selected == null` 时 `repository.removeIf(subjectId, memory)` (记忆仍等于本次读到的值时才删除: 用户可能在等待期间已重新「播放并记住」) → NOT_FOUND. 记录实际下标.
 * 6. 剧集按记住的集号对应关系 ([expectedSiteSort]): 上次把站点集号为 a 的项当作第 p 集播放, 目标第 t 集找与 a 同类、编号为 a + (t − p) 的项;
 *    有多项时取离 `memory.episodeIndex + (t − p)` 最近的一项. 命中下标 i →
 *    `media = source.createMedia(memory.subject, channel.name, episodes[i], episodeInfo.sort)` → 再次确认 `selected == null` →
 *    `mediaSelector.select(media)` → 更新记忆 → SELECTED_BY_SORT.
 *    不直接拿目标集号去比: 站点可能把整部 OVA 列作 OVA01、OVA02 接在正片后面, 用户把 OVA01 当第 1 集播放后,
 *    第 2 集直接比集号会命中正片的 02. 记忆的项解析不出集号时对应关系未知, 不按集号找 (同一线路里的正片仍可能对得上号, 但不是用户要的那一项).
 * 7. 否则按位置, 前置守卫: `episodeInfo.sort is EpisodeSort.Normal && memory.playedAsSort is EpisodeSort.Normal
 *    && episodeInfo.sort.number == memory.playedAsSort.number + 1f`; 不满足 → NOT_FOUND.
 *    满足且 `episodes.getOrNull(memory.episodeIndex + 1)` 存在, 且不与对应关系矛盾 (推算得出集号时, 第 k+1 项解析出的集号不能是别的号) →
 *    `createMedia(..., episodeInfo.sort)` → 再次确认 `selected == null` → `selectTemporarily` → 更新记忆 (episodeIndex = k+1, 保持链式) → SELECTED_BY_POSITION.
 * 8. 都没有 → NOT_FOUND (不记入缓存: 记忆结构有效, 只是这一集不存在).
 *
 * 记忆更新: `withContext(NonCancellable) { runCatching { repository.setIf(subjectId, expected = memory, memory.copy(channelIndex = 实际下标, channelName = channel.name,
 *   episodeIndex = i, episodeSort = episodes[i].episodeSort?.takeUnless { it is Unknown }, playedAsSort = episodeInfo.sort)) }.onFailure { log } }`,
 *   独立 try/catch, 失败只记日志 (select 已经发生, 返回值必须反映它; 切集也不能把半次写入取消).
 *   比较相等才写 (CAS): 从读记忆到写回之间隔着条目页请求, 用户在这期间「播放并记住」的新记忆必须保留, 不相等只记日志.
 * 所有非 [CancellationException] 的异常 (网络 / BlockedException / UnsupportedOperationException / DataStore) → NOT_FOUND; 不弹验证码.
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
            if (mediaSelector.selected.value != null) return ReplayResult.NOT_FOUND
            val memory = repository.get(subjectId) ?: return ReplayResult.NOT_FOUND
            if (isRecentStructuralMiss(subjectId, memory)) return ReplayResult.NOT_FOUND

            val source = allInstances.first().firstOrNull { instance ->
                instance.isEnabled && instance.mediaSourceId == memory.mediaSourceId && instance.source.supportsBrowsing
            }?.source ?: return ReplayResult.NOT_FOUND

            val channels = withTimeoutOrNull(REPLAY_TIMEOUT) { source.browseSubject(memory.subject) }
                ?: return ReplayResult.NOT_FOUND
            if (channels.isEmpty()) {
                recordStructuralMiss(subjectId, memory)
                return ReplayResult.NOT_FOUND
            }

            val channelIndex = channels.getOrNull(memory.channelIndex)
                ?.takeIf { it.name == memory.channelName }
                ?.let { memory.channelIndex }
                ?: channels.indexOfFirst { it.name == memory.channelName }.takeIf { it >= 0 }
            if (channelIndex == null) {
                // 线路列表非空但按下标和名称都找不到: 记忆已结构性失效, 删除它, 免得这个条目每集都付一次条目页请求.
                // 只删本次读到的那条 (CAS), 且用户已手动选择时不删: 等待条目页期间用户可能已重新「播放并记住」.
                if (mediaSelector.selected.value != null) return ReplayResult.NOT_FOUND
                if (!repository.removeIf(subjectId, memory)) {
                    logger.info { "Browse memory for subject $subjectId changed while browsing, keeping the newer memory" }
                }
                return ReplayResult.NOT_FOUND
            }
            val channel = channels[channelIndex]
            val episodes = channel.episodes

            val expected = expectedSiteSort(memory, episodeInfo.sort)
            val sortIndex = expected?.let { findEpisodeIndex(episodes, it) }
            if (sortIndex != null) {
                val media = source.createMedia(memory.subject, channel.name, episodes[sortIndex], episodeInfo.sort)
                if (mediaSelector.selected.value != null) return ReplayResult.NOT_FOUND
                mediaSelector.select(media)
                logger.info { "Replayed browse memory for subject $subjectId by sort ${episodeInfo.sort}: ${media.mediaId}" }
                updateMemory(subjectId, memory, channelIndex, channel, sortIndex, episodeInfo.sort)
                return ReplayResult.SELECTED_BY_SORT
            }

            if (!isNextEpisode(target = episodeInfo.sort, played = memory.playedAsSort)) return ReplayResult.NOT_FOUND
            val positionIndex = memory.episodeIndex + 1
            val episode = episodes.getOrNull(positionIndex) ?: return ReplayResult.NOT_FOUND
            // 推算得出集号却没找到: 第 k+1 项若解析出了集号, 它就不是目标集 (例如 OVA 列表之后的 SP01).
            if (expected != null && episode.episodeSort.parsedOrNull() != null) return ReplayResult.NOT_FOUND
            val media = source.createMedia(memory.subject, channel.name, episode, episodeInfo.sort)
            if (mediaSelector.selected.value != null) return ReplayResult.NOT_FOUND
            mediaSelector.selectTemporarily(media)
            logger.info { "Replayed browse memory for subject $subjectId by position $positionIndex: ${media.mediaId}" }
            updateMemory(subjectId, memory, channelIndex, channel, positionIndex, episodeInfo.sort)
            return ReplayResult.SELECTED_BY_POSITION
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Failed to replay browse memory for subject $subjectId, falling back to auto select" }
            return ReplayResult.NOT_FOUND
        }
    }

    /**
     * 目标集在站点上应有的集号: 与记住的项 [like] 同类 (正片, 或同一种特殊类型), 编号为 [number].
     * [anchor] 是按位置推算的下标, 用于同一线路里有多项同号时挑选.
     */
    private class ExpectedSiteSort(val like: EpisodeSort, val number: Float, val anchor: Float)

    /**
     * 按记住的对应关系推算. 站点编号与条目一致 (a = p)、续季从 01 重新编号 (a = 01, p = 13)、
     * 整部 OVA 条目列作 OVA01… (a = OVA01, p = 01) 都是同一条规则.
     * 记住的项解析不出编号, 或目标集与上次播放的集不是同一类编号 (正片与 SP) 时推算不了, 返回 null.
     */
    private fun expectedSiteSort(memory: ManualBrowseMemory, target: EpisodeSort): ExpectedSiteSort? {
        val remembered = memory.episodeSort.parsedOrNull() ?: return null
        val rememberedNumber = remembered.number ?: return null
        val playedNumber = memory.playedAsSort.number ?: return null
        val targetNumber = target.number ?: return null
        if (!sameSeries(memory.playedAsSort, target)) return null
        val offset = targetNumber - playedNumber
        return ExpectedSiteSort(remembered, rememberedNumber + offset, memory.episodeIndex + offset)
    }

    private fun findEpisodeIndex(episodes: List<BrowseEpisode>, expected: ExpectedSiteSort): Int? =
        episodes.indices
            .filter { i ->
                val sort = episodes[i].episodeSort.parsedOrNull()
                sort != null && sameSeries(sort, expected.like) && sort.number == expected.number
            }
            .minByOrNull { i -> abs(i - expected.anchor) }

    /**
     * 同一类编号: 都是正片, 或是同一种特殊类型 (OVA 与 OVA, SP 与 SP).
     */
    private fun sameSeries(a: EpisodeSort, b: EpisodeSort): Boolean = when (a) {
        is EpisodeSort.Normal -> b is EpisodeSort.Normal
        is EpisodeSort.Special -> b is EpisodeSort.Special && a.type == b.type
        is EpisodeSort.Unknown -> false
    }

    private fun EpisodeSort?.parsedOrNull(): EpisodeSort? = this?.takeUnless { it is EpisodeSort.Unknown }

    /**
     * 按位置回放的守卫: 只有顺序看下一集时, 上次第 k 项的下一项才可信.
     */
    private fun isNextEpisode(target: EpisodeSort, played: EpisodeSort): Boolean =
        target is EpisodeSort.Normal && played is EpisodeSort.Normal && target.number == played.number + 1f

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
                        episodeSort = channel.episodes[episodeIndex].episodeSort?.takeUnless { it is EpisodeSort.Unknown },
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
