/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.preference.PlayerShortcut
import me.him188.ani.app.data.models.preference.PlayerShortcutAction
import me.him188.ani.app.data.models.preference.PlayerShortcutConfig
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.input.PlayerShortcutKeys
import me.him188.ani.app.ui.foundation.input.displayText
import me.him188.ani.app.ui.foundation.input.shortcutDisplayText
import me.him188.ani.app.ui.foundation.input.toPlayerShortcut
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_player_shortcut_seek_backward
import me.him188.ani.app.ui.lang.settings_player_shortcut_seek_forward
import me.him188.ani.app.ui.lang.settings_player_shortcut_seek_forward_description
import me.him188.ani.app.ui.lang.settings_player_shortcut_speed_down
import me.him188.ani.app.ui.lang.settings_player_shortcut_speed_preset
import me.him188.ani.app.ui.lang.settings_player_shortcut_speed_reset
import me.him188.ani.app.ui.lang.settings_player_shortcut_speed_up
import me.him188.ani.app.ui.lang.settings_player_shortcut_toggle_danmaku
import me.him188.ani.app.ui.lang.settings_player_shortcut_toggle_fullscreen
import me.him188.ani.app.ui.lang.settings_player_shortcut_toggle_pause_resume
import me.him188.ani.app.ui.lang.settings_player_shortcut_toggle_player_stats
import me.him188.ani.app.ui.lang.settings_player_shortcut_volume_description
import me.him188.ani.app.ui.lang.settings_player_shortcut_volume_down
import me.him188.ani.app.ui.lang.settings_player_shortcut_volume_up
import me.him188.ani.app.ui.lang.settings_player_shortcuts_cancel
import me.him188.ani.app.ui.lang.settings_player_shortcuts_capture_active
import me.him188.ani.app.ui.lang.settings_player_shortcuts_capture_idle
import me.him188.ani.app.ui.lang.settings_player_shortcuts_confirm
import me.him188.ani.app.ui.lang.settings_player_shortcuts_conflict
import me.him188.ani.app.ui.lang.settings_player_shortcuts_edit_hint
import me.him188.ani.app.ui.lang.settings_player_shortcuts_group_playback
import me.him188.ani.app.ui.lang.settings_player_shortcuts_group_seek
import me.him188.ani.app.ui.lang.settings_player_shortcuts_group_speed
import me.him188.ani.app.ui.lang.settings_player_shortcuts_group_volume
import me.him188.ani.app.ui.lang.settings_player_shortcuts_none
import me.him188.ani.app.ui.lang.settings_player_shortcuts_remove
import me.him188.ani.app.ui.lang.settings_player_shortcuts_reset
import me.him188.ani.app.ui.lang.settings_player_shortcuts_reset_all
import me.him188.ani.app.ui.lang.settings_player_shortcuts_reset_all_confirm
import me.him188.ani.app.ui.lang.settings_player_shortcuts_unsupported_key
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.utils.platform.Platform
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal object PlayerShortcutsTestTags {
    const val ITEM_PREFIX = "player_shortcut_item_"
    const val CAPTURE = "player_shortcut_capture"
    const val CHIP_PREFIX = "player_shortcut_chip_"
    const val CONFIRM = "player_shortcut_confirm"
    const val CANCEL = "player_shortcut_cancel"
    const val RESET = "player_shortcut_reset"
    const val RESET_ALL = "player_shortcuts_reset_all"
    const val RESET_ALL_CONFIRM = "player_shortcuts_reset_all_confirm"
    const val HINT = "player_shortcut_hint"

    fun item(action: PlayerShortcutAction) = ITEM_PREFIX + action.name
    fun chip(shortcut: PlayerShortcut) = CHIP_PREFIX + shortcut.toString()
}

private class ShortcutGroup(
    val title: StringResource,
    val actions: List<PlayerShortcutAction>,
)

private val SHORTCUT_GROUPS = listOf(
    ShortcutGroup(
        Lang.settings_player_shortcuts_group_playback,
        listOf(
            PlayerShortcutAction.TOGGLE_PAUSE_RESUME,
            PlayerShortcutAction.TOGGLE_FULLSCREEN,
            PlayerShortcutAction.TOGGLE_DANMAKU,
            PlayerShortcutAction.TOGGLE_PLAYER_STATS,
        ),
    ),
    ShortcutGroup(
        Lang.settings_player_shortcuts_group_seek,
        listOf(PlayerShortcutAction.SEEK_BACKWARD, PlayerShortcutAction.SEEK_FORWARD),
    ),
    ShortcutGroup(
        Lang.settings_player_shortcuts_group_volume,
        listOf(PlayerShortcutAction.VOLUME_UP, PlayerShortcutAction.VOLUME_DOWN),
    ),
    ShortcutGroup(
        Lang.settings_player_shortcuts_group_speed,
        listOf(
            PlayerShortcutAction.PLAYBACK_SPEED_DOWN,
            PlayerShortcutAction.PLAYBACK_SPEED_UP,
            PlayerShortcutAction.PLAYBACK_SPEED_RESET,
            PlayerShortcutAction.PLAYBACK_SPEED_1X,
            PlayerShortcutAction.PLAYBACK_SPEED_2X,
            PlayerShortcutAction.PLAYBACK_SPEED_3X,
        ),
    ),
)

/**
 * 播放器键盘快捷键页面: 按组列出所有操作和当前按键, 点击一项打开 [PlayerShortcutEditDialog] 修改.
 */
@Composable
fun PlayerShortcutsTab(
    state: SettingsState<PlayerShortcutConfig>,
    modifier: Modifier = Modifier,
) {
    val config by state
    var editingAction by remember { mutableStateOf<PlayerShortcutAction?>(null) }

    editingAction?.let { action ->
        PlayerShortcutEditDialog(
            action = action,
            config = config,
            onConfirm = { shortcuts ->
                state.update(config.withShortcuts(action, shortcuts))
                editingAction = null
            },
            onDismissRequest = { editingAction = null },
        )
    }

    SettingsTab(modifier) {
        for (group in SHORTCUT_GROUPS) {
            Group(title = { Text(stringResource(group.title)) }, useThinHeader = true) {
                group.actions.forEachIndexed { index, action ->
                    if (index != 0) HorizontalDividerItem()
                    ShortcutItem(
                        action = action,
                        shortcuts = config.shortcutsFor(action),
                        onClick = { editingAction = action },
                    )
                }
            }
        }
    }
}

/**
 * 页面顶栏的「恢复全部默认」按钮, 没有任何自定义时禁用. 点击后需要确认.
 */
@Composable
fun PlayerShortcutsResetAction(state: SettingsState<PlayerShortcutConfig>) {
    val config by state
    var showConfirm by remember { mutableStateOf(false) }
    IconButton(
        onClick = { showConfirm = true },
        enabled = config.overrides.isNotEmpty(),
        modifier = Modifier.testTag(PlayerShortcutsTestTags.RESET_ALL),
    ) {
        Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(Lang.settings_player_shortcuts_reset_all))
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            icon = { Icon(Icons.Rounded.RestartAlt, contentDescription = null) },
            title = { Text(stringResource(Lang.settings_player_shortcuts_reset_all)) },
            text = { Text(stringResource(Lang.settings_player_shortcuts_reset_all_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.update(PlayerShortcutConfig.Default)
                        showConfirm = false
                    },
                    modifier = Modifier.testTag(PlayerShortcutsTestTags.RESET_ALL_CONFIRM),
                ) {
                    Text(stringResource(Lang.settings_player_shortcuts_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(stringResource(Lang.settings_player_shortcuts_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScope.ShortcutItem(
    action: PlayerShortcutAction,
    shortcuts: List<PlayerShortcut>,
    onClick: () -> Unit,
) {
    val description = action.renderDescription()
    Item(
        headlineContent = { Text(action.renderName()) },
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(PlayerShortcutsTestTags.item(action)),
        supportingContent = description?.let { { Text(it) } },
        trailingContent = {
            if (shortcuts.isEmpty()) {
                Text(stringResource(Lang.settings_player_shortcuts_none))
            } else {
                FlowRow(
                    Modifier.widthIn(max = 240.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (shortcut in shortcuts) {
                        ShortcutKeyCap(shortcut.displayText(isMacOs = LocalPlatform.current is Platform.MacOS))
                    }
                }
            }
        },
    )
}

/**
 * 键帽样式的按键组合文本.
 */
@Composable
private fun ShortcutKeyCap(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

private sealed class CaptureHint {
    data object UnsupportedKey : CaptureHint()
}

/**
 * 编辑一个操作的快捷键. 点击采集区后按下按键即可添加, 点击已添加的按键将其移除; 「确认」才写入配置.
 *
 * 添加的组合若正被其他操作使用, 会提示冲突; 确认后该组合改为属于本操作 (由 [PlayerShortcutConfig.withShortcuts] 处理).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerShortcutEditDialog(
    action: PlayerShortcutAction,
    config: PlayerShortcutConfig,
    onConfirm: (List<PlayerShortcut>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val isMacOs = LocalPlatform.current is Platform.MacOS
    val pending = remember(action) { mutableStateListOf<PlayerShortcut>().apply { addAll(config.shortcutsFor(action)) } }
    var hint by remember { mutableStateOf<CaptureHint?>(null) }
    val defaults = PlayerShortcutConfig.DEFAULT_BINDINGS.getValue(action)
    val conflicts = pending.mapNotNull { shortcut ->
        config.actionFor(shortcut)?.takeIf { it != action }?.let { shortcut to it }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Rounded.Keyboard, contentDescription = null) },
        title = { Text(action.renderName()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(Lang.settings_player_shortcuts_edit_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (pending.isEmpty()) {
                    Text(
                        stringResource(Lang.settings_player_shortcuts_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (shortcut in pending) {
                            InputChip(
                                selected = false,
                                onClick = { pending.remove(shortcut) },
                                label = { Text(shortcut.displayText(isMacOs)) },
                                modifier = Modifier.testTag(PlayerShortcutsTestTags.chip(shortcut)),
                                trailingIcon = {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = stringResource(Lang.settings_player_shortcuts_remove),
                                        Modifier.size(InputChipDefaults.IconSize),
                                    )
                                },
                            )
                        }
                    }
                }
                ShortcutCaptureField(
                    isMacOs = isMacOs,
                    onCaptured = { shortcut ->
                        hint = null
                        if (shortcut !in pending) pending.add(shortcut)
                    },
                    onUnsupportedKey = { hint = CaptureHint.UnsupportedKey },
                )
                val hintText = when (hint) {
                    CaptureHint.UnsupportedKey -> stringResource(Lang.settings_player_shortcuts_unsupported_key)
                    null -> null
                }
                if (hintText != null || conflicts.isNotEmpty()) {
                    Column(Modifier.testTag(PlayerShortcutsTestTags.HINT)) {
                        hintText?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        for ((shortcut, other) in conflicts) {
                            Text(
                                stringResource(
                                    Lang.settings_player_shortcuts_conflict,
                                    shortcut.displayText(isMacOs),
                                    other.renderName(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pending.toList()) },
                modifier = Modifier.testTag(PlayerShortcutsTestTags.CONFIRM),
            ) {
                Text(stringResource(Lang.settings_player_shortcuts_confirm))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        pending.clear()
                        pending.addAll(defaults)
                        hint = null
                    },
                    enabled = pending.toList() != defaults,
                    modifier = Modifier.testTag(PlayerShortcutsTestTags.RESET),
                ) {
                    Text(stringResource(Lang.settings_player_shortcuts_reset))
                }
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.testTag(PlayerShortcutsTestTags.CANCEL),
                ) {
                    Text(stringResource(Lang.settings_player_shortcuts_cancel))
                }
            }
        },
    )
}

/**
 * 按键采集区. 获得焦点后监听按键, 把按下的组合交给 [onCaptured]; Escape 和 Tab 不拦截, 留给对话框关闭和焦点遍历.
 * 对话框打开时自动获得焦点.
 */
@Composable
private fun ShortcutCaptureField(
    isMacOs: Boolean,
    onCaptured: (PlayerShortcut) -> Unit,
    onUnsupportedKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var heldModifiers by remember { mutableStateOf("") }
    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }

    val borderColor = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) heldModifiers = ""
            }
            .onPreviewKeyEvent { event ->
                when {
                    event.key == Key.Escape || event.key == Key.Tab -> false
                    PlayerShortcutKeys.isModifierKey(event.key) -> {
                        heldModifiers = heldModifiersText(event, isMacOs)
                        true
                    }

                    event.type == KeyEventType.KeyDown -> {
                        heldModifiers = ""
                        val shortcut = event.toPlayerShortcut()
                        if (shortcut == null) onUnsupportedKey() else onCaptured(shortcut)
                        true
                    }

                    else -> true
                }
            }
            .clickable { focusRequester.requestFocus() }
            .testTag(PlayerShortcutsTestTags.CAPTURE),
        shape = MaterialTheme.shapes.medium,
        color = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (focused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(if (focused) 2.dp else 1.dp, borderColor),
    ) {
        Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                when {
                    !focused -> stringResource(Lang.settings_player_shortcuts_capture_idle)
                    heldModifiers.isNotEmpty() -> heldModifiers
                    else -> stringResource(Lang.settings_player_shortcuts_capture_active)
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 修饰键按住时的实时预览, 如 `Ctrl + Shift + …`. 松开修饰键的事件里该修饰键不再计入.
 */
private fun heldModifiersText(event: KeyEvent, isMacOs: Boolean): String {
    val releasing = event.type == KeyEventType.KeyUp
    val ctrl = event.isCtrlPressed && !(releasing && (event.key == Key.CtrlLeft || event.key == Key.CtrlRight))
    val alt = event.isAltPressed && !(releasing && (event.key == Key.AltLeft || event.key == Key.AltRight))
    val shift = event.isShiftPressed && !(releasing && (event.key == Key.ShiftLeft || event.key == Key.ShiftRight))
    val meta = event.isMetaPressed && !(releasing && (event.key == Key.MetaLeft || event.key == Key.MetaRight))
    if (!ctrl && !alt && !shift && !meta) return ""
    return shortcutDisplayText(ctrl, alt, shift, meta, keyLabel = "…", isMacOs = isMacOs)
}

@Composable
internal fun PlayerShortcutAction.renderName(): String = when (this) {
    PlayerShortcutAction.TOGGLE_PAUSE_RESUME -> stringResource(Lang.settings_player_shortcut_toggle_pause_resume)
    PlayerShortcutAction.TOGGLE_FULLSCREEN -> stringResource(Lang.settings_player_shortcut_toggle_fullscreen)
    PlayerShortcutAction.TOGGLE_DANMAKU -> stringResource(Lang.settings_player_shortcut_toggle_danmaku)
    PlayerShortcutAction.TOGGLE_PLAYER_STATS -> stringResource(Lang.settings_player_shortcut_toggle_player_stats)
    PlayerShortcutAction.SEEK_BACKWARD -> stringResource(Lang.settings_player_shortcut_seek_backward)
    PlayerShortcutAction.SEEK_FORWARD -> stringResource(Lang.settings_player_shortcut_seek_forward)
    PlayerShortcutAction.VOLUME_UP -> stringResource(Lang.settings_player_shortcut_volume_up)
    PlayerShortcutAction.VOLUME_DOWN -> stringResource(Lang.settings_player_shortcut_volume_down)
    PlayerShortcutAction.PLAYBACK_SPEED_DOWN -> stringResource(Lang.settings_player_shortcut_speed_down)
    PlayerShortcutAction.PLAYBACK_SPEED_UP -> stringResource(Lang.settings_player_shortcut_speed_up)
    PlayerShortcutAction.PLAYBACK_SPEED_RESET -> stringResource(Lang.settings_player_shortcut_speed_reset)
    PlayerShortcutAction.PLAYBACK_SPEED_1X -> stringResource(Lang.settings_player_shortcut_speed_preset, "1")
    PlayerShortcutAction.PLAYBACK_SPEED_2X -> stringResource(Lang.settings_player_shortcut_speed_preset, "2")
    PlayerShortcutAction.PLAYBACK_SPEED_3X -> stringResource(Lang.settings_player_shortcut_speed_preset, "3")
}

@Composable
private fun PlayerShortcutAction.renderDescription(): String? = when (this) {
    PlayerShortcutAction.SEEK_FORWARD -> stringResource(Lang.settings_player_shortcut_seek_forward_description)
    PlayerShortcutAction.VOLUME_UP, PlayerShortcutAction.VOLUME_DOWN ->
        stringResource(Lang.settings_player_shortcut_volume_description)

    else -> null
}
