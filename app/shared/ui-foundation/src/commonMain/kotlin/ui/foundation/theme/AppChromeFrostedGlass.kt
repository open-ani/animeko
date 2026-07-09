/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.utils.platform.isAndroid

val LocalAppChromeHazeState = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun isAppChromeFrostedGlassActive(): Boolean {
    return LocalThemeSettings.current.enableFrostedGlassEffect &&
            LocalPlatform.current.isAndroid() &&
            LocalAppChromeHazeState.current != null
}

@Composable
fun Modifier.appChromeHazeSource(): Modifier {
    val hazeState = LocalAppChromeHazeState.current ?: return this
    if (!isAppChromeFrostedGlassActive()) return this

    return hazeSource(hazeState)
}

@Composable
fun Modifier.appChromeFrostedGlass(
    enabled: Boolean,
    containerColor: Color,
): Modifier {
    val hazeState = LocalAppChromeHazeState.current ?: return this
    if (!enabled) return this

    return hazeEffect(state = hazeState) {
        blurRadius = 24.dp
        tints = listOf(HazeTint(containerColor.copy(alpha = 0.56f)))
        noiseFactor = 0.08f
    }
}
