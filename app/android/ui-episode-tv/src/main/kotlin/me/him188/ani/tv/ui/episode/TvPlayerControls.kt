/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SwitchVideo
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
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
 * 固定高度的底部黑渐变; 播放器系焦点视觉 = 浅底深色内容整块反色.
 */
internal object TvPlayerControlsDefaults {
    /** 内容水平安全边距 (overscan 48dp). */
    val HorizontalPadding: Dp = 48.dp

    /** 聚焦反色: 底. */
    val FocusedContainer: Color = TvPlayerSurfaceDefaults.FocusedContainer

    /** 聚焦反色: 内容. */
    val FocusedContent: Color = TvPlayerSurfaceDefaults.FocusedContent

    /** 胶囊底色 (未聚焦). */
    val CapsuleContainer: Color = TvPlayerSurfaceDefaults.Raised.copy(alpha = .94f)

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
 */
@Composable
internal fun TvPlayerControlsOverlay(
    title: TvEpisodeTitle,
    clockText: String,
    mediaLabel: String?,
    positionMillis: Long,
    durationMillis: Long,
    bufferedFraction: Float,
    scrubMillis: Long?,
    playStateLabel: String,
    speedLabel: String,
    aspectLabel: String,
    activePanel: TvPlayerPanel?,
    options: TvPlayerOptionsState,
    seekBarModifier: Modifier,
    iconRowModifier: Modifier,
    seekBackButtonModifier: Modifier,
    sourceButtonModifier: Modifier,
    speedButtonModifier: Modifier,
    subtitleButtonModifier: Modifier,
    episodesButtonModifier: Modifier,
    capsuleAnchor: (TvPlayerPanel) -> Modifier,
    onTogglePanel: (TvPlayerPanel) -> Unit,
    onSeekBack: () -> Unit,
    onNextEpisode: () -> Unit,
    onSeekForward: () -> Unit,
    onOpenSourceDialog: () -> Unit,
    onOpenSpeed: () -> Unit,
    onCycleAspect: () -> Unit,
    onToggleDanmaku: () -> Unit,
    onSubtitles: () -> Unit,
    onEpisodes: () -> Unit,
    modifier: Modifier = Modifier,
    panelHost: (@Composable () -> Unit)? = null,
    episodeStrip: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxSize()) {
        // Keep the scrim independent of panel height so opening a panel cannot wash out the controls.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .4f to Color.Black.copy(alpha = .72f),
                        1f to Color.Black.copy(alpha = .96f),
                    ),
                ),
        )
        // 顶部 scrim + 标题/时钟
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.85f),
                        .65f to Color.Black.copy(alpha = 0.65f),
                        1f to Color.Transparent,
                    ),
                )
                .padding(
                    start = TvPlayerControlsDefaults.HorizontalPadding,
                    end = TvPlayerControlsDefaults.HorizontalPadding,
                    top = 28.dp,
                    bottom = 40.dp,
                ),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title.subjectName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TvPlayerControlsDefaults.Content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    title.episodeLine,
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = TvPlayerControlsDefaults.SecondaryContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                .padding(top = 12.dp, bottom = 28.dp),
        ) {
            Column(Modifier.padding(horizontal = TvPlayerControlsDefaults.HorizontalPadding)) {
                val capsuleList = rememberLazyListState()
                val density = LocalDensity.current
                // Anchor the panel to the visible trigger; clamp the last pills to the safe right edge.
                if (panelHost != null && activePanel != null) BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                ) {
                    val triggerOffset =
                        capsuleList.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activePanel.ordinal }?.offset
                            ?: 0
                    val start = with(density) { triggerOffset.toDp() }.coerceIn(
                        0.dp,
                        (maxWidth - activePanel.width).coerceAtLeast(0.dp),
                    )
                    Box(Modifier.offset(x = start)) { panelHost() }
                }

                // 功能胶囊行: 确认开/关对应浮出面板
                LazyRow(
                    state = capsuleList,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    items(TvPlayerPanel.entries) { panel ->
                        CapsuleChipButton(
                            panel = panel,
                            active = activePanel == panel,
                            onClick = { onTogglePanel(panel) },
                            modifier = capsuleAnchor(panel),
                            label = when (panel) {
                                TvPlayerPanel.Collection -> options.collectionType.tvLabel()
                                TvPlayerPanel.Recommendations -> "推荐"
                                TvPlayerPanel.VideoSettings -> "画质"
                                else -> panel.title
                            },
                        )
                    }
                }

                if (scrubMillis != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            Modifier
                                .width(192.dp)
                                .height(108.dp)
                                .shadow(8.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(TvPlayerSurfaceDefaults.Container),
                        ) {
                            options.preview?.let {
                                Image(
                                    it,
                                    contentDescription = "目标位置画面预览",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                            if (options.preview == null) Text(
                                if (options.previewLoading) "正在加载预览…" else "暂无画面预览",
                                modifier = Modifier.align(Alignment.Center),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray,
                            )
                        }
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(
                                formatTime(scrubMillis),
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                "预览位置",
                                color = TvPlayerSurfaceDefaults.Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TvRemoteHint("确认", "跳转")
                                TvRemoteHint("返回", "取消")
                            }
                        }
                    }
                }

                // 进度条行: 左当前时间 (拖拽预览时显示目标) · TvSeekBar · 右总时长
                var seekBarFocused by remember { mutableStateOf(false) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
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
                        Canvas(
                            Modifier
                                .fillMaxWidth()
                                .height(16.dp),
                        ) {
                            if (durationMillis > 0) options.chapters.forEach { chapter ->
                                val x = size.width * (chapter.offsetMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
                                drawLine(
                                    Color.White.copy(alpha = .7f),
                                    Offset(x, size.height / 2 - 3.dp.toPx()),
                                    Offset(x, size.height / 2 + 3.dp.toPx()),
                                    2.dp.toPx(),
                                )
                            }
                        }
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerIconButton(
                            Icons.Rounded.Replay10,
                            label = "后退 10 秒",
                            onClick = onSeekBack,
                            modifier = seekBackButtonModifier,
                        )
                        PlayerIconButton(Icons.Rounded.SkipNext, "下一集", onClick = onNextEpisode)
                        PlayerIconButton(Icons.Rounded.Forward30, "前进 30 秒", onClick = onSeekForward)
                        PlayerLabelButton(Icons.Rounded.ViewModule, "选集", onEpisodes, episodesButtonModifier)
                        PlayerLabelButton(
                            Icons.Rounded.ChatBubbleOutline,
                            if (options.danmakuEnabled) "弹幕开" else "弹幕关",
                            onToggleDanmaku,
                            Modifier.testTag("tv-danmaku-toggle"),
                        )
                    }
                    Box(Modifier.weight(1f))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerLabelButton(
                            Icons.Rounded.SwitchVideo,
                            "选源",
                            onClick = onOpenSourceDialog,
                            modifier = sourceButtonModifier.testTag("tv-source-button"),
                        )
                        PlayerLabelButton(
                            Icons.Rounded.Speed,
                            speedLabel,
                            onClick = onOpenSpeed,
                            modifier = speedButtonModifier.testTag("tv-speed-button"),
                        )
                        if (options.supportsSubtitles) PlayerLabelButton(
                            Icons.Rounded.Subtitles,
                            "字幕",
                            onSubtitles,
                            subtitleButtonModifier,
                        )
                        PlayerLabelButton(Icons.Rounded.AspectRatio, aspectLabel, onClick = onCycleAspect)
                    }
                }
            }

            episodeStrip?.invoke()
        }
    }
}

/** 面板胶囊按钮: 聚焦白底黑内容反色; [active] (面板开着) 时底色提亮一档. */
@Composable
private fun CapsuleChipButton(
    panel: TvPlayerPanel,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = panel.title,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primary.copy(alpha = .22f).compositeOver(TvPlayerSurfaceDefaults.Container)
            } else {
                TvPlayerControlsDefaults.CapsuleContainer
            },
            focusedContainerColor = TvPlayerControlsDefaults.FocusedContainer,
            contentColor = TvPlayerControlsDefaults.Content,
            focusedContentColor = TvPlayerControlsDefaults.FocusedContent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(
            Modifier
                .heightIn(min = 40.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(panel.icon, contentDescription = null, Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** 图标按钮: 聚焦白底黑内容整块反色 (播放器系焦点视觉, 附录 A). */
@Composable
private fun PlayerIconButton(
    icon: ImageVector,
    label: String,
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
        Icon(
            icon, contentDescription = label,
            Modifier
                .padding(10.dp)
                .size(24.dp),
        )
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
            Modifier
                .heightIn(min = 44.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
