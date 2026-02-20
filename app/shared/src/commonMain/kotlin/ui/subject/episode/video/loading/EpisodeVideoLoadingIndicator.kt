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
import me.him188.ani.app.videoplayer.ui.VideoLoadingIndicator
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.Unspecified
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
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
    VideoLoadingIndicator(
        showProgress = state is VideoLoadingState.Progressing,
        text = {
            if (playerError) {
                TextWithBorder("播放失败, 请更换数据源", color = MaterialTheme.colorScheme.error)
                return@VideoLoadingIndicator
            }
            when (state) {
                VideoLoadingState.Initial -> {
                    if (optimizeForFullscreen) {
                        TextWithBorder("正在自动选择数据源，请稍候")
                    } else {
                        TextWithBorder("正在自动选择数据源，请稍候")
                    }
                }

                VideoLoadingState.ResolvingSource -> {
                    TextWithBorder(
                        "正在解析资源链接\n通常几秒内完成，否则请切换数据源",
                        textAlign = TextAlign.Center,
                    )
                }

                is VideoLoadingState.DecodingData -> {
                    TextWithBorder(
                        if (!state.isBt) {
                            "资源解析成功, 正在准备视频"
                        } else {
                            "正在解析磁力链或查询元数据\n通常几秒内完成, 否则请尝试切换数据源或先缓存再看"
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
                                append("正在缓冲")
                                if (speed != FileSize.Unspecified) {
                                    appendLine()
                                    append(speed.toString())
                                    append("/s")
                                }

                                if (tooLong) {
                                    appendLine()
                                    if (state.isBt) {
                                        append("BT 初始缓冲耗时稍长, 请耐心等待 30 秒")
                                        appendLine()
                                        append("若持续没有速度, 可尝试切换数据源")
                                    } else {
                                        append("缓冲耗时过长, 可尝试切换数据源")
                                    }
                                }
                            }
                        }
                    }

                    TextWithBorder(text, textAlign = TextAlign.Center)
                }

                is VideoLoadingState.Failed -> {
                    TextWithBorder(
                        "加载失败: ${renderCause(state)}",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        modifier,
    )
}

fun renderCause(cause: VideoLoadingState.Failed): String = when (cause) {
    is VideoLoadingState.ResolutionTimedOut -> "解析超时"
    is VideoLoadingState.UnknownError -> "未知错误"
    is VideoLoadingState.UnsupportedMedia -> "不支持该文件类型"
    VideoLoadingState.NoMatchingFile -> "未找到可播放的文件"
    VideoLoadingState.Cancelled -> "已取消"
    VideoLoadingState.NetworkError -> "网络错误"
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
