/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SubtitlesOff
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import me.him188.ani.leanback.ui.foundation.widgets.TvSeekBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    /** 主内容色. */
    val Content: Color = Color.White

    /** 次要内容色. */
    val SecondaryContent: Color = Color.White.copy(alpha = 0.72f)

    /** 浅色内容色. */
    val TertiaryContent: Color = Color.White.copy(alpha = 0.32f)
}

@Composable
private fun TvPlayerClock(modifier: Modifier = Modifier) {
    val text by produceState("") {
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            value = format.format(Date())
            delay(30_000)
        }
    }
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.titleMedium,
        color = TvPlayerControlsDefaults.SecondaryContent,
    )
}

/** 顶部标题和时钟独立于底部控制器/推荐横排的切换. */
@Composable
internal fun TvPlayerTitleBar(title: TvEpisodeTitle, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .testTag("tv-player-title-bar")
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
        TvPlayerClock(Modifier.padding(start = 24.dp, top = 4.dp))
    }
}

/**
 * 播放器底部控制层 (atv-architecture.md §8.3):
 * 选集条/面板/预览 -> 胶囊行 -> 进度条行 -> 图标行 -> 推荐提示.
 *
 * 纯视图组件: 焦点锚点/按键语义由 Screen 组装成 modifier 注入
 * ([seekBarModifier]/[iconRowModifier], §14.7-2), 本层只画状态.
 */
@Composable
internal fun TvPlayerControlsOverlay(
    sourceIconUrl: String?,
    sourceLabel: String,
    positionMillis: Long,
    durationMillis: Long,
    bufferedFraction: Float,
    hasNextEpisode: Boolean,
    scrubMillis: Long?,
    speedLabel: String,
    aspectLabel: String,
    activePanel: TvPlayerPanel?,
    options: TvPlayerOptionsState,
    seekBarModifier: Modifier,
    iconRowModifier: Modifier,
    nextEpisodeButtonModifier: Modifier,
    sourceButtonModifier: Modifier,
    speedButtonModifier: Modifier,
    subtitleButtonModifier: Modifier,
    episodesButtonModifier: Modifier,
    capsuleAnchor: (TvPlayerPanel) -> Modifier,
    onTogglePanel: (TvPlayerPanel) -> Unit,
    onNextEpisode: () -> Unit,
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
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Both control rows collapse to icons together as the player narrows.
        val showButtonLabels = maxWidth >= 760.dp
        val chipOffsets = remember { mutableStateMapOf<TvPlayerPanel, Float>() }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 28.dp),
        ) {
            episodeStrip?.invoke()
            Column(Modifier.padding(horizontal = TvPlayerControlsDefaults.HorizontalPadding)) {
                val density = LocalDensity.current
                // Anchor panels to the laid-out chips in either label mode and keep them within the safe edge.
                if (panelHost != null && activePanel != null) BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                ) {
                    val triggerOffset = chipOffsets[activePanel] ?: 0f
                    val start = with(density) { triggerOffset.toDp() }.coerceIn(
                        0.dp,
                        (maxWidth - activePanel.width).coerceAtLeast(0.dp),
                    )
                    Box(Modifier.offset(x = start)) { panelHost() }
                }

                if (scrubMillis != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (options.previewAvailable && options.videoConfig.enableFramePreview) Box(
                            Modifier
                                .width(192.dp)
                                .height(108.dp)
                                .shadow(8.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(TvPlayerSurfaceDefaults.Container)
                                .testTag("tv-seek-preview-frame"),
                        ) {
                            options.preview?.let {
                                Image(
                                    it,
                                    contentDescription = "目标位置画面预览",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                            if (options.preview == null && options.previewLoading) CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center).size(28.dp).testTag("tv-seek-preview-loading"),
                                color = Color.White,
                                strokeWidth = 3.dp,
                            )
                        }
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(
                                formatTime(scrubMillis),
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            options.chapters.firstOrNull {
                                scrubMillis in it.offsetMillis..<it.offsetMillis + it.durationMillis
                            }?.name?.takeIf { it.isNotBlank() }?.let { name ->
                                Text(name, color = TvPlayerSurfaceDefaults.Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TvRemoteHint("确认", "跳转")
                                TvRemoteHint("返回", "取消")
                            }
                        }
                    }
                }

                // 功能胶囊行: 确认开/关对应浮出面板
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .focusGroup()
                        .testTag("tv-player-chips"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (panel in TvPlayerPanel.entries) key(panel) {
                        CapsuleChipButton(
                            panel = panel,
                            active = activePanel == panel,
                            onClick = { onTogglePanel(panel) },
                            modifier = capsuleAnchor(panel)
                                .onPlaced { chipOffsets[panel] = it.positionInParent().x }
                                .testTag("tv-player-chip-${panel.name}"),
                            label = when (panel) {
                                TvPlayerPanel.Collection -> options.collectionType.tvLabel()
                                TvPlayerPanel.VideoSettings -> "画质增强"
                                else -> panel.title
                            },
                            showLabel = showButtonLabels,
                        )
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
                            .then(seekBarModifier)
                            .testTag("tv-player-seekbar"),
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

                // 图标行: 左组 (下一集/选集/弹幕) · 右组 (数据源/倍速/字幕/画面比例)
                Row(
                    iconRowModifier
                        .fillMaxWidth()
                        .testTag("tv-player-icon-row")
                        .padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (hasNextEpisode) PlayerIconButton(
                            Icons.Rounded.SkipNext,
                            label = "下一集",
                            onClick = onNextEpisode,
                            modifier = nextEpisodeButtonModifier,
                        )
                        PlayerLabelButton(
                            Icons.Rounded.ViewModule,
                            "选集",
                            onEpisodes,
                            episodesButtonModifier.then(if (hasNextEpisode) Modifier else nextEpisodeButtonModifier),
                            showLabel = showButtonLabels,
                        )
                        PlayerLabelButton(
                            if (options.danmakuEnabled) Icons.Rounded.Subtitles else Icons.Rounded.SubtitlesOff,
                            if (options.danmakuEnabled) "弹幕开" else "弹幕关",
                            onToggleDanmaku,
                            Modifier.testTag("tv-danmaku-toggle"),
                            showLabel = showButtonLabels,
                        )
                    }
                    Box(Modifier.weight(1f))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerLabelButton(
                            Icons.Rounded.DisplaySettings,
                            sourceLabel,
                            onClick = onOpenSourceDialog,
                            modifier = sourceButtonModifier.testTag("tv-source-button"),
                            iconContent = { TvSourceIcon(sourceIconUrl, modifier = Modifier.size(20.dp)) },
                            showLabel = showButtonLabels,
                            labelMaxWidth = 96.dp,
                        )
                        PlayerLabelButton(
                            Icons.Rounded.Speed,
                            speedLabel,
                            onClick = onOpenSpeed,
                            modifier = speedButtonModifier.testTag("tv-speed-button"),
                            showLabel = showButtonLabels,
                        )
                        if (options.supportsSubtitles) PlayerLabelButton(
                            Icons.Rounded.Subtitles,
                            "字幕",
                            onSubtitles,
                            subtitleButtonModifier,
                            showLabel = showButtonLabels,
                        )
                        PlayerLabelButton(
                            Icons.Rounded.AspectRatio,
                            aspectLabel,
                            onClick = onCycleAspect,
                            showLabel = showButtonLabels,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(top = 8.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("tv-recommendations-hint"),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = TvPlayerControlsDefaults.TertiaryContent,
            )
            Text(
                "显示推荐条目",
                style = MaterialTheme.typography.labelLarge,
                color = TvPlayerControlsDefaults.TertiaryContent,
            )
        }
    }
}

/** 描边功能按钮: 聚焦白底黑字; [active] (面板开着) 时强调描边与图标. */
@Composable
private fun CapsuleChipButton(
    panel: TvPlayerPanel,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = panel.title,
    showLabel: Boolean = true,
) {
    val outlineColor =
        if (active) MaterialTheme.colorScheme.primary else TvPlayerControlsDefaults.Content.copy(alpha = .45f)
    Surface(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = label
            selected = active
        },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = TvPlayerControlsDefaults.FocusedContainer,
            contentColor = if (active) MaterialTheme.colorScheme.primary else TvPlayerControlsDefaults.Content,
            focusedContentColor = TvPlayerControlsDefaults.FocusedContent,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(if (active) 2.dp else 1.dp, outlineColor), shape = CircleShape),
            focusedBorder = Border(
                BorderStroke(1.dp, TvPlayerControlsDefaults.FocusedContainer),
                shape = CircleShape,
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier
                .heightIn(min = 40.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(panel.icon, contentDescription = null, Modifier.size(18.dp))
            if (showLabel) Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
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
    iconContent: (@Composable () -> Unit)? = null,
    showLabel: Boolean = true,
    labelMaxWidth: Dp = Dp.Infinity,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = text },
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
            if (iconContent != null) iconContent() else Icon(icon, contentDescription = null, Modifier.size(20.dp))
            if (showLabel) Text(
                text,
                modifier = Modifier.widthIn(max = labelMaxWidth),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
