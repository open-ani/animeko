/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import me.him188.ani.app.videoplayer.videoenhancement.VideoEnhancementMode
import me.him188.ani.danmaku.api.DanmakuServiceId


internal enum class TvCollectionPrompt { Remove, MarkAllWatched }

internal sealed interface TvDanmakuAdjustment {
    data class Parameter(val property: TvDanmakuProperty) : TvDanmakuAdjustment
    data class Timing(val serviceId: DanmakuServiceId) : TvDanmakuAdjustment
}

@Composable
internal fun TvOptionRow(
    title: String,
    value: String = "",
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    checked: Boolean? = null,
    supportingText: String? = null,
    icon: ImageVector? = null,
    valueIcon: ImageVector? = null,
    adjustable: Boolean = false,
    filled: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick, enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                this.selected = selected
                if (checked != null) {
                    role = Role.Switch
                    toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                }
            },
        colors = tvPlayerOptionColors(selected, filled),
        shape = ClickableSurfaceDefaults.shape(TvPlayerSurfaceDefaults.ItemShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier
                .heightIn(min = if (compact) 36.dp else 48.dp)
                .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { Icon(it, null, Modifier.size(20.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                supportingText?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = .7f),
                    )
                }
            }
            if (value.isNotEmpty() || adjustable || valueIcon != null) Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                valueIcon?.let { Icon(it, null, Modifier.size(20.dp)) }
                if (adjustable) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, Modifier.size(16.dp))
                Text(value, style = MaterialTheme.typography.labelLarge)
                if (adjustable) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(16.dp))
            }
            if (selected && checked == null) Icon(Icons.Rounded.Check, "已选择", Modifier.size(20.dp))
            if (checked != null) {
                val color = LocalContentColor.current
                Canvas(Modifier.size(36.dp, 20.dp)) {
                    drawRoundRect(
                        color.copy(alpha = if (checked) .45f else .18f),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                    drawCircle(
                        color,
                        radius = size.height / 2 - 3.dp.toPx(),
                        center = Offset(
                            if (checked) size.width - size.height / 2 else size.height / 2,
                            size.height / 2,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun TvPlayerDialogSurface(
    dialog: TvPlayerDialog,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    if (dialog != TvPlayerDialog.Speed) {
        TvOptionModal(title, modifier, subtitle, content = content)
        return
    }
    Box(Modifier.fillMaxSize().padding(end = 48.dp, bottom = 150.dp), contentAlignment = Alignment.BottomEnd) {
        Column(
            modifier.width(320.dp).heightIn(max = 340.dp)
                .tvPlayerSurface().padding(16.dp)
                .testTag("tv-speed-popup")
                .focusProperties { onExit = { cancelFocus() } }.focusGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TvPlayerSurfaceDefaults.Content)
            content()
        }
    }
}

@Composable
internal fun TvSubtitleDialog(
    options: TvPlayerOptionsState,
    onIntent: (TvEpisodeIntent) -> Boolean,
    entryModifier: Modifier,
) {
    // Capture the opening selection; later track updates must not steal the user's focus.
    val entryId = remember { options.selectedSubtitleId?.takeIf { id -> options.subtitles.any { it.id == id } } }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (entryId == null) 0 else options.subtitles.indexOfFirst { it.id == entryId } + 1,
    )
    LazyColumn(state = listState, modifier = Modifier.testTag("tv-subtitle-options")) {
        item {
            TvOptionRow(
                "关闭字幕",
                modifier = if (entryId == null) entryModifier else Modifier,
                selected = options.selectedSubtitleId == null,
            ) { onIntent(TvEpisodeIntent.SelectSubtitle(null)) }
        }
        items(options.subtitles, key = { it.id }) { subtitle ->
            TvOptionRow(
                subtitle.label,
                modifier = if (subtitle.id == entryId) entryModifier else Modifier,
                selected = subtitle.id == options.selectedSubtitleId,
            ) { onIntent(TvEpisodeIntent.SelectSubtitle(subtitle.id)) }
        }
        if (options.subtitles.isEmpty()) item {
            Text("此资源没有可切换的字幕", color = TvPlayerSurfaceDefaults.Muted, modifier = Modifier.padding(16.dp))
        }
    }
}

internal fun Modifier.tvStepKeys(onStep: (Int) -> Unit): Modifier = onPreviewKeyEvent {
    val direction = when (it.key) {
        Key.DirectionLeft -> -1
        Key.DirectionRight -> 1
        else -> return@onPreviewKeyEvent false
    }
    if (it.type == KeyEventType.KeyDown) onStep(direction)
    true
}

/** Left closes the settings page until the user explicitly enters a value's adjustment. */
@Composable
internal fun TvDanmakuAdjustmentRow(
    title: String,
    value: String,
    adjusting: Boolean,
    onAdjustingChange: (Boolean) -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onReset: (() -> Unit)? = null,
) {
    TvOptionRow(
        title,
        value,
        adjustable = adjusting,
        valueIcon = if (adjusting) null else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        modifier = modifier
            .onFocusChanged { if (!it.hasFocus && adjusting) onAdjustingChange(false) }
            .semantics { stateDescription = if (adjusting) "调整中" else "" }
            .then(if (adjusting) Modifier.tvStepKeys(onStep) else Modifier),
    ) {
        if (adjusting) onReset?.invoke()
        onAdjustingChange(!adjusting)
    }
}

@Composable
internal fun TvOptionTextField(
    value: String,
    label: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    password: Boolean = false
) {
    val colors = LocalTvPlayerSurfaceColors.current
    var focused by remember { mutableStateOf(false) }
    var editingValue by remember { mutableStateOf(value) }
    var hasLocalEdit by remember { mutableStateOf(false) }
    // Echo IME edits synchronously; a delayed projection must not replace a newer edit.
    // The ViewModel still receives every edit and owns validation and submission.
    LaunchedEffect(value, focused) {
        if (!focused || !hasLocalEdit) editingValue = value
    }
    Column(Modifier.padding(horizontal = 4.dp, vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.muted)
        BasicTextField(
            editingValue,
            {
                editingValue = it
                hasLocalEdit = true
                onChange(it)
            },
            modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .onFocusChanged {
                    focused = it.hasFocus
                    if (!focused) hasLocalEdit = false
                }
                .background(colors.raised, TvPlayerSurfaceDefaults.ItemShape)
                .border(
                    if (focused) 2.dp else 1.dp,
                    if (focused) colors.focusedContainer else colors.outline,
                    TvPlayerSurfaceDefaults.ItemShape,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.content),
            singleLine = true,
            cursorBrush = SolidColor(colors.content),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun TvOptionModal(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    width: Dp = 480.dp,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .78f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier
                .width(width)
                .heightIn(max = TvPlayerSurfaceDefaults.ModalMaxHeight)
                .tvPlayerSurface()
                .padding(24.dp)
                .focusProperties { onExit = { cancelFocus() } }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        color = TvPlayerSurfaceDefaults.Content,
                    )
                }
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = TvPlayerSurfaceDefaults.Muted,
                    )
                }
            }
            TvPlayerDivider()
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

/** Shared panel chrome and scrolling; callers fill the list with their own content. */
@Composable
internal fun TvPlayerOptionPanelLayout(
    panel: TvPlayerPanel,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    listModifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val colors = LocalTvPlayerSurfaceColors.current
    val list: @Composable () -> Unit = {
        LazyColumn(
            listModifier.tvPanelScrollEdges(listState, colors.container).focusGroup(),
            state = listState,
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
    if (panel.presentation == TvPlayerPanelPresentation.Sidebar) {
        Box(modifier.fillMaxSize()) { list() }
    } else {
        TvPlayerPanelSurface(
            title = panel.title,
            icon = panel.icon,
            showHeader = panel != TvPlayerPanel.Collection,
            modifier = modifier.width(panel.width).heightIn(max = TvPlayerSurfaceDefaults.PanelMaxHeight),
        ) { list() }
    }
}

@Composable
internal fun TvSpeedDialog(state: TvEpisodeUiState, onIntent: (TvEpisodeIntent) -> Boolean, entryModifier: Modifier) {
    val config = state.options.videoConfig
    LazyColumn(Modifier.testTag("tv-speed-options"), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        item {
            TvSpeedControl(
                speed = state.playbackSpeed,
                minSpeed = config.minPlaybackSpeed,
                maxSpeed = config.maxPlaybackSpeed,
                modifier = entryModifier,
                onStep = { onIntent(TvEpisodeIntent.AdjustSpeed(it)) },
            )
        }
        item {
            TvOptionRow(
                "记住播放速度",
                checked = config.rememberPlaybackSpeed,
            ) { onIntent(TvEpisodeIntent.ToggleRememberSpeed) }
        }
        if (!config.rememberPlaybackSpeed) item {
            TvOptionRow(
                "默认速度",
                "${config.playbackSpeed}x", adjustable = true,
                modifier = Modifier.tvStepKeys { onIntent(TvEpisodeIntent.SetDefaultSpeed(config.playbackSpeed + it * .25f)) },
            ) {}
        }
        item {
            TvOptionRow(
                "长按倍速",
                "${config.fastForwardSpeed}x", adjustable = true,
                modifier = Modifier.tvStepKeys { onIntent(TvEpisodeIntent.SetHoldSpeed(config.fastForwardSpeed + it * .25f)) },
            ) {}
        }
    }
}

@Composable
internal fun TvEnhancementSelector(
    selectedMode: VideoEnhancementMode,
    entryModifier: Modifier,
    onSelect: (VideoEnhancementMode) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(TvPlayerSurfaceDefaults.Raised, CircleShape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        VideoEnhancementMode.entries.forEach { mode ->
            Surface(
                onClick = { onSelect(mode) },
                modifier = Modifier.weight(1f)
                    .then(if (mode == selectedMode) entryModifier else Modifier)
                    .semantics { selected = mode == selectedMode },
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = tvPlayerOptionColors(mode == selectedMode),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (mode == selectedMode) Icon(Icons.Rounded.Check, "已选择", Modifier.size(16.dp))
                    Text(
                        when (mode) {
                            VideoEnhancementMode.OFF -> "原始画质"
                            VideoEnhancementMode.PERFORMANCE -> "性能优先"
                            VideoEnhancementMode.QUALITY -> "画质优先"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
