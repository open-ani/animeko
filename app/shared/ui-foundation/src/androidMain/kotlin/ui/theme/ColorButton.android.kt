/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.theme

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.ui.foundation.ProvideFoundationCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults

@Preview
@Composable
fun PreviewColorButton() {
    ProvideFoundationCompositionLocalsForPreview {
        FlowRow {
            var currentColor by remember { mutableStateOf(AniThemeDefaults.colorList[0]) }
            AniThemeDefaults.colorList.forEach {
                ColorButton(
                    onClick = { currentColor = it },
                    baseColor = it,
                    selected = currentColor == it,
                )
            }
        }
    }
}