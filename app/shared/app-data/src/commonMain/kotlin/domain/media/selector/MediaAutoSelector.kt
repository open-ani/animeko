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
internal class MediaAutoSelector(
    private val mediaSelector: MediaSelector,
) {
    data class Config(
        val preferredSourceId: String? = null,
        /** [SelectEvent.trigger] of the selection this call makes. */
        val trigger: SelectTrigger = SelectTrigger.AUTO_STARTUP,
        /** Select available local caches first; otherwise wait for their lookups before considering network media. */
        val selectCache: Boolean = true,
        val blacklist: Set<String> = emptySet(),
        /** Null waits for the preferred source kind to complete, using the existing BT preference rules. */
        val web: Web? = null,
        /** Startup may fall back to another kind; player-error replacement stays within WEB. */
        val fallbackToOtherKinds: Boolean = false,
    )

    /**
     * Web deadlines start only after the remembered source has finished without a selection.
     * Deadlines only wait for pending sources: once every Web source has settled, the selection is the one the
     * last reachable stage would make. An infinite deadline is never reached, so its stages stay closed.
     */
    data class Web(
        val sourceTiers: MediaSelectorSourceTiers,
        val fastSelect: Boolean = true,
        val exactMatchAfter: Duration = 5.seconds,
        val fuzzyMatchAfter: Duration = 15.seconds,
        val instantTier: MediaSourceTier = DefaultInstantTier,
        val waitForPendingSources: Boolean = true,
    ) {
        init {
            require(exactMatchAfter >= Duration.ZERO)
            require(fuzzyMatchAfter >= Duration.ZERO)
        }

        companion object {
            /** First-deadline stage accepts exact matches whose effective tier is at most this. */
            val DefaultInstantTier = MediaSourceTier(0u)
        }
    }

    /**
     * A selection made while suspended ends this call; only [expectedSelection] may be replaced.
     *
     * Returns what happened: the written selection with its reason, or why nothing was selected.
     */
    suspend fun select(
        session: MediaFetchSession,
        config: Config = Config(),
        expectedSelection: Media? = null,
    ): MediaAutoSelectOutcome = coroutineScope {
        if (mediaSelector.selected.value != expectedSelection) {
            return@coroutineScope MediaAutoSelectOutcome.Exhausted(MediaAutoSelectExhaustCause.PRECONDITION_SELECTED)
        }
        val stage = MutableStateFlow(MediaAutoSelectStage.PREFERRED_SOURCE)
        var outcome: MediaAutoSelectOutcome = MediaAutoSelectOutcome.Cancelled
        var deadlines: Job? = null
        try {
            combine(
                mediaSelector.autoSelectSnapshots(sourceSnapshots(session)), stage, mediaSelector.selected,
            ) { snapshot, phase, currentSelection ->
                if (currentSelection != expectedSelection) Decision.Exhausted(MediaAutoSelectExhaustCause.PREEMPTED)
                else decide(snapshot, config, phase)
            }.first { decision ->
                when (decision) {
                    Decision.StartFallback -> {
                        if (stage.value == MediaAutoSelectStage.PREFERRED_SOURCE) {
                            val web = checkNotNull(config.web)
                            stage.value = if (web.fastSelect) MediaAutoSelectStage.INSTANT else MediaAutoSelectStage.EXACT
                            deadlines = launch {
                                delay(web.exactStageAfter)
                                stage.value = MediaAutoSelectStage.EXACT
                                delay((web.fuzzyMatchAfter - web.exactStageAfter).coerceAtLeast(Duration.ZERO))
                                stage.value = MediaAutoSelectStage.FUZZY
                            }
                        }
                        false
                    }
                    Decision.Wait -> false
                    is Decision.Exhausted -> {
                        outcome = MediaAutoSelectOutcome.Exhausted(decision.cause)
                        true
                    }
                    is Decision.Select -> {
                        logger.info {
                            "Auto select: reason=${decision.reason}, stage=${stage.value}, source=${decision.media.mediaSourceId}, media=${decision.media.mediaId}"
                        }
                        val written = mediaSelector.selectAutomatically(decision.media, expectedSelection, config.trigger)
                        outcome = if (written != null) {
                            MediaAutoSelectOutcome.Selected(
                                decision.media, decision.reason, stage.value, decision.matchKind, decision.tier,
                            )
                        } else {
                            MediaAutoSelectOutcome.Rejected(decision.media, decision.reason)
                        }
                        true
                    }
                }
            }
        } finally {
            deadlines?.cancel()
        }
        outcome
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

    private sealed interface Decision {
        data object Wait : Decision
        data object StartFallback : Decision
        data class Exhausted(val cause: MediaAutoSelectExhaustCause) : Decision
        data class Select(
            val media: Media,
            val reason: MediaAutoSelectReason,
            val matchKind: SubjectMatchKind? = null,
            val tier: MediaSourceTier? = null,
        ) : Decision
    }

    /** Pure decision over a coherent snapshot. Only [select] advances time or writes selection. */
    private fun decide(snapshot: MediaAutoSelectSnapshot, config: Config, stage: MediaAutoSelectStage): Decision {
        val preferredSource = snapshot.sources.firstOrNull {
            it.kind == MediaSourceKind.WEB && it.mediaSourceId == config.preferredSourceId
        }
        val candidates = snapshot.candidates.filter { it.result.mediaId !in config.blacklist }
        val preferred = snapshot.preferred.filter { it.result.mediaId !in config.blacklist }

        // A ready cache wins immediately, even over a completed remembered WEB source.
        if (config.selectCache) {
            // Caches of the whole subject are candidates until the episode is known, so wait for it.
            if (!snapshot.context.hasEpisode) return Decision.Wait
            (preferred.firstOrNull { it.result.kind == MediaSourceKind.LocalCache }
                ?: candidates.firstOrNull { it.result.kind == MediaSourceKind.LocalCache })?.let {
                return Decision.Select(it.result, MediaAutoSelectReason.LOCAL_CACHE)
            }
            if (snapshot.sources.any { it.kind == MediaSourceKind.LocalCache && !it.state.isFinal }) {
                return Decision.Wait
            }
        }

        if (stage == MediaAutoSelectStage.PREFERRED_SOURCE && preferredSource?.state?.isFinal == true &&
            snapshot.context.allFieldsLoaded()
        ) {
            findWebCandidate(snapshot, preferred.filter { it.result.mediaSourceId == preferredSource.mediaSourceId })?.let {
                return Decision.Select(it, MediaAutoSelectReason.PREFERRED_SOURCE)
            }
        }

        if (config.web == null) {
            return decideOnCompletion(snapshot, preferred, MediaAutoSelectReason.PREFERRED_KIND_COMPLETED)
        }
        if (stage == MediaAutoSelectStage.PREFERRED_SOURCE) {
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
        reason: MediaAutoSelectReason,
    ): Decision {
        // With no enabled sources of the preferred kind, wait for every source (CompletedConditions semantics).
        val preferredSources = snapshot.sources.filter {
            it.kind == snapshot.settings.preferKind && it.state !is MediaSourceFetchState.Disabled
        }
        val waitingFor = preferredSources.ifEmpty { snapshot.sources }
        if (waitingFor.any { !it.state.isFinal }) return Decision.Wait
        if (preferred.isEmpty()) return Decision.Exhausted(MediaAutoSelectExhaustCause.NO_PREFERRED_CANDIDATES)
        if (!snapshot.context.allFieldsLoaded()) return Decision.Wait
        val media = MediaSelectionDecider.findByPreference(
            preferred, snapshot.preference, snapshot.availableAlliances, snapshot.context, snapshot.settings,
        ) ?: return Decision.Exhausted(MediaAutoSelectExhaustCause.NO_PREFERENCE_MATCH)
        return Decision.Select(media, reason)
    }

    private fun decideWeb(
        snapshot: MediaAutoSelectSnapshot,
        config: Config,
        stage: MediaAutoSelectStage,
        candidates: List<MaybeExcludedMedia.Included>,
        preferred: List<MaybeExcludedMedia.Included>,
    ): Decision {
        val web = checkNotNull(config.web)
        val webSources = snapshot.sources.filter { it.kind == MediaSourceKind.WEB }
        val allCompleted = webSources.all { it.state.isFinal }
        if (!web.fastSelect && !allCompleted) return Decision.Wait
        // Settled sources produce no new candidates, so waiting for the remaining deadlines cannot change the choice.
        val effectiveStage = if (webSources.all { it.state.isSettled }) web.lastStage else stage
        val succeededIds = webSources.filter { it.state is MediaSourceFetchState.Succeed }.map { it.mediaSourceId }.toSet()
        val webCandidates = candidates.filter { it.result.kind == MediaSourceKind.WEB && it.result.mediaSourceId in succeededIds }
        if (webSources.any { it.results.isNotEmpty() } && !snapshot.context.allFieldsLoaded()) return Decision.Wait

        val eligible = webCandidates.filter { candidate ->
            val exact = candidate.metadata.subjectMatchKind == SubjectMatchKind.EXACT
            when (effectiveStage) {
                MediaAutoSelectStage.INSTANT -> exact && web.sourceTiers.get(
                    candidate.result.mediaSourceId, candidate.result.properties.alliance,
                ) <= web.instantTier
                MediaAutoSelectStage.EXACT -> exact
                MediaAutoSelectStage.FUZZY -> true
                MediaAutoSelectStage.PREFERRED_SOURCE -> error("Handled before Web fallback")
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
                    when (effectiveStage) {
                        MediaAutoSelectStage.INSTANT -> MediaAutoSelectReason.WEB_INSTANT
                        MediaAutoSelectStage.EXACT -> MediaAutoSelectReason.WEB_EXACT
                        MediaAutoSelectStage.FUZZY -> MediaAutoSelectReason.WEB_FUZZY
                        MediaAutoSelectStage.PREFERRED_SOURCE -> error("Handled before Web fallback")
                    },
                    candidate.metadata.subjectMatchKind,
                    web.sourceTiers.get(media.mediaSourceId, media.properties.alliance),
                )
            }
        }

        // Empty final results can finish early; candidates belonging to a later phase must wait.
        if (allCompleted && webCandidates.isEmpty()) {
            return if (config.fallbackToOtherKinds) {
                decideOnCompletion(
                    snapshot, preferred.filter { it.result.kind != MediaSourceKind.WEB }, MediaAutoSelectReason.BT_FALLBACK,
                )
            } else Decision.Exhausted(MediaAutoSelectExhaustCause.NO_WEB_CANDIDATES)
        }
        if (effectiveStage == MediaAutoSelectStage.FUZZY && (allCompleted || !web.waitForPendingSources)) {
            return Decision.Exhausted(MediaAutoSelectExhaustCause.FUZZY_DEADLINE)
        }
        return Decision.Wait
    }

    /** Fast select disabled skips the instant stage. */
    private val Web.exactStageAfter: Duration get() = if (fastSelect) exactMatchAfter else Duration.ZERO

    /** The last stage the deadline timer reaches; it stays behind an infinite deadline. */
    private val Web.lastStage: MediaAutoSelectStage
        get() = when {
            exactStageAfter.isInfinite() -> MediaAutoSelectStage.INSTANT
            fuzzyMatchAfter.isInfinite() -> MediaAutoSelectStage.EXACT
            else -> MediaAutoSelectStage.FUZZY
        }

    /**
     * Final with no automatic retry pending. A rate-limited source restarts itself after its cooldown and may
     * still return results, so it keeps the deadlines in effect like a running source.
     */
    private val MediaSourceFetchState.isSettled: Boolean
        get() = isFinal && this !is MediaSourceFetchState.RateLimited

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

/**
 * 自动选择的阶段. 记忆的 Web 源完成前为 [PREFERRED_SOURCE]; 之后按 Web 的两个截止时间依次放宽到
 * [INSTANT] (精确匹配且阶级不超过 [MediaAutoSelector.Web.instantTier]), [EXACT] (任意阶级的精确匹配), [FUZZY] (允许模糊匹配).
 */
enum class MediaAutoSelectStage { PREFERRED_SOURCE, INSTANT, EXACT, FUZZY }

/**
 * 自动选择选中某个资源的依据.
 */
enum class MediaAutoSelectReason {
    /** 本集已有本地缓存. */
    LOCAL_CACHE,

    /** 记忆的 Web 源返回了可用资源. */
    PREFERRED_SOURCE,

    /** 偏好类型 (非 Web) 的数据源全部完成后按偏好选出. */
    PREFERRED_KIND_COMPLETED,

    /** Web 第一阶段: 精确匹配且阶级足够高. */
    WEB_INSTANT,

    /** Web 第二阶段: 精确匹配. */
    WEB_EXACT,

    /** Web 第三阶段: 允许模糊匹配. */
    WEB_FUZZY,

    /** Web 源全部完成且没有 Web 候选, 回退到其他类型. */
    BT_FALLBACK,
}

/**
 * 自动选择没有选出资源的原因.
 */
enum class MediaAutoSelectExhaustCause {
    /** 调用时已经有选择. */
    PRECONDITION_SELECTED,

    /** 等待期间选择被别人 (通常是用户) 改变. */
    PREEMPTED,

    /** 偏好类型的源全部完成, 没有候选. */
    NO_PREFERRED_CANDIDATES,

    /** 有候选但没有一个满足偏好. */
    NO_PREFERENCE_MATCH,

    /** Web 源全部完成, 没有 Web 候选, 且不回退到其他类型. */
    NO_WEB_CANDIDATES,

    /** 已到模糊匹配阶段, 源已完成 (或不等待), 仍没有候选. */
    FUZZY_DEADLINE,
}

/**
 * 一次 [MediaAutoSelector.select] 的结果.
 */
sealed interface MediaAutoSelectOutcome {
    /** 选中并写入. */
    data class Selected(
        val media: Media,
        val reason: MediaAutoSelectReason,
        val stage: MediaAutoSelectStage,
        val matchKind: SubjectMatchKind?,
        val tier: MediaSourceTier?,
    ) : MediaAutoSelectOutcome

    /** 做出了决定但写入失败: 决定与写入之间选择被改变. */
    data class Rejected(val media: Media, val reason: MediaAutoSelectReason) : MediaAutoSelectOutcome

    /** 没有可选的资源. */
    data class Exhausted(val cause: MediaAutoSelectExhaustCause) : MediaAutoSelectOutcome

    /** 调用被取消 (切集, 查询条件变化等). */
    data object Cancelled : MediaAutoSelectOutcome

    /** 选中并写入的资源, 其他结果为 `null`. */
    val selectedMedia: Media? get() = (this as? Selected)?.media
}
