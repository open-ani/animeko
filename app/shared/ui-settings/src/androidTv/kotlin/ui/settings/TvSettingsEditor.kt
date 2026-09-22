/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_danmaku_cancel
import me.him188.ani.app.ui.lang.settings_danmaku_import_description
import me.him188.ani.app.ui.lang.settings_danmaku_regex_invalid
import me.him188.ani.app.ui.lang.settings_import
import me.him188.ani.app.ui.lang.settings_oss_licenses_homepage
import me.him188.ani.app.ui.lang.settings_playback_speed_max
import me.him188.ani.app.ui.lang.settings_playback_speed_min
import me.him188.ani.app.ui.lang.settings_player_playback_speed_range_description
import me.him188.ani.app.ui.lang.settings_media_source_save_button
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.TvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusHotkey
import me.him188.ani.tv.ui.foundation.layout.TvModalOverlay
import me.him188.ani.tv.ui.foundation.widgets.LocalTvOptionColors
import me.him188.ani.tv.ui.foundation.widgets.TvOptionModal
import me.him188.ani.tv.ui.foundation.widgets.TvOptionRow
import me.him188.ani.tv.ui.foundation.widgets.TvOptionStepper
import org.jetbrains.compose.resources.stringResource

internal fun editorKey(id: String) = TvFocusKey("settings-editor-$id")

@Composable
internal fun TvSettingsEditor(
    dialog: TvSettingsDialog,
    focus: TvFocusScope,
    onIntent: (TvSettingsIntent) -> Unit,
    onOpenUrl: (String) -> Unit,
    onImport: () -> Unit,
    onClose: (deleted: Boolean) -> Unit,
) {
    if (dialog is TvSettingsDialog.Order) {
        TvSettingsOrderEditor(dialog, focus) { onClose(false) }
        return
    }
    if (dialog is TvSettingsDialog.Regex) {
        TvSettingsRegexEditor(dialog, focus, onIntent, onClose)
        return
    }
    val close = { onClose(false) }
    LaunchedEffect(dialog) {
        focus.request(editorKey(if (dialog is TvSettingsDialog.Choice && dialog.values.any { it.id == dialog.selected }) {
            dialog.selected
        } else "entry"))
    }
    TvModalOverlay(onClose = close, background = {}) {
        when (dialog) {
            is TvSettingsDialog.Choice -> TvOptionModal(dialog.title, Modifier.testTag("tv-settings-editor")) {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    dialog.values.forEachIndexed { index, option ->
                        TvOptionRow(
                            title = option.title, selected = option.id == dialog.selected,
                            showSelectionIndicator = false,
                            trailingIcon = if (option.id == dialog.selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                            modifier = Modifier.testTag("tv-settings-choice-${option.id}")
                                .semantics { role = Role.RadioButton }
                                .tvFocusAnchor(focus, editorKey(option.id))
                                .then(if (index == 0) Modifier.tvFocusAnchor(focus, editorKey("entry")) else Modifier),
                        ) { dialog.onSelect(option.id); close() }
                    }
                }
            }
            is TvSettingsDialog.Input -> {
                var text by remember(dialog) { mutableStateOf(dialog.initial) }
                val valid = dialog.validate(text)
                TvOptionModal(
                    dialog.title, Modifier.testTag("tv-settings-editor"), subtitle = dialog.description,
                    footer = { EditorActions(valid, { dialog.onSave(text); close() }, close, focus = focus) },
                ) {
                    OutlinedTextField(
                        text, { text = it },
                        Modifier.fillMaxWidth().tvFocusAnchor(focus, editorKey("entry"))
                            .tvFocusHotkey(focus, Key.DirectionDown to editorKey(if (valid) "save" else "cancel"))
                            .testTag("tv-settings-input"),
                        isError = !valid, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.request(editorKey(if (valid) "save" else "cancel")) }),
                    )
                    if (!valid) Text(stringResource(Lang.settings_danmaku_regex_invalid), color = MaterialTheme.colorScheme.error)
                }
            }
            is TvSettingsDialog.SpeedRange -> {
                var min by remember(dialog) { mutableFloatStateOf(dialog.config.minPlaybackSpeed) }
                var max by remember(dialog) { mutableFloatStateOf(dialog.config.maxPlaybackSpeed) }
                TvOptionModal(
                    dialog.title, Modifier.testTag("tv-settings-editor"),
                    subtitle = stringResource(Lang.settings_player_playback_speed_range_description),
                    footer = { EditorActions(true, {
                        val range = min..max
                        onIntent(TvSettingsIntent.Video { withPlaybackSpeedRange(range) })
                        close()
                    }, close) },
                ) {
                    Text(stringResource(Lang.settings_playback_speed_min))
                    TvOptionStepper(
                        stringResource(Lang.settings_playback_speed_min), "$min×",
                        min > VideoScaffoldConfig.MIN_SUPPORTED_PLAYBACK_SPEED, min + .25f < max,
                        { step -> min = (min + step * .25f).coerceIn(VideoScaffoldConfig.MIN_SUPPORTED_PLAYBACK_SPEED, max - .25f) },
                        Modifier.tvFocusAnchor(focus, editorKey("entry")).testTag("tv-settings-speed-min"),
                    )
                    Text(stringResource(Lang.settings_playback_speed_max))
                    TvOptionStepper(
                        stringResource(Lang.settings_playback_speed_max), "$max×",
                        max - .25f > min, max < VideoScaffoldConfig.MAX_SUPPORTED_PLAYBACK_SPEED,
                        { step -> max = (max + step * .25f).coerceIn(min + .25f, VideoScaffoldConfig.MAX_SUPPORTED_PLAYBACK_SPEED) },
                        Modifier.testTag("tv-settings-speed-max"),
                    )
                }
            }
            is TvSettingsDialog.Order, is TvSettingsDialog.Regex -> Unit
            is TvSettingsDialog.Link -> TvSettingsLinkEditor(dialog, focus, onOpenUrl)
            is TvSettingsDialog.Info -> InfoEditor(dialog, focus, onOpenUrl)
            is TvSettingsDialog.Import -> TvOptionModal(
                dialog.title, Modifier.testTag("tv-settings-editor"),
                footer = {
                    EditorActions(true, { onImport(); close() }, close, Modifier.tvFocusAnchor(focus, editorKey("entry")), stringResource(Lang.settings_import))
                },
            ) { Text(stringResource(Lang.settings_danmaku_import_description)) }
        }
    }
}

@Composable
private fun InfoEditor(dialog: TvSettingsDialog.Info, focus: TvFocusScope, onOpenUrl: (String) -> Unit) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    var scrollKey by remember { mutableStateOf<Key?>(null) }
    TvOptionModal(
        dialog.title, Modifier.testTag("tv-settings-editor"),
        footer = dialog.website?.let { url ->
            { TvOptionRow(stringResource(Lang.settings_oss_licenses_homepage)) { onOpenUrl(url) } }
        },
    ) {
        Column(
            Modifier.fillMaxWidth().tvFocusAnchor(focus, editorKey("entry"))
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == scrollKey) {
                        scrollKey = null
                        true
                    } else if (event.type == KeyEventType.KeyDown) {
                        val direction = when (event.key) {
                            Key.DirectionDown -> if (scroll.canScrollForward) 1 else 0
                            Key.DirectionUp -> if (scroll.canScrollBackward) -1 else 0
                            else -> 0
                        }
                        if (direction != 0) {
                            scrollKey = event.key
                            scope.launch { scroll.animateScrollBy(direction * scroll.viewportSize * .6f) }
                            true
                        } else event.key == scrollKey
                    } else false
                }
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .04f), RoundedCornerShape(12.dp))
                .border(1.dp, if (focused) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent, RoundedCornerShape(12.dp))
                .verticalScroll(scroll).focusable().testTag("tv-settings-info").padding(12.dp),
        ) { Text(dialog.text, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
internal fun EditorActions(
    enabled: Boolean,
    save: () -> Unit,
    close: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String = stringResource(Lang.settings_media_source_save_button),
    focus: TvFocusScope? = null,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsActionButton(
            actionLabel, enabled = enabled,
            modifier = Modifier.weight(1f).testTag("tv-settings-save")
                .then(if (focus != null) Modifier.tvFocusAnchor(focus, editorKey("save")) else Modifier), onClick = save,
        )
        SettingsActionButton(
            stringResource(Lang.settings_danmaku_cancel),
            modifier = Modifier.weight(1f).testTag("tv-settings-cancel")
                .then(if (focus != null) Modifier.tvFocusAnchor(focus, editorKey("cancel")) else Modifier), onClick = close,
        )
    }
}

@Composable
internal fun SettingsActionButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalTvOptionColors.current
    Button(
        onClick, modifier, enabled = enabled,
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ButtonDefaults.colors(
            containerColor = colors.raised, contentColor = colors.content,
            focusedContainerColor = colors.focusedContainer, focusedContentColor = colors.focusedContent,
        ),
    ) { Text(label) }
}
