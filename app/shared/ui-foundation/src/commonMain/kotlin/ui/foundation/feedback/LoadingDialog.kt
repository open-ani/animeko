/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.loading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.ui.tooling.preview.Preview


@Composable
fun ConnectingDialog(
    text: @Composable (() -> Unit)? = { Text("Connecting...") },
    extra: @Composable ColumnScope.() -> Unit = {},
    progress: @Composable (RowScope.() -> Unit)? = {
        LinearProgressIndicator(
            Modifier.width(128.dp),
        )
    },
    confirmButton: @Composable (() -> Unit)? = null,
    onDismissRequest: (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(
        dismissOnBackPress = onDismissRequest != null,
        dismissOnClickOutside = onDismissRequest != null,
    ),
) {
    Dialog(
        onDismissRequest = { onDismissRequest?.invoke() },
        properties = properties,
    ) {
        Column(
            Modifier
                .width(IntrinsicSize.Min)
                .clip(MaterialTheme.shapes.medium)
                .shadow(4.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            text?.let {
                Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                    ProvideTextStyle(value = MaterialTheme.typography.titleMedium) {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                            text()
                        }
                    }
                }
            }
            progress?.let { p ->
                Row(modifier = Modifier.padding(top = 32.dp)) {
                    p()
                }
            }
            extra()

            if (confirmButton != null) {
                Row(
                    Modifier.padding(top = 8.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    confirmButton()
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun PreviewConnectingDialog() {
    ConnectingDialog()
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun PreviewConnectingDialogConfirm() {
    ConnectingDialog(
        confirmButton = {
            TextButton(onClick = { }) {
                Text("取消")
            }
        },
    )
}
