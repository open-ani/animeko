/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.tools.getOrZero
import me.him188.ani.app.ui.cache.CacheActionDropdown
import me.him188.ani.app.ui.cache.DeleteActionDialog
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_episode_download_failed
import me.him188.ani.app.ui.lang.cache_episode_pause_download
import me.him188.ani.app.ui.lang.cache_episode_resume_download
import me.him188.ani.app.ui.lang.cache_episode_status_paused
import me.him188.ani.app.ui.lang.cache_episode_watched_progress
import me.him188.ani.app.ui.lang.cache_filter_status_finished
import me.him188.ani.app.ui.lang.cache_management_episode_label
import me.him188.ani.app.ui.lang.cache_management_invalid_cache_info
import me.him188.ani.app.ui.lang.cache_management_more_actions
import me.him188.ani.app.ui.lang.cache_management_play
import me.him188.ani.app.ui.lang.cache_management_streaming_not_supported
import me.him188.ani.app.ui.mediafetch.MediaSourceInfoProvider
import org.jetbrains.compose.resources.stringResource

/**
 * 新设计的剧集缓存行, 用于条目缓存页与全局缓存管理页的详情栏.
 *
 * - 已完成: 标题 + "1.2 GB · AnimeGarden · 已完成" + 播放/更多按钮
 * - 下载中: 标题 + "890 MB / 1.3 GB · AnimeGarden" + 暂停/更多按钮 + 进度条 + 速度/百分比
 * - 已暂停: 同上, 主操作为继续, 进度条右侧显示 "已暂停"
 * - 多选模式: 行首复选框, 行尾操作隐藏
 *
 * 设计稿: [Figma](https://www.figma.com/design/LET1n9mmDa6npDTIlUuJjU/Animeko?node-id=1657-414)
 *
 * @param showSubjectTitle 是否将条目名作为标题展示 (用于跨条目的场景). 否则展示 "第x话 · 名称".
 */
@Composable
fun CacheEpisodeRow(
    episode: CacheEpisodeState,
    mediaSourceInfoProvider: MediaSourceInfoProvider?,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onEnterSelection: () -> Unit,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
    onViewDetail: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showSubjectTitle: Boolean = false,
    // 设计稿: 手机上行通栏无圆角 (选中高亮铺满全宽), 宽屏详情栏内为圆角.
    shape: Shape = MaterialTheme.shapes.medium,
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showConfirmDelete by rememberSaveable { mutableStateOf(false) }

    if (showConfirmDelete) {
        DeleteActionDialog(
            onDismiss = { showConfirmDelete = false },
            onConfirm = {
                onDelete()
                showConfirmDelete = false
            },
        )
    }

    val containerColor by animateColorAsState(
        if (selectionMode && selected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
    )
    Surface(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = {
                    if (selectionMode) {
                        onToggleSelected()
                    } else {
                        showMenu = true
                    }
                },
                onLongClick = onEnterSelection,
            ),
        shape = shape,
        color = containerColor,
    ) {
        Column(
            Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 设计稿: 多选模式下复选框在行首, 行尾单项操作隐藏.
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelected() },
                    )
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val title = if (showSubjectTitle) {
                        episode.subjectName
                    } else {
                        stringResource(Lang.cache_management_episode_label, episode.sort, episode.displayName)
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        cacheEpisodeMetaText(episode, mediaSourceInfoProvider),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!selectionMode) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CacheEpisodePrimaryAction(episode, onPlay = onPlay, onResume = onResume, onPause = onPause)

                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Rounded.MoreVert, stringResource(Lang.cache_management_more_actions))
                            }
                            CacheActionDropdown(
                                show = showMenu,
                                onDismiss = { showMenu = false },
                                episode = episode,
                                onPlay = {
                                    onPlay()
                                    showMenu = false
                                },
                                onResume = {
                                    onResume()
                                    showMenu = false
                                },
                                onPause = {
                                    onPause()
                                    showMenu = false
                                },
                                onViewDetail = onViewDetail?.let {
                                    {
                                        it()
                                        showMenu = false
                                    }
                                },
                                onDelete = { showConfirmDelete = true },
                            )
                        }
                    }
                }
            }

            val showPlaybackProgress = episode.isFinished && episode.hasPlaybackProgress
            AniAnimatedVisibility(!episode.isFinished || showPlaybackProgress) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val displayedProgress = if (showPlaybackProgress) {
                        episode.playbackProgress
                    } else {
                        episode.progress
                    }
                    val progress by animateFloatAsState(displayedProgress.getOrZero())
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (showPlaybackProgress) {
                                    Modifier.testTag(CacheEpisodeRowTestTags.playbackProgress(episode.cacheId))
                                } else {
                                    Modifier
                                },
                            ),
                        strokeCap = StrokeCap.Round,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val statusText = when {
                            showPlaybackProgress -> stringResource(
                                Lang.cache_episode_watched_progress,
                                episode.playbackProgressText ?: "",
                            )
                            episode.isFailed -> stringResource(Lang.cache_episode_download_failed)
                            episode.isPaused -> stringResource(Lang.cache_episode_status_paused)
                            else -> episode.speedText
                        }
                        statusText?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (episode.isFailed) MaterialTheme.colorScheme.error else Color.Unspecified,
                            )
                        }
                        if (!showPlaybackProgress) {
                            episode.progressText?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
        }
    }
}

object CacheEpisodeRowTestTags {
    private const val PLAYBACK_PROGRESS_PREFIX = "cache_episode_playback_progress_"

    fun playbackProgress(cacheId: String): String = PLAYBACK_PROGRESS_PREFIX + cacheId
}

/**
 * 行尾的主操作按钮: 已完成 → 播放, 下载中 → 暂停, 已暂停 → 继续. 失败时不显示.
 */
@Composable
private fun CacheEpisodePrimaryAction(
    episode: CacheEpisodeState,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
) {
    val toaster = LocalToaster.current
    val invalidCacheInfoText = stringResource(Lang.cache_management_invalid_cache_info)
    val streamingNotSupportedText = stringResource(Lang.cache_management_streaming_not_supported)
    val primaryIconColors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
    when {
        episode.isFinished -> {
            IconButton(
                onClick = {
                    when (episode.playability) {
                        CacheEpisodeState.Playability.PLAYABLE -> onPlay()
                        CacheEpisodeState.Playability.INVALID_SUBJECT_EPISODE_ID ->
                            toaster.toast(invalidCacheInfoText)

                        CacheEpisodeState.Playability.STREAMING_NOT_SUPPORTED ->
                            toaster.toast(streamingNotSupportedText)
                    }
                },
                colors = primaryIconColors,
            ) {
                Icon(Icons.Rounded.PlayArrow, stringResource(Lang.cache_management_play))
            }
        }

        episode.isPaused -> {
            IconButton(onClick = onResume, colors = primaryIconColors) {
                Icon(Icons.Rounded.PlayArrow, stringResource(Lang.cache_episode_resume_download))
            }
        }

        !episode.isFailed -> {
            IconButton(onClick = onPause, colors = primaryIconColors) {
                Icon(Icons.Rounded.Pause, stringResource(Lang.cache_episode_pause_download))
            }
        }
    }
}

/**
 * "1.2 GB · AnimeGarden · 已完成" 形式的行副标题.
 */
@Composable
private fun cacheEpisodeMetaText(
    episode: CacheEpisodeState,
    mediaSourceInfoProvider: MediaSourceInfoProvider?,
): String {
    val sourceName = episode.mediaSourceId?.let { id ->
        mediaSourceInfoProvider?.rememberMediaSourceInfo(id)?.value?.displayName
    }
    val statusText = when {
        episode.isFinished -> stringResource(Lang.cache_filter_status_finished)
        else -> null
    }
    return listOfNotNull(episode.detailedSizeText, sourceName, statusText)
        .joinToString(" · ")
}
