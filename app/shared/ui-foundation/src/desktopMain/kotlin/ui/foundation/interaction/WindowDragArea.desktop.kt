/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.utils.platform.Platform

@Composable
actual inline fun WindowDragArea(
    modifier: Modifier,
    crossinline content: @Composable () -> Unit
) {
    if (LocalPlatform.current is Platform.Windows) {
        Box(modifier) {
            content()
        }
    } else {
        val windowScope = LocalPlatformWindow.current.windowScope
        windowScope?.run {
            WindowDraggableArea(modifier) {
                content()
            }
        } ?: content()
    }
}
