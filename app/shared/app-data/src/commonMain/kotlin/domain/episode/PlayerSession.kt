/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.MediaSourceOpenException
import me.him188.ani.app.domain.media.resolver.OpenFailures
import me.him188.ani.app.domain.media.resolver.ResolutionFailures
import me.him188.ani.app.domain.media.resolver.TorrentMediaDataProvider
import me.him188.ani.app.domain.media.resolver.UnsupportedMediaException
import me.him188.ani.app.domain.media.resolver.VideoSourceResolutionException
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.openani.mediamp.MediampPlayer
import kotlin.coroutines.CoroutineContext

class MediaFetchSelectBundle(
    val mediaFetchSession: MediaFetchSession,
    val mediaSelector: MediaSelector,
)

// episodeId 改变, 需要全部清空
// 如果 episodeId 不变, 但是 EpisodeCollectionInfo 变了, 只需要更新一些信息.

/**
 * PlayerSession 封装对 [MediampPlayer] 的控制. 主要是解析 Media 并播放: [loadMedia].
 */
class PlayerSession(
    val player: MediampPlayer,
    private val koin: Koin,
    private val mainDispatcher: CoroutineContext = Dispatchers.Main.immediate,
) : KoinComponent {
    private val mediaResolver: MediaResolver by inject()

    private val _videoLoadingStateFlow: MutableStateFlow<VideoLoadingState> =
        MutableStateFlow(VideoLoadingState.Initial)

    /**
     * 当前的视频加载状态.
     */
    val videoLoadingState: StateFlow<VideoLoadingState> get() = _videoLoadingStateFlow.asStateFlow()

    /**
     * 解析 media 并开始播放这个 media.
     */
    suspend fun loadMedia(media: Media?, episodeInfo: EpisodeMetadata) = coroutineScope {
        val backgroundScope = this
        _videoLoadingStateFlow.value = VideoLoadingState.Initial // 避免一直显示已取消 (.Cancelled)
        stopPlayer()
        if (media == null) {
            return@coroutineScope
        }

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
            player.setMediaData(data)
            logger.info { "playerState.applySourceToPlayer with source = $source" }
            _videoLoadingStateFlow.value = VideoLoadingState.Succeed(isBt = source is TorrentMediaDataProvider)
        } catch (e: UnsupportedMediaException) {
            logger.error { IllegalStateException("Failed to resolve video source, unsupported media", e) }
            _videoLoadingStateFlow.value = VideoLoadingState.UnsupportedMedia
            stopPlayer()
        } catch (e: MediaSourceOpenException) { // during playerState.setVideoSource
            logger.error {
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
            stopPlayer()
        } catch (e: VideoSourceResolutionException) { // during videoSourceResolver.resolve
            logger.error {
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
            stopPlayer()
        } catch (e: CancellationException) { // 切换数据源
            _videoLoadingStateFlow.value = VideoLoadingState.Cancelled
            throw e
        } catch (e: Throwable) {
            logger.error { IllegalStateException("Failed to resolve video source with unknown error", e) }
            _videoLoadingStateFlow.value = VideoLoadingState.UnknownError(e)
            stopPlayer()
        }
    }

    fun close() {
        player.close()
    }

    private suspend fun stopPlayer() {
        withContext(mainDispatcher) {
            player.stopPlayback()
        }
    }

    override fun getKoin(): Koin = koin

    companion object {
        private val logger = logger<PlayerSession>()
    }
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
//            mediaSourceManager.createFetchFetchSessionFlow(flowOf(request))
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
