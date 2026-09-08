/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.foundation.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun TvOptionTextField(
    value: String,
    label: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    labelStyle: TextStyle = MaterialTheme.typography.labelMedium,
) {
    val colors = LocalTvOptionColors.current
    var focused by remember { mutableStateOf(false) }
    var editingValue by remember { mutableStateOf(value) }
    var hasLocalEdit by remember { mutableStateOf(false) }
    // Echo IME edits synchronously; a delayed projection must not replace a newer edit.
    // The ViewModel still receives every edit and owns validation and submission.
    LaunchedEffect(value, focused) {
        if (!focused || !hasLocalEdit) editingValue = value
    }
    Column(Modifier.padding(horizontal = 4.dp, vertical = 5.dp)) {
        Text(label, style = labelStyle, color = colors.muted)
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
                .background(colors.raised, TvOptionDefaults.ItemShape)
                .border(
                    if (focused) 2.dp else 1.dp,
                    if (focused) colors.focusedContainer else colors.outline,
                    TvOptionDefaults.ItemShape,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            readOnly = readOnly,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            textStyle = textStyle.copy(color = colors.content),
            singleLine = true,
            cursorBrush = SolidColor(colors.content),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        )
    }
}
