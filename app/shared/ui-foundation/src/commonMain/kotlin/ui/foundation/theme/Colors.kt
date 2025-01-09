/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun Color.looming(): Color {
    return copy(alpha = 0.90f)
}

@Composable
fun Color.slightlyWeaken(): Color {
    return copy(alpha = 0.618f)
}

@Composable
fun Color.weaken(): Color {
    return copy(alpha = 0.5f)
}

@Composable
fun Color.stronglyWeaken(): Color {
    return copy(alpha = 1 - 0.618f)
}

@Composable
fun Color.disabledWeaken(): Color {
    return copy(alpha = 0.12f)
}

//@Stable
//fun aniDarkColorTheme(): ColorScheme {
//    PaletteTokens.run {
//        return darkColorScheme()
//    }
//}
//
//@Stable
//fun aniLightColorTheme(): ColorScheme = lightColorScheme(
//)
