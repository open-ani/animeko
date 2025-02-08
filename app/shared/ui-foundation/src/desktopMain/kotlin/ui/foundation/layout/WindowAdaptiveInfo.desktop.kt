/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.layout

import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.toSize
import androidx.window.core.layout.WindowSizeClass

/**
 * Calculates and returns [WindowAdaptiveInfo] of the provided context. It's a convenient function
 * that uses the default [WindowSizeClass] constructor and the default [Posture] calculation
 * functions to retrieve [WindowSizeClass] and [Posture].
 *
 * @return [WindowAdaptiveInfo] of the provided context
 */
@Composable
@Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
actual fun currentWindowAdaptiveInfo1(): WindowAdaptiveInfo {
    val backupState = remember { mutableStateOf<WindowAdaptiveInfo?>(null) }

    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    return try {
        val size = with(density) { windowInfo.containerSize.toSize().toDpSize() }
        return WindowAdaptiveInfo(
            WindowSizeClass.compute(size.width.value, size.height.value), // This line might throw
            Posture(),
        )
    } catch (e: IllegalStateException) {
        // Exception in thread "AWT-EventQueue-0" java.lang.IllegalStateException: layout state is not idle before measure starts
        backupState.value ?: throw e // 没有备份, 那只能抛出异常了
    }
}
