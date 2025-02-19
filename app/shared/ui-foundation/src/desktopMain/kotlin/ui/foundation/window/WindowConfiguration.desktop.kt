/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.window.WindowPlacement
import me.him188.ani.app.platform.LocalDesktopContext
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.utils.platform.isWindows

@Composable
actual fun collectWindowConfiguration(): State<WindowConfiguration> {
    val context = LocalDesktopContext.current
    val platform = LocalPlatform.current
    val platformWindow = LocalPlatformWindow.current
    return remember(context, platform, platformWindow) {
        derivedStateOf {
            WindowConfiguration(
                isFullScreen = if (platform.isWindows()) {
                    platformWindow.isUndecoratedFullscreen
                } else {
                    context.windowState.placement == WindowPlacement.Fullscreen
                },
                isLandscape = true
            )
            
        }
    }
}