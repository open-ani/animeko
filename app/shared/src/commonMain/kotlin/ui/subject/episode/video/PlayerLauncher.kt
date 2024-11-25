/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.ResolutionFailures
import me.him188.ani.app.domain.media.resolver.TorrentVideoSource
import me.him188.ani.app.domain.media.resolver.UnsupportedMediaException
import me.him188.ani.app.domain.media.resolver.VideoSourceResolutionException
import me.him188.ani.app.domain.media.resolver.VideoSourceResolver
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.ui.foundation.BackgroundScope
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSourceInfoProvider
import me.him188.ani.app.ui.subject.episode.statistics.VideoLoadingState
import me.him188.ani.app.ui.subject.episode.statistics.VideoStatistics
import me.him188.ani.app.videoplayer.data.OpenFailures
import me.him188.ani.app.videoplayer.data.VideoSource
import me.him188.ani.app.videoplayer.data.VideoSourceOpenException
import me.him188.ani.app.videoplayer.ui.state.PlayerState
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext

/**
 * 将 [MediaSelector] 和 [videoSourceResolver] 结合, 为 [playerState] 提供视频源.
 * 还会提供 [videoStatistics], 可以获取当前的加载状态.
 *
 * 这实际上就是启动了一个一直运行的协程:
 * 当 [MediaSelector] 选择到资源的时候, 使用 [videoSourceResolver] 解析出 [VideoSource],
 * 然后把它设置给 [playerState] (通过 [PlayerState.setVideoSource]).
 */
class PlayerLauncher(
    mediaSelector: MediaSelector,
    private val videoSourceResolver: VideoSourceResolver,
    private val playerState: PlayerState,
    private val mediaSourceInfoProvider: MediaSourceInfoProvider,
    episodeInfo: Flow<EpisodeInfo?>,
    mediaSourceLoading: Flow<Boolean>,
    parentCoroutineContext: CoroutineContext,
) : HasBackgroundScope by BackgroundScope(parentCoroutineContext) {
    private companion object {
        private val logger = logger<PlayerLauncher>()
    }

    private val videoLoadingStateFlow: MutableStateFlow<VideoLoadingState> = MutableStateFlow(VideoLoadingState.Initial)

    val videoStatistics: VideoStatistics = VideoStatistics(
        playingMedia = mediaSelector.selected.produceState(),
        playingMediaSourceInfo = mediaSelector.selected.flatMapLatest {
            mediaSourceInfoProvider.getSourceInfoFlow(it?.mediaSourceId ?: return@flatMapLatest emptyFlow())
        }.produceState(null),
        playingFilename = playerState.videoData.map { it?.filename }.produceState(null),
        mediaSourceLoading = mediaSourceLoading.produceState(true),
        videoLoadingState = videoLoadingStateFlow.produceState(VideoLoadingState.Initial),
    )

    init {
        mediaSelector.selected.transformLatest { media ->
            videoLoadingStateFlow.value = VideoLoadingState.Initial // 避免一直显示已取消 (.Cancelled)
            playerState.clearVideoSource() // 只要 media 换了就清空
            if (media == null) {
                return@transformLatest
            }

            try {
                val info = episodeInfo.filterNotNull().first()
                videoLoadingStateFlow.value = VideoLoadingState.ResolvingSource
                val source = videoSourceResolver.resolve(
                    media,
                    EpisodeMetadata(
                        title = info.displayName,
                        ep = info.ep,
                        sort = info.sort,
                    ),
                )
                videoLoadingStateFlow.compareAndSet(
                    VideoLoadingState.ResolvingSource,
                    VideoLoadingState.DecodingData(isBt = media.kind == MediaSourceKind.BitTorrent),
                )
                playerState.setVideoSource(source)
                logger.info { "playerState.applySourceToPlayer with source = $source" }
                videoLoadingStateFlow.value = VideoLoadingState.Succeed(isBt = source is TorrentVideoSource)
            } catch (e: UnsupportedMediaException) {
                logger.error { IllegalStateException("Failed to resolve video source, unsupported media", e) }
                videoLoadingStateFlow.value = VideoLoadingState.UnsupportedMedia
                playerState.clearVideoSource()
            } catch (e: VideoSourceOpenException) { // during playerState.setVideoSource
                logger.error {
                    IllegalStateException(
                        "Failed to resolve video source due to VideoSourceOpenException",
                        e,
                    )
                }
                videoLoadingStateFlow.value = when (e.reason) {
                    OpenFailures.NO_MATCHING_FILE -> VideoLoadingState.NoMatchingFile
                    OpenFailures.UNSUPPORTED_VIDEO_SOURCE -> VideoLoadingState.UnsupportedMedia
                    OpenFailures.ENGINE_DISABLED -> VideoLoadingState.UnsupportedMedia
                }
                playerState.clearVideoSource()
            } catch (e: VideoSourceResolutionException) { // during videoSourceResolver.resolve
                logger.error {
                    IllegalStateException(
                        "Failed to resolve video source due to VideoSourceResolutionException",
                        e,
                    )
                }
                videoLoadingStateFlow.value = when (e.reason) {
                    ResolutionFailures.FETCH_TIMEOUT -> VideoLoadingState.ResolutionTimedOut
                    ResolutionFailures.ENGINE_ERROR -> VideoLoadingState.UnknownError(e)
                    ResolutionFailures.NETWORK_ERROR -> VideoLoadingState.NetworkError
                    ResolutionFailures.NO_MATCHING_RESOURCE -> VideoLoadingState.NoMatchingFile
                }
                playerState.clearVideoSource()
            } catch (e: CancellationException) { // 切换数据源
                videoLoadingStateFlow.value = VideoLoadingState.Cancelled
                throw e
            } catch (e: Throwable) {
                logger.error { IllegalStateException("Failed to resolve video source with unknown error", e) }
                videoLoadingStateFlow.value = VideoLoadingState.UnknownError(e)
                playerState.clearVideoSource()
                emit(null)
            }
        }.launchIn(backgroundScope)
    }
}
