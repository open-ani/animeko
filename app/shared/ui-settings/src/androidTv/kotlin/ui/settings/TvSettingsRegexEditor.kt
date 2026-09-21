/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_danmaku_cancel
import me.him188.ani.app.ui.lang.settings_danmaku_delete_rule_description
import me.him188.ani.app.ui.lang.settings_danmaku_delete_rule_title
import me.him188.ani.app.ui.lang.settings_danmaku_regex_expression
import me.him188.ani.app.ui.lang.settings_danmaku_regex_invalid
import me.him188.ani.app.ui.lang.settings_danmaku_rule_enabled
import me.him188.ani.app.ui.lang.settings_media_source_subscription_delete
import me.him188.ani.leanback.ui.foundation.focus.TvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.focus.tvFocusHotkey
import me.him188.ani.leanback.ui.foundation.layout.TvModalOverlay
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionModal
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionRow
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TvSettingsRegexEditor(
    dialog: TvSettingsDialog.Regex,
    focus: TvFocusScope,
    onIntent: (TvSettingsIntent) -> Unit,
    onClose: (Boolean) -> Unit,
) {
    var text by remember(dialog) { mutableStateOf(dialog.filter.regex) }
    var enabled by remember(dialog) { mutableStateOf(dialog.filter.enabled) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var openedConfirmation by remember { mutableStateOf(false) }
    val valid = text.isNotBlank() && isTvSettingsRegexValid(text)
    LaunchedEffect(confirmingDelete) {
        focus.request(editorKey(when {
            confirmingDelete -> "delete-cancel"
            openedConfirmation -> "delete"
            else -> "entry"
        }))
    }
    val wasConfirming = confirmingDelete
    val close = {
        if (wasConfirming == confirmingDelete) {
            if (confirmingDelete) confirmingDelete = false else onClose(false)
        }
    }
    TvModalOverlay(onClose = close, background = {}) {
        if (confirmingDelete) TvOptionModal(
            stringResource(Lang.settings_danmaku_delete_rule_title), Modifier.testTag("tv-settings-delete-confirmation"),
            subtitle = stringResource(Lang.settings_danmaku_delete_rule_description),
            footer = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsActionButton(
                        stringResource(Lang.settings_danmaku_cancel),
                        modifier = Modifier.weight(1f).tvFocusAnchor(focus, editorKey("delete-cancel"))
                            .testTag("tv-settings-delete-cancel"),
                    ) { confirmingDelete = false }
                    SettingsActionButton(
                        stringResource(Lang.settings_media_source_subscription_delete),
                        modifier = Modifier.weight(1f).testTag("tv-settings-delete-confirm"),
                    ) { onIntent(TvSettingsIntent.RemoveRegex(dialog.filter)); onClose(true) }
                }
            },
        ) { Text(dialog.filter.regex, maxLines = 3) }
        else TvOptionModal(
            dialog.title, Modifier.testTag("tv-settings-editor"),
            footer = {
                EditorActions(valid, {
                    onIntent(TvSettingsIntent.SaveRegex(dialog.filter.copy(regex = text, enabled = enabled), dialog.isNew))
                    onClose(false)
                }, { onClose(false) })
            },
        ) {
            OutlinedTextField(
                text, { text = it },
                Modifier.fillMaxWidth().tvFocusAnchor(focus, editorKey("entry"))
                    .tvFocusHotkey(focus, Key.DirectionDown to editorKey("rule-enabled")).testTag("tv-settings-input"),
                label = { Text(stringResource(Lang.settings_danmaku_regex_expression)) },
                isError = text.isNotEmpty() && !valid, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.request(editorKey("rule-enabled")) }),
            )
            if (text.isNotEmpty() && !valid) Text(
                stringResource(Lang.settings_danmaku_regex_invalid), color = MaterialTheme.colorScheme.error,
            )
            TvOptionRow(
                stringResource(Lang.settings_danmaku_rule_enabled), checked = enabled,
                modifier = Modifier.tvFocusAnchor(focus, editorKey("rule-enabled")).testTag("tv-settings-rule-enabled"),
            ) { enabled = !enabled }
            if (!dialog.isNew) TvOptionRow(
                stringResource(Lang.settings_media_source_subscription_delete),
                modifier = Modifier.tvFocusAnchor(focus, editorKey("delete")).testTag("tv-settings-regex-delete"),
            ) { openedConfirmation = true; confirmingDelete = true }
        }
    }
}
