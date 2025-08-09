/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.window

import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import me.him188.ani.app.platform.PlatformWindow

class LinuxWindowUtils : AwtWindowUtils() {
    override suspend fun setUndecoratedFullscreen(
        window: PlatformWindow,
        windowState: WindowState,
        undecorated: Boolean
    ) {
        if (undecorated) {
            windowState.apply { placement = WindowPlacement.Fullscreen }
        } else {
            windowState.apply { placement = WindowPlacement.Floating }
        }
    }
}
