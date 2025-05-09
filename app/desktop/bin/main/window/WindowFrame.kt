/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatformDesktop

@Composable
fun FrameWindowScope.WindowFrame(
    windowState: WindowState,
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    when (currentPlatformDesktop()) {
        is Platform.MacOS -> {
            MacOSWindowFrame(windowState, content)
        }

        is Platform.Windows -> {
            WindowsWindowFrame(
                windowState = windowState,
                onCloseRequest = onCloseRequest,
                content = content,
            )
        }

        else -> {
            content()
        }
    }
}