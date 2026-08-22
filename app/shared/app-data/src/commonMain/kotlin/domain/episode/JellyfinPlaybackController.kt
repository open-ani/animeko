/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.domain.media.hls.HlsPlaybackProxySession
import me.him188.ani.app.domain.media.resolver.JellyfinMediaDataProvider
import me.him188.ani.app.domain.media.resolver.JellyfinPlaybackQualityState
import me.him188.ani.app.domain.media.resolver.PreparedJellyfinPlayback
import me.him188.ani.datasources.jellyfin.JellyfinPlaybackPlan
import me.him188.ani.datasources.jellyfin.JellyfinPlaybackQuality
import me.him188.ani.datasources.jellyfin.JellyfinPlaybackQualityMode
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.audioTracks
import org.openani.mediamp.metadata.AudioTrack
import org.openani.mediamp.metadata.TrackGroup
import org.openani.mediamp.source.MediaData
import kotlin.coroutines.CoroutineContext

internal data class PreparedJellyfinMediaData(
    val data: MediaData,
    val proxySession: HlsPlaybackProxySession?,
)

/**
 * Playback values captured immediately before replacing a Jellyfin stream.
 */
data class JellyfinPlaybackReplacementSnapshot(
    val positionMillis: Long,
    val durationMillis: Long,
    val playWhenReady: Boolean,
)

internal class JellyfinPlaybackOwner(
    internal val provider: JellyfinMediaDataProvider,
)

internal data class DetachedJellyfinPlayback(
    val provider: JellyfinMediaDataProvider?,
    val plan: JellyfinPlaybackPlan?,
)

/**
 * Owns only the Jellyfin-specific part of an episode playback.
 *
 * This controller serializes Jellyfin stream replacement and detachment, negotiates a new plan,
 * maps the selected audio stream, and cleans up Jellyfin transcodes without changing the generic
 * player lifecycle.
 */
internal class JellyfinPlaybackController(
    private val player: MediampPlayer,
    private val mainDispatcher: CoroutineContext,
) {
    private var provider: JellyfinMediaDataProvider? = null
    private val _qualityState = MutableStateFlow<JellyfinPlaybackQualityState?>(null)
    private val playbackTransitionMutex = Mutex()

    val qualityState: StateFlow<JellyfinPlaybackQualityState?> = _qualityState.asStateFlow()
    val hasPlayback: Boolean get() = provider != null

    fun install(provider: JellyfinMediaDataProvider) {
        this.provider = provider
        _qualityState.value = provider.qualityState.value
    }

    fun captureOwner(): JellyfinPlaybackOwner? {
        return provider?.let(::JellyfinPlaybackOwner)
    }

    suspend fun detach(): DetachedJellyfinPlayback = playbackTransitionMutex.withLock {
        detachLocked()
    }

    private fun detachLocked(): DetachedJellyfinPlayback {
        val currentProvider = provider
        provider = null
        _qualityState.value = null
        return DetachedJellyfinPlayback(
            provider = currentProvider,
            plan = currentProvider?.takeCurrentPlan(),
        )
    }

    suspend fun discard(provider: JellyfinMediaDataProvider) {
        stopEncodingSafely(provider, provider.takeCurrentPlan())
    }

    suspend fun stopEncoding(playback: DetachedJellyfinPlayback) {
        stopEncodingSafely(playback.provider, playback.plan)
    }

    suspend fun switch(
        owner: JellyfinPlaybackOwner,
        quality: JellyfinPlaybackQuality,
        prepareMediaData: suspend (MediaData) -> PreparedJellyfinMediaData,
        replaceProxySession: (HlsPlaybackProxySession?) -> HlsPlaybackProxySession?,
        onReplacementFailure: suspend () -> Unit,
        beforeReplace: suspend (JellyfinPlaybackReplacementSnapshot) -> Unit,
    ): Result<Unit> = playbackTransitionMutex.withLock {
        val currentProvider = provider
        if (currentProvider !== owner.provider) {
            return@withLock Result.failure(
                IllegalStateException("The selected media changed before the quality switch started"),
            )
        }
        if (currentProvider.qualityState.value?.selected == quality) {
            return@withLock Result.success(Unit)
        }

        currentProvider.setSwitching(true)
        _qualityState.value = currentProvider.qualityState.value
        try {
            replacePlayback(
                provider = currentProvider,
                quality = quality,
                prepareMediaData = prepareMediaData,
                replaceProxySession = replaceProxySession,
                onReplacementFailure = onReplacementFailure,
                beforeReplace = beforeReplace,
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to switch Jellyfin playback quality" }
            Result.failure(e)
        } finally {
            currentProvider.setSwitching(false)
            if (provider === currentProvider) {
                _qualityState.value = currentProvider.qualityState.value
            }
        }
    }

    private suspend fun replacePlayback(
        provider: JellyfinMediaDataProvider,
        quality: JellyfinPlaybackQuality,
        prepareMediaData: suspend (MediaData) -> PreparedJellyfinMediaData,
        replaceProxySession: (HlsPlaybackProxySession?) -> HlsPlaybackProxySession?,
        onReplacementFailure: suspend () -> Unit,
        beforeReplace: suspend (JellyfinPlaybackReplacementSnapshot) -> Unit,
    ) {
        val selectedAudioStreamIndex = selectedAudioStreamIndex(provider)
        var prepared: PreparedJellyfinPlayback? = null
        var preparedProxySession: HlsPlaybackProxySession? = null
        var replacementStarted = false
        var pausedForReplacement = false

        try {
            val nextPlayback = provider.prepare(
                quality = quality,
                startPositionMillis = 0L,
                forceAutoDetection = quality.mode == JellyfinPlaybackQualityMode.AUTO,
                audioStreamIndex = selectedAudioStreamIndex,
            ).also { prepared = it }
            val preparedData = prepareMediaData(nextPlayback.data).also {
                preparedProxySession = it.proxySession
            }.data

            val snapshot = snapshotPlayback()
            if (snapshot.playWhenReady) {
                withContext(mainDispatcher) {
                    player.pause()
                }
                pausedForReplacement = true
            }
            beforeReplace(snapshot)

            replacementStarted = true
            player.setMediaData(
                preparedData,
                playWhenReady = snapshot.playWhenReady,
                startPositionMillis = snapshot.positionMillis,
            )
            restoreSelectedAudioStream(nextPlayback.plan)

            val previousPlan = provider.commit(nextPlayback)
            val previousProxySession = replaceProxySession(preparedProxySession)
            preparedProxySession = null
            _qualityState.value = provider.qualityState.value

            closeProxySession(previousProxySession)
            withContext(NonCancellable) {
                stopEncodingSafely(provider, previousPlan)
            }
        } catch (e: Throwable) {
            withContext(NonCancellable) {
                closeProxySession(preparedProxySession)
                stopEncodingSafely(provider, prepared?.plan)
                if (replacementStarted) {
                    val failedPlayback = if (this@JellyfinPlaybackController.provider === provider) {
                        detachLocked()
                    } else {
                        DetachedJellyfinPlayback(provider = null, plan = null)
                    }
                    try {
                        onReplacementFailure()
                    } catch (cleanupError: Throwable) {
                        logger.warn(cleanupError) {
                            "Failed to stop playback after a Jellyfin quality switch error"
                        }
                    }
                    stopEncodingSafely(failedPlayback.provider, failedPlayback.plan)
                } else if (pausedForReplacement && this@JellyfinPlaybackController.provider === provider) {
                    try {
                        withContext(mainDispatcher) {
                            player.play()
                        }
                    } catch (resumeError: Throwable) {
                        logger.warn(resumeError) {
                            "Failed to resume playback after a Jellyfin quality switch error"
                        }
                    }
                }
            }
            throw e
        }
    }

    private suspend fun snapshotPlayback(): JellyfinPlaybackReplacementSnapshot {
        return withContext(mainDispatcher) {
            val durationMillis = player.mediaProperties.value?.durationMillis
            check(durationMillis != null && durationMillis > 0L) {
                "Cannot switch Jellyfin quality before the media duration is available"
            }
            JellyfinPlaybackReplacementSnapshot(
                positionMillis = player.currentPositionMillis.value.coerceIn(0L, durationMillis),
                durationMillis = durationMillis,
                playWhenReady = player.state.value.playWhenReady,
            )
        }
    }

    private suspend fun selectedAudioStreamIndex(provider: JellyfinMediaDataProvider): Int? {
        val group = player.audioTracks
        val selected = group?.selected?.value
        if (group == null || selected == null) {
            return provider.audioStreamIndexForQualitySwitch(
                playerAudioTrackCount = null,
                selectedPlayerAudioTrackIndex = null,
            )
        }

        val candidates = withTimeoutOrNull(TRACK_SELECTION_TIMEOUT_MILLIS) {
            group.candidates.firstOrNull { it.isNotEmpty() }
        }.orEmpty()
        val selectedIndex = candidates.indexOfFirst { candidate ->
            candidate == selected ||
                    candidate.internalId == selected.internalId ||
                    candidate.id == selected.id
        }
        check(selectedIndex >= 0) {
            "The selected player audio track is not present in its candidates"
        }
        return provider.audioStreamIndexForQualitySwitch(
            playerAudioTrackCount = candidates.size,
            selectedPlayerAudioTrackIndex = selectedIndex,
        )
    }

    private suspend fun restoreSelectedAudioStream(plan: JellyfinPlaybackPlan) {
        if (plan.isTranscoding || plan.audioStreamIndices.size <= 1) return
        val selectedStreamIndex = plan.selectedAudioStreamIndex ?: return
        val selectedOrdinal = plan.audioStreamIndices.indexOf(selectedStreamIndex)
        check(selectedOrdinal >= 0) {
            "The selected Jellyfin audio stream is not present in the playback plan"
        }

        val group: TrackGroup<AudioTrack> = checkNotNull(player.audioTracks) {
            "The player cannot select among multiple Jellyfin audio streams"
        }
        val candidates = withTimeoutOrNull(TRACK_SELECTION_TIMEOUT_MILLIS) {
            group.candidates.first { it.isNotEmpty() }
        } ?: error("Timed out waiting for Jellyfin audio tracks")
        if (candidates.size != plan.audioStreamIndices.size) {
            logger.warn {
                "Cannot restore the selected Jellyfin audio stream by ordinal: " +
                        "playerTracks=${candidates.size}, jellyfinStreams=${plan.audioStreamIndices.size}; " +
                        "keeping the player's selected track"
            }
            return
        }
        check(group.select(candidates[selectedOrdinal])) {
            "The player rejected the selected Jellyfin audio stream"
        }
    }

    private fun closeProxySession(session: HlsPlaybackProxySession?) {
        if (session == null) return
        try {
            session.close()
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to close an HLS playback proxy session" }
        }
    }

    private suspend fun stopEncodingSafely(
        provider: JellyfinMediaDataProvider?,
        plan: JellyfinPlaybackPlan?,
    ) {
        if (provider == null) return
        try {
            provider.stopEncoding(plan)
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to stop a Jellyfin transcoding session" }
        }
    }

    private companion object {
        const val TRACK_SELECTION_TIMEOUT_MILLIS = 2_000L
        val logger = logger<JellyfinPlaybackController>()
    }
}
