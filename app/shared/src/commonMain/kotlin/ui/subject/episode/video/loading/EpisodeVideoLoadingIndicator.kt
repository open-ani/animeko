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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import me.him188.ani.app.ui.foundation.TextWithBorder
import me.him188.ani.app.ui.subject.episode.statistics.VideoLoadingState
import me.him188.ani.app.videoplayer.ui.VideoLoadingIndicator
import me.him188.ani.app.videoplayer.ui.state.PlaybackState
import me.him188.ani.app.videoplayer.ui.state.PlayerState
import me.him188.ani.datasources.api.topic.FileSize
import kotlin.time.Duration.Companion.seconds

@Composable // see preview
fun EpisodeVideoLoadingIndicator(
    playerState: PlayerState,
    videoLoadingState: VideoLoadingState,
    optimizeForFullscreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val isBuffering by playerState.isBuffering.collectAsStateWithLifecycle(false)
    val state by playerState.state.collectAsStateWithLifecycle()

    val speed by remember(playerState) {
        playerState.videoData.filterNotNull().flatMapLatest { video ->
            video.networkStats.map { it.downloadSpeed }
        }
    }.collectAsStateWithLifecycle(FileSize.Unspecified)

    if (isBuffering ||
        state == PlaybackState.PAUSED_BUFFERING || // 如果不加这个, 就会有一段时间资源名字还没显示出来, 也没显示缓冲中
        state == PlaybackState.ERROR ||
        videoLoadingState !is VideoLoadingState.Succeed
    ) {
        EpisodeVideoLoadingIndicator(
            videoLoadingState,
            speedProvider = { speed },
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
                        TextWithBorder("请在右上角选择数据源，或等待自动选择")
                    } else {
                        TextWithBorder("请选择数据源，或等待自动选择")
                    }
                }

                VideoLoadingState.ResolvingSource -> {
                    TextWithBorder(
                        "正在解析资源链接\n若 30 秒内未完成, 请尝试切换数据源",
                        textAlign = TextAlign.Center,
                    )
                }

                is VideoLoadingState.DecodingData -> {
                    TextWithBorder(
                        if (!state.isBt) {
                            "资源解析成功, 正在准备视频"
                        } else {
                            "正在解析磁力链或查询元数据\n若 15 秒内未完成, 请尝试切换数据源或先缓存再看"
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
