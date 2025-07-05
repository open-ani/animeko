/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.framework.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.effects.defaultFocus
import me.him188.ani.app.ui.foundation.effects.onKey
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor


/**
 * @param sanitizeValue 每当用户输入时调用, 可以清除首尾空格等
 * @param onValueChangeCompleted 当用户点击对话框的 "确认" 时调用
 */
@SettingsDsl
@Composable
fun SettingsScope.TextFieldItem(
    value: String,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    description: @Composable ((value: String) -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    onValueChangeCompleted: (value: String) -> Unit = {},
    inverseTitleDescription: Boolean = false,
    isErrorProvider: (value: String) -> Boolean = { false }, // calculated in a derivedState
    sanitizeValue: (value: String) -> String = { it },
    textFieldDescription: @Composable ((value: String) -> Unit)? = description,
    exposedItem: @Composable (value: String) -> Unit = { Text(it) },
    extra: @Composable ColumnScope.(editingValue: MutableState<String>) -> Unit = {}
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    // 保存了的值
    val valueText = @Composable {
        if (placeholder != null && value.isEmpty()) {
            placeholder()
        } else {
            exposedItem(value)
        }
    }
    Box {
        Item(
            headlineContent = {
                if (inverseTitleDescription) {
                    valueText()
                } else {
                    title()
                }
            },
            modifier.clickable(onClick = { showDialog = true }),
            leadingContent = icon?.let {
                {
                    SettingsDefaults.ItemIcon {
                        it()
                    }
                }
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (inverseTitleDescription) {
                        title()
                    } else {
                        valueText()
                    }
                }
            },
            trailingContent = {
                IconButton({ showDialog = true }) {
                    Icon(Icons.Rounded.Edit, "编辑", tint = MaterialTheme.colorScheme.primary)
                }
            },
        )

        if (showDialog) {
            // 正在编辑的值
            val editingValueState = rememberSaveable(value) {
                mutableStateOf(value)
            }
            var editingValue by editingValueState
            val error by remember(isErrorProvider) {
                derivedStateOf {
                    isErrorProvider(editingValue)
                }
            }
            val onConfirm = remember(onValueChangeCompleted) {
                {
                    onValueChangeCompleted(editingValue)
                    showDialog = false
                }
            }

            TextFieldDialog(
                onDismissRequest = { showDialog = false },
                onConfirm = onConfirm,
                title = title,
                confirmEnabled = !error,
                description = { textFieldDescription?.invoke(editingValue) },
                extra = { extra(editingValueState) },
            ) {
                OutlinedTextField(
                    value = editingValue,
                    onValueChange = { editingValue = sanitizeValue(it) },
                    shape = MaterialTheme.shapes.medium,
                    keyboardActions = KeyboardActions {
                        if (!error) {
                            onConfirm()
                        }
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth()
                        .defaultFocus()
                        .onKey(Key.Enter) {
                            if (!error) {
                                onConfirm()
                            }
                        },
                    isError = error,
                )
            }
        }
    }
}


/**
 * [TextFieldItem] 使用
 */
@Composable
internal fun SettingsScope.TextFieldDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    title: @Composable () -> Unit,
    confirmEnabled: Boolean = true,
    description: @Composable (() -> Unit)? = null,
    extra: @Composable (ColumnScope.() -> Unit) = {},
    textField: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
            ) {
                Text("确认")
            }
        },
        title = title,
        text = {
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row {
                    textField()
                }

                extra()

                ProvideTextStyleContentColor(
                    MaterialTheme.typography.labelMedium,
                    LocalContentColor.current.copy(labelAlpha),
                ) {
                    description?.let {
                        Row(Modifier.padding(horizontal = 8.dp)) {
                            it()
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("取消") }
        },
    )
}
