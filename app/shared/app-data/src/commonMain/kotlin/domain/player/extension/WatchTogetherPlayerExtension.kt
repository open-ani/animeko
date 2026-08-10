/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.watchtogether.LocalPlaybackBridge
import me.him188.ani.app.domain.watchtogether.PlaybackDirective
import me.him188.ani.app.domain.watchtogether.WatchTogetherManager
import me.him188.ani.app.domain.watchtogether.WatchTogetherState
import me.him188.ani.app.domain.watchtogether.positionAt
import me.him188.ani.client.models.AniWatchTogetherPlayback
import me.him188.ani.client.models.AniWatchTogetherWatchingInfo
import me.him188.ani.utils.coroutines.sampleWithInitial
import org.koin.core.Koin
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.isMediaLoaded
import kotlin.math.abs

class WatchTogetherPlayerExtension(
    private val context: PlayerExtensionContext,
    koin: Koin,
) : PlayerExtension("WatchTogether") {
    private val bridge: LocalPlaybackBridge by koin.inject()
    private val manager: WatchTogetherManager by koin.inject()
    private val owner = Any()

    override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
        val mediaLoaded = CompletableDeferred<Unit>()
        // Completed once the hold-first-play decision has settled (paused, or not applicable);
        // until then the reporter suppresses PLAYING fixes so the transient auto-play between
        // load and hold never reaches the room.
        val holdSettled = CompletableDeferred<Unit>()
        backgroundTaskScope.launch("MediaLoadedListener") {
            context.subscribeEvents<EpisodeFetchSelectPlayState.MediaLoadedEvent>().collectLatest { event ->
                if (event.episodeId == episodeSession.episodeId && mediaLoaded.isActive) mediaLoaded.complete(Unit)
            }
        }

        backgroundTaskScope.launch("Reporter") {
            // Publish the episode before media selection/loading so followers can open the page and load in parallel.
            val initialInfo = episodeSession.infoBundleFlow.filterNotNull().first()
            bridge.updateLocalWatching(
                owner,
                AniWatchTogetherWatchingInfo(
                    subjectId = initialInfo.subjectId,
                    episodeId = initialInfo.episodeId,
                    subjectName = initialInfo.subjectInfo.displayName,
                    episodeSort = initialInfo.episodeInfo.sort.toString(),
                    episodeName = initialInfo.episodeInfo.displayName,
                    positionMillis = 0L,
                    positionAtMillis = manager.serverNowMillis(),
                    durationMillis = 0L,
                    paused = true,
                    loading = true,
                    playbackRate = 1f,
                ),
            )

            mediaLoaded.await()
            combine(
                episodeSession.infoBundleFlow.filterNotNull(),
                // Drives the fix cadence only; the emitted value reads the live position below so
                // state-edge fixes (e.g. resume right after a reload) never carry a stale sample.
                context.player.currentPositionMillis.sampleWithInitial(REPORT_SAMPLE_INTERVAL_MILLIS),
                context.player.state,
                context.player.mediaProperties,
            ) { info, _, playerState, mediaProperties ->
                // Stable-state gate: no fixes while (re)loading media (Idle/Opening/Error, e.g.
                // switching sources mid-episode), or the transient 0 position would be broadcast
                // and followers would be yanked to it. The last stable fix stays current instead.
                // v2 change: fixes start as soon as the media is loaded (Ready/Ended) — the initial
                // buffer is reported as buffering instead of being skipped like v1's READY gate did.
                if (!playerState.isMediaLoaded) return@combine null
                // Suppress by intent so the transient auto-play (even while still buffering)
                // never reaches the room before the hold decision settles.
                if (!holdSettled.isCompleted && playerState.playWhenReady) return@combine null
                val durationMillis = mediaProperties?.durationMillis ?: 0L
                AniWatchTogetherWatchingInfo(
                    subjectId = info.subjectId,
                    episodeId = info.episodeId,
                    subjectName = info.subjectInfo.displayName,
                    episodeSort = info.episodeInfo.sort.toString(),
                    episodeName = info.episodeInfo.displayName,
                    positionMillis = context.player.currentPositionMillis.value.coerceAtLeast(0L),
                    positionAtMillis = manager.serverNowMillis(),
                    durationMillis = durationMillis,
                    paused = !playerState.playWhenReady,
                    buffering = playerState.isBuffering,
                    // Loading ends only once the player learns the total duration.
                    loading = durationMillis <= 0L,
                    playbackRate = context.player.features[PlaybackSpeed]?.value ?: 1f,
                )
            }.filterNotNull().collect { bridge.updateLocalWatching(owner, it) }
        }

        backgroundTaskScope.launch("HoldAfterLoad") {
            // In a room, nobody auto-plays after the first load: everyone pauses and waits for
            // the host to press play, so the host can start once the whole room has loaded.
            // Mid-episode source reloads don't re-trigger (mediaLoaded completes once per episode).
            mediaLoaded.await()
            // Stable = actually playing, or loaded and pausing by intent.
            val firstStable = context.player.state
                .filter { it.isPlaying || (it.mediaStatus == MediaStatus.Ready && !it.playWhenReady) }
                .first()
            if (firstStable.isPlaying && isRoomSyncActive()) {
                withContext(Dispatchers.Main.immediate) { context.player.pause() }
                // Wait for the pause to take effect so the reporter's playing suppression covers
                // the whole transient; bounded in case the player ignores the pause.
                withTimeoutOrNull(HOLD_SETTLE_TIMEOUT_MILLIS) {
                    context.player.state.filter { !it.isPlaying }.first()
                }
            }
            holdSettled.complete(Unit)
        }

        backgroundTaskScope.launch("InitialFollowerSync") {
            mediaLoaded.await()
            // Serialized after the hold decision so the initial sync and the hold don't fight
            // over the play/pause state.
            holdSettled.await()
            context.player.state
                .filter { it.isPlaying || (it.mediaStatus == MediaStatus.Ready && !it.playWhenReady) }
                .first()
            bridge.targetPlayback.value?.let { applyPlayback(episodeSession, it, forceSeek = true) }
        }

        backgroundTaskScope.launch("FollowerDirectives") {
            bridge.directives.collect { directive ->
                when (directive) {
                    is PlaybackDirective.Sync -> applyPlayback(
                        episodeSession,
                        directive.playback,
                        forceSeek = directive.initial,
                    )

                    is PlaybackDirective.SwitchEpisode -> {
                        if (directive.playback.info.subjectId != context.subjectId) return@collect
                        switchEpisodeIfNeeded(directive.episodeId)
                    }
                }
            }
        }
    }

    private suspend fun applyPlayback(
        episodeSession: EpisodeSession,
        playback: AniWatchTogetherPlayback,
        forceSeek: Boolean,
    ) {
        val host = playback.info
        if (host.subjectId != context.subjectId || host.episodeId != episodeSession.episodeId) return
        if (host.buffering == true) {
            delay(BUFFERING_DEBOUNCE_MILLIS)
            val latest = bridge.targetPlayback.value?.info
            if (latest?.subjectId != host.subjectId || latest.episodeId != host.episodeId || latest.buffering != true) return
        }

        val targetPosition = host.positionAt(manager.serverNowMillis())
        val appliedDelta = withContext(Dispatchers.Main.immediate) {
            val player = context.player
            var delta: Long? = null
            val localPosition = player.currentPositionMillis.value
            if (forceSeek || abs(localPosition - targetPosition) > POSITION_CORRECTION_THRESHOLD_MILLIS) {
                player.seekTo(targetPosition)
                delta = targetPosition - localPosition
            }
            player.features[PlaybackSpeed]?.set(host.playbackRate ?: 1f)
            // Sync the play intent (playWhenReady), not the momentary clock state: pausing must
            // stick even mid-buffer, and play() at Ended replays so followers stay in sync.
            if (host.paused) {
                if (player.state.value.playWhenReady) player.pause()
            } else if (!player.state.value.playWhenReady) {
                player.play()
            }
            delta
        }
        if (appliedDelta != null && !forceSeek) bridge.notifyCorrectionApplied(appliedDelta)
    }

    /** Host and following members participate in room sync; free-watching members do not. */
    private fun isRoomSyncActive(): Boolean {
        val state = manager.state.value
        return state is WatchTogetherState.InRoom &&
                (state.session.isHost || state.session.following.value)
    }

    @OptIn(UnsafeEpisodeSessionApi::class)
    private suspend fun switchEpisodeIfNeeded(episodeId: Int) {
        if (context.getCurrentEpisodeId() != episodeId) context.switchEpisode(episodeId)
    }

    override suspend fun onBeforeSwitchEpisode(newEpisodeId: Int) {
        bridge.clearLocalWatching(owner)
    }

    override suspend fun onClose() {
        bridge.clearLocalWatching(owner)
    }

    companion object : EpisodePlayerExtensionFactory<WatchTogetherPlayerExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): WatchTogetherPlayerExtension =
            WatchTogetherPlayerExtension(context, koin)

        private const val REPORT_SAMPLE_INTERVAL_MILLIS = 1_000L
        private const val POSITION_CORRECTION_THRESHOLD_MILLIS = 3_000L
        private const val BUFFERING_DEBOUNCE_MILLIS = 2_000L
        private const val HOLD_SETTLE_TIMEOUT_MILLIS = 2_000L
    }
}
