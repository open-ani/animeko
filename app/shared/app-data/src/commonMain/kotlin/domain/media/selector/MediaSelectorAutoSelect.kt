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
import me.him188.ani.app.domain.media.fetch.awaitCompletion
import me.him188.ani.app.domain.media.selector.engine.WebAutoSelectConfig
import me.him188.ani.app.domain.media.selector.engine.decideWebAutoSelect
import me.him188.ani.app.domain.media.selector.engine.runWebAutoSelect
import me.him188.ani.app.domain.mediasource.codec.MediaSourceTier
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
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
 * WEB 相关的自动选择 ([fastSelectWebSources], [trySelectPreferredWebSource], [autoSelectWeb]) 都是同一个纯决策函数
 * [decideWebAutoSelect] 加同一个执行循环 [runWebAutoSelect] 的不同配置. 有关数据源选择算法, 参阅 [MediaSelector], 尤其是 "快速选择" 部分.
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
     * 偏好 WEB 时的完整自动选择: 记忆的 web 源 (优先且阻塞) → 分阶段快速选择 → 所有 WEB 源结束后的默认选择;
     * 全程本地缓存可随时胜出. 规则见 [decideWebAutoSelect].
     *
     * 返回成功选择的 [Media]; 已有选择、或最终选不出时返回 `null`. 无论结果如何, 返回即表示自动选择结束.
     *
     * @param preferredWebMediaSourceId 按番剧记忆的 web 源, 见 [trySelectPreferredWebSource].
     * @param fastSelect 是否启用分阶段快速选择 ([MediaSelectorSettings.fastSelectWebKind]). 关闭时只在所有 WEB 源结束后选一次.
     * @param lowTierToleranceDuration 第一段超时 ([MediaSelectorSettings.fastSelectWebLowTierToleranceDuration]).
     * @param fuzzyFallbackDuration 第二段超时, 之后允许模糊匹配兜底.
     */
    suspend fun autoSelectWeb(
        mediaFetchSession: MediaFetchSession,
        sourceTiers: MediaSelectorSourceTiers,
        preferredWebMediaSourceId: String?,
        fastSelect: Boolean,
        lowTierToleranceDuration: Duration,
        fuzzyFallbackDuration: Duration = maxOf(DefaultFuzzyFallbackDuration, lowTierToleranceDuration),
    ): Media? {
        return mediaSelector.runWebAutoSelect(
            mediaFetchSession,
            WebAutoSelectConfig(
                sourceTiers = sourceTiers,
                preferredWebSourceId = preferredWebMediaSourceId,
                selectCache = true,
                fastSelect = fastSelect,
                lowTierToleranceDuration = lowTierToleranceDuration,
                fuzzyFallbackDuration = fuzzyFallbackDuration,
                defaultWhenAllCompleted = true,
                currentSelection = null,
            ),
        )
    }

    /**
     * 快速选择 Web 数据源的 [Media]. 逻辑详见 [MediaSelector] 中 "快速选择" 部分的说明, 规则实现见 [decideWebAutoSelect].
     *
     * 阶段随时间推进:
     * 1. 立即选择: 只选有效 tier 不超过 [instantSelectTierThreshold] 且条目名称精确匹配的资源;
     * 2. 经过 [lowTierToleranceDuration] 后: 任意 tier, 但只选精确匹配, 按 tier 升序;
     * 3. 经过 [fuzzyFallbackDuration] 后, 或所有 WEB 源都已结束查询: 先按 tier 升序选精确匹配, 再按 tier 升序选模糊匹配.
     *
     * 返回成功选择的 [Media] 对象. 以下情况返回 `null`:
     * - 会话中没有 WEB 源;
     * - [overrideUserSelection] 为 `false` 且已经有选择;
     * - [overrideUserSelection] 为 `true` 但等待期间选择被外部改变;
     * - 所有 WEB 源都已结束查询, 且在允许模糊匹配的前提下仍然选不出任何 WEB 资源.
     *
     * 只要还有 WEB 源在查询中, 此函数就会一直挂起等待.
     *
     * @param overrideUserSelection 是否覆盖调用时的选择.
     * 若为 `true`, 则以调用时的选择为基准替换选择. 两个例外: 当前选择本身已满足某一档的条件时, 到这一档就保留它 (返回它);
     * 等待期间选择被别人 (通常是用户) 改掉时, 放弃覆盖并返回 `null`.
     * 若为 `false`, 如果已经有选择, 则此函数不会做任何事情.
     * @param blacklistMediaIds 黑名单, 这些 media 不会被选择.
     * @param lowTierToleranceDuration 第一段超时: 之后放开 tier 限制, 只要求精确匹配.
     * @param fuzzyFallbackDuration 第二段超时: 之后允许模糊匹配兜底. 小于 [lowTierToleranceDuration] 时视为与其相等.
     * @param instantSelectTierThreshold Low Tier 与 High Tier 的分界线, 小于等于此 Tier 的资源可被立即选择.
     */ // #1323
    suspend fun fastSelectWebSources(
        mediaFetchSession: MediaFetchSession,
        sourceTiers: MediaSelectorSourceTiers,
        overrideUserSelection: Boolean = false,
        blacklistMediaIds: Set<String> = emptySet(),
        lowTierToleranceDuration: Duration = 5.seconds,
        fuzzyFallbackDuration: Duration = DefaultFuzzyFallbackDuration,
        instantSelectTierThreshold: MediaSourceTier = InstantSelectTierThreshold,
    ): Media? {
        if (mediaFetchSession.mediaSourceResults.none { it.kind == MediaSourceKind.WEB }) return null
        val current = mediaSelector.selected.value
        if (!overrideUserSelection && current != null) return null
        return mediaSelector.runWebAutoSelect(
            mediaFetchSession,
            WebAutoSelectConfig(
                sourceTiers = sourceTiers,
                preferredWebSourceId = null,
                selectCache = false,
                fastSelect = true,
                lowTierToleranceDuration = lowTierToleranceDuration,
                fuzzyFallbackDuration = fuzzyFallbackDuration,
                instantSelectTierThreshold = instantSelectTierThreshold,
                blacklistMediaIds = blacklistMediaIds,
                defaultWhenAllCompleted = false, // 快速选择只在 WEB 之间选
                currentSelection = if (overrideUserSelection) current else null,
            ),
        )
    }

    /**
     * 从用户偏好的 web 数据源选择媒体: 等该源查询结束, 只在它的偏好候选里选. 选不出、该源不在会话中、或已有选择时返回 `null`.
     */
    suspend fun trySelectPreferredWebSource(
        mediaFetchSession: MediaFetchSession,
        preferredWebMediaSourceId: String?,
    ): Media? {
        if (preferredWebMediaSourceId == null) return null
        if (mediaFetchSession.mediaSourceResults.none { it.mediaSourceId == preferredWebMediaSourceId && it.kind == MediaSourceKind.WEB }) {
            return null
        }
        return mediaSelector.runWebAutoSelect(
            mediaFetchSession,
            WebAutoSelectConfig(
                sourceTiers = MediaSelectorSourceTiers.Empty, // 只试记忆源, 不涉及 tier
                preferredWebSourceId = preferredWebMediaSourceId,
                stopAfterPreferredSource = true,
                selectCache = false,
                fastSelect = false,
                currentSelection = null,
            ),
        )
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
         * 快速选择第一阶段的 tier 阈值: 有效 tier 小于等于此值且条目名称精确匹配的资源, 在其数据源查询成功后不经等待立即选择.
         *
         * @see MediaSelector
         */
        val InstantSelectTierThreshold = MediaSourceTier(0u)

        /**
         * 快速选择第二段超时的默认值: 从快速选择开始计时, 超过此时长后才允许选择条目名称模糊匹配的资源.
         *
         * @see MediaSelector
         */
        val DefaultFuzzyFallbackDuration = 15.seconds
    }
}

private const val STOP = true
