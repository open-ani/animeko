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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.semantics.semantics
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
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusHotkey
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class OptionFieldFocus : TvFocusKey { Name, Password, Submit }

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
            if (value.isNotEmpty() || adjustable) Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
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

internal fun Modifier.tvStepKeys(onStep: (Int) -> Unit): Modifier = onPreviewKeyEvent {
    val direction = when (it.key) {
        Key.DirectionLeft -> -1
        Key.DirectionRight -> 1
        else -> return@onPreviewKeyEvent false
    }
    if (it.type == KeyEventType.KeyDown) onStep(direction)
    true
}

@Composable
internal fun TvOptionTextField(
    value: String,
    label: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    password: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    var editingValue by remember { mutableStateOf(value) }
    var hasLocalEdit by remember { mutableStateOf(false) }
    // Echo IME edits synchronously; a delayed projection must not replace a newer edit.
    // The ViewModel still receives every edit and owns validation and submission.
    LaunchedEffect(value, focused) {
        if (!focused || !hasLocalEdit) editingValue = value
    }
    Column(Modifier.padding(horizontal = 4.dp, vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TvPlayerSurfaceDefaults.Muted)
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
                .background(TvPlayerSurfaceDefaults.Raised, TvPlayerSurfaceDefaults.ItemShape)
                .border(
                    if (focused) 2.dp else 1.dp,
                    if (focused) TvPlayerSurfaceDefaults.FocusedContainer else TvPlayerSurfaceDefaults.Outline,
                    TvPlayerSurfaceDefaults.ItemShape,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
            singleLine = true,
            cursorBrush = SolidColor(Color.White),
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
                    TvRemoteHint("返回", "关闭")
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

@Composable
internal fun TvInteractivePanel(
    panel: TvPlayerPanel,
    state: TvEpisodeUiState,
    together: TvTogetherState,
    onIntent: (TvEpisodeIntent) -> Boolean,
    onTogetherIntent: (TvTogetherIntent) -> Unit,
    entryModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val options = state.options
    val fields = rememberTvFocusScope()
    fields.Resolver()
    TvPlayerPanelSurface(
        title = panel.title,
        icon = panel.icon,
        subtitle = if (panel == TvPlayerPanel.DanmakuSettings) "上下选择 · 左右调整" else null,
        showHeader = panel != TvPlayerPanel.Collection,
        modifier = modifier
            .width(panel.width)
            .heightIn(max = TvPlayerSurfaceDefaults.PanelMaxHeight),
    ) {
        LazyColumn(
            Modifier
                .tvFocusNavSignal(fields)
                .focusGroup(),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (panel) {
                TvPlayerPanel.Collection -> {
                    when {
                        options.confirmRemoveCollection -> {
                            item { Text("确定取消收藏？", color = Color.White, modifier = Modifier.padding(10.dp)) }
                            item {
                                TvOptionRow(
                                    "取消收藏",
                                    modifier = entryModifier,
                                ) { onIntent(TvEpisodeIntent.ConfirmRemoveCollection) }
                            }
                            item { TvOptionRow("保留收藏") { onIntent(TvEpisodeIntent.Back) } }
                        }

                        options.offerMarkAllWatched -> {
                            item {
                                TvOptionRow(
                                    "将全部剧集标为已看",
                                    modifier = entryModifier,
                                ) { onIntent(TvEpisodeIntent.MarkAllWatched) }
                            }
                            item { TvOptionRow("仅修改收藏状态") { onIntent(TvEpisodeIntent.Back) } }
                        }

                        else -> items(UnifiedCollectionType.entries) { type ->
                            TvOptionRow(
                                if (type == UnifiedCollectionType.NOT_COLLECTED) "取消收藏" else type.tvLabel(),
                                enabled = !options.collectionBusy,
                                compact = true,
                                modifier = if (type == UnifiedCollectionType.entries.first()) entryModifier else Modifier,
                                selected = type == options.collectionType,
                            ) { onIntent(TvEpisodeIntent.SetCollection(type)) }
                        }
                    }
                }

                TvPlayerPanel.DanmakuSettings -> {
                    items(TvDanmakuProperty.entries) { property ->
                        val config = options.danmakuConfig
                        val (label, value) = when (property) {
                            TvDanmakuProperty.FontSize -> "字号" to config.style.fontSize.value.roundToInt().toString()
                            TvDanmakuProperty.Opacity -> "不透明度" to "${(config.style.alpha * 100).roundToInt()}%"
                            TvDanmakuProperty.Speed -> "移动速度" to config.speed.roundToInt().toString()
                            TvDanmakuProperty.Density -> "密度" to "${100 - config.safeSeparation.value.roundToInt()}"
                            TvDanmakuProperty.Area -> "显示区域" to "${(config.displayArea * 100).roundToInt()}%"
                            TvDanmakuProperty.Stroke -> "描边" to config.style.strokeWidth.roundToInt().toString()
                            TvDanmakuProperty.Weight -> "字重" to config.style.fontWeight.weight.toString()
                            TvDanmakuProperty.Top -> "顶部弹幕" to if (config.enableTop) "开启" else "关闭"
                            TvDanmakuProperty.Bottom -> "底部弹幕" to if (config.enableBottom) "开启" else "关闭"
                            TvDanmakuProperty.Floating -> "滚动弹幕" to if (config.enableFloating) "开启" else "关闭"
                            TvDanmakuProperty.Color -> "彩色弹幕" to if (config.enableColor) "开启" else "关闭"
                        }
                        val checked = when (property) {
                            TvDanmakuProperty.Top -> config.enableTop
                            TvDanmakuProperty.Bottom -> config.enableBottom
                            TvDanmakuProperty.Floating -> config.enableFloating
                            TvDanmakuProperty.Color -> config.enableColor
                            else -> null
                        }
                        TvOptionRow(
                            label, if (checked == null) value else "",
                            adjustable = checked == null,
                            checked = checked,
                            modifier = (if (property == TvDanmakuProperty.FontSize) entryModifier else Modifier)
                                .tvStepKeys { onIntent(TvEpisodeIntent.AdjustDanmaku(property, it)) },
                        ) { onIntent(TvEpisodeIntent.AdjustDanmaku(property, 1)) }
                    }
                    item { TvPlayerSectionLabel("弹幕来源与时间校准") }
                    items(options.danmakuOrigins, key = { "origin-${it.serviceId.value}" }) { origin ->
                        Column {
                            TvOptionRow(
                                origin.name,
                                checked = origin.enabled,
                            ) { onIntent(TvEpisodeIntent.ToggleDanmakuSource(origin.serviceId)) }
                            Text(
                                origin.match,
                                color = Color.LightGray,
                                modifier = Modifier.padding(horizontal = 14.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TvOptionRow(
                                "时间校准", "${origin.shiftMillis / 1000f}s",
                                supportingText = "左右调整 · 确认归零", adjustable = true,
                                modifier = Modifier.tvStepKeys {
                                    onIntent(
                                        TvEpisodeIntent.ShiftDanmakuSource(
                                            origin.serviceId,
                                            it * 500L,
                                        ),
                                    )
                                },
                            ) {
                                onIntent(TvEpisodeIntent.ShiftDanmakuSource(origin.serviceId, null))
                            }
                            if (origin.canMatch) TvOptionRow("重新匹配弹幕") {
                                onIntent(
                                    TvEpisodeIntent.MatchDanmaku(
                                        origin.providerId,
                                    ),
                                )
                            }
                        }
                    }
                }

                TvPlayerPanel.VideoSettings -> {
                    if (options.enhancementMode != null) {
                        items(VideoEnhancementMode.entries) { mode ->
                            TvOptionRow(
                                when (mode) {
                                    VideoEnhancementMode.OFF -> "原始画质"
                                    VideoEnhancementMode.PERFORMANCE -> "性能优先"
                                    VideoEnhancementMode.QUALITY -> "画质优先"
                                },
                                modifier = if (mode == VideoEnhancementMode.OFF) entryModifier else Modifier,
                                selected = mode == options.enhancementMode,
                            ) { onIntent(TvEpisodeIntent.SetEnhancement(mode)) }
                        }
                    }
                    item { TvPlayerSectionLabel("播放信息") }
                    val stats = options.stats
                    val rows = listOfNotNull(
                        "播放器" to (stats?.backend ?: "正在读取…"),
                        stats?.resolution?.let { "分辨率" to it }, stats?.frameRate?.let { "帧率" to "$it fps" },
                        stats?.videoCodec?.let { "视频解码" to it }, stats?.audioCodec?.let { "音频解码" to it },
                        stats?.videoBitrate?.let { "视频码率" to "${it / 1000} kbps" },
                        stats?.realtimeInputBitrate?.let { "带宽估计" to "${it / 1000} kbps" },
                        stats?.decodedVideoFrames?.let { "已解码帧" to it.toString() },
                        stats?.droppedVideoFrames?.let { "丢帧" to it.toString() },
                    )
                    items(rows) { (label, value) ->
                        TvOptionRow(
                            label,
                            value,
                            modifier = if (options.enhancementMode == null && label == "播放器") entryModifier else Modifier,
                            onClick = {},
                        )
                    }
                }

                TvPlayerPanel.Together -> {
                    if (together.requiresLogin) item {
                        TvOptionRow(
                            "请先在 TV 首页登录账号",
                            modifier = entryModifier,
                            onClick = {},
                        )
                    }
                    else if (!together.joined) {
                        item {
                            TvOptionTextField(
                                together.roomName, "房间名称", { onTogetherIntent(TvTogetherIntent.RoomName(it)) },
                                entryModifier
                                    .tvFocusAnchor(fields, OptionFieldFocus.Name)
                                    .tvFocusHotkey(fields, Key.DirectionDown to OptionFieldFocus.Password),
                            )
                        }
                        item {
                            TvOptionTextField(
                                together.password, "房间密码", { onTogetherIntent(TvTogetherIntent.Password(it)) },
                                Modifier
                                    .tvFocusAnchor(fields, OptionFieldFocus.Password)
                                    .tvFocusHotkey(
                                        fields,
                                        Key.DirectionUp to OptionFieldFocus.Name,
                                        Key.DirectionDown to OptionFieldFocus.Submit,
                                    ),
                                password = true,
                            )
                        }
                        item {
                            TvOptionRow(
                                when {
                                    together.joining -> "正在加入… · 取消"
                                    together.error != null -> "重新加入"
                                    else -> "加入 / 创建房间"
                                },
                                modifier = Modifier.tvFocusAnchor(fields, OptionFieldFocus.Submit),
                                icon = Icons.Rounded.Groups,
                                filled = true,
                                supportingText = together.error,
                            ) { onTogetherIntent(if (together.joining) TvTogetherIntent.CancelJoin else TvTogetherIntent.Join) }
                        }
                        item {
                            Text(
                                "输入相同的房间名称即可一起看。\n房间不存在时将自动创建。",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    } else if (together.confirmLeave) {
                        item {
                            TvOptionRow(
                                if (together.isHost) "确定解散房间" else "确定退出房间",
                                modifier = entryModifier,
                            ) { onTogetherIntent(TvTogetherIntent.Leave) }
                        }
                        item { TvOptionRow("留在房间") { onTogetherIntent(TvTogetherIntent.CancelLeave) } }
                    } else {
                        item {
                            Text(
                                "${together.roomName} · ${together.connection}",
                                color = Color.White,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                        if (together.watching.isNotBlank()) item {
                            Text(
                                together.watching,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                        if (!together.isHost) item {
                            TvOptionRow(
                                "跟随房主",
                                checked = together.following,
                                modifier = entryModifier,
                            ) { onTogetherIntent(TvTogetherIntent.ToggleFollowing) }
                        }
                        item {
                            TvOptionRow(
                                if (together.isHost) "解散房间" else "退出房间",
                                modifier = if (together.isHost) entryModifier else Modifier,
                            ) { onTogetherIntent(TvTogetherIntent.AskLeave) }
                        }
                        items(together.members) {
                            TvOptionRow(it, onClick = {})
                        }
                    }
                }

                else -> Unit
            }
        }
    }
}

@Composable
internal fun TvSpeedDialog(state: TvEpisodeUiState, onIntent: (TvEpisodeIntent) -> Boolean, entryModifier: Modifier) {
    val config = state.options.videoConfig
    val speeds = remember(config.minPlaybackSpeed, config.maxPlaybackSpeed) {
        generateSequence(config.minPlaybackSpeed) { it + .25f }.takeWhile { it <= config.maxPlaybackSpeed + .001f }
            .toList()
    }
    val initialSpeed = remember { speeds.minByOrNull { abs(it - state.playbackSpeed) } }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (speeds.indexOf(initialSpeed) - 2).coerceAtLeast(0))
    LazyColumn(Modifier.testTag("tv-speed-options"), state = list, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items(speeds) { speed ->
            TvOptionRow(
                "${speed}x", if (speed == 1f) "正常速度" else "",
                modifier = if (speed == initialSpeed) entryModifier else Modifier,
                selected = abs(speed - state.playbackSpeed) < .01f,
            ) { onIntent(TvEpisodeIntent.SetSpeed(speed)) }
        }
        item { TvPlayerSectionLabel("速度偏好") }
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
                "长按确认键的速度",
                "${config.fastForwardSpeed}x", adjustable = true,
                modifier = Modifier.tvStepKeys { onIntent(TvEpisodeIntent.SetHoldSpeed(config.fastForwardSpeed + it * .25f)) },
            ) {}
        }
    }
}

@Composable
internal fun TvDanmakuMatchPanel(
    state: TvDanmakuMatchState,
    onIntent: (TvEpisodeIntent) -> Boolean,
    entryModifier: Modifier
) {
    val fields = rememberTvFocusScope()
    fields.Resolver()
    LazyColumn(Modifier.tvFocusNavSignal(fields), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (state.selectedSubject == null) {
            item {
                TvOptionTextField(
                    state.query,
                    "番剧名称",
                    { onIntent(TvEpisodeIntent.DanmakuQuery(it)) },
                    entryModifier.tvFocusHotkey(fields, Key.DirectionDown to OptionFieldFocus.Submit),
                )
            }
            item {
                TvOptionRow(
                    if (state.loading) "正在搜索…" else "搜索",
                    enabled = !state.loading,
                    modifier = Modifier.tvFocusAnchor(fields, OptionFieldFocus.Submit),
                    icon = Icons.Rounded.Search,
                    filled = true,
                ) { onIntent(TvEpisodeIntent.SearchDanmaku) }
            }
            items(state.subjects, key = { it.id }) { subject ->
                TvOptionRow(subject.name) { onIntent(TvEpisodeIntent.SelectDanmakuSubject(subject.id)) }
            }
            if (state.searched && !state.loading && state.subjects.isEmpty()) item {
                Text(
                    "没有匹配结果，请修改名称后重试",
                    color = Color.LightGray,
                )
            }
        } else {
            item { TvOptionRow("返回番剧列表", modifier = entryModifier) { onIntent(TvEpisodeIntent.Back) } }
            item { Text(state.selectedSubject.name, color = Color.White) }
            if (state.loading) item { Text("正在加载…", color = Color.LightGray) }
            items(state.episodes, key = { it.id }) { episode ->
                TvOptionRow(episode.name, enabled = !state.loading) {
                    onIntent(
                        TvEpisodeIntent.SelectDanmakuEpisode(
                            episode.id,
                        ),
                    )
                }
            }
        }
        state.error?.let {
            item {
                Text(it, color = Color(0xFFFFC5AA))
                TvOptionRow("重新搜索") {
                    onIntent(
                        TvEpisodeIntent.SearchDanmaku,
                    )
                }
            }
        }
    }
}
