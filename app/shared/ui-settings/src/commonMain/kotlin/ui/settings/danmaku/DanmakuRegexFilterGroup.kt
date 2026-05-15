/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.danmaku

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow.Companion.Ellipsis
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.ui.foundation.getClipEntryText
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.rememberAsyncHandler
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_danmaku_add_regex
import me.him188.ani.app.ui.lang.settings_danmaku_add_regex_filter
import me.him188.ani.app.ui.lang.settings_danmaku_cancel
import me.him188.ani.app.ui.lang.settings_danmaku_confirm
import me.him188.ani.app.ui.lang.settings_danmaku_export_to_clipboard
import me.him188.ani.app.ui.lang.settings_danmaku_import_description
import me.him188.ani.app.ui.lang.settings_danmaku_import_failed
import me.him188.ani.app.ui.lang.settings_danmaku_import_from_clipboard
import me.him188.ani.app.ui.lang.settings_danmaku_import_success
import me.him188.ani.app.ui.lang.settings_danmaku_import_title
import me.him188.ani.app.ui.lang.settings_danmaku_regex_description
import me.him188.ani.app.ui.lang.settings_danmaku_regex_expression
import me.him188.ani.app.ui.lang.settings_danmaku_regex_filter_group
import me.him188.ani.app.ui.lang.settings_danmaku_regex_invalid
import me.him188.ani.app.ui.lang.settings_mediasource_rss_copied_to_clipboard
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.utils.platform.Uuid
import org.jetbrains.compose.resources.stringResource

fun isValidRegex(pattern: String): Boolean {
    return try {
        Regex(pattern)
        true
    } catch (e: Exception) {
        false
    }
}

@Composable
internal fun SettingsScope.DanmakuRegexFilterGroup(
    state: DanmakuRegexFilterState,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    if (showAdd) {
        AddRegexFilterDialog(
            onDismissRequest = {
                showAdd = false
            },
            onAdd = state.add,
            title = { Text(stringResource(Lang.settings_danmaku_add_regex_filter)) },
        )
    }

    val scope = rememberAsyncHandler()
    val clipboard = LocalClipboard.current
    val toaster = LocalToaster.current
    val copiedToast = stringResource(Lang.settings_mediasource_rss_copied_to_clipboard)

    if (showImportDialog) {
        val importSuccess = stringResource(Lang.settings_danmaku_import_success)
        val importFailed = stringResource(Lang.settings_danmaku_import_failed)

        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            icon = { Icon(Icons.Rounded.ContentPaste, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(Lang.settings_danmaku_import_title)) },
            text = { Text(stringResource(Lang.settings_danmaku_import_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val text = clipboard.getClipEntryText()
                                ?.takeIf { it.isNotBlank() }
                            val result = text?.let { state.onImport(it) } == true
                            toaster.toast(if (result) importSuccess else importFailed)
                            showImportDialog = false
                        }
                    },
                ) {
                    Text(stringResource(Lang.settings_danmaku_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(stringResource(Lang.settings_danmaku_cancel))
                }
            },
        )
    }

    Group(
        title = {
            Text(
                stringResource(Lang.settings_danmaku_regex_filter_group),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        actions = {
            Row {
                IconButton(
                    {
                        showAdd = true
                    },
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(Lang.settings_danmaku_add_regex))
                }
            }
        },
    ) {
        FlowRow(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.list.forEach { item ->
                RegexFilterItem(
                    item,
                    onDelete = { state.remove(item) },
                    onDisable = { state.switch(item) },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = {
                    scope.launch {
                        val data = state.onExport()
                        clipboard.setClipEntryText(data)
                        toaster.toast(copiedToast)
                    }
                },
            ) {
                Text(stringResource(Lang.settings_danmaku_export_to_clipboard))
            }

            TextButton(onClick = { showImportDialog = true }) {
                Text(stringResource(Lang.settings_danmaku_import_from_clipboard))
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.38f

@Composable
fun RegexFilterItem(
    item: DanmakuRegexFilter,
    isEnabled: Boolean = item.enabled,
    onDisable: () -> Unit,
    onDelete: () -> Unit
) {
//    var showConfirmDelete by rememberSaveable { mutableStateOf(false) }
    ElevatedFilterChip(
        selected = item.enabled,
        onClick = onDisable,
        label = {
            Text(
                item.regex, Modifier.ifThen(!isEnabled) { alpha(DISABLED_ALPHA) },
                maxLines = 1,
                overflow = Ellipsis,
                textAlign = TextAlign.Center,
            )
        },
        trailingIcon = {
            Icon(
                Icons.Rounded.Close,
                contentDescription = null,
                Modifier.clickable(onClick = onDelete),
            )
        },
    )

//    if (showConfirmDelete) {
//        AlertDialog(
//            onDismissRequest = { showConfirmDelete = false },
//            icon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
//            title = { Text("删除正则") },
//            text = { Text("确认删除 \"${item.regex}\"？") },
//            confirmButton = {
//                TextButton({ onDelete(); showConfirmDelete = false }) {
//                    Text(
//                        "删除",
//                        color = MaterialTheme.colorScheme.error,
//                    )
//                }
//            },
//            dismissButton = { TextButton({ showConfirmDelete = false }) { Text("取消") } },
//            modifier = Modifier.fillMaxWidth()
//                .onKeyEvent { event: KeyEvent ->
//                    if (event.key == Key.Enter) {
//                        onDelete()
//                        true // Consume the event
//                    } else {
//                        false // Pass the event to other handlers
//                    }
//                },
//        )
//    }
}


@Composable
fun AddRegexFilterDialog(
    onDismissRequest: () -> Unit,
    onAdd: (DanmakuRegexFilter) -> Unit,
    title: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var regexTextFieldValue by rememberSaveable { mutableStateOf("") }
    val isBlank by remember { derivedStateOf { regexTextFieldValue.isBlank() } }
    val validRegex by remember { derivedStateOf { isValidRegex(regexTextFieldValue) } }
    var isError by remember { mutableStateOf(false) }

    fun handleAdd() {
        if (!isBlank && validRegex) {
            isError = false
            onAdd(
                DanmakuRegexFilter(
                    id = Uuid.randomString(),
                    name = "",
                    regex = regexTextFieldValue,
                    enabled = true,
                ),
            )
            regexTextFieldValue = ""
            onDismissRequest()
        } else {
            isError = true
        }
        focusManager.clearFocus()
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    handleAdd()
                }, // Pass the text field value to onConfirm
                enabled = !isBlank,
            ) {
                Text(stringResource(Lang.settings_danmaku_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Lang.settings_danmaku_cancel))
            }
        },
        title = title,
        text = {
            OutlinedTextField(
                value = regexTextFieldValue,
                onValueChange = {
                    regexTextFieldValue = it
                    isError = false
                },
                label = { Text(stringResource(Lang.settings_danmaku_regex_expression)) },
                modifier = Modifier.fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onKeyEvent { event: KeyEvent ->
                        if (event.key == Key.Enter) {
                            handleAdd()
                            true // Consume the event
                        } else {
                            false // Pass the event to other handlers
                        }
                    },
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { handleAdd() },
                ),
                supportingText = {
                    if (isError) {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(Lang.settings_danmaku_regex_invalid),
                        )
                    } else {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(Lang.settings_danmaku_regex_description),
                        )
                    }
                },
                isError = isError,
                singleLine = true,
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        },
    )
}
