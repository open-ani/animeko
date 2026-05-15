/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.FileDownloadOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.him188.ani.app.tools.Progress
import me.him188.ani.app.tools.getOrZero
import me.him188.ani.app.tools.rememberUiMonoTasker
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.ui.cache.CacheActionDropdown
import me.him188.ani.app.ui.cache.DeleteActionDialog
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.interaction.clickableAndMouseRightClick
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_episode_cover
import me.him188.ani.app.ui.lang.cache_episode_download_completed
import me.him188.ani.app.ui.lang.cache_episode_download_failed
import me.him188.ani.app.ui.lang.cache_episode_downloading
import me.him188.ani.app.ui.lang.cache_episode_manage_item
import me.him188.ani.app.ui.lang.cache_episode_pause_download
import me.him188.ani.app.ui.lang.cache_episode_resume_download
import me.him188.ani.datasources.api.topic.FileSize.Companion.Unspecified
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

@Immutable
enum class CacheEpisodePaused {
    IN_PROGRESS,
    PAUSED,
    FAILED,
    COMPLETED,
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
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    val listItemColors = ListItemDefaults.colors(containerColor = containerColor)
    val scope = rememberUiMonoTasker()
    val coverText = stringResource(Lang.cache_episode_cover)
    val resumeDownloadText = stringResource(Lang.cache_episode_resume_download)
    val pauseDownloadText = stringResource(Lang.cache_episode_pause_download)
    val manageItemText = stringResource(Lang.cache_episode_manage_item)

    if (showConfirm) {
        DeleteActionDialog(
            onDismiss = { showConfirm = false },
            onConfirm = {
                onDelete()
                showConfirm = false
            },
        )
    }
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
                AsyncImage(state.screenShots.first(), coverText)
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
                            Crossfade(state.state, Modifier.size(20.dp)) {
                                DownloadStateIcon(it)
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
                                    Icon(Icons.Rounded.Restore, resumeDownloadText)
                                }
                            } else if (!state.isFailed) {
                                IconButton(onPause) {
                                    Icon(Icons.Rounded.Pause, pauseDownloadText, Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                }

                // 总是展示的更多操作. 实际上点击整个 ListItem 都能展示 dropdown, 但留有这个按钮避免用户无法发现点击 list 能展开.
                IconButton({ showDropdown = true }) {
                    Icon(Icons.Rounded.MoreVert, manageItemText)
                }
            }
            CacheActionDropdown(
                show = showDropdown,
                onDismiss = { showDropdown = false },
                episode = state,
                onPlay = onPlay,
                onResume = onResume,
                onPause = onPause,
                onDelete = { showConfirm = true },
                offset = DpOffset.Zero,
            )
        },
        colors = listItemColors,
    )
}

@Composable
internal fun DownloadStateIcon(
    state: CacheEpisodePaused,
    modifier: Modifier = Modifier
) {
    when (state) {
        CacheEpisodePaused.COMPLETED -> Icon(
            Icons.Rounded.DownloadDone,
            stringResource(Lang.cache_episode_download_completed),
            modifier,
        )
        CacheEpisodePaused.FAILED -> ProvideContentColor(MaterialTheme.colorScheme.error) {
            Icon(Icons.Rounded.FileDownloadOff, stringResource(Lang.cache_episode_download_failed), modifier)
        }

        else -> Icon(Icons.Rounded.Downloading, stringResource(Lang.cache_episode_downloading), modifier)
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

@OptIn(TestOnly::class)
@Preview
@Composable
private fun PreviewCacheGroupCardCompleted() = ProvideCompositionLocalsForPreview {
    Box(Modifier.background(Color.DarkGray)) {
        PreviewCacheEpisodeItem(
            createTestCacheEpisode(
                1,
                progress = 1f.toProgress(),
                downloadSpeed = Unspecified,
                totalSize = 888.megaBytes,
                initialState = CacheEpisodePaused.COMPLETED,
            ),
        )
    }
}

@OptIn(TestOnly::class)
@Preview
@Composable
private fun PreviewCacheGroupCardFailed() = ProvideCompositionLocalsForPreview {
    Box(Modifier.background(Color.DarkGray)) {
        PreviewCacheEpisodeItem(
            createTestCacheEpisode(
                1,
                progress = 0.7f.toProgress(),
                downloadSpeed = Unspecified,
                totalSize = 888.megaBytes,
                initialState = CacheEpisodePaused.FAILED,
            ),
        )
    }
}
