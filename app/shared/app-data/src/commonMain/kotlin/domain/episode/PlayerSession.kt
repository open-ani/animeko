/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.domain.media.hls.HlsPlaybackPreparer
import me.him188.ani.app.domain.media.hls.HlsPlaybackProxySession
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.JellyfinMediaDataProvider
import me.him188.ani.app.domain.media.resolver.JellyfinPlaybackQualityState
import me.him188.ani.app.domain.media.resolver.MediaResolutionException
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.MediaSourceOpenException
import me.him188.ani.app.domain.media.resolver.OpenFailures
import me.him188.ani.app.domain.media.resolver.PreparedJellyfinPlayback
import me.him188.ani.app.domain.media.resolver.ResolutionFailures
import me.him188.ani.app.domain.media.resolver.TorrentBackedMediaDataProvider
import me.him188.ani.app.domain.media.resolver.UnsupportedMediaException
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.domain.settings.GetVideoScaffoldConfigUseCase
import me.him188.ani.app.domain.watchtogether.PlaybackAutomationGate
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.jellyfin.JellyfinPlaybackQuality
import me.him188.ani.datasources.jellyfin.JellyfinPlaybackQualityMode
import me.him188.ani.datasources.jellyfin.JellyfinPlaybackPlan
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.core.Koin
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.features.audioTracks
import org.openani.mediamp.features.subtitleTracks
import org.openani.mediamp.metadata.AudioTrack
import org.openani.mediamp.metadata.SubtitleTrack
import org.openani.mediamp.metadata.TrackGroup
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.UriMediaData
import kotlin.coroutines.CoroutineContext

class MediaFetchSelectBundle(
    val mediaFetchSession: MediaFetchSession,
    val mediaSelector: MediaSelector,
)

/**
 * The last valid playback progress kept visible while a Jellyfin quality switch replaces the media.
 */
data class JellyfinPlaybackProgressSnapshot(
    val positionMillis: Long,
    val durationMillis: Long,
)

// episodeId 改变, 需要全部清空
// 如果 episodeId 不变, 但是 EpisodeCollectionInfo 变了, 只需要更新一些信息.

/**
 * PlayerSession 封装对 [MediampPlayer] 的控制. 主要是解析 Media 并播放: [loadMedia].
 */
class PlayerSession(
    val player: MediampPlayer,
    koin: Koin,
    private val mainDispatcher: CoroutineContext = Dispatchers.Main.immediate,
) {
    val mediaResolver: MediaResolver by koin.inject()
    private val hlsPlaybackPreparer: HlsPlaybackPreparer by koin.inject()
    private val getVideoScaffoldConfigUseCase: GetVideoScaffoldConfigUseCase by koin.inject()
    private val playbackAutomationGate: PlaybackAutomationGate? = koin.getOrNull()

    private var hlsPlaybackProxySession: HlsPlaybackProxySession? = null
    private var jellyfinMediaDataProvider: JellyfinMediaDataProvider? = null
    private val jellyfinQualitySwitchMutex = Mutex()
    private val playbackMutationMutex = Mutex()
    private val playbackGeneration = atomic(0L)
    private val closed = atomic(false)
    private var installedPlaybackGeneration = NO_PLAYBACK_GENERATION

    private val _videoLoadingStateFlow: MutableStateFlow<VideoLoadingState> =
        MutableStateFlow(VideoLoadingState.Initial)
    private val _jellyfinPlaybackQualityState = MutableStateFlow<JellyfinPlaybackQualityState?>(null)
    private val _jellyfinPlaybackProgressSnapshot = MutableStateFlow<JellyfinPlaybackProgressSnapshot?>(null)

    /**
     * 当前的视频加载状态.
     */
    val videoLoadingState: StateFlow<VideoLoadingState> get() = _videoLoadingStateFlow.asStateFlow()

    val jellyfinPlaybackQualityState: StateFlow<JellyfinPlaybackQualityState?>
        get() = _jellyfinPlaybackQualityState.asStateFlow()

    /**
     * A temporary progress override while the Jellyfin stream is being replaced or rolled back.
     */
    val jellyfinPlaybackProgressSnapshot: StateFlow<JellyfinPlaybackProgressSnapshot?>
        get() = _jellyfinPlaybackProgressSnapshot.asStateFlow()

    /**
     * 解析 media 并开始播放这个 media.
     */
    suspend fun loadMedia(media: Media?, episodeInfo: EpisodeMetadata) = coroutineScope {
        check(!closed.value) { "PlayerSession is closed" }
        val generation = invalidatePlayback()
        val backgroundScope = this
        _videoLoadingStateFlow.value = VideoLoadingState.Initial // 避免一直显示已取消 (.Cancelled)
        stopPlayback(generation)
        if (media == null) {
            return@coroutineScope
        }

        var preparedHlsPlaybackProxySession: HlsPlaybackProxySession? = null
        var uninstalledJellyfinProvider: JellyfinMediaDataProvider? = null
        try {
            ensureCurrentPlaybackGeneration(generation)
            _videoLoadingStateFlow.value = VideoLoadingState.ResolvingSource
            val source = mediaResolver.resolve(
                media,
                episodeInfo,
            )
            ensureCurrentPlaybackGeneration(generation)
            _videoLoadingStateFlow.compareAndSet(
                VideoLoadingState.ResolvingSource,
                VideoLoadingState.DecodingData(isBt = media.kind == MediaSourceKind.BitTorrent),
            )

            val data = source.open(scopeForCleanup = backgroundScope) // may throw MediaSourceOpenException
            val jellyfinProvider = source as? JellyfinMediaDataProvider
            uninstalledJellyfinProvider = jellyfinProvider
            ensureCurrentPlaybackGeneration(generation)
            val preparedData = prepareHlsPlaybackIfEnabled(data).also {
                preparedHlsPlaybackProxySession = it.session
            }.data
            ensureCurrentPlaybackGeneration(generation)

            playbackMutationMutex.withLock {
                ensureCurrentPlaybackGeneration(generation)
                jellyfinMediaDataProvider = jellyfinProvider
                uninstalledJellyfinProvider = null
                installedPlaybackGeneration = generation
                _jellyfinPlaybackQualityState.value = jellyfinProvider?.qualityState?.value

                logger.info { "Set media data to player" }
                player.setMediaData(preparedData)
                hlsPlaybackProxySession = preparedHlsPlaybackProxySession
                preparedHlsPlaybackProxySession = null

                _videoLoadingStateFlow.value = VideoLoadingState.Succeed(
                    isBt = source is TorrentBackedMediaDataProvider,
                )
                withContext(mainDispatcher) {
                    player.resume()
                }
                logger.info { "resuming" }
            }
        } catch (_: PlaybackSupersededException) {
            // A newer load or stop owns the player. Only the uninstalled resources are cleaned below.
        } catch (e: UnsupportedMediaException) {
            logger.warn { IllegalStateException("Failed to resolve video source, unsupported media", e) }
            if (isCurrentPlaybackGeneration(generation)) {
                _videoLoadingStateFlow.value = VideoLoadingState.UnsupportedMedia
            }
            stopPlayback(generation)
        } catch (e: MediaSourceOpenException) { // during playerState.setVideoSource
            logger.warn {
                IllegalStateException(
                    "Failed to resolve video source due to VideoSourceOpenException",
                    e,
                )
            }
            if (isCurrentPlaybackGeneration(generation)) {
                _videoLoadingStateFlow.value = when (e.reason) {
                    OpenFailures.NO_MATCHING_FILE -> VideoLoadingState.NoMatchingFile
                    OpenFailures.UNSUPPORTED_VIDEO_SOURCE -> VideoLoadingState.UnsupportedMedia
                    OpenFailures.ENGINE_DISABLED -> VideoLoadingState.UnsupportedMedia
                }
            }
            stopPlayback(generation)
        } catch (e: MediaResolutionException) { // during MediaResolver.resolve
            logger.warn {
                IllegalStateException(
                    "Failed to resolve video source due to VideoSourceResolutionException",
                    e,
                )
            }
            if (isCurrentPlaybackGeneration(generation)) {
                _videoLoadingStateFlow.value = when (e.reason) {
                    ResolutionFailures.FETCH_TIMEOUT -> VideoLoadingState.ResolutionTimedOut
                    ResolutionFailures.ENGINE_ERROR -> VideoLoadingState.UnknownError(e)
                    ResolutionFailures.NETWORK_ERROR -> VideoLoadingState.NetworkError
                    ResolutionFailures.NO_MATCHING_RESOURCE -> VideoLoadingState.NoMatchingFile
                }
            }
            stopPlayback(generation)
        } catch (e: CancellationException) { // 切换数据源
            if (isCurrentPlaybackGeneration(generation)) {
                _videoLoadingStateFlow.value = VideoLoadingState.Cancelled
            }
            withContext(NonCancellable) {
                stopPlayback(generation)
            }
            throw e
        } catch (e: Throwable) {
            logger.error { IllegalStateException("Failed to resolve video source with unknown error", e) }
            if (isCurrentPlaybackGeneration(generation)) {
                _videoLoadingStateFlow.value = VideoLoadingState.UnknownError(e)
            }
            stopPlayback(generation)
        } finally {
            closeHlsPlaybackProxySession(preparedHlsPlaybackProxySession)
            withContext(NonCancellable) {
                uninstalledJellyfinProvider?.let { provider ->
                    stopEncodingSafely(provider, provider.takeCurrentPlan())
                }
            }
        }
    }

    suspend fun switchJellyfinPlaybackQuality(quality: JellyfinPlaybackQuality): Result<Unit> {
        val requestedGeneration = playbackGeneration.value
        return jellyfinQualitySwitchMutex.withLock qualitySwitch@{
            var owner: JellyfinPlaybackOwner? = null

            try {
                val capturedOwner = playbackMutationMutex.withLock {
                    ensureCurrentPlaybackGeneration(requestedGeneration)
                    val generation = playbackGeneration.value
                    val provider = jellyfinMediaDataProvider
                        ?: return@withLock null
                    if (installedPlaybackGeneration != generation) {
                        return@withLock null
                    }
                    JellyfinPlaybackOwner(generation, provider).also {
                        provider.setSwitching(true)
                        _jellyfinPlaybackQualityState.value = provider.qualityState.value
                    }
                } ?: return@qualitySwitch Result.failure(
                    IllegalStateException("The current media is not from Jellyfin"),
                )
                owner = capturedOwner
                suppressPlaybackAutomation {
                    switchJellyfinPlaybackQuality(capturedOwner, quality)
                }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.warn(e) { "Failed to switch Jellyfin playback quality" }
                Result.failure(e)
            } finally {
                withContext(NonCancellable) {
                    owner?.let { playbackOwner ->
                        playbackOwner.provider.setSwitching(false)
                        playbackMutationMutex.withLock {
                            if (isCurrentPlaybackOwner(playbackOwner)) {
                                _jellyfinPlaybackProgressSnapshot.value = null
                                _jellyfinPlaybackQualityState.value = playbackOwner.provider.qualityState.value
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun stopPlayback() {
        val generation = invalidatePlayback()
        stopPlayback(generation)
    }

    suspend fun close() {
        if (!closed.compareAndSet(expect = false, update = true)) return
        invalidatePlayback()
        var resources: DetachedPlaybackResources? = null
        var closeFailure: Throwable? = null
        withContext(NonCancellable) {
            playbackMutationMutex.withLock {
                resources = detachPlaybackResources()
                try {
                    player.close()
                } catch (e: Throwable) {
                    closeFailure = e
                }
            }
            resources?.let { detached ->
                closeHlsPlaybackProxySession(detached.proxySession)
                stopEncodingSafely(detached.provider, detached.plan)
            }
        }
        closeFailure?.let { throw it }
    }

    private fun invalidatePlayback(): Long = playbackGeneration.incrementAndGet()

    private fun isCurrentPlaybackGeneration(generation: Long): Boolean {
        return !closed.value && playbackGeneration.value == generation
    }

    private fun ensureCurrentPlaybackGeneration(generation: Long) {
        if (!isCurrentPlaybackGeneration(generation)) {
            throw PlaybackSupersededException()
        }
    }

    private fun isCurrentPlaybackOwner(owner: JellyfinPlaybackOwner): Boolean {
        return isCurrentPlaybackGeneration(owner.generation) &&
                installedPlaybackGeneration == owner.generation &&
                jellyfinMediaDataProvider === owner.provider
    }

    private fun ensureCurrentPlaybackOwner(owner: JellyfinPlaybackOwner) {
        if (!isCurrentPlaybackOwner(owner)) {
            throw PlaybackSupersededException()
        }
    }

    private suspend fun stopPlayback(generation: Long) {
        var resources: DetachedPlaybackResources? = null
        var stopFailure: Throwable? = null
        withContext(NonCancellable) {
            playbackMutationMutex.withLock {
                if (!isCurrentPlaybackGeneration(generation)) {
                    return@withLock
                }
                resources = detachPlaybackResources()
                try {
                    stopPlayer()
                } catch (e: Throwable) {
                    stopFailure = e
                }
            }
            resources?.let { detached ->
                closeHlsPlaybackProxySession(detached.proxySession)
                stopEncodingSafely(detached.provider, detached.plan)
            }
        }
        stopFailure?.let { throw it }
    }

    private fun detachPlaybackResources(): DetachedPlaybackResources {
        val provider = jellyfinMediaDataProvider
        val resources = DetachedPlaybackResources(
            provider = provider,
            plan = provider?.takeCurrentPlan(),
            proxySession = hlsPlaybackProxySession,
        )
        hlsPlaybackProxySession = null
        jellyfinMediaDataProvider = null
        installedPlaybackGeneration = NO_PLAYBACK_GENERATION
        _jellyfinPlaybackQualityState.value = null
        _jellyfinPlaybackProgressSnapshot.value = null
        return resources
    }

    private suspend fun stopPlayer() {
        withContext(mainDispatcher) {
            player.stopPlayback()
        }
    }

    private suspend fun prepareHlsPlaybackIfEnabled(data: MediaData): PreparedMediaData {
        if (data !is UriMediaData) {
            return PreparedMediaData(data)
        }
        val enabled = getVideoScaffoldConfigUseCase
            .invoke()
            .first()
            .enableExperimentalHlsSegmentFiltering
        if (!enabled) {
            return PreparedMediaData(data)
        }
        val result = hlsPlaybackPreparer.prepare(data)
        return PreparedMediaData(result.data, result.session)
    }

    private suspend fun switchJellyfinPlaybackQuality(
        owner: JellyfinPlaybackOwner,
        quality: JellyfinPlaybackQuality,
    ) {
        val (snapshot, selectedAudioStreamIndex) = playbackMutationMutex.withLock {
            ensureCurrentPlaybackOwner(owner)
            val playbackSnapshot = snapshotPlaybackForQualitySwitch()
            val audioStreamIndex = owner.provider.audioStreamIndexForQualitySwitch(
                playerAudioTrackCount = playbackSnapshot.trackSelection.audioCandidates?.size,
                selectedPlayerAudioTrackIndex = playbackSnapshot.trackSelection.selectedAudioTrackIndex,
            )
            ensureCurrentPlaybackOwner(owner)
            playbackSnapshot to audioStreamIndex
        }
        var prepared: PreparedJellyfinPlayback? = null
        var preparedProxySession: HlsPlaybackProxySession? = null
        var installedNewData = false
        var committed = false

        try {
            val nextPlayback = owner.provider.prepare(
                quality = quality,
                // Keep the player timeline aligned with the complete episode. Starting the Jellyfin
                // transcode at the current position would expose a new zero-based stream, so restore
                // the position in the player after the replacement media has actually started.
                startPositionMillis = 0L,
                forceAutoDetection = quality.mode == JellyfinPlaybackQualityMode.AUTO,
                audioStreamIndex = selectedAudioStreamIndex,
            ).also { prepared = it }
            ensureCurrentPlaybackGeneration(owner.generation)
            val preparedData = prepareHlsPlaybackIfEnabled(nextPlayback.data).also {
                preparedProxySession = it.session
            }.data
            ensureCurrentPlaybackGeneration(owner.generation)

            var previousPlan: JellyfinPlaybackPlan? = null
            var previousProxySession: HlsPlaybackProxySession? = null
            playbackMutationMutex.withLock {
                ensureCurrentPlaybackOwner(owner)
                installedNewData = true
                player.setMediaData(preparedData)
                restorePlayback(snapshot, owner, nextPlayback.plan)
                currentCoroutineContext().ensureActive()
                ensureCurrentPlaybackOwner(owner)

                previousPlan = owner.provider.commit(nextPlayback)
                committed = true
                previousProxySession = hlsPlaybackProxySession
                hlsPlaybackProxySession = preparedProxySession
                preparedProxySession = null
                _jellyfinPlaybackQualityState.value = owner.provider.qualityState.value
            }
            closeHlsPlaybackProxySession(previousProxySession)
            withContext(NonCancellable) {
                stopEncodingSafely(owner.provider, previousPlan)
            }
        } catch (e: Throwable) {
            withContext(NonCancellable) {
                closeHlsPlaybackProxySession(preparedProxySession)
                if (!committed) {
                    stopEncodingSafely(owner.provider, prepared?.plan)
                }
                playbackMutationMutex.withLock {
                    if (!committed && installedNewData && isCurrentPlaybackOwner(owner)) {
                        rollbackPlayback(snapshot, owner)
                    }
                }
            }
            throw e
        }
    }

    private suspend fun snapshotPlaybackForQualitySwitch(): JellyfinPlaybackSwitchSnapshot {
        val progressSnapshot = withContext(mainDispatcher) {
            val durationMillis = player.mediaProperties.value
                ?.durationMillis
                ?.takeIf { it > 0L }
                ?: return@withContext null
            JellyfinPlaybackProgressSnapshot(
                positionMillis = player.getCurrentPositionMillis().coerceIn(0L, durationMillis),
                durationMillis = durationMillis,
            )
        }
        val positionMillis = progressSnapshot?.positionMillis ?: withContext(mainDispatcher) {
            player.getCurrentPositionMillis().coerceAtLeast(0L)
        }
        _jellyfinPlaybackProgressSnapshot.value = progressSnapshot
        // Buffering is a transient pause while playback is still intended to continue.
        // PlaybackState.isPlaying only recognizes PLAYING, which would turn this stall into
        // a user pause after replacing the media.
        val playbackState = player.playbackState.value
        val shouldResume =
            playbackState == PlaybackState.PLAYING || playbackState == PlaybackState.PAUSED_BUFFERING
        return JellyfinPlaybackSwitchSnapshot(
            positionMillis = positionMillis,
            shouldResume = shouldResume,
            previousData = checkNotNull(player.mediaData.first { it != null }),
            trackSelection = snapshotTrackSelection(),
        )
    }

    private suspend fun resumeAndAwaitMediaStart(owner: JellyfinPlaybackOwner) {
        withContext(mainDispatcher) {
            ensureCurrentPlaybackOwner(owner)
            player.resume()
        }
        val state = withTimeoutOrNull(JELLYFIN_SWITCH_TIMEOUT_MILLIS) {
            while (true) {
                currentCoroutineContext().ensureActive()
                ensureCurrentPlaybackOwner(owner)
                val playbackState = player.playbackState.value
                if (playbackState == PlaybackState.ERROR || playbackState == PlaybackState.DESTROYED) {
                    return@withTimeoutOrNull playbackState
                }
                val positionMillis = withContext(mainDispatcher) {
                    player.getCurrentPositionMillis()
                }
                val durationMillis = player.mediaProperties.value?.durationMillis ?: 0L
                if (positionMillis > 0L && durationMillis > 0L) {
                    return@withTimeoutOrNull playbackState
                }
                delay(MEDIA_START_POLL_INTERVAL_MILLIS)
            }
            error("Unreachable")
        } ?: error("Timed out waiting for the selected Jellyfin playback quality")
        check(state != PlaybackState.ERROR && state != PlaybackState.DESTROYED) {
            "The player rejected the selected Jellyfin playback quality"
        }
    }

    private suspend fun restorePlayback(
        snapshot: JellyfinPlaybackSwitchSnapshot,
        owner: JellyfinPlaybackOwner,
        playbackPlan: JellyfinPlaybackPlan? = null,
    ) {
        // Both mpv and ExoPlayer install replacement media only after resume. Their public state can
        // become PLAYING before the replacement timeline is usable, so wait for it to advance before
        // seeking. Otherwise the media initialization can overwrite the restored position.
        resumeAndAwaitMediaStart(owner)
        withContext(mainDispatcher) {
            ensureCurrentPlaybackOwner(owner)
            player.seekTo(snapshot.positionMillis)
            if (!snapshot.shouldResume) {
                player.pause()
            }
        }
        ensureCurrentPlaybackOwner(owner)
        restoreTrackSelection(snapshot.trackSelection, playbackPlan)
    }

    private suspend fun rollbackPlayback(
        snapshot: JellyfinPlaybackSwitchSnapshot,
        owner: JellyfinPlaybackOwner,
    ) {
        try {
            ensureCurrentPlaybackOwner(owner)
            player.setMediaData(snapshot.previousData)
            restorePlayback(snapshot, owner)
        } catch (rollbackError: Throwable) {
            logger.warn(rollbackError) {
                "Failed to restore the previous playback after a Jellyfin quality switch error"
            }
        }
    }

    private suspend fun snapshotTrackSelection(): TrackSelectionSnapshot {
        val audioTrackGroup = player.audioTracks
        val selectedAudioTrack = audioTrackGroup?.selected?.value
        val audioCandidates = if (audioTrackGroup != null && selectedAudioTrack != null) {
            withTimeoutOrNull(TRACK_RESTORE_TIMEOUT_MILLIS) {
                audioTrackGroup.candidates.firstOrNull { it.isNotEmpty() }
            }.orEmpty()
        } else {
            null
        }
        val selectedAudioTrackIndex = selectedAudioTrack?.let { selected ->
            val index = audioCandidates?.indexOfFirst { candidate ->
                candidate == selected ||
                        candidate.internalId == selected.internalId ||
                        candidate.id == selected.id
            } ?: -1
            check(index >= 0) { "The selected player audio track is not present in its candidates" }
            index
        }
        return TrackSelectionSnapshot(
            audio = selectedAudioTrack,
            audioCandidates = audioCandidates,
            selectedAudioTrackIndex = selectedAudioTrackIndex,
            subtitle = player.subtitleTracks?.selected?.value,
        )
    }

    private suspend fun restoreTrackSelection(
        selection: TrackSelectionSnapshot,
        playbackPlan: JellyfinPlaybackPlan?,
    ) {
        restoreAudioTrackSelection(player.audioTracks, selection.audio, playbackPlan)
        restoreTrackSelection(player.subtitleTracks, selection.subtitle) { candidate, selected ->
            candidate.internalId == selected.internalId || candidate.id == selected.id
        }
    }

    private suspend fun restoreAudioTrackSelection(
        group: TrackGroup<AudioTrack>?,
        selected: AudioTrack?,
        playbackPlan: JellyfinPlaybackPlan?,
    ) {
        val serverAudioStreamIndex = playbackPlan
            ?.takeUnless(JellyfinPlaybackPlan::isTranscoding)
            ?.selectedAudioStreamIndex
        if (serverAudioStreamIndex == null) {
            restoreTrackSelection(group, selected) { candidate, previous ->
                candidate.internalId == previous.internalId || candidate.id == previous.id
            }
            return
        }

        val audioStreamOrdinal = playbackPlan.audioStreamIndices.indexOf(serverAudioStreamIndex)
        check(audioStreamOrdinal >= 0) {
            "The selected Jellyfin audio stream is not present in the playback plan"
        }
        if (playbackPlan.audioStreamIndices.size == 1) {
            return
        }
        checkNotNull(group) { "The player cannot select among multiple Jellyfin audio streams" }
        val candidates = withTimeoutOrNull(TRACK_RESTORE_TIMEOUT_MILLIS) {
            group.candidates.first { it.isNotEmpty() }
        } ?: error("Timed out waiting for Jellyfin audio tracks")
        check(candidates.size == playbackPlan.audioStreamIndices.size) {
            "Player audio tracks do not match the Jellyfin media streams"
        }
        check(group.select(candidates[audioStreamOrdinal])) {
            "The player rejected the selected Jellyfin audio stream"
        }
    }

    private suspend fun <T> restoreTrackSelection(
        group: TrackGroup<T>?,
        selected: T?,
        matches: (T, T) -> Boolean,
    ) {
        if (group == null) return
        if (selected == null) {
            group.select(null)
            return
        }
        val candidates = withTimeoutOrNull(TRACK_RESTORE_TIMEOUT_MILLIS) {
            group.candidates.first { it.isNotEmpty() }
        } ?: return
        candidates.firstOrNull { matches(it, selected) }?.let(group::select)
    }

    private suspend fun <T> suppressPlaybackAutomation(block: suspend () -> T): T {
        val gate = playbackAutomationGate
        return if (gate == null) block() else gate.suppressDuring(block)
    }

    private fun closeHlsPlaybackProxySession(session: HlsPlaybackProxySession?) {
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
            logger.warn(e) { "Failed to clean up a Jellyfin transcoding session" }
        }
    }

    companion object {
        private const val NO_PLAYBACK_GENERATION = -1L
        private const val JELLYFIN_SWITCH_TIMEOUT_MILLIS = 15_000L
        private const val MEDIA_START_POLL_INTERVAL_MILLIS = 50L
        private const val TRACK_RESTORE_TIMEOUT_MILLIS = 2_000L
        private val logger = logger<PlayerSession>()
    }

    private data class PreparedMediaData(
        val data: MediaData,
        val session: HlsPlaybackProxySession? = null,
    )

    private data class DetachedPlaybackResources(
        val provider: JellyfinMediaDataProvider?,
        val plan: JellyfinPlaybackPlan?,
        val proxySession: HlsPlaybackProxySession?,
    )

    private data class TrackSelectionSnapshot(
        val audio: AudioTrack?,
        val audioCandidates: List<AudioTrack>?,
        val selectedAudioTrackIndex: Int?,
        val subtitle: SubtitleTrack?,
    )

    private data class JellyfinPlaybackSwitchSnapshot(
        val positionMillis: Long,
        val shouldResume: Boolean,
        val previousData: MediaData,
        val trackSelection: TrackSelectionSnapshot,
    )

    private data class JellyfinPlaybackOwner(
        val generation: Long,
        val provider: JellyfinMediaDataProvider,
    )

    private class PlaybackSupersededException : CancellationException("Playback was superseded")
}


//class EpisodeMediaFetchSelectMediator(
//    val subjectId: Int,
//    private val bundleFlow: Flow<SubjectEpisodeInfoBundle>,
//    private val flowContext: CoroutineContext = Dispatchers.Default,
//    private val koin: Koin = GlobalKoin,
//) : KoinComponent {
//    private val mediaSourceManager: MediaSourceManager by inject()
//
//    private val flowScope = CoroutineScope(flowContext)
//
//    val mediaFetchSession: SharedFlow<MediaFetchSession> = bundleFlow
////        .flatMapLatest {
////            combine(it.subjectCollectionInfoFlow, it.episodeCollectionInfoFlow) { subject, episode ->
////                MediaFetchRequest.create(subject.subjectInfo, episode.episodeInfo)
////            }
////        }
//        .map {
//            MediaFetchRequest.create(it.subjectCollectionInfo.subjectInfo, it.episodeCollectionInfo.episodeInfo)
//        }
//        .distinctUntilChanged() // re-create fetch session iff part of the infos related to fetch changes.
//        .flatMapLatest { request ->
//            mediaSourceManager.createFetchFetchSession(flowOf(request))
//        } // the above won't throw.
//        .shareIn(flowScope, SharingStarted.WhileSubscribed(), 1)
//
//    val mediaSelector: SharedFlow<MediaSelector> = mediaFetchSession
//        .map { fetchSession ->
//            MediaSelectorFactory.withKoin(getKoin())
//                .create(subjectId, fetchSession.cumulativeResults)
//        }
//        .shareIn(flowScope, SharingStarted.WhileSubscribed(), 1)
//
//    override fun getKoin(): Koin = koin
//}

//interface SubjectEpisodeCollectionSession {
//    val subjectId: Int
//
//    /**
//     * A flow of the current episode id.
//     */
//    val episodeIdFlow: StateFlow<Int>
//
//    /**
//     * A flow of the current episode info.
//     */
//    val episodeInfoFlow: Flow<EpisodeCollectionInfo>
//
//    suspend fun switchEpisode(episodeId: Int)
//
//    data class Output(
//        val subjectInfo: SubjectCollectionInfo,
//        val episodeInfo: EpisodeCollectionInfo,
//    )
//}
//
///**
// *
// */
//class SubjectEpisodeCollectionSessionImpl(
//    override val subjectId: Int,
//    initialEpisodeId: Int,
//    private val flowContext: CoroutineContext = Dispatchers.Default,
//    private val koin: Koin = GlobalKoin
//) : SubjectEpisodeCollectionSession, KoinComponent {
//    private val getEpisodeCollectionInfoFlowUseCase: GetEpisodeCollectionInfoFlowUseCase by inject()
//
//    class State(
//        val subjectId: Int,
//        val episodeId: Int,
//    )
//
//    private val stateFlow = MutableStateFlow(State(subjectId, initialEpisodeId))
//
//    override val episodeIdFlow = stateFlow.map { it.episodeId }.stateIn(
//        CoroutineScope(flowContext), SharingStarted.WhileSubscribed(), initialEpisodeId,
//    )
//
//    override val episodeInfoFlow: Flow<EpisodeCollectionInfo> = episodeIdFlow.flatMapLatest {
//        getEpisodeCollectionInfoFlowUseCase(subjectId, it)
//    }
//
//    override suspend fun switchEpisode(
//        episodeId: Int,
//    ) {
//
//    }
//
//    override fun getKoin(): Koin = koin
//}
