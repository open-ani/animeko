/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.him188.ani.app.platform.window.BasicWindowProc
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.isWindows

actual open class PlatformWindow(
    val windowHandle: Long,
    val windowScope: WindowScope? = null,
    val windowState: WindowState,
    val platform: Platform
) {
    internal var savedWindowsWindowState: SavedWindowsWindowState? = null

    internal var windowsWindowProc by mutableStateOf<BasicWindowProc?>(null)

    val accentColor: StateFlow<Color> by derivedStateOf {
        windowsWindowProc?.accentColor ?: MutableStateFlow(Color.Unspecified)
    }

    private var isWindowsUndecoratedFullscreen by mutableStateOf(false)
    
    actual val isUndecoratedFullscreen: Boolean by derivedStateOf {
        if (platform.isWindows()) {
            isWindowsUndecoratedFullscreen
        } else {
            windowState.placement == WindowPlacement.Fullscreen
        }
    }
    
    actual val deviceOrientation: DeviceOrientation = DeviceOrientation.LANDSCAPE
    
    internal fun onWindowsUndecoratedFullscreenStateChange(newState: Boolean) {
        isWindowsUndecoratedFullscreen = newState
    }
}

class SavedWindowsWindowState(
    val style: Int,
    val exStyle: Int,
    val rect: Rect,
    val maximized: Boolean,
)
