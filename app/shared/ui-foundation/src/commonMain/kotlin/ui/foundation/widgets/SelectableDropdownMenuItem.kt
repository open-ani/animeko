/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.ifThen

/**
 * 为 [DropdownMenuItem] 增加了 [selected], 为 `true` 时会显示 [Check] 图标并调整背景颜色. 符合 M3 规范.
 *
 * [specs](https://m3.material.io/components/menus/specs)
 *
 * @see DropdownMenuItem
 */
@Composable
fun SelectableDropdownMenuItem(
    selected: Boolean,
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource? = null,
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Unspecified
    }
    val textColor = MaterialTheme.colorScheme.contentColorFor(backgroundColor)
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier.ifThen(backgroundColor != Color.Unspecified) {
            background(backgroundColor)
        },
        leadingIcon,
        trailingIcon = {
            if (selected) {
                Icon(Icons.Rounded.Check, null)
            }
        },
        enabled,
        colors = MenuDefaults.itemColors().copy(
            textColor = textColor,
            disabledTextColor = textColor.copy(alpha = 0.38f),
        ),
        contentPadding,
        interactionSource,
    )
}

@Composable
@Preview
private fun PreviewSelectableDropdownMenuItem() {
    ProvideCompositionLocalsForPreview {
        Surface {
            Column {
                SelectableDropdownMenuItem(
                    selected = true,
                    text = { Text("Hello") },
                    {},
                )

                Spacer(Modifier.height(16.dp))

                SelectableDropdownMenuItem(
                    selected = false,
                    text = { Text("Hello") },
                    {},
                )

            }
        }
    }
}
