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
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.watchtogether.LocalPlaybackBridge
import me.him188.ani.app.domain.watchtogether.PlaybackDirective
import me.him188.ani.app.domain.watchtogether.WatchTogetherManager
import me.him188.ani.app.domain.watchtogether.positionAt
import me.him188.ani.client.models.AniWatchTogetherPlayback
import me.him188.ani.client.models.AniWatchTogetherWatchingInfo
import me.him188.ani.utils.coroutines.sampleWithInitial
import org.koin.core.Koin
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.isPlaying
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
        backgroundTaskScope.launch("MediaLoadedListener") {
            context.subscribeEvents<EpisodeFetchSelectPlayState.MediaLoadedEvent>().collectLatest { event ->
                if (event.episodeId == episodeSession.episodeId && mediaLoaded.isActive) mediaLoaded.complete(Unit)
            }
        }

        backgroundTaskScope.launch("Reporter") {
            mediaLoaded.await()
            combine(
                episodeSession.infoBundleFlow.filterNotNull(),
                context.player.currentPositionMillis.sampleWithInitial(REPORT_SAMPLE_INTERVAL_MILLIS),
                context.player.playbackState,
                context.player.mediaProperties,
            ) { info, position, playbackState, mediaProperties ->
                AniWatchTogetherWatchingInfo(
                    subjectId = info.subjectId,
                    episodeId = info.episodeId,
                    subjectName = info.subjectInfo.displayName,
                    episodeSort = info.episodeInfo.sort.toString(),
                    episodeName = info.episodeInfo.displayName,
                    positionMillis = position.coerceAtLeast(0L),
                    positionAtMillis = manager.serverNowMillis(),
                    durationMillis = mediaProperties?.durationMillis ?: 0L,
                    paused = playbackState != PlaybackState.PLAYING,
                    buffering = playbackState == PlaybackState.PAUSED_BUFFERING,
                    playbackRate = context.player.features[PlaybackSpeed]?.value ?: 1f,
                )
            }.collect { bridge.updateLocalWatching(owner, it) }
        }

        backgroundTaskScope.launch("InitialFollowerSync") {
            mediaLoaded.await()
            context.player.playbackState
                .filter { it == PlaybackState.PLAYING || it == PlaybackState.PAUSED }
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
        withContext(Dispatchers.Main.immediate) {
            val player = context.player
            val localPosition = player.currentPositionMillis.value
            if (forceSeek || abs(localPosition - targetPosition) > POSITION_CORRECTION_THRESHOLD_MILLIS) {
                player.seekTo(targetPosition)
            }
            player.features[PlaybackSpeed]?.set(host.playbackRate ?: 1f)
            if (host.paused) {
                if (player.playbackState.value.isPlaying) player.pause()
            } else if (player.playbackState.value == PlaybackState.PAUSED) {
                player.resume()
            }
        }
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
    }
}
