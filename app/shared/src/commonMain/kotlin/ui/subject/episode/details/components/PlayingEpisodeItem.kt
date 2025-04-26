/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.details.components

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Outbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.episode.share.MediaShareData
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.icons.PlayingIcon
import me.him188.ani.app.ui.foundation.layout.paddingIfNotEmpty
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcons
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.utils.platform.isAndroid

/**
 * 剧集详情页中的正在播放的剧集卡片. 需要放在合适的 `Card` 中.
 *
 * 可交互内容:
 * - 标记看过
 * - 切换数据源
 * - 跳转缓存页
 */
@Composable
fun PlayingEpisodeItem(
    episodeSort: @Composable () -> Unit,
    title: @Composable () -> Unit,
    watchStatus: @Composable () -> Unit,
    mediaSelected: Boolean,
    mediaLabels: @Composable FlowRowScope.() -> Unit,
    filename: @Composable () -> Unit,
    videoLoadingSummary: @Composable RowScope.() -> Unit,
    mediaSource: @Composable RowScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
    playingIcon: @Composable () -> Unit = {
        if (LocalPlatform.current.isAndroid()) {
            PlayingIcon() // 这个动画跑满帧率, 在渲染效率低的 PC 上会跑满 CPU
        }
    },
    rowSpacing: Dp = 16.dp,
    horizontalPadding: Dp = 20.dp,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        // top padding 16.dp
        // 每两排之间留 20.dp. 每个 Row 需要保证自己有 20.dp bottom padding

        Row(
            Modifier.padding(
                horizontal = horizontalPadding,
                vertical = rowSpacing - 12.dp, // 让 12.dp 给 watchStatus button
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 标题和"看过"按钮
            // padding 以 text 的为准, top = bottom = 8 + 12 = 20
            FlowRow(
                Modifier.padding(vertical = 12.dp).weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.CenterVertically),
            ) {
                ProvideTextStyle(
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                ) {
                    ProvideContentColor(MaterialTheme.colorScheme.primary) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            playingIcon()
                            episodeSort()
                        }
                        SelectionContainer {
                            title()
                        }
                    }
                }
            }
            Box(Modifier.align(Alignment.Top).padding(start = 16.dp).height(48.dp)) {
                watchStatus()
            }
        }
        // 上面 Row 有 bottom padding = rowSpacing

        if (mediaSelected) {
            // 1080P · 简中 · 122 MB · XX 字幕组
            ProvideTextStyleContentColor(
                MaterialTheme.typography.labelLarge,
            ) {
                FlowRow(
                    Modifier
                        .padding(top = 4.dp) // 额外多 pad 一点
                        .padding(horizontal = horizontalPadding)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    mediaLabels()
                }
            }

            // 文件名
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                Row(
                    Modifier
                        .paddingIfNotEmpty(top = rowSpacing - 8.dp, bottom = 8.dp) // 和下面保持一致, 自带 8dp
                        .padding(horizontal = horizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    filename()
                }
            }

            // ! 不支持的视频类型
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                // 上面的 InfoRow 的 Icon 有 8dp padding, 所以这里就不 pad 了
                Row(
                    Modifier
                        .padding(
                            start = horizontalPadding - 8.dp, // 让给 icon
                            end = horizontalPadding,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    videoLoadingSummary()
                }
            }

            // 上面的 InfoRow 的 Icon 有 8dp padding
            Spacer(Modifier.height(rowSpacing - 8.dp))
        } else {
            Spacer(Modifier.height(8.dp)) // 额外 pad 一点,  否则 "选择数据源" 按钮到 "看过" 按钮之间有点挤
        }

        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            Row(
                Modifier.padding(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    Modifier.offset(x = (-8).dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions()
                }
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, alignment = Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    mediaSource()
                }
            }
        }
    }
}

object PlayingEpisodeItemDefaults {
    @Composable
    fun ActionCache(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        IconButton(onClick, modifier) {
            Icon(Icons.Rounded.Download, "缓存")
        }
    }

    @Composable
    fun ActionShare(
        data: MediaShareData,
        modifier: Modifier = Modifier,
    ) {
        var showShareDropdown by rememberSaveable { mutableStateOf(false) }
        Box {
            IconButton({ showShareDropdown = true }, modifier) {
                Icon(Icons.Rounded.Outbox, "分享")
            }
            ShareEpisodeDropdown(
                data, showShareDropdown,
                { showShareDropdown = false },
            )
        }
    }

    @Composable
    fun MediaSource(
        media: Media?,
        mediaSourceInfo: MediaSourceInfo?,
        isLoading: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (media != null) {
                OutlinedButton(
                    onClick = onClick,
                    Modifier,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalContentColor.current),
                ) {
                    Icon(MediaSourceIcons.location(media.location, media.kind), null)

                    Text(
                        mediaSourceInfo?.displayName ?: "未知",
                        Modifier.padding(start = 12.dp).align(Alignment.CenterVertically),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            } else {
                Button(
                    onClick = onClick,
                    Modifier,
                ) {
                    Icon(Icons.Rounded.DisplaySettings, null)

                    Text(
                        "选择数据源",
                        Modifier.padding(start = 12.dp).align(Alignment.CenterVertically),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            AniAnimatedVisibility(
                isLoading,
                Modifier.align(Alignment.CenterVertically),
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it }),
            ) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
        }
    }
}
