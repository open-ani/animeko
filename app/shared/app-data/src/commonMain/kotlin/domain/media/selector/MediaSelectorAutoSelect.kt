/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.takeWhile
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.awaitCompletedResults
import me.him188.ani.app.domain.media.fetch.awaitCompletion
import me.him188.ani.app.domain.mediasource.codec.MediaSourceTier
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.logging.logger
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 访问 [MediaSelector] 的自动选择功能
 */
inline val MediaSelector.autoSelect get() = MediaSelectorAutoSelect(this)

/**
 * [MediaSelector] 自动选择功能.
 *
 * 有关数据源选择算法, 参阅 [MediaSelector], 尤其是 "快速选择" 部分.
 */
class MediaSelectorAutoSelect(
    private val mediaSelector: MediaSelector,
) {
    /**
     * 等待所有数据源查询完成, 然后根据用户的偏好设置自动选择.
     *
     * 返回成功选择的 [Media] 对象. 当用户已经手动选择过一个别的 [Media], 或者没有可选的 [Media] 时返回 `null`.
     *
     * @param waitForKind 等待此数据源类型完成后, 才执行选择. 如果为 `null`, 则等待所有数据源查询完成.
     */
    suspend fun awaitCompletedAndSelectDefault(
        mediaFetchSession: MediaFetchSession,
        waitForKind: Flow<MediaSourceKind?> = flowOf(null)
    ): Media? {
        // 等全部加载完成
        mediaFetchSession.awaitCompletion { completedConditions ->
            return@awaitCompletion waitForKind.first()?.let {
                completedConditions[it]
            } ?: completedConditions.allCompleted()
        }
        if (mediaSelector.selected.value == null) {
            val selected = mediaSelector.trySelectDefault()
            return selected
        }
        return null
    }

    /**
     * 快速选择 Web 数据源的 [Media]. 逻辑详见 [MediaSelector] 中 "快速选择" 部分的说明.
     *
     * 返回成功选择的 [Media] 对象. 当用户已经手动选择过一个别的 [Media], 或者没有可选的 [Media] 时返回 `null`.
     *
     * @param overrideUserSelection 是否覆盖用户选择.
     * 若为 `true`, 则允许替换调用开始时的选择；等待期间的新选择不会被覆盖.
     * 若为 `false`, 如果用户已经选择了一个 media, 则此函数不会做任何事情.
     * @param blacklistMediaIds 黑名单, 这些 media 不会被选择. 如果遇到黑名单中的 media, 将会跳过.
     * @param lowTierToleranceDuration 详见 [MediaSelector] 中 "快速选择" 部分的说明.
     * @param instantSelectTierThreshold 有效 tier 不超过此阈值的精确匹配资源可立即选择。
     * @param fuzzyMatchToleranceDuration 从本次选择开始计算的模糊匹配截止时间，默认 15 秒。
     * 不早于 lowTierToleranceDuration；无限等待时不允许模糊匹配。
     * @param waitForPendingSources 到最后阶段仍无结果时是否继续等查询中的源。播放失败换源传 false。
     */ // #1323
    suspend fun fastSelectWebSources(
        mediaFetchSession: MediaFetchSession,
        sourceTiers: MediaSelectorSourceTiers,
        overrideUserSelection: Boolean = false,
        blacklistMediaIds: Set<String> = emptySet(),
        lowTierToleranceDuration: Duration = 5.seconds,
        instantSelectTierThreshold: MediaSourceTier = InstantSelectTierThreshold,
        fuzzyMatchToleranceDuration: Duration = 15.seconds,
        waitForPendingSources: Boolean = true,
    ): Media? = mediaSelector.runWebAutoSelect(
        mediaFetchSession,
        WebAutoSelectConfig(
            sourceTiers = sourceTiers,
            exactMatchAfter = lowTierToleranceDuration,
            fuzzyMatchAfter = fuzzyMatchToleranceDuration,
            instantTier = instantSelectTierThreshold,
            blacklist = blacklistMediaIds,
            waitForPendingSources = waitForPendingSources,
        ),
        expectedSelection = if (overrideUserSelection) mediaSelector.selected.value else null,
    )

    /**
     * 从用户偏好的 web 数据源快速选择媒体, 如果没有则返回 null
     */
    @OptIn(UnsafeOriginalMediaAccess::class) // 仅用 original 读 mediaSourceId 判断该源是否已进入候选流, 不参与选择
    suspend fun trySelectPreferredWebSource(
        mediaFetchSession: MediaFetchSession,
        preferredWebMediaSourceId: String?,
    ): Media? {
        if (preferredWebMediaSourceId == null) return null

        // 等待该源查询完成. 若该源不存在则直接返回.
        val result = mediaFetchSession.mediaSourceResults
            .firstOrNull { it.mediaSourceId == preferredWebMediaSourceId && it.kind == MediaSourceKind.WEB }
            ?: return null
        result.awaitCompletion()

        suspend fun trySelect(): Media? = mediaSelector.trySelectFromMediaSources(
            listOf(preferredWebMediaSourceId),
            overrideUserSelection = false,
            blacklistMediaIds = emptySet(),
            allowNonPreferred = false, // 只从这一个源里选
        )

        // 先做一次快照选择. 候选流已就绪时立即成功, 保持与旧实现一致的时序 (不引入额外挂起, 以免在
        // 上层 select {} 竞争中把该源的选择让给兜底 clause).
        trySelect()?.let { return it }

        // 快照落空有两种可能:
        // 1) 该源确实没有可选结果;
        // 2) awaitCompletion 只保证该源自身状态完成, 而 DefaultMediaSelector 的候选流 (filteredCandidates /
        //    preferredCandidates) 是经 combine + shareIn(replay=1) 从 cumulativeResults 异步派生的, 传播有延迟,
        //    此刻 shareIn 回放给 .first() 的还是不含该源结果的旧快照 (生产环境的实际竞态).
        // 先排除 (1): 该源没有任何结果就无需选择.
        if (result.awaitCompletedResults().isEmpty()) return null

        // 处理 (2): 挂起等候选流吸收该源的结果后再快照一次; 若仍为空说明该源确实没有可选项.
        // filterMediaList 是 1:1 map (Included/Excluded), 该源既然有结果就一定会出现在 filteredCandidates 里,
        // 所以此处不会永久挂起.
        mediaSelector.filteredCandidates.first { candidates ->
            candidates.any { it.original.mediaSourceId == preferredWebMediaSourceId }
        }
        return trySelect()
    }

    /**
     * 自动选择第一个 [MediaSourceKind.LocalCache] [Media].
     *
     * 当成功选择了一个 [Media] 时返回它. 若已经选择了一个别的, 或没有 [MediaSourceKind.LocalCache] 类型的 [Media] 供选择, 返回 `null`.
     */
    suspend fun selectCached(
        mediaFetchSession: MediaFetchSession,
        maxAttempts: Int = Int.MAX_VALUE,
    ): Media? {
        val isSuccess = object {
            @Volatile
            var value: Media? = null

            @Volatile
            var attempted = 0
        }
        combine(
            mediaFetchSession.cumulativeResults,
        ) { _ ->
            if (mediaSelector.selected.value != null) {
                // 用户已经选择了
                isSuccess.value = null
                return@combine STOP
            }

            val selected = mediaSelector.trySelectCached()
            if (selected != null) {
                isSuccess.value = selected
                STOP
            } else {
                if (++isSuccess.attempted >= maxAttempts) {
                    // 尝试次数过多
                    STOP
                } else {
                    // 继续等待
                    !STOP
                }
            }
        }.takeWhile { it == !STOP }.collect()
        return isSuccess.value
    }

    // #355 播放时自动启用上次临时启用选择的数据源
    suspend fun autoEnableLastSelected(mediaFetchSession: MediaFetchSession) {
        val lastSelectedId = mediaSelector.mediaSourceId.finalSelected.first()
        val lastSelected = mediaFetchSession.mediaSourceResults.firstOrNull {
            it.mediaSourceId == lastSelectedId
        } ?: return
        lastSelected.enable()
    }

    companion object {
        /**
         * 如果快速选择数据源功能为启用状态 ([MediaSelectorSettings.fastSelectWebKind]),
         * 不经过任何等待, 只要该数据源查询成功并且有精确匹配资源就立即选择.
         *
         * @see MediaSelector
         */
        val InstantSelectTierThreshold = MediaSourceTier(0u)
    }
}

private const val STOP = true

// 日常没啥用, 只有出 bug 了才会用到
private val logger = /*SilentLogger*/logger<MediaSelectorAutoSelect>()
