/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.danmaku.DanmakuLoadingState
import me.him188.ani.app.domain.media.fetch.MediaFetcher
import me.him188.ani.app.domain.media.player.data.filenameOrNull
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_select_media_source
import me.him188.ani.app.ui.lang.subject_episode_statistics_danmaku_disabled
import me.him188.ani.app.ui.lang.subject_episode_statistics_danmaku_load_failed_tap
import me.him188.ani.app.ui.lang.subject_episode_statistics_danmaku_loading
import me.him188.ani.app.ui.lang.subject_episode_statistics_danmaku_sources_count
import me.him188.ani.app.ui.lang.subject_episode_statistics_error_message
import me.him188.ani.app.ui.lang.subject_episode_statistics_now_playing
import me.him188.ani.app.ui.lang.subject_episode_statistics_show_less
import me.him188.ani.app.ui.lang.subject_episode_statistics_show_more
import me.him188.ani.app.ui.media.rememberMediaDetailsStrings
import me.him188.ani.app.ui.media.renderProperties
import me.him188.ani.app.ui.mediafetch.MediaSourceInfoProvider
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceInfo
import org.jetbrains.compose.resources.stringResource
import org.openani.mediamp.MediampPlayer

class VideoStatisticsCollector(
    mediaSelector: Flow<MediaSelector>,
    videoLoadingStateFlow: Flow<VideoLoadingState>,
    playerState: MediampPlayer,
    private val mediaSourceInfoProvider: MediaSourceInfoProvider,
    mediaSourceLoading: Flow<Boolean>,
    backgroundScope: CoroutineScope,
) {
    val videoStatisticsFlow: StateFlow<VideoStatistics> = kotlin.run {
        val selectedMediaFlow = mediaSelector.flatMapLatest { it.selected }
        combine(
            selectedMediaFlow,
            selectedMediaFlow.flatMapLatest {
                mediaSourceInfoProvider.getSourceInfoFlow(it?.mediaSourceId ?: return@flatMapLatest emptyFlow())
            },
            selectedMediaFlow
                .combine(playerState.mediaData.map { it?.filenameOrNull }) { selectedMedia, filename ->
                    filename ?: selectedMedia?.originalTitle
                },
            mediaSourceLoading,
            videoLoadingStateFlow,
            ::VideoStatistics,
        ).stateIn(
            backgroundScope,
            SharingStarted.WhileSubscribed(),
            VideoStatistics.Placeholder,
        )
    }
}


/**
 * 视频统计信息, 用于获取当前播放器正在播放的视频的来源 [Media] 和文件名, 以及弹幕信息.
 */
data class VideoStatistics(
    /**
     * 从 [MediaSelector] 选择后就有
     */
    val playingMedia: Media?,
    /**
     * [playingMedia] 所属的 [MediaSourceInfo]
     */
    val playingMediaSourceInfo: MediaSourceInfo?,
    /**
     * 要播放器获取到视频文件后才有
     */
    val playingFilename: String?,
    /**
     * [MediaFetcher] 的所有数据源是否都已经加载完成.
     */
    val mediaSourceLoading: Boolean,
    val videoLoadingState: VideoLoadingState,
    val isPlaceholder: Boolean = false,
) {
    companion object {
        val Placeholder = VideoStatistics(
            null, null, null, false, VideoLoadingState.Initial,
            isPlaceholder = true,
        )
    }
}

@Composable
fun DanmakuMatchInfoSummaryBanner(
    danmakuStatistics: DanmakuStatistics,
    expanded: Boolean,
    toggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val danmakuLoadingState = danmakuStatistics.danmakuLoadingState
    val danmakuLoadFailedTapText = stringResource(Lang.subject_episode_statistics_danmaku_load_failed_tap)
    val showLessText = stringResource(Lang.subject_episode_statistics_show_less)
    val showMoreText = stringResource(Lang.subject_episode_statistics_show_more)
    val danmakuDisabledText = stringResource(Lang.subject_episode_statistics_danmaku_disabled)
    val danmakuLoadingText = stringResource(Lang.subject_episode_statistics_danmaku_loading)
    var showDialog by rememberSaveable { mutableStateOf(false) }
    if (showDialog) {
        val text = remember(danmakuLoadingState) {
            when (danmakuLoadingState) {
                is DanmakuLoadingState.Failed -> danmakuLoadingState.cause.stackTraceToString()
                else -> danmakuLoadingState.toString()
            }
        }
        SimpleErrorDialog(
            { text },
            onDismissRequest = { showDialog = false },
        )
    }
    Card(
        onClick = {
            if (danmakuLoadingState is DanmakuLoadingState.Failed) {
                showDialog = true
            } else {
                toggleExpanded()
            }
        },
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (danmakuLoadingState) {
                is DanmakuLoadingState.Failed -> {
                    ProvideContentColor(MaterialTheme.colorScheme.error) {
                        Icon(Icons.Rounded.ErrorOutline, null)
                        Text(
                            danmakuLoadFailedTapText,
                            Modifier.weight(1f).padding(start = 12.dp),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Icon(Icons.Outlined.ChevronRight, danmakuLoadFailedTapText, Modifier.size(20.dp))
                    }
                }

                is DanmakuLoadingState.Success -> {
                    Icon(
                        Icons.Rounded.Subtitles,
                        contentDescription = null,
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(
                            Lang.subject_episode_statistics_danmaku_sources_count,
                            danmakuStatistics.fetchResults.size,
                            danmakuStatistics.fetchResults.sumOf { it.matchInfo.count },
                        ),
                        Modifier.weight(1f).padding(start = 12.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (expanded) {
                        Icon(Icons.Rounded.ArrowDropUp, showLessText, Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Outlined.ChevronRight, showMoreText, Modifier.size(20.dp))
                    }
                }

                DanmakuLoadingState.Idle,
                DanmakuLoadingState.Loading -> {
                    if (!danmakuStatistics.danmakuEnabled) {
                        Text(
                            danmakuDisabledText,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    } else {
                        Text(
                            danmakuLoadingText,
                            Modifier.weight(1f),
                            softWrap = false,
                            style = MaterialTheme.typography.titleSmall,
                        )

                        CircularProgressIndicator(Modifier.padding(start = 16.dp).size(20.dp), strokeWidth = 3.dp)
                    }
                }
            }
        }
    }
}


@Composable
fun VideoStatistics(
    state: VideoStatistics,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val mediaDetailsStrings = rememberMediaDetailsStrings()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
            when (val loadingState = state.videoLoadingState) {
                is VideoLoadingState.Succeed -> {
                    val mediaPropertiesText by remember(state.playingMedia, mediaDetailsStrings) {
                        derivedStateOf {
                            state.playingMedia?.renderProperties(mediaDetailsStrings)
                        }
                    }
                    NowPlayingLabel(mediaPropertiesText, state.playingFilename)
                }

                is VideoLoadingState.UnknownError -> {
                    ErrorTextBox(
                        remember(loadingState) { loadingState.cause.toString() },
                        {
                            scope.launch {
                                clipboard.setClipEntryText(loadingState.cause.stackTraceToString())
                            }
                        },
                        Modifier.padding(top = 8.dp).fillMaxWidth(),
                    )
                }

                else -> {}
            }
        }
    }
}

/**
 * 显示正在播放的那行字
 */
@Composable
private fun NowPlayingLabel(
    playingMedia: String?,
    filename: String?,
    modifier: Modifier = Modifier,
) {
    val nowPlayingText = stringResource(Lang.subject_episode_statistics_now_playing)
    val selectMediaSourceText = stringResource(Lang.subject_episode_select_media_source)
    Row(modifier) {
        ProvideTextStyle(MaterialTheme.typography.titleMedium) {
            if (playingMedia != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Text(
                            nowPlayingText,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            playingMedia,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }

                    if (filename != null) {
                        SelectionContainer {
                            Text(
                                filename,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            } else {
                Text(selectMediaSourceText)
            }
        }
    }
}

@Composable
private fun ErrorTextBox(
    text: String,
    onCopy: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        text,
        onValueChange = {},
        modifier,
        label = { Text(stringResource(Lang.subject_episode_statistics_error_message)) },
        shape = MaterialTheme.shapes.medium,
        readOnly = true,
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = onCopy) {
                Icon(Icons.Rounded.ContentCopy, null)
            }
        },
    )
}
