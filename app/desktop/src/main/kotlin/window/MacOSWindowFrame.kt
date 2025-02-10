/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop.window

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.layout.LocalCaptionButtonInsets
import me.him188.ani.app.ui.foundation.layout.LocalTitleBarInsets
import me.him188.ani.app.ui.foundation.layout.ZeroInsets
import me.him188.ani.app.ui.foundation.layout.isSystemInFullscreen
import me.him188.ani.utils.platform.currentPlatformDesktop
import me.him188.ani.utils.platform.isMacOS

@Composable
fun FrameWindowScope.MacOSWindowFrame(windowState: WindowState, content: @Composable () -> Unit) {
    // This actually runs only once since app is never changed.
    val windowImmersed = true

    SideEffect {
        // https://www.formdev.com/flatlaf/macos/
        if (currentPlatformDesktop().isMacOS()) {
            window.rootPane.putClientProperty("apple.awt.application.appearance", "system")
            window.rootPane.putClientProperty("apple.awt.fullscreenable", true)
            if (windowImmersed) {
                window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            } else {
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", false)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", false)
            }
        }
    }
    if (LocalPlatform.current.isMacOS()) {
        // CMP bug, 退出全屏后窗口会变为 Maximized, 而不是还原到 Floating
        if (!isSystemInFullscreen() && windowState.placement == WindowPlacement.Maximized) {
            SideEffect {
                windowState.placement = WindowPlacement.Floating
            }
        }
    }

    CompositionLocalProvider(
        LocalTitleBarInsets provides if (!isSystemInFullscreen()) {
            WindowInsets(top = 28.dp) // 实际上是 22, 但是为了美观, 加大到 28
        } else {
            ZeroInsets
        },
        LocalCaptionButtonInsets provides if (!isSystemInFullscreen()) {
            WindowInsets(left = 80.dp, top = 28.dp)
        } else {
            ZeroInsets
        },
        content = content,
    )
}