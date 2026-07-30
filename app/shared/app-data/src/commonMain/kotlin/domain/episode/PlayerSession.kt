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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
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
        val backgroundScope = this
        _videoLoadingStateFlow.value = VideoLoadingState.Initial // 避免一直显示已取消 (.Cancelled)
        stopPlayback()
        if (media == null) {
            return@coroutineScope
        }

        var preparedHlsPlaybackProxySession: HlsPlaybackProxySession? = null
        try {
            _videoLoadingStateFlow.value = VideoLoadingState.ResolvingSource
            val source = mediaResolver.resolve(
                media,
                episodeInfo,
            )
            _videoLoadingStateFlow.compareAndSet(
                VideoLoadingState.ResolvingSource,
                VideoLoadingState.DecodingData(isBt = media.kind == MediaSourceKind.BitTorrent),
            )

            val data = source.open(scopeForCleanup = backgroundScope) // may throw MediaSourceOpenException
            val jellyfinProvider = source as? JellyfinMediaDataProvider
            jellyfinMediaDataProvider = jellyfinProvider
            _jellyfinPlaybackQualityState.value = jellyfinProvider?.qualityState?.value
            val preparedData = prepareHlsPlaybackIfEnabled(data).also {
                preparedHlsPlaybackProxySession = it.session
            }.data

            logger.info { "Set media data to player" }
            player.setMediaData(preparedData)
            hlsPlaybackProxySession = preparedHlsPlaybackProxySession
            preparedHlsPlaybackProxySession = null

            _videoLoadingStateFlow.value = VideoLoadingState.Succeed(isBt = source is TorrentBackedMediaDataProvider)
            withContext(mainDispatcher) {
                player.resume()
            }
            logger.info { "resuming" }
        } catch (e: UnsupportedMediaException) {
            logger.warn { IllegalStateException("Failed to resolve video source, unsupported media", e) }
            _videoLoadingStateFlow.value = VideoLoadingState.UnsupportedMedia
            stopPlayback()
        } catch (e: MediaSourceOpenException) { // during playerState.setVideoSource
            logger.warn {
                IllegalStateException(
                    "Failed to resolve video source due to VideoSourceOpenException",
                    e,
                )
            }
            _videoLoadingStateFlow.value = when (e.reason) {
                OpenFailures.NO_MATCHING_FILE -> VideoLoadingState.NoMatchingFile
                OpenFailures.UNSUPPORTED_VIDEO_SOURCE -> VideoLoadingState.UnsupportedMedia
                OpenFailures.ENGINE_DISABLED -> VideoLoadingState.UnsupportedMedia
            }
            stopPlayback()
        } catch (e: MediaResolutionException) { // during MediaResolver.resolve
            logger.warn {
                IllegalStateException(
                    "Failed to resolve video source due to VideoSourceResolutionException",
                    e,
                )
            }
            _videoLoadingStateFlow.value = when (e.reason) {
                ResolutionFailures.FETCH_TIMEOUT -> VideoLoadingState.ResolutionTimedOut
                ResolutionFailures.ENGINE_ERROR -> VideoLoadingState.UnknownError(e)
                ResolutionFailures.NETWORK_ERROR -> VideoLoadingState.NetworkError
                ResolutionFailures.NO_MATCHING_RESOURCE -> VideoLoadingState.NoMatchingFile
            }
            stopPlayback()
        } catch (e: CancellationException) { // 切换数据源
            _videoLoadingStateFlow.value = VideoLoadingState.Cancelled
            throw e
        } catch (e: Throwable) {
            logger.error { IllegalStateException("Failed to resolve video source with unknown error", e) }
            _videoLoadingStateFlow.value = VideoLoadingState.UnknownError(e)
            stopPlayback()
        } finally {
            preparedHlsPlaybackProxySession?.close()
        }
    }

    suspend fun switchJellyfinPlaybackQuality(quality: JellyfinPlaybackQuality): Result<Unit> {
        return jellyfinQualitySwitchMutex.withLock {
            val provider = jellyfinMediaDataProvider
                ?: return@withLock Result.failure(IllegalStateException("The current media is not from Jellyfin"))
            provider.setSwitching(true)
            _jellyfinPlaybackQualityState.value = provider.qualityState.value

            try {
                suppressPlaybackAutomation {
                    switchJellyfinPlaybackQuality(provider, quality)
                }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.warn(e) { "Failed to switch Jellyfin playback quality" }
                Result.failure(e)
            } finally {
                _jellyfinPlaybackProgressSnapshot.value = null
                provider.setSwitching(false)
                _jellyfinPlaybackQualityState.value = provider.qualityState.value
            }
        }
    }

    suspend fun stopPlayback() {
        stopPlayer()
        closeHlsPlaybackProxySession()
        jellyfinMediaDataProvider?.stopCurrentEncoding()
        jellyfinMediaDataProvider = null
        _jellyfinPlaybackQualityState.value = null
        _jellyfinPlaybackProgressSnapshot.value = null
    }

    fun close() {
        closeHlsPlaybackProxySession()
        jellyfinMediaDataProvider = null
        _jellyfinPlaybackQualityState.value = null
        _jellyfinPlaybackProgressSnapshot.value = null
        player.close()
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
        provider: JellyfinMediaDataProvider,
        quality: JellyfinPlaybackQuality,
    ) {
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
        val previousData = checkNotNull(player.mediaData.first { it != null })
        val trackSelection = snapshotTrackSelection()
        val prepared = provider.prepare(
            quality = quality,
            // Keep the player timeline aligned with the complete episode. Starting the Jellyfin
            // transcode at the current position would expose a new zero-based stream, so restore
            // the position in the player after the replacement media has actually started.
            startPositionMillis = 0L,
            forceAutoDetection = quality.mode == JellyfinPlaybackQualityMode.AUTO,
        )
        var preparedProxySession: HlsPlaybackProxySession? = null
        var installedNewData = false

        try {
            val preparedData = prepareHlsPlaybackIfEnabled(prepared.data).also {
                preparedProxySession = it.session
            }.data
            player.setMediaData(preparedData)
            installedNewData = true
            restorePlayback(positionMillis, shouldResume, trackSelection)
            currentCoroutineContext().ensureActive()

            val previousPlan = provider.commit(prepared)
            val previousProxySession = hlsPlaybackProxySession
            hlsPlaybackProxySession = preparedProxySession
            preparedProxySession = null
            _jellyfinPlaybackQualityState.value = provider.qualityState.value

            previousProxySession?.close()
            withContext(NonCancellable) {
                provider.stopEncoding(previousPlan)
            }
        } catch (e: Throwable) {
            preparedProxySession?.close()
            provider.stopEncoding(prepared.plan)
            if (installedNewData) {
                rollbackPlayback(previousData, positionMillis, shouldResume, trackSelection)
            }
            throw e
        }
    }

    private suspend fun resumeAndAwaitMediaStart() {
        withContext(mainDispatcher) {
            player.resume()
        }
        val state = withTimeoutOrNull(JELLYFIN_SWITCH_TIMEOUT_MILLIS) {
            while (true) {
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
        positionMillis: Long,
        shouldResume: Boolean,
        trackSelection: TrackSelectionSnapshot,
    ) {
        // Both mpv and ExoPlayer install replacement media only after resume. Their public state can
        // become PLAYING before the replacement timeline is usable, so wait for it to advance before
        // seeking. Otherwise the media initialization can overwrite the restored position.
        resumeAndAwaitMediaStart()
        withContext(mainDispatcher) {
            player.seekTo(positionMillis)
            if (!shouldResume) {
                player.pause()
            }
        }
        restoreTrackSelection(trackSelection)
    }

    private suspend fun rollbackPlayback(
        previousData: MediaData,
        positionMillis: Long,
        shouldResume: Boolean,
        trackSelection: TrackSelectionSnapshot,
    ) {
        try {
            player.setMediaData(previousData)
            restorePlayback(positionMillis, shouldResume, trackSelection)
        } catch (rollbackError: Throwable) {
            logger.warn(rollbackError) {
                "Failed to restore the previous playback after a Jellyfin quality switch error"
            }
        }
    }

    private fun snapshotTrackSelection(): TrackSelectionSnapshot {
        return TrackSelectionSnapshot(
            audio = player.audioTracks?.selected?.value,
            subtitle = player.subtitleTracks?.selected?.value,
        )
    }

    private suspend fun restoreTrackSelection(selection: TrackSelectionSnapshot) {
        restoreTrackSelection(player.audioTracks, selection.audio) { candidate, selected ->
            candidate.internalId == selected.internalId || candidate.id == selected.id
        }
        restoreTrackSelection(player.subtitleTracks, selection.subtitle) { candidate, selected ->
            candidate.internalId == selected.internalId || candidate.id == selected.id
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

    private fun closeHlsPlaybackProxySession() {
        hlsPlaybackProxySession?.close()
        hlsPlaybackProxySession = null
    }

    companion object {
        private const val JELLYFIN_SWITCH_TIMEOUT_MILLIS = 15_000L
        private const val MEDIA_START_POLL_INTERVAL_MILLIS = 50L
        private const val TRACK_RESTORE_TIMEOUT_MILLIS = 2_000L
        private val logger = logger<PlayerSession>()
    }

    private data class PreparedMediaData(
        val data: MediaData,
        val session: HlsPlaybackProxySession? = null,
    )

    private data class TrackSelectionSnapshot(
        val audio: AudioTrack?,
        val subtitle: SubtitleTrack?,
    )
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
