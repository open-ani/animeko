/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video.loading

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.domain.media.player.data.DownloadingMediaData
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.TextWithBorder
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_video_loading_auto_selecting
import me.him188.ani.app.ui.lang.subject_episode_video_loading_buffering
import me.him188.ani.app.ui.lang.subject_episode_video_loading_buffering_bt_no_speed_try_switch
import me.him188.ani.app.ui.lang.subject_episode_video_loading_buffering_bt_too_long
import me.him188.ani.app.ui.lang.subject_episode_video_loading_buffering_too_long
import me.him188.ani.app.ui.lang.subject_episode_video_loading_cause_cancelled
import me.him188.ani.app.ui.lang.subject_episode_video_loading_cause_network_error
import me.him188.ani.app.ui.lang.subject_episode_video_loading_cause_no_matching_file
import me.him188.ani.app.ui.lang.subject_episode_video_loading_cause_resolution_timed_out
import me.him188.ani.app.ui.lang.subject_episode_video_loading_cause_unknown_error
import me.him188.ani.app.ui.lang.subject_episode_video_loading_cause_unsupported_media
import me.him188.ani.app.ui.lang.subject_episode_video_loading_decoding_bt
import me.him188.ani.app.ui.lang.subject_episode_video_loading_decoding_data
import me.him188.ani.app.ui.lang.subject_episode_video_loading_failed_prefix
import me.him188.ani.app.ui.lang.subject_episode_video_loading_player_error
import me.him188.ani.app.ui.lang.subject_episode_video_loading_resolving_source
import me.him188.ani.app.videoplayer.ui.VideoLoadingIndicator
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.Unspecified
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import org.jetbrains.compose.resources.stringResource
import org.openani.mediamp.ExperimentalMediampApi
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.features.Buffering
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMediampApi::class)
@Composable // see preview
fun EpisodeVideoLoadingIndicator(
    playerState: MediampPlayer,
    videoLoadingState: VideoLoadingState,
    optimizeForFullscreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val buffering = playerState.features[Buffering]
    val isBuffering by (buffering?.isBuffering ?: remember { flowOf(false) }).collectAsStateWithLifecycle(false)
    val state by playerState.playbackState.collectAsStateWithLifecycle()

    val speed by remember(playerState) {
        playerState.mediaData.filterNotNull().flatMapLatest { video ->
            if (video is DownloadingMediaData) {
                video.networkStats
            } else {
                flowOf(null)
            }
        }
    }.collectAsStateWithLifecycle(null)

    if (isBuffering ||
        state == PlaybackState.PAUSED_BUFFERING || // 如果不加这个, 就会有一段时间资源名字还没显示出来, 也没显示缓冲中
        state == PlaybackState.ERROR ||
        videoLoadingState !is VideoLoadingState.Succeed
    ) {
        EpisodeVideoLoadingIndicator(
            videoLoadingState,
            speedProvider = {
                speed?.downloadSpeed?.bytes ?: FileSize.Unspecified
            },
            optimizeForFullscreen = optimizeForFullscreen,
            playerError = state == PlaybackState.ERROR,
            modifier = modifier,
        )
    }
}

@Composable
fun EpisodeVideoLoadingIndicator(
    state: VideoLoadingState,
    speedProvider: () -> FileSize,
    optimizeForFullscreen: Boolean,
    playerError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val playerErrorText = stringResource(Lang.subject_episode_video_loading_player_error)
    val autoSelectingText = stringResource(Lang.subject_episode_video_loading_auto_selecting)
    val resolvingSourceText = stringResource(Lang.subject_episode_video_loading_resolving_source)
    val decodingDataText = stringResource(Lang.subject_episode_video_loading_decoding_data)
    val decodingBtText = stringResource(Lang.subject_episode_video_loading_decoding_bt)
    val bufferingText = stringResource(Lang.subject_episode_video_loading_buffering)
    val bufferingBtTooLongText = stringResource(Lang.subject_episode_video_loading_buffering_bt_too_long)
    val bufferingNoSpeedTrySwitchText =
        stringResource(Lang.subject_episode_video_loading_buffering_bt_no_speed_try_switch)
    val bufferingTooLongText = stringResource(Lang.subject_episode_video_loading_buffering_too_long)
    val failedPrefix = stringResource(Lang.subject_episode_video_loading_failed_prefix)
    val causeLabels = VideoLoadingCauseLabels(
        resolutionTimedOut = stringResource(Lang.subject_episode_video_loading_cause_resolution_timed_out),
        unknownError = stringResource(Lang.subject_episode_video_loading_cause_unknown_error),
        unsupportedMedia = stringResource(Lang.subject_episode_video_loading_cause_unsupported_media),
        noMatchingFile = stringResource(Lang.subject_episode_video_loading_cause_no_matching_file),
        cancelled = stringResource(Lang.subject_episode_video_loading_cause_cancelled),
        networkError = stringResource(Lang.subject_episode_video_loading_cause_network_error),
    )
    VideoLoadingIndicator(
        showProgress = state is VideoLoadingState.Progressing,
        text = {
            if (playerError) {
                TextWithBorder(playerErrorText, color = MaterialTheme.colorScheme.error)
                return@VideoLoadingIndicator
            }
            when (state) {
                VideoLoadingState.Initial -> {
                    TextWithBorder(autoSelectingText)
                }

                VideoLoadingState.ResolvingSource -> {
                    TextWithBorder(
                        resolvingSourceText,
                        textAlign = TextAlign.Center,
                    )
                }

                is VideoLoadingState.DecodingData -> {
                    TextWithBorder(
                        if (!state.isBt) {
                            decodingDataText
                        } else {
                            decodingBtText
                        },
                        textAlign = TextAlign.Center,
                    )
                }

                is VideoLoadingState.Succeed -> {
                    var tooLong by rememberSaveable {
                        mutableStateOf(false)
                    }
                    val speed by remember { derivedStateOf(speedProvider) }
                    val speedIsZero by remember { derivedStateOf { speed == FileSize.Zero } }
                    if (speedIsZero) {
                        LaunchedEffect(true) {
                            delay(15.seconds)
                            tooLong = true
                        }
                    }
                    val text by remember {
                        derivedStateOf {
                            buildString {
                                append(bufferingText)
                                if (speed != FileSize.Unspecified) {
                                    appendLine()
                                    append(speed.toString())
                                    append("/s")
                                }

                                if (tooLong) {
                                    appendLine()
                                    if (state.isBt) {
                                        append(bufferingBtTooLongText)
                                        appendLine()
                                        append(bufferingNoSpeedTrySwitchText)
                                    } else {
                                        append(bufferingTooLongText)
                                    }
                                }
                            }
                        }
                    }

                    TextWithBorder(text, textAlign = TextAlign.Center)
                }

                is VideoLoadingState.Failed -> {
                    TextWithBorder(
                        "$failedPrefix${renderCause(state, causeLabels)}",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        modifier,
    )
}

private data class VideoLoadingCauseLabels(
    val resolutionTimedOut: String,
    val unknownError: String,
    val unsupportedMedia: String,
    val noMatchingFile: String,
    val cancelled: String,
    val networkError: String,
)

private fun renderCause(cause: VideoLoadingState.Failed, labels: VideoLoadingCauseLabels): String = when (cause) {
    is VideoLoadingState.ResolutionTimedOut -> labels.resolutionTimedOut
    is VideoLoadingState.UnknownError -> labels.unknownError
    is VideoLoadingState.UnsupportedMedia -> labels.unsupportedMedia
    VideoLoadingState.NoMatchingFile -> labels.noMatchingFile
    VideoLoadingState.Cancelled -> labels.cancelled
    VideoLoadingState.NetworkError -> labels.networkError
}

@Preview(name = "Selecting Media")
@Composable
private fun PreviewEpisodeVideoLoadingIndicator() {
    ProvideCompositionLocalsForPreview {
        EpisodeVideoLoadingIndicator(
            VideoLoadingState.Initial,
            speedProvider = { 0.3.bytes },
            optimizeForFullscreen = false,
        )
    }
}

@Preview(name = "Selecting Media")
@Composable
private fun PreviewEpisodeVideoLoadingIndicatorFullscreen() {
    ProvideCompositionLocalsForPreview {
        EpisodeVideoLoadingIndicator(
            VideoLoadingState.Initial,
            speedProvider = { 0.3.bytes },
            optimizeForFullscreen = true,
        )
    }
}

@Preview(name = "ResolvingSource")
@Composable
private fun PreviewEpisodeVideoLoadingIndicator2() {
    ProvideCompositionLocalsForPreview {
        EpisodeVideoLoadingIndicator(
            VideoLoadingState.ResolvingSource,
            speedProvider = { 0.3.bytes },
            optimizeForFullscreen = false,
        )
    }
}

@Preview(name = "ResolvingSource")
@Composable
private fun PreviewEpisodeVideoLoadingIndicator5() {
    ProvideCompositionLocalsForPreview {
        EpisodeVideoLoadingIndicator(
            VideoLoadingState.DecodingData(true),
            speedProvider = { 0.3.bytes },
            optimizeForFullscreen = false,
        )
    }
}

private fun successState() = VideoLoadingState.Succeed(isBt = true)

@Preview(name = "Buffering")
@Composable
private fun PreviewEpisodeVideoLoadingIndicator3() {
    ProvideCompositionLocalsForPreview {
        EpisodeVideoLoadingIndicator(
            successState(),
            speedProvider = { 0.3.bytes },
            optimizeForFullscreen = false,
        )
    }
}

@Preview(name = "Failed")
@Composable
private fun PreviewEpisodeVideoLoadingIndicator7() {
    ProvideCompositionLocalsForPreview {
        EpisodeVideoLoadingIndicator(
            VideoLoadingState.ResolutionTimedOut,
            speedProvider = { Unspecified },
            optimizeForFullscreen = false,
        )
    }
}

@Preview(name = "Buffering - No Speed")
@Composable
private fun PreviewEpisodeVideoLoadingIndicator4() {
    ProvideCompositionLocalsForPreview {
        EpisodeVideoLoadingIndicator(
            successState(),
            speedProvider = { Unspecified },
            optimizeForFullscreen = false,
        )
    }
}
