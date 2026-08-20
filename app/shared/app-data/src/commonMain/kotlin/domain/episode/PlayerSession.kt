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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.him188.ani.app.domain.media.hls.HlsPlaybackPreparer
import me.him188.ani.app.domain.media.hls.HlsPlaybackProxySession
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.JellyfinMediaDataProvider
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
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.jellyfin.JellyfinPlaybackQuality
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.core.Koin
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.PlaybackException
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.UriMediaData
import kotlin.coroutines.CoroutineContext

class MediaFetchSelectBundle(
    val mediaFetchSession: MediaFetchSession,
    val mediaSelector: MediaSelector,
)

/**
 * Playback values captured immediately before replacing a Jellyfin stream.
 */
data class JellyfinPlaybackReplacementSnapshot(
    val positionMillis: Long,
    val durationMillis: Long,
    val playWhenReady: Boolean,
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

    private var hlsPlaybackProxySession: HlsPlaybackProxySession? = null
    private val jellyfinPlaybackController = JellyfinPlaybackController(player, mainDispatcher)
    private val playbackOperationMutex = Mutex()

    private val _videoLoadingStateFlow: MutableStateFlow<VideoLoadingState> =
        MutableStateFlow(VideoLoadingState.Initial)

    /**
     * 当前的视频加载状态.
     */
    val videoLoadingState: StateFlow<VideoLoadingState> get() = _videoLoadingStateFlow.asStateFlow()

    val jellyfinPlaybackQualityState get() = jellyfinPlaybackController.qualityState

    /**
     * 解析 media 并开始播放这个 media.
     */
    suspend fun loadMedia(media: Media?, episodeInfo: EpisodeMetadata) = playbackOperationMutex.withLock {
        loadMediaLocked(media, episodeInfo)
    }

    private suspend fun loadMediaLocked(media: Media?, episodeInfo: EpisodeMetadata) = coroutineScope {
        val backgroundScope = this
        _videoLoadingStateFlow.value = VideoLoadingState.Initial // 避免一直显示已取消 (.Cancelled)
        stopPlaybackLocked()
        if (media == null) {
            return@coroutineScope
        }

        var preparedHlsPlaybackProxySession: HlsPlaybackProxySession? = null
        var openedJellyfinProvider: JellyfinMediaDataProvider? = null
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

            val jellyfinProvider = source as? JellyfinMediaDataProvider
            openedJellyfinProvider = jellyfinProvider
            val data = source.open(scopeForCleanup = backgroundScope) // may throw MediaSourceOpenException
            val preparedData = prepareHlsPlaybackIfEnabled(data).also {
                preparedHlsPlaybackProxySession = it.session
            }.data

            logger.info { "Set media data to player: $preparedData" }
            // v2: setMediaData 挂起直到媒体真正打开, 并直接携带播放意图, 无需再单独 resume.
            player.setMediaData(preparedData, playWhenReady = true)
            hlsPlaybackProxySession = preparedHlsPlaybackProxySession
            preparedHlsPlaybackProxySession = null
            jellyfinPlaybackController.install(jellyfinProvider)
            openedJellyfinProvider = null

            _videoLoadingStateFlow.value = VideoLoadingState.Succeed(isBt = source is TorrentBackedMediaDataProvider)
        } catch (e: UnsupportedMediaException) {
            logger.warn { IllegalStateException("Failed to resolve video source, unsupported media", e) }
            _videoLoadingStateFlow.value = VideoLoadingState.UnsupportedMedia
            stopPlaybackLocked()
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
            stopPlaybackLocked()
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
            stopPlaybackLocked()
        } catch (e: CancellationException) { // 切换数据源 (含 MediaLoadCancellationException)
            _videoLoadingStateFlow.value = VideoLoadingState.Cancelled
            throw e
        } catch (e: PlaybackException) { // during player.setMediaData, 播放器拒绝了这个媒体
            logger.warn { IllegalStateException("Player rejected the media data", e) }
            _videoLoadingStateFlow.value = VideoLoadingState.UnknownError(e)
            stopPlaybackLocked()
        } catch (e: Throwable) {
            logger.error { IllegalStateException("Failed to resolve video source with unknown error", e) }
            _videoLoadingStateFlow.value = VideoLoadingState.UnknownError(e)
            stopPlaybackLocked()
        } finally {
            closeHlsPlaybackProxySession(preparedHlsPlaybackProxySession)
            withContext(NonCancellable) {
                openedJellyfinProvider?.let { provider ->
                    jellyfinPlaybackController.discard(provider)
                }
            }
        }
    }

    suspend fun switchJellyfinPlaybackQuality(
        quality: JellyfinPlaybackQuality,
        beforeReplace: suspend (JellyfinPlaybackReplacementSnapshot) -> Unit = {},
    ): Result<Unit> {
        val owner = jellyfinPlaybackController.captureOwner()
            ?: return Result.failure(IllegalStateException("The current media is not from Jellyfin"))

        return playbackOperationMutex.withLock {
            jellyfinPlaybackController.switch(
                owner = owner,
                quality = quality,
                prepareMediaData = { data ->
                    prepareHlsPlaybackIfEnabled(data).let {
                        PreparedJellyfinMediaData(it.data, it.session)
                    }
                },
                replaceProxySession = { next ->
                    hlsPlaybackProxySession.also {
                        hlsPlaybackProxySession = next
                    }
                },
                onReplacementFailure = ::stopPlaybackLockedSafely,
                beforeReplace = beforeReplace,
            )
        }
    }

    suspend fun stopPlayback() {
        playbackOperationMutex.withLock {
            stopPlaybackLocked()
        }
    }

    suspend fun close() {
        playbackOperationMutex.withLock {
            stopPlaybackLocked()
            player.close()
        }
    }

    private suspend fun stopPlaybackLocked() {
        val jellyfinPlayback = jellyfinPlaybackController.detach()

        var stopFailure: Throwable? = null
        try {
            stopPlayer()
        } catch (e: Throwable) {
            stopFailure = e
        }
        closeHlsPlaybackProxySession(hlsPlaybackProxySession)
        hlsPlaybackProxySession = null
        withContext(NonCancellable) {
            jellyfinPlaybackController.stopEncoding(jellyfinPlayback)
        }
        stopFailure?.let { throw it }
    }

    private suspend fun stopPlaybackLockedSafely() {
        try {
            stopPlaybackLocked()
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to stop playback while cleaning up Jellyfin resources" }
        }
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

    private fun closeHlsPlaybackProxySession(session: HlsPlaybackProxySession?) {
        if (session == null) return
        try {
            session.close()
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to close an HLS playback proxy session" }
        }
    }

    companion object {
        private val logger = logger<PlayerSession>()
    }

    private data class PreparedMediaData(
        val data: MediaData,
        val session: HlsPlaybackProxySession? = null,
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
