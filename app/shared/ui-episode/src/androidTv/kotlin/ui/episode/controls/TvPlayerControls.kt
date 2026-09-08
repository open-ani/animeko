/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.controls

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
import androidx.compose.ui.text.rememberTextMeasurer
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
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.tv_player_remote_back
import me.him188.ani.app.ui.lang.tv_player_remote_confirm
import me.him188.ani.app.ui.lang.tv_player_seek_jump
import me.him188.ani.app.ui.lang.tv_player_seek_preview
import me.him188.ani.app.ui.lang.tv_player_show_recommendations
import me.him188.ani.app.ui.lang.video_player_cancel
import me.him188.ani.app.ui.lang.video_player_chapter
import me.him188.ani.app.ui.lang.video_player_danmaku_off
import me.him188.ani.app.ui.lang.video_player_danmaku_on
import me.him188.ani.app.ui.lang.video_player_next_episode
import me.him188.ani.app.ui.lang.video_player_select_episode
import me.him188.ani.app.ui.lang.video_player_subtitle
import me.him188.ani.leanback.ui.episode.TvEpisodeTitle
import me.him188.ani.leanback.ui.episode.TvPlayerOptionsState
import me.him188.ani.leanback.ui.episode.components.TvRemoteHint
import me.him188.ani.leanback.ui.episode.presentation.TvPlayerPanel
import me.him188.ani.leanback.ui.episode.presentation.title
import me.him188.ani.leanback.ui.episode.settings.tvLabel
import me.him188.ani.leanback.ui.episode.source.TvSourceIcon
import me.him188.ani.leanback.ui.foundation.formatPlaybackTime
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionDefaults
import me.him188.ani.leanback.ui.foundation.widgets.TvSeekBar
import org.jetbrains.compose.resources.stringResource
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
    val FocusedContainer: Color = TvOptionDefaults.FocusedContainer

    /** 聚焦反色: 内容. */
    val FocusedContent: Color = TvOptionDefaults.FocusedContent

    /** 主内容色. */
    val Content: Color = Color.White

    /** 次要内容色. */
    val SecondaryContent: Color = Color.White.copy(alpha = 0.72f)

    /** 浅色内容色. */
    val TertiaryContent: Color = Color.White.copy(alpha = 0.32f)
}

private data class PlayerButtonDimensions(val sidePadding: Dp, val iconSize: Dp, val contentSpacing: Dp) {
    fun widthWithLabel(labelWidth: Dp): Dp = sidePadding * 2 + iconSize + contentSpacing + labelWidth
}

// Measurement and rendering share the same dimensions so translated labels cannot drift out of bounds.
private val capsuleDimensions = PlayerButtonDimensions(14.dp, 18.dp, 8.dp)

private val labelButtonDimensions = PlayerButtonDimensions(12.dp, 20.dp, 6.dp)

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
        val chipLabels = TvPlayerPanel.entries.associateWith { panel ->
            when (panel) {
                TvPlayerPanel.Collection -> options.collectionType.tvLabel()
                else -> panel.title
            }
        }
        val episodesLabel = stringResource(Lang.video_player_select_episode)
        val danmakuLabel = stringResource(if (options.danmakuEnabled) Lang.video_player_danmaku_on else Lang.video_player_danmaku_off)
        val subtitleLabel = stringResource(Lang.video_player_subtitle)
        val textMeasurer = rememberTextMeasurer(cacheSize = 32)
        val density = LocalDensity.current
        val labelStyle = MaterialTheme.typography.labelLarge
        fun labelWidth(label: String): Dp = with(density) {
            textMeasurer.measure(label, labelStyle, softWrap = false, maxLines = 1).size.width.toDp()
        }
        // Use the translated text and current font scale without subcomposing focusable controls.
        val chipWidth = chipLabels.values.fold(8.dp) { width, label -> width + capsuleDimensions.widthWithLabel(labelWidth(label)) } +
            12.dp * (chipLabels.size - 1)
        val leftWidth = labelButtonDimensions.widthWithLabel(labelWidth(episodesLabel)) +
            labelButtonDimensions.widthWithLabel(labelWidth(danmakuLabel)) + 8.dp +
            if (hasNextEpisode) 44.dp + 8.dp else 0.dp
        val rightLabels = listOfNotNull(speedLabel, subtitleLabel.takeIf { options.supportsSubtitles }, aspectLabel)
        val rightWidth = labelButtonDimensions.widthWithLabel(labelWidth(sourceLabel).coerceAtMost(96.dp)) +
            rightLabels.fold(0.dp) { width, label -> width + 4.dp + labelButtonDimensions.widthWithLabel(labelWidth(label)) }
        val availableWidth = maxWidth - TvPlayerControlsDefaults.HorizontalPadding * 2
        // Both rows collapse together. Keep enough room to separate the two bottom action groups.
        val showButtonLabels = chipWidth <= availableWidth && leftWidth + 16.dp + rightWidth <= availableWidth
        val chipOffsets = remember { mutableStateMapOf<TvPlayerPanel, Float>() }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 28.dp),
        ) {
            episodeStrip?.invoke()
            Column(Modifier.padding(horizontal = TvPlayerControlsDefaults.HorizontalPadding)) {
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
                                .background(TvOptionDefaults.Container)
                                .testTag("tv-seek-preview-frame"),
                        ) {
                            options.preview?.let {
                                Image(
                                    it,
                                    contentDescription = stringResource(Lang.tv_player_seek_preview),
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
                                formatPlaybackTime(scrubMillis),
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            options.chapters.firstOrNull {
                                scrubMillis in it.offsetMillis..<it.offsetMillis + it.durationMillis
                            }?.let { chapter ->
                                val name = chapter.name ?: stringResource(Lang.video_player_chapter)
                                if (name.isNotBlank()) Text(name, color = TvOptionDefaults.Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TvRemoteHint(stringResource(Lang.tv_player_remote_confirm), stringResource(Lang.tv_player_seek_jump))
                                TvRemoteHint(stringResource(Lang.tv_player_remote_back), stringResource(Lang.video_player_cancel))
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
                            label = chipLabels.getValue(panel),
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
                        formatPlaybackTime(scrubMillis ?: positionMillis),
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
                        formatPlaybackTime(durationMillis),
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
                            label = stringResource(Lang.video_player_next_episode),
                            onClick = onNextEpisode,
                            modifier = nextEpisodeButtonModifier.testTag("tv-next-episode-button"),
                        )
                        PlayerLabelButton(
                            Icons.Rounded.ViewModule,
                            episodesLabel,
                            onEpisodes,
                            episodesButtonModifier.then(if (hasNextEpisode) Modifier else nextEpisodeButtonModifier).testTag("tv-episodes-button"),
                            showLabel = showButtonLabels,
                        )
                        PlayerLabelButton(
                            if (options.danmakuEnabled) Icons.Rounded.Subtitles else Icons.Rounded.SubtitlesOff,
                            danmakuLabel,
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
                            iconContent = { TvSourceIcon(sourceIconUrl, modifier = Modifier.size(labelButtonDimensions.iconSize)) },
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
                            subtitleLabel,
                            onSubtitles,
                            subtitleButtonModifier.testTag("tv-subtitles-button"),
                            showLabel = showButtonLabels,
                        )
                        PlayerLabelButton(
                            Icons.Rounded.AspectRatio,
                            aspectLabel,
                            onClick = onCycleAspect,
                            modifier = Modifier.testTag("tv-aspect-button"),
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
                stringResource(Lang.tv_player_show_recommendations),
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
                .padding(horizontal = capsuleDimensions.sidePadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(capsuleDimensions.contentSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(panel.icon, contentDescription = null, Modifier.size(capsuleDimensions.iconSize))
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
                .padding(horizontal = labelButtonDimensions.sidePadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(labelButtonDimensions.contentSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconContent != null) iconContent() else Icon(icon, contentDescription = null, Modifier.size(labelButtonDimensions.iconSize))
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
