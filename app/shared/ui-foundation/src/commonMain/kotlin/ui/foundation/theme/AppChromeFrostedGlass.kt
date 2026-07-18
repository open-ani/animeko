/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import me.him188.ani.app.ui.foundation.ifThen

/**
 * The [HazeState] shared by the app chrome (top app bar and navigation bar) and the page content
 * behind it.
 *
 * Provided only where frosted-glass chrome is supported (currently the main screen). Screens
 * outside this scope see `null` and render opaque chrome.
 */
val LocalAppChromeHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Extra insets occupied by app chrome overlaying the page content, e.g. the frosted navigation bar
 * placed on top of the content. Page content should add these to its content padding so that
 * scrollable content is not permanently obscured by the chrome.
 *
 * Zero when the chrome does not overlay content.
 */
val LocalAppChromeOverlayInsets = compositionLocalOf<WindowInsets> { WindowInsets(0, 0, 0, 0) }

/**
 * Whether the frosted-glass app chrome is enabled and can be rendered in this composition.
 */
@Composable
fun isAppChromeFrostedGlassActive(): Boolean {
    return LocalThemeSettings.current.enableFrostedGlassEffect &&
            LocalAppChromeHazeState.current != null
}

/**
 * Marks this node as the content behind the frosted-glass app chrome.
 *
 * Apply this to the page content of a screen, as a **sibling** of (not a parent of) any node using
 * [appChromeFrostedGlass] — Haze cannot blur a source from an effect nested inside it.
 *
 * @param backgroundColor drawn behind the content when the effect is active, so that the recorded
 * blur layer is opaque. Pass the containing Scaffold's `containerColor`.
 */
@Composable
fun Modifier.appChromeHazeSource(backgroundColor: Color = Color.Unspecified): Modifier {
    val hazeState = LocalAppChromeHazeState.current ?: return this
    if (!isAppChromeFrostedGlassActive()) return this

    return hazeSource(hazeState)
        .ifThen(backgroundColor.isSpecified) { background(backgroundColor) }
}

/**
 * Renders this chrome element as a frosted-glass panel, blurring the content marked with
 * [appChromeHazeSource] that lies behind it.
 */
@Composable
fun Modifier.appChromeFrostedGlass(
    enabled: Boolean,
    containerColor: Color,
): Modifier {
    val hazeState = LocalAppChromeHazeState.current ?: return this
    if (!enabled) return this

    return hazeEffect(state = hazeState) {
        blurRadius = 24.dp
        tints = listOf(HazeTint(containerColor.copy(alpha = 0.8f)))
        noiseFactor = 0.08f
    }
}
