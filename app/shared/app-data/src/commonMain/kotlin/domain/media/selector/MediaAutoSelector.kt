/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.MediaPreference.Companion.ANY_FILTER
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.fetch.isFinal
import me.him188.ani.app.domain.media.selector.MatchMetadata.SubjectMatchKind
import me.him188.ani.app.domain.mediasource.codec.MediaSourceTier
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Owns one automatic selection: source subscriptions, deadlines, decisions and the final write.
 * Startup and player-error replacement share this entry point. Each call has its own lifecycle.
 */
internal class MediaAutoSelector(private val mediaSelector: MediaSelector) {
    data class Config(
        val preferredSourceId: String? = null,
        /** Select available local caches first; otherwise wait for their lookups before considering network media. */
        val selectCache: Boolean = true,
        val blacklist: Set<String> = emptySet(),
        /** Null waits for the preferred source kind to complete, using the existing BT preference rules. */
        val web: Web? = null,
        /** Startup may fall back to another kind; player-error replacement stays within WEB. */
        val fallbackToOtherKinds: Boolean = false,
    )

    /** Web deadlines start only after the remembered source has finished without a selection. */
    data class Web(
        val sourceTiers: MediaSelectorSourceTiers,
        val fastSelect: Boolean = true,
        val exactMatchAfter: Duration = 5.seconds,
        val fuzzyMatchAfter: Duration = 15.seconds,
        val instantTier: MediaSourceTier = MediaSourceTier(0u),
        val waitForPendingSources: Boolean = true,
    ) {
        init {
            require(exactMatchAfter >= Duration.ZERO)
            require(fuzzyMatchAfter >= Duration.ZERO)
        }
    }

    /** A selection made while suspended ends this call; only [expectedSelection] may be replaced. */
    suspend fun select(
        session: MediaFetchSession,
        config: Config = Config(),
        expectedSelection: Media? = null,
    ): Media? = coroutineScope {
        if (mediaSelector.selected.value != expectedSelection) return@coroutineScope null
        val stage = MutableStateFlow(Stage.PreferredSource)
        var result: Media? = null
        var deadlines: Job? = null
        try {
            combine(
                mediaSelector.autoSelectSnapshots(sourceSnapshots(session)), stage, mediaSelector.selected,
            ) { snapshot, phase, currentSelection ->
                if (currentSelection != expectedSelection) Decision.Exhausted
                else decide(snapshot, config, phase)
            }.first { decision ->
                when (decision) {
                    Decision.StartFallback -> {
                        if (stage.value == Stage.PreferredSource) {
                            val web = checkNotNull(config.web)
                            stage.value = if (web.fastSelect) Stage.Instant else Stage.Exact
                            deadlines = launch {
                                val exactAfter = if (web.fastSelect) web.exactMatchAfter else Duration.ZERO
                                delay(exactAfter)
                                stage.value = Stage.Exact
                                delay((web.fuzzyMatchAfter - exactAfter).coerceAtLeast(Duration.ZERO))
                                stage.value = Stage.Fuzzy
                            }
                        }
                        false
                    }
                    Decision.Wait -> false
                    Decision.Exhausted -> true
                    is Decision.Select -> {
                        logger.info {
                            "Auto select: reason=${decision.reason}, source=${decision.media.mediaSourceId}, media=${decision.media.mediaId}"
                        }
                        result = mediaSelector.selectAutomatically(decision.media, expectedSelection)
                        true
                    }
                }
            }
        } finally {
            deadlines?.cancel()
        }
        result
    }

    private fun sourceSnapshots(session: MediaFetchSession): Flow<List<MediaSourceSelectionSnapshot>> {
        if (session.mediaSourceResults.isEmpty()) return flowOf(emptyList())
        return combine(session.mediaSourceResults.map { source ->
            // Keep a stable subscription to results: these subscriptions also drive lazy source queries.
            combine(source.state, source.results) { state, results ->
                // combine can observe a terminal state before delivering the corresponding results event.
                // Fetcher publishes terminal state after results have entered its replay cache, so read
                // that cache while our stable subscription is still alive. Never pair Succeed with an
                // older result list (including an empty list).
                val currentResults = if (state.isFinal) source.results.first() else results
                val currentState = source.state.value
                MediaSourceSelectionSnapshot(
                    source.mediaSourceId, source.kind,
                    if (currentState == state) state else MediaSourceFetchState.Working,
                    currentResults,
                )
            }
        }) { it.toList() }
    }

    private enum class Stage { PreferredSource, Instant, Exact, Fuzzy }

    private sealed interface Decision {
        data object Wait : Decision
        data object StartFallback : Decision
        data object Exhausted : Decision
        data class Select(val media: Media, val reason: String) : Decision
    }

    /** Pure decision over a coherent snapshot. Only [select] advances time or writes selection. */
    private fun decide(snapshot: MediaAutoSelectSnapshot, config: Config, stage: Stage): Decision {
        val preferredSource = snapshot.sources.firstOrNull {
            it.kind == MediaSourceKind.WEB && it.mediaSourceId == config.preferredSourceId
        }
        val candidates = snapshot.candidates.filter { it.result.mediaId !in config.blacklist }
        val preferred = snapshot.preferred.filter { it.result.mediaId !in config.blacklist }

        // A ready cache wins immediately, even over a completed remembered WEB source.
        if (config.selectCache) {
            (preferred.firstOrNull { it.result.kind == MediaSourceKind.LocalCache }
                ?: candidates.firstOrNull { it.result.kind == MediaSourceKind.LocalCache })?.let {
                return Decision.Select(it.result, "local cache")
            }
            if (snapshot.sources.any { it.kind == MediaSourceKind.LocalCache && !it.state.isFinal }) {
                return Decision.Wait
            }
        }

        if (stage == Stage.PreferredSource && preferredSource?.state?.isFinal == true &&
            snapshot.context.allFieldsLoaded()
        ) {
            findWebCandidate(snapshot, preferred.filter { it.result.mediaSourceId == preferredSource.mediaSourceId })?.let {
                return Decision.Select(it, "preferred source")
            }
        }

        if (config.web == null) return decideOnCompletion(snapshot, preferred)
        if (stage == Stage.PreferredSource) {
            if (preferredSource != null && !preferredSource.state.isFinal) return Decision.Wait
            if (preferredSource != null && preferredSource.results.isNotEmpty() &&
                !snapshot.context.allFieldsLoaded()
            ) return Decision.Wait
            return Decision.StartFallback
        }
        return decideWeb(snapshot, config, stage, candidates, preferred)
    }

    private fun decideOnCompletion(
        snapshot: MediaAutoSelectSnapshot,
        preferred: List<MaybeExcludedMedia.Included>,
    ): Decision {
        // With no enabled sources of the preferred kind, wait for every source (CompletedConditions semantics).
        val preferredSources = snapshot.sources.filter {
            it.kind == snapshot.settings.preferKind && it.state !is MediaSourceFetchState.Disabled
        }
        val waitingFor = preferredSources.ifEmpty { snapshot.sources }
        if (waitingFor.any { !it.state.isFinal }) return Decision.Wait
        if (preferred.isEmpty()) return Decision.Exhausted
        if (!snapshot.context.allFieldsLoaded()) return Decision.Wait
        val media = MediaSelectionDecider.findByPreference(
            preferred, snapshot.preference, snapshot.availableAlliances, snapshot.context, snapshot.settings,
        ) ?: return Decision.Exhausted
        return Decision.Select(media, "preferred kind completed")
    }

    private fun decideWeb(
        snapshot: MediaAutoSelectSnapshot,
        config: Config,
        stage: Stage,
        candidates: List<MaybeExcludedMedia.Included>,
        preferred: List<MaybeExcludedMedia.Included>,
    ): Decision {
        val web = checkNotNull(config.web)
        val webSources = snapshot.sources.filter { it.kind == MediaSourceKind.WEB }
        val allCompleted = webSources.all { it.state.isFinal }
        if (!web.fastSelect && !allCompleted) return Decision.Wait
        val succeededIds = webSources.filter { it.state is MediaSourceFetchState.Succeed }.map { it.mediaSourceId }.toSet()
        val webCandidates = candidates.filter { it.result.kind == MediaSourceKind.WEB && it.result.mediaSourceId in succeededIds }
        if (webSources.any { it.results.isNotEmpty() } && !snapshot.context.allFieldsLoaded()) return Decision.Wait

        val eligible = webCandidates.filter { candidate ->
            val exact = candidate.metadata.subjectMatchKind == SubjectMatchKind.EXACT
            when (stage) {
                Stage.Instant -> exact && web.sourceTiers.get(
                    candidate.result.mediaSourceId, candidate.result.properties.alliance,
                ) <= web.instantTier
                Stage.Exact -> exact
                Stage.Fuzzy -> true
                Stage.PreferredSource -> error("Handled before Web fallback")
            }
        }
        // Preferences apply within each match/tier group, never ahead of match quality or tier.
        val groups = eligible.groupBy {
            Pair(
                it.metadata.subjectMatchKind != SubjectMatchKind.EXACT,
                web.sourceTiers.get(it.result.mediaSourceId, it.result.properties.alliance),
            )
        }.toList().sortedWith(compareBy({ it.first.first }, { it.first.second }))
        val preferredIds = preferred.map { it.result.mediaId }.toSet()
        for ((_, group) in groups) {
            val media = findWebCandidate(snapshot, group.filter { it.result.mediaId in preferredIds })
                ?: findWebCandidate(snapshot, group, relax = true)
            if (media != null) {
                val candidate = group.first { it.result == media }
                return Decision.Select(
                    media,
                    "${stage.name}, match=${candidate.metadata.subjectMatchKind}, tier=${web.sourceTiers.get(media.mediaSourceId, media.properties.alliance)}",
                )
            }
        }

        // Empty final results can finish early; candidates belonging to a later phase must wait.
        if (allCompleted && webCandidates.isEmpty()) {
            return if (config.fallbackToOtherKinds) {
                decideOnCompletion(snapshot, preferred.filter { it.result.kind != MediaSourceKind.WEB })
            } else Decision.Exhausted
        }
        if (stage == Stage.Fuzzy && (allCompleted || !web.waitForPendingSources)) return Decision.Exhausted
        return Decision.Wait
    }

    private fun findWebCandidate(
        snapshot: MediaAutoSelectSnapshot,
        candidates: List<MaybeExcludedMedia.Included>,
        relax: Boolean = false,
    ): Media? = MediaSelectionDecider.findByPreference(
        candidates,
        if (relax) snapshot.preference.copy(
            alliance = ANY_FILTER, resolution = ANY_FILTER, subtitleLanguageId = ANY_FILTER, mediaSourceId = ANY_FILTER,
        ) else snapshot.preference.copy(alliance = ANY_FILTER),
        snapshot.availableAlliances, snapshot.context, snapshot.settings,
    )

    private companion object {
        val logger = logger<MediaAutoSelector>()
    }
}
