/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Recommend
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwitchVideo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import me.him188.ani.tv.ui.foundation.widgets.TvSeekBar

/**
 * 播放器控制层样式 (atv-architecture.md §8.3 / 附录 A):
 * scrim 380/180dp 黑渐变; 播放器系焦点视觉 = 白底黑内容整块反色.
 */
internal object TvPlayerControlsDefaults {
    /** 内容水平安全边距 (overscan 48dp). */
    val HorizontalPadding: Dp = 48.dp

    /** 聚焦反色: 底. */
    val FocusedContainer: Color = Color.White

    /** 聚焦反色: 内容. */
    val FocusedContent: Color = Color.Black

    /** 胶囊底色 (未聚焦). */
    val CapsuleContainer: Color = Color.White.copy(alpha = 0.16f)

    /** 主内容色. */
    val Content: Color = Color.White

    /** 次要内容色. */
    val SecondaryContent: Color = Color.White.copy(alpha = 0.72f)
}

/**
 * 播放器控制层 (atv-architecture.md §8.3):
 * 顶部 [标题两行 + 时钟] -> 底部 [胶囊行 -> 进度条行 -> 图标行 -> 选集条 slot].
 *
 * 纯视图组件: 焦点锚点/按键语义由 Screen 组装成 modifier 注入
 * ([seekBarModifier]/[iconRowModifier], §14.7-2), 本层只画状态.
 * 胶囊行在浮出面板落地前为静态信息 (M5 面板接入后改为按钮).
 */
@Composable
internal fun TvPlayerControlsOverlay(
    title: TvEpisodeViewModel.TitleInfo,
    clockText: String,
    mediaLabel: String?,
    positionMillis: Long,
    durationMillis: Long,
    bufferedFraction: Float,
    scrubMillis: Long?,
    playStateLabel: String,
    speedLabel: String,
    aspectLabel: String,
    seekBarModifier: Modifier,
    iconRowModifier: Modifier,
    seekBackButtonModifier: Modifier,
    onSeekBack: () -> Unit,
    onNextEpisode: () -> Unit,
    onSeekForward: () -> Unit,
    onOpenSourceDialog: () -> Unit,
    onCycleSpeed: () -> Unit,
    onCycleAspect: () -> Unit,
    modifier: Modifier = Modifier,
    episodeStrip: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxSize()) {
        // 顶部 scrim + 标题/时钟
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.75f),
                        1f to Color.Transparent,
                    ),
                )
                .padding(
                    start = TvPlayerControlsDefaults.HorizontalPadding,
                    end = TvPlayerControlsDefaults.HorizontalPadding,
                    top = 24.dp,
                    bottom = 40.dp,
                ),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title.subjectName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TvPlayerControlsDefaults.Content,
                )
                Text(
                    title.episodeLine,
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = TvPlayerControlsDefaults.SecondaryContent,
                )
            }
            Text(
                clockText,
                Modifier.padding(start = 24.dp, top = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                color = TvPlayerControlsDefaults.SecondaryContent,
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.88f),
                    ),
                )
                .padding(top = 56.dp, bottom = 20.dp),
        ) {
            Column(Modifier.padding(horizontal = TvPlayerControlsDefaults.HorizontalPadding)) {
                // 功能胶囊行 (面板接入前为静态信息层)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CapsuleChip(Icons.Rounded.Recommend, "相关推荐")
                    CapsuleChip(Icons.Rounded.Groups, "制作人员")
                    CapsuleChip(Icons.Rounded.Face, "角色")
                    CapsuleChip(Icons.Rounded.Comment, "评论")
                    CapsuleChip(Icons.Rounded.FormatListBulleted, "弹幕列表")
                }

                // 进度条行: 左当前时间 (拖拽预览时显示目标) · TvSeekBar · 右总时长
                var seekBarFocused by remember { mutableStateOf(false) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        formatTime(scrubMillis ?: positionMillis),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (scrubMillis != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            TvPlayerControlsDefaults.Content
                        },
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            // 观察者须在 focusable 之前 (onFocusChanged 只观察链上其后的焦点目标)
                            .onFocusChanged { seekBarFocused = it.isFocused }
                            .then(seekBarModifier),
                    ) {
                        TvSeekBar(
                            positionMillis = positionMillis,
                            durationMillis = durationMillis,
                            bufferedFraction = bufferedFraction,
                            scrubMillis = scrubMillis,
                            showDot = seekBarFocused,
                        )
                    }
                    Text(
                        formatTime(durationMillis),
                        style = MaterialTheme.typography.titleSmall,
                        color = TvPlayerControlsDefaults.Content,
                    )
                }

                // 图标行: 左组 (回跳/下一集/前跳) · 右组 (数据源/倍速/画面比例/状态)
                Row(
                    iconRowModifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlayerIconButton(
                            Icons.Rounded.Replay10,
                            onClick = onSeekBack,
                            modifier = seekBackButtonModifier,
                        )
                        PlayerIconButton(Icons.Rounded.SkipNext, onClick = onNextEpisode)
                        PlayerIconButton(Icons.Rounded.Forward30, onClick = onSeekForward)
                    }
                    Box(Modifier.weight(1f))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerLabelButton(
                            Icons.Rounded.SwitchVideo,
                            mediaLabel ?: "数据源",
                            onClick = onOpenSourceDialog,
                        )
                        PlayerLabelButton(Icons.Rounded.Speed, speedLabel, onClick = onCycleSpeed)
                        PlayerLabelButton(Icons.Rounded.AspectRatio, aspectLabel, onClick = onCycleAspect)
                        Text(
                            playStateLabel,
                            Modifier.padding(start = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = TvPlayerControlsDefaults.SecondaryContent,
                        )
                    }
                }
            }

            episodeStrip?.invoke()
        }
    }
}

@Composable
private fun CapsuleChip(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(TvPlayerControlsDefaults.CapsuleContainer, CircleShape)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, Modifier.size(18.dp), tint = TvPlayerControlsDefaults.Content)
        Text(text, style = MaterialTheme.typography.labelLarge, color = TvPlayerControlsDefaults.Content)
    }
}

/** 图标按钮: 聚焦白底黑内容整块反色 (播放器系焦点视觉, 附录 A). */
@Composable
private fun PlayerIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = playerInverseSurfaceColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Icon(icon, contentDescription = null, Modifier.padding(8.dp).size(26.dp))
    }
}

/** 带文字的图标按钮 (数据源/倍速/画面比例). */
@Composable
private fun PlayerLabelButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = playerInverseSurfaceColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
internal fun playerInverseSurfaceColors() = ClickableSurfaceDefaults.colors(
    containerColor = Color.Transparent,
    focusedContainerColor = TvPlayerControlsDefaults.FocusedContainer,
    contentColor = TvPlayerControlsDefaults.Content,
    focusedContentColor = TvPlayerControlsDefaults.FocusedContent,
)

internal fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
