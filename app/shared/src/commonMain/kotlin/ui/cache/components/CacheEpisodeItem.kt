/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.him188.ani.app.tools.Progress
import me.him188.ani.app.tools.getOrZero
import me.him188.ani.app.tools.rememberUiMonoTasker
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.interaction.clickableAndMouseRightClick
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.datasources.api.topic.FileSize.Companion.Unspecified
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.ui.tooling.preview.Preview

@Immutable
enum class CacheEpisodePaused {
    IN_PROGRESS,
    PAUSED,
}

@Composable
fun CacheEpisodeItem(
    state: CacheEpisodeState,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    var showDropdown by remember { mutableStateOf(false) }
    val listItemColors = ListItemDefaults.colors(containerColor = containerColor)
    val scope = rememberUiMonoTasker()
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${state.sort}",
                    softWrap = false,
                )

                Text(
                    state.displayName,
                    Modifier.padding(start = 8.dp).basicMarquee(),
                )
            }
        },
        modifier.clickableAndMouseRightClick { showDropdown = true },
        leadingContent = if (state.screenShots.isEmpty()) null else {
            {
                AsyncImage(state.screenShots.first(), "封面")
            }
        },
        supportingContent = {
            Column(
                Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProvideTextStyleContentColor(MaterialTheme.typography.labelLarge) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Crossfade(state.isFinished, Modifier.size(20.dp)) {
                                if (it) {
                                    Icon(Icons.Rounded.DownloadDone, "下载完成")
                                } else {
                                    Icon(Icons.Rounded.Downloading, "下载中")
                                }
                            }

                            state.sizeText?.let {
                                Text(it, Modifier.padding(end = 8.dp), softWrap = false)
                            }
                        }

                        Box(Modifier, contentAlignment = Alignment.BottomEnd) {
                            Row(
                                Modifier.basicMarquee(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp, alignment = Alignment.End),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                state.speedText?.let {
                                    Text(it, softWrap = false)
                                }

                                Box(contentAlignment = Alignment.CenterEnd) {
                                    Text("100.0%", Modifier.alpha(0f), softWrap = false)
                                    state.progressText?.let {
                                        Text(it, softWrap = false)
                                    }
                                }
                            }
                        }
                    }
                }

                if (!state.isFinished) {
                    Crossfade(state.isProgressUnspecified) {
                        if (it) {
                            LinearProgressIndicator(
                                Modifier.fillMaxWidth(),
                                trackColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                                strokeCap = StrokeCap.Round,
                            )
                        } else {
                            val progress by animateFloatAsState(state.progress.getOrZero())
                            LinearProgressIndicator(
                                { progress },
                                Modifier.fillMaxWidth(),
                                trackColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                                strokeCap = StrokeCap.Round,
                            )
                        }
                    }
                }
            }
        },
        trailingContent = {
            // 仅当有足够宽度时, 才展示当前状态下的推荐操作
            val showPrimaryAction = currentWindowAdaptiveInfo1().isWidthAtLeastMedium
            Row(horizontalArrangement = Arrangement.aligned(Alignment.End)) {
                // 当前状态下的推荐操作
                val isActionInProgress = scope.isRunning.collectAsStateWithLifecycle()
                AnimatedVisibility(showPrimaryAction) {
                    if (isActionInProgress.value) {
                        IconButton(
                            onClick = {
                                // no-op
                            },
                            enabled = false,
                            colors = IconButtonDefaults.iconButtonColors().run {
                                copy(disabledContainerColor = containerColor, disabledContentColor = contentColor)
                            },
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    } else {
                        if (!state.isFinished) {
                            if (state.isPaused) {
                                IconButton(onResume) {
                                    Icon(Icons.Rounded.Restore, "继续下载")
                                }
                            } else {
                                IconButton(onPause) {
                                    Icon(Icons.Rounded.Pause, "暂停下载", Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                }

                // 总是展示的更多操作. 实际上点击整个 ListItem 都能展示 dropdown, 但留有这个按钮避免用户无法发现点击 list 能展开.
                IconButton({ showDropdown = true }) {
                    Icon(Icons.Rounded.MoreVert, "管理此项")
                }
            }
            Dropdown(
                showDropdown, { showDropdown = false },
                state,
                onPlay = { onPlay() },
                onResume = onResume,
                onPause = onPause,
                onDelete = onDelete,
            )
        },
        colors = listItemColors,
    )
}

@Composable
private fun Dropdown(
    showDropdown: Boolean,
    onDismissRequest: () -> Unit,
    state: CacheEpisodeState,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    if (showConfirm) {
        AlertDialog(
            { showConfirm = false },
            icon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除缓存") },
            text = { Text("删除后不可恢复，确认删除吗?") },
            confirmButton = {
                TextButton(
                    {
                        onDelete()
                        showConfirm = false
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton({ showConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
    DropdownMenu(showDropdown, onDismissRequest, modifier) {
        if (!state.isFinished) {
            if (state.isPaused) {
                DropdownMenuItem(
                    text = { Text("继续下载") },
                    leadingIcon = { Icon(Icons.Rounded.Restore, null) },
                    onClick = {
                        onResume()
                        onDismissRequest()
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text("暂停下载") },
                    leadingIcon = { Icon(Icons.Rounded.Pause, null) },
                    onClick = {
                        onPause()
                        onDismissRequest()
                    },
                )
            }
        }

        val toaster = LocalToaster.current
        DropdownMenuItem(
            text = { Text("播放") },
            leadingIcon = {
                // 这个内容如果太大会导致影响 text
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    // 这个图标比其他图标小
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.requiredSize(28.dp))
                }
            },
            onClick = {
                when (state.playability) {
                    CacheEpisodeState.Playability.PLAYABLE -> {
                        onPlay()
                        onDismissRequest()
                    }

                    CacheEpisodeState.Playability.INVALID_SUBJECT_EPISODE_ID -> {
                        toaster.toast("信息无效，无法播放")
                    }

                    CacheEpisodeState.Playability.STREAMING_NOT_SUPPORTED -> {
                        toaster.toast("此资源不支持边下边播，请等待下载完成")
                    }
                }
            },
        )

        ProvideContentColor(MaterialTheme.colorScheme.error) {
            DropdownMenuItem(
                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    showConfirm = true
                    onDismissRequest()
                },
            )
        }
    }
}


@OptIn(TestOnly::class)
@Preview
@Composable
private fun PreviewCacheGroupCardMissingTotalSize() = ProvideCompositionLocalsForPreview {
    Box(Modifier.background(Color.DarkGray)) {
        PreviewCacheEpisodeItem(
            createTestCacheEpisode(
                1,
                progress = 0.5f.toProgress(),
                downloadSpeed = 233.megaBytes,
                totalSize = Unspecified,
            ),
        )
    }
}

@OptIn(TestOnly::class)
@Preview
@Composable
private fun PreviewCacheGroupCardMissingProgress() = ProvideCompositionLocalsForPreview {
    Box(Modifier.background(Color.DarkGray)) {
        PreviewCacheEpisodeItem(
            createTestCacheEpisode(
                1,
                progress = Progress.Unspecified,
                downloadSpeed = 233.megaBytes,
                totalSize = 888.megaBytes,
            ),
        )
    }
}

@OptIn(TestOnly::class)
@Preview
@Composable
private fun PreviewCacheGroupCardMissingDownloadSpeed() = ProvideCompositionLocalsForPreview {
    Box(Modifier.background(Color.DarkGray)) {
        PreviewCacheEpisodeItem(
            createTestCacheEpisode(
                1,
                progress = 0.3f.toProgress(),
                downloadSpeed = Unspecified,
                totalSize = 888.megaBytes,
            ),
        )
    }
}

@Composable
private fun PreviewCacheEpisodeItem(
    state: CacheEpisodeState,
    modifier: Modifier = Modifier,
) {
    CacheEpisodeItem(
        state,
        onPlay = { },
        onResume = {},
        onPause = {},
        onDelete = {},
        modifier = modifier,
    )

}
