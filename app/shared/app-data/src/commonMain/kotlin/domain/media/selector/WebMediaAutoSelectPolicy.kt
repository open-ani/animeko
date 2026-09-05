/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import me.him188.ani.app.data.models.preference.MediaPreference.Companion.ANY_FILTER
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.fetch.isFinal
import me.him188.ani.app.domain.media.selector.MatchMetadata.SubjectMatchKind
import me.him188.ani.app.domain.mediasource.codec.MediaSourceTier
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal data class WebAutoSelectConfig(
    val sourceTiers: MediaSelectorSourceTiers,
    val preferredSourceId: String? = null,
    val selectCache: Boolean = false,
    val fastSelect: Boolean = true,
    val exactMatchAfter: Duration = 5.seconds,
    val fuzzyMatchAfter: Duration = 15.seconds,
    val instantTier: MediaSourceTier = MediaSourceTier(0u),
    val blacklist: Set<String> = emptySet(),
    val waitForPendingSources: Boolean = true,
) {
    init {
        require(exactMatchAfter >= Duration.ZERO)
        require(fuzzyMatchAfter >= Duration.ZERO)
    }
}

internal enum class WebAutoSelectStage { PreferredSource, Instant, Exact, Fuzzy }

internal sealed interface WebAutoSelectDecision {
    data object Wait : WebAutoSelectDecision
    data object StartFallback : WebAutoSelectDecision
    data object Exhausted : WebAutoSelectDecision
    data class Select(val media: Media, val reason: String) : WebAutoSelectDecision
}

/** Pure policy. Deadlines only widen eligibility; source completion never bypasses a deadline. */
internal fun decideWebAutoSelect(
    snapshot: MediaAutoSelectSnapshot,
    config: WebAutoSelectConfig,
    stage: WebAutoSelectStage,
): WebAutoSelectDecision {
    val webSources = snapshot.sources.filter { it.kind == MediaSourceKind.WEB }
    val preferredSource = webSources.firstOrNull { it.mediaSourceId == config.preferredSourceId }
    val candidates = snapshot.candidates.filter { it.result.mediaId !in config.blacklist }
    val preferred = snapshot.preferred.filter { it.result.mediaId !in config.blacklist }

    fun find(list: List<MaybeExcludedMedia.Included>, relax: Boolean = false): Media? = findMediaByPreference(
        list,
        if (relax) snapshot.preference.copy(
            alliance = ANY_FILTER, resolution = ANY_FILTER, subtitleLanguageId = ANY_FILTER, mediaSourceId = ANY_FILTER,
        ) else snapshot.preference.copy(alliance = ANY_FILTER),
        snapshot.availableAlliances, snapshot.context, snapshot.settings,
    )

    if (stage == WebAutoSelectStage.PreferredSource && preferredSource?.state?.isFinal == true &&
        snapshot.context.allFieldsLoaded()
    ) {
        find(preferred.filter { it.result.mediaSourceId == preferredSource.mediaSourceId })?.let {
            return WebAutoSelectDecision.Select(it, "preferred source")
        }
    }

    // Cache can win while the remembered source is still pending, as before.
    if (config.selectCache) {
        (preferred.firstOrNull { it.result.kind == MediaSourceKind.LocalCache }
            ?: candidates.firstOrNull { it.result.kind == MediaSourceKind.LocalCache })?.let {
            return WebAutoSelectDecision.Select(it.result, "local cache")
        }
    }

    if (stage == WebAutoSelectStage.PreferredSource) {
        if (preferredSource != null && !preferredSource.state.isFinal) return WebAutoSelectDecision.Wait
        if (preferredSource != null && preferredSource.results.isNotEmpty() &&
            !snapshot.context.allFieldsLoaded()
        ) return WebAutoSelectDecision.Wait
        return WebAutoSelectDecision.StartFallback
    }

    val allCompleted = webSources.all { it.state.isFinal }
    val allRelevantCompleted = allCompleted && (!config.selectCache || snapshot.sources
        .filter { it.kind == MediaSourceKind.LocalCache }.all { it.state.isFinal })
    if (!config.fastSelect && !allCompleted) return WebAutoSelectDecision.Wait
    val succeededIds = webSources.filter { it.state is MediaSourceFetchState.Succeed }.map { it.mediaSourceId }.toSet()
    val webCandidates = candidates.filter { it.result.kind == MediaSourceKind.WEB && it.result.mediaSourceId in succeededIds }

    if (webSources.any { it.results.isNotEmpty() } && !snapshot.context.allFieldsLoaded()) return WebAutoSelectDecision.Wait

    val eligible = webCandidates.filter { candidate ->
        val exact = candidate.metadata.subjectMatchKind == SubjectMatchKind.EXACT
        when (stage) {
            WebAutoSelectStage.Instant -> exact && config.sourceTiers.get(
                candidate.result.mediaSourceId, candidate.result.properties.alliance,
            ) <= config.instantTier
            WebAutoSelectStage.Exact -> exact
            WebAutoSelectStage.Fuzzy -> true
            WebAutoSelectStage.PreferredSource -> error("Handled above")
        }
    }
    // Only apply preferences within the best match/tier group. Never reorder by source-list index.
    val groups = eligible.groupBy {
        Pair(it.metadata.subjectMatchKind != SubjectMatchKind.EXACT,
            config.sourceTiers.get(it.result.mediaSourceId, it.result.properties.alliance))
    }.toList().sortedWith(compareBy({ it.first.first }, { it.first.second }))
    val preferredIds = preferred.map { it.result.mediaId }.toSet()
    for ((_, group) in groups) {
        val selected = find(group.filter { it.result.mediaId in preferredIds }) ?: find(group, relax = true)
        if (selected != null) {
            val candidate = group.first { it.result == selected }
            return WebAutoSelectDecision.Select(
                selected,
                "${stage.name}, match=${candidate.metadata.subjectMatchKind}, tier=${config.sourceTiers.get(selected.mediaSourceId, selected.properties.alliance)}",
            )
        }
    }

    // Empty final result sets can finish early. Non-empty candidates belonging to a later phase must wait.
    if (allRelevantCompleted && webCandidates.isEmpty()) return WebAutoSelectDecision.Exhausted
    if (stage == WebAutoSelectStage.Fuzzy && (allRelevantCompleted || !config.waitForPendingSources)) {
        return WebAutoSelectDecision.Exhausted
    }
    return WebAutoSelectDecision.Wait
}
