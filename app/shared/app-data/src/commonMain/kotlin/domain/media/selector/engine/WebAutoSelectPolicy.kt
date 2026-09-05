/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector.engine

import me.him188.ani.app.data.models.preference.MediaPreference.Companion.ANY_FILTER
import me.him188.ani.app.domain.media.selector.MatchMetadata
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.mediasource.codec.MediaSourceTier
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.isLocalCache
import me.him188.ani.datasources.api.source.MediaSourceKind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * WEB 自动选择策略的配置. 在一次自动选择开始时快照, 过程中不变.
 *
 * @property preferredWebSourceId 按番剧记忆的 web 源. 非 `null` 时先进入 [WebAutoSelectStage.PREFERRED_SOURCE]:
 * 等该源查询结束并只从它里面选, 选不出才放行后续阶段. 该源不在会话中时视作立即落空.
 * @property stopAfterPreferredSource 记忆源落空后直接结束, 不进入后续阶段 (用于单独的 "只试记忆源" 调用).
 * @property selectCache 每个快照都优先检查本地缓存, 有就选.
 * @property fastSelect 是否启用分阶段快速选择. 关闭时不按时间推进阶段, 只在所有 WEB 源结束后按 [WebAutoSelectStage.FUZZY] 规则选一次.
 * @property lowTierToleranceDuration 第一段: 之后从 [WebAutoSelectStage.INSTANT] 进入 [WebAutoSelectStage.EXACT_ONLY].
 * @property fuzzyFallbackDuration 第二段: 之后进入 [WebAutoSelectStage.FUZZY]. 小于第一段时视为与其相等. 两段都从记忆源放行之后起算.
 * @property defaultWhenAllCompleted 所有 WEB 源都结束、分阶段规则仍选不出时, 是否退回到 "按偏好从偏好候选 (含非 WEB) 里选一个" 的默认选择.
 * 编排入口为 `true` (对应旧的兜底 clause); 播放失败换源为 `false` (只在 WEB 之间换).
 * @property currentSelection 决策开始时的选择. 非 `null` 表示覆盖模式: 当它本身满足某一档的条件时保留它而不是换成同档的别的资源.
 */
internal data class WebAutoSelectConfig(
    val sourceTiers: MediaSelectorSourceTiers,
    val preferredWebSourceId: String? = null,
    val stopAfterPreferredSource: Boolean = false,
    val selectCache: Boolean = false,
    val fastSelect: Boolean = true,
    val lowTierToleranceDuration: Duration = 5.seconds,
    val fuzzyFallbackDuration: Duration = 15.seconds,
    val instantSelectTierThreshold: MediaSourceTier = MediaSourceTier(0u),
    val blacklistMediaIds: Set<String> = emptySet(),
    val defaultWhenAllCompleted: Boolean = false,
    val currentSelection: Media? = null,
)

/**
 * 自动选择的阶段. 由执行循环随时间推进, 策略函数本身不关心时间.
 */
internal enum class WebAutoSelectStage {
    /** 等待记忆的 web 源查询结束并只从它里面选. */
    PREFERRED_SOURCE,

    /** 只选有效 tier 不超过阈值且条目名称精确匹配的资源, 源一查询成功就选. */
    INSTANT,

    /** 任意 tier, 只选精确匹配, 按有效 tier 升序逐档. */
    EXACT_ONLY,

    /** 先精确后模糊, 各按有效 tier 升序. */
    FUZZY,
}

/**
 * 策略函数对一个快照给出的决定.
 */
internal sealed class WebAutoSelectDecision {
    /** 当前快照下没有可做的事, 等下一个快照或阶段推进. */
    data object Wait : WebAutoSelectDecision()

    /** 记忆源阶段结束且没有选中, 应放行并开始计时进入后续阶段. */
    data object ReleasePreferredSourceGate : WebAutoSelectDecision()

    /** 选择这个资源并结束. */
    data class Select(val media: Media, val reason: String) : WebAutoSelectDecision()

    /** 确定选不出任何资源, 结束. */
    data object Finish : WebAutoSelectDecision()
}

/**
 * WEB 自动选择的纯决策函数: 给定一致的快照、配置与当前阶段, 返回该做什么. 没有 suspend, 没有副作用, 没有时间.
 *
 * 规则按固定顺序求值 (优先级是结构性的, 不依赖并发时序):
 *
 * 1. [WebAutoSelectConfig.selectCache] 开启时, 任何阶段只要有本地缓存就选缓存.
 * 2. [WebAutoSelectStage.PREFERRED_SOURCE]: 记忆源未结束则等; 结束后只从它的偏好候选里选; 选不出则放行.
 * 3. 分阶段规则. 所有 WEB 源都已结束时直接按 [WebAutoSelectStage.FUZZY] 评估 (没有必要再等):
 *    - INSTANT: 精确匹配且有效 tier ≤ 阈值, 一组;
 *    - EXACT_ONLY: 精确匹配, 按有效 tier 升序分组;
 *    - FUZZY: 精确匹配各 tier 升序, 然后模糊匹配各 tier 升序.
 *    组内先在用户偏好候选里按偏好选, 再放开全部偏好选. 覆盖模式下当前选择若在组内则保留它.
 *    严格逐组尝试保证了 tier 优先级不会被数据源列表顺序或分辨率/语言偏好跨 tier 覆盖.
 * 4. 所有 WEB 源都已结束仍选不出: [WebAutoSelectConfig.defaultWhenAllCompleted] 时按偏好从偏好候选 (任意类型) 里选一个 (旧 `trySelectDefault` 语义), 否则 [WebAutoSelectDecision.Finish].
 *    此时 "都已结束" 的口径与旧兜底一致: 没有启用的 WEB 源时改为等所有源结束 ([AutoSelectSnapshot.allSourcesFinalForPreferredWeb]).
 * 5. 否则 [WebAutoSelectDecision.Wait].
 *
 * 需要 [MediaSelectorContext.allFieldsLoaded][me.him188.ani.app.domain.media.selector.MediaSelectorContext.allFieldsLoaded] 才能做偏好选择;
 * context 未加载而又有候选时返回 [WebAutoSelectDecision.Wait].
 */
internal fun decideWebAutoSelect(
    snapshot: AutoSelectSnapshot,
    config: WebAutoSelectConfig,
    stage: WebAutoSelectStage,
): WebAutoSelectDecision {
    val blacklist = config.blacklistMediaIds
    val contextLoaded = snapshot.context.allFieldsLoaded()

    fun find(pool: List<MaybeExcludedMedia.Included>, relaxAll: Boolean): Media? {
        if (pool.isEmpty()) return null
        val preference = if (relaxAll) {
            snapshot.mergedPreference.copy(
                alliance = ANY_FILTER,
                resolution = ANY_FILTER,
                subtitleLanguageId = ANY_FILTER,
                mediaSourceId = ANY_FILTER,
            )
        } else {
            snapshot.mergedPreference.copy(alliance = ANY_FILTER)
        }
        return findMediaByPreference(pool, preference, snapshot.availableAlliances, snapshot.context, snapshot.settings)
    }

    // 1. 本地缓存
    if (config.selectCache) {
        val cached = snapshot.preferred.firstOrNull { it.result.isLocalCache() }
            ?: snapshot.included.firstOrNull { it.result.isLocalCache() }
        if (cached != null) return WebAutoSelectDecision.Select(cached.result, "local cache")
    }

    // 2. 记忆的 web 源
    if (stage == WebAutoSelectStage.PREFERRED_SOURCE) {
        val release = if (config.stopAfterPreferredSource) WebAutoSelectDecision.Finish else WebAutoSelectDecision.ReleasePreferredSourceGate
        val source = snapshot.webSources.firstOrNull { it.mediaSourceId == config.preferredWebSourceId } ?: return release
        if (!source.isFinal) return WebAutoSelectDecision.Wait
        val pool = snapshot.preferred.filter { it.result.mediaSourceId == source.mediaSourceId && it.result.mediaId !in blacklist }
        if (pool.isEmpty()) return release
        if (!contextLoaded) return WebAutoSelectDecision.Wait
        return find(pool, relaxAll = false)
            ?.let { WebAutoSelectDecision.Select(it, "preferred web source") }
            ?: release
    }

    // 3. 分阶段规则
    // 编排入口 (defaultWhenAllCompleted) 沿用旧兜底的完成条件: 没有启用的 WEB 源时等所有源结束, 只用 BT/缓存的用户才不会被立刻放弃.
    val allFinal = if (config.defaultWhenAllCompleted) snapshot.allSourcesFinalForPreferredWeb else snapshot.allWebSourcesFinal
    val effectiveStage = when {
        allFinal -> WebAutoSelectStage.FUZZY
        !config.fastSelect -> return WebAutoSelectDecision.Wait // 关闭快速选择: 只等全部结束
        else -> stage
    }
    val eligible = snapshot.included.filter {
        it.result.kind == MediaSourceKind.WEB &&
                it.result.mediaSourceId in snapshot.succeededWebSourceIds &&
                it.result.mediaId !in blacklist
    }
    if (eligible.isNotEmpty() && !contextLoaded) return WebAutoSelectDecision.Wait

    val preferredIds = snapshot.preferred.mapTo(HashSet()) { it.result.mediaId }
    for (group in groupsFor(effectiveStage, eligible, config)) {
        config.currentSelection?.let { current ->
            if (group.any { it.result.mediaId == current.mediaId }) {
                return WebAutoSelectDecision.Select(current, "$effectiveStage: keep current selection")
            }
        }
        val selected = find(group.filter { it.result.mediaId in preferredIds }, relaxAll = false)
            ?: find(group, relaxAll = true)
        if (selected != null) {
            val tier = config.sourceTiers.get(selected.mediaSourceId, selected.properties.alliance)
            return WebAutoSelectDecision.Select(selected, "$effectiveStage: tier=${tier.value}")
        }
    }

    // 4. 全部结束仍选不出
    if (allFinal) {
        if (!config.defaultWhenAllCompleted) return WebAutoSelectDecision.Finish
        val pool = snapshot.preferred.filter { it.result.mediaId !in blacklist }
        if (pool.isEmpty()) return WebAutoSelectDecision.Finish // 与 trySelectDefault 一致: 没有候选时不等 context
        if (!contextLoaded) return WebAutoSelectDecision.Wait
        return findMediaByPreference(pool, snapshot.mergedPreference, snapshot.availableAlliances, snapshot.context, snapshot.settings)
            ?.let { WebAutoSelectDecision.Select(it, "default after all web sources completed") }
            ?: WebAutoSelectDecision.Finish
    }

    // 5.
    return WebAutoSelectDecision.Wait
}

/**
 * 按阶段把 [eligible] 切成依次尝试的组. 组的顺序就是优先级; 空组已被剔除.
 */
private fun groupsFor(
    stage: WebAutoSelectStage,
    eligible: List<MaybeExcludedMedia.Included>,
    config: WebAutoSelectConfig,
): List<List<MaybeExcludedMedia.Included>> {
    fun MaybeExcludedMedia.Included.isExact() = metadata.subjectMatchKind == MatchMetadata.SubjectMatchKind.EXACT
    fun MaybeExcludedMedia.Included.tier() = config.sourceTiers.get(result.mediaSourceId, result.properties.alliance)

    fun byTier(list: List<MaybeExcludedMedia.Included>): List<List<MaybeExcludedMedia.Included>> =
        list.groupBy { it.tier() }.entries.sortedBy { it.key }.map { it.value }

    return when (stage) {
        WebAutoSelectStage.PREFERRED_SOURCE -> error("PREFERRED_SOURCE is handled before grouping")

        WebAutoSelectStage.INSTANT ->
            listOf(eligible.filter { it.isExact() && it.tier() <= config.instantSelectTierThreshold })
                .filter { it.isNotEmpty() }

        WebAutoSelectStage.EXACT_ONLY -> byTier(eligible.filter { it.isExact() })

        WebAutoSelectStage.FUZZY -> byTier(eligible.filter { it.isExact() }) + byTier(eligible.filter { !it.isExact() })
    }
}
