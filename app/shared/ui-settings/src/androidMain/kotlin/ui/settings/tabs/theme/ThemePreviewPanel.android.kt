/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.theme.appColorScheme

@Preview
@Composable
fun PreviewThemePreviewPanel() {
    ProvideCompositionLocalsForPreview {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePreviewPanel(
                colorScheme = appColorScheme(isDark = false),
                modifier = Modifier.size(96.dp, 146.dp),
            )
            ThemePreviewPanel(
                colorScheme = appColorScheme(isDark = true),
                modifier = Modifier.size(96.dp, 146.dp),
            )
            DiagonalMixedThemePreviewPanel(
                leftTopColorScheme = appColorScheme(isDark = false),
                rightBottomColorScheme = appColorScheme(isDark = true),
                modifier = Modifier.size(96.dp, 146.dp),
            )
        }
    }
}