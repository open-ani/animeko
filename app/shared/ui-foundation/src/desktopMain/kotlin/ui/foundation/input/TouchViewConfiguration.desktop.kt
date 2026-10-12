/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * Android's `ViewConfiguration.TOUCH_SLOP`. Compose Desktop hard-codes 18 dp instead, so a finger
 * has to travel more than twice as far before a drag starts and touch feels sticky.
 */
private val TOUCH_SLOP = 8.dp

/**
 * Provides a [ViewConfiguration] with a touch slop matching Android for the content.
 *
 * Mouse drags derive their slop from the touch slop by a fixed ratio, so they are not affected in any
 * noticeable way. Each `Window` has its own scene and view configuration, so this has to wrap every
 * window's content separately.
 */
@Composable
fun ProvideTouchViewConfiguration(content: @Composable () -> Unit) {
    val base = LocalViewConfiguration.current
    val density = LocalDensity.current
    val configuration = remember(base, density) { TouchViewConfiguration(base, density) }
    CompositionLocalProvider(LocalViewConfiguration provides configuration, content = content)
}

private class TouchViewConfiguration(
    private val base: ViewConfiguration,
    density: Density,
) : ViewConfiguration by base {
    override val touchSlop: Float = with(density) { TOUCH_SLOP.toPx() }
}
