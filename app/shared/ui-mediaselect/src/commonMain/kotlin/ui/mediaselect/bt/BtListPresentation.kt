/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.bt

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.app.domain.media.selector.UnsafeOriginalMediaAccess
import me.him188.ani.app.domain.media.selector.filter.MediaSelectorFilterSortAlgorithm
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind

/**
 * BT 页会话内 UI 状态 (不持久化). 由 EpisodeViewModel 持有一份, 传入每次重建的 [me.him188.ani.app.ui.mediafetch.MediaSelectorState].
 */
@Stable
class BtFilterState {
    /**
     * 「第 N 话」chip. true = 基于 filteredCandidates; false = 基于 subjectCandidates.
     */
    val episodeFilterEnabled: MutableStateFlow<Boolean> = MutableStateFlow(true)

    /**
     * 「Mikan ▾」纯 UI 过滤的 mediaSourceId; null = 全部数据源 (不写偏好).
     * 指向的源从会话消失时由页面重置为 null (见 [BtResourcesPage]).
     */
    val sourceFilter: MutableStateFlow<String?> = MutableStateFlow(null)
}

/**
 * BT 页的原始输入. 不做 kind 过滤与偏好过滤, 由 [projectBtList] 投影.
 */
@Immutable
data class BtCandidates(
    /**
     * `MediaSelector.filteredCandidates`: 应用第 0 条规则, 含 `Excluded(EpisodeMismatch)` 项.
     */
    val filtered: List<MaybeExcludedMedia>,
    /**
     * `MediaSelector.subjectCandidates`: 不应用第 0 条规则.
     */
    val subject: List<MaybeExcludedMedia>,
    /**
     * `MediaPreference.Empty.copy(alliance/resolution/subtitleLanguageId/mediaSourceId = 各自 finalSelected)`.
     */
    val preference: MediaPreference,
    val selected: Media?,
)

sealed interface BtRowExclusion {
    /**
     * 来自 MediaSelector 的排除原因, 含 EpisodeMismatch (标签「集数不符」).
     */
    data class Reason(val reason: MediaExclusionReason) : BtRowExclusion

    /**
     * Included 但不在四维偏好筛选结果里 (标签「低于偏好」).
     */
    data object BelowPreference : BtRowExclusion
}

/**
 * 一行. [media] 是 MaybeExcludedMedia.original (opt-in 在 [projectBtList] 内完成).
 */
@Immutable
data class BtRow(
    val media: Media,
    val exclusion: BtRowExclusion?,
    val isSelected: Boolean,
) {
    val id: String get() = media.mediaId
    val isExcluded: Boolean get() = exclusion != null

    /**
     * kind == LocalCache → 标签行首「已缓存」(现有 key subject_episode_cached).
     */
    val isCached: Boolean get() = media.kind == MediaSourceKind.LocalCache
}

@Immutable
data class BtListPresentation(
    /**
     * 主列表: Included 且偏好内, 已按 sourceFilter 过滤, 保持基础列表顺序.
     */
    val included: List<BtRow>,
    /**
     * 「显示已被排除的 N 条」展开后追加: Excluded (所有原因) + 低于偏好, 已按 sourceFilter 过滤, 保持基础列表顺序.
     */
    val excluded: List<BtRow>,
    val selected: Media?,
    val episodeFilterEnabled: Boolean,
    val sourceFilter: String?,
    /**
     * 基础列表 (kind 过滤后, 偏好过滤前, 源过滤前) 按 mediaSourceId 分组的条数; 面板里没有的源显示 0.
     */
    val sourceCounts: Map<String, Int>,
    /**
     * 基础列表总条数 = 「全部数据源 N 条」.
     */
    val totalCount: Int,
    /**
     * 三项 finalSelected (来自 [BtCandidates.preference]); 「筛选 (n)」的 n = 非空个数.
     */
    val resolution: String?,
    val subtitleLanguageId: String?,
    val alliance: String?,
    /**
     * 从基础列表自算的候选值, 去重保序; 不用 MediaPreferenceItem.available.
     */
    val availableResolutions: List<String>,
    val availableSubtitleLanguageIds: List<String>,
    val availableAlliances: List<String>,
    val isPlaceholder: Boolean = false,
) {
    val activeFilterCount: Int get() = listOfNotNull(resolution, subtitleLanguageId, alliance).size

    companion object {
        val Placeholder: BtListPresentation = BtListPresentation(
            included = emptyList(),
            excluded = emptyList(),
            selected = null,
            episodeFilterEnabled = true,
            sourceFilter = null,
            sourceCounts = emptyMap(),
            totalCount = 0,
            resolution = null,
            subtitleLanguageId = null,
            alliance = null,
            availableResolutions = emptyList(),
            availableSubtitleLanguageIds = emptyList(),
            availableAlliances = emptyList(),
            isPlaceholder = true,
        )
    }
}

/**
 * 纯函数:
 * base = (episodeFilterEnabled ? candidates.filtered : candidates.subject).filter { original.kind == BitTorrent || original.kind == LocalCache }
 * preferredIds = algorithm.filterByPreference(base, candidates.preference).filterIsInstance<Included>().mapTo(HashSet()) { it.result.mediaId }
 * 每项: Excluded → Reason(exclusionReason); 否则 mediaId !in preferredIds → BelowPreference; 否则主列表. isSelected = original == candidates.selected.
 * sourceFilter != null 时 included / excluded 只保留 media.mediaSourceId == sourceFilter; sourceCounts / totalCount / available* 不受 sourceFilter 影响.
 * available*: resolution 非空 distinct / subtitleLanguageIds flatten distinct / alliance 非空 distinct, 按 base 顺序.
 */
@OptIn(UnsafeOriginalMediaAccess::class)
fun projectBtList(
    candidates: BtCandidates,
    episodeFilterEnabled: Boolean,
    sourceFilter: String?,
    algorithm: MediaSelectorFilterSortAlgorithm = MediaSelectorFilterSortAlgorithm(),
): BtListPresentation {
    val base = (if (episodeFilterEnabled) candidates.filtered else candidates.subject).filter {
        val kind = it.original.kind
        kind == MediaSourceKind.BitTorrent || kind == MediaSourceKind.LocalCache
    }
    val preferredIds = algorithm.filterByPreference(base, candidates.preference)
        .filterIsInstance<MaybeExcludedMedia.Included>()
        .mapTo(HashSet()) { it.result.mediaId }

    val included = ArrayList<BtRow>()
    val excluded = ArrayList<BtRow>()
    for (item in base) {
        val media = item.original
        if (sourceFilter != null && media.mediaSourceId != sourceFilter) continue
        val exclusion = when (item) {
            is MaybeExcludedMedia.Excluded -> BtRowExclusion.Reason(item.exclusionReason)
            is MaybeExcludedMedia.Included -> if (media.mediaId in preferredIds) null else BtRowExclusion.BelowPreference
        }
        val row = BtRow(media, exclusion, isSelected = media == candidates.selected)
        if (exclusion == null) included.add(row) else excluded.add(row)
    }

    val sourceCounts = LinkedHashMap<String, Int>()
    val resolutions = LinkedHashSet<String>()
    val subtitleLanguageIds = LinkedHashSet<String>()
    val alliances = LinkedHashSet<String>()
    for (item in base) {
        val media = item.original
        sourceCounts[media.mediaSourceId] = (sourceCounts[media.mediaSourceId] ?: 0) + 1
        media.properties.resolution.takeIf { it.isNotBlank() }?.let { resolutions.add(it) }
        subtitleLanguageIds.addAll(media.properties.subtitleLanguageIds)
        media.properties.alliance.takeIf { it.isNotBlank() }?.let { alliances.add(it) }
    }

    return BtListPresentation(
        included = included,
        excluded = excluded,
        selected = candidates.selected,
        episodeFilterEnabled = episodeFilterEnabled,
        sourceFilter = sourceFilter,
        sourceCounts = sourceCounts,
        totalCount = base.size,
        resolution = candidates.preference.resolution,
        subtitleLanguageId = candidates.preference.subtitleLanguageId,
        alliance = candidates.preference.alliance,
        availableResolutions = resolutions.toList(),
        availableSubtitleLanguageIds = subtitleLanguageIds.toList(),
        availableAlliances = alliances.toList(),
        isPlaceholder = false,
    )
}
