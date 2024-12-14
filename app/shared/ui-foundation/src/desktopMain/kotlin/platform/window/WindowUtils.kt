/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.window

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.WindowState
import me.him188.ani.app.platform.PlatformWindow
import me.him188.ani.utils.platform.Platform
import java.awt.Cursor
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage


/**
 * @see AwtWindowUtils
 */
interface WindowUtils {
    fun setTitleBarColor(hwnd: Long, color: Color): Boolean {
        return false
    }

    suspend fun setUndecoratedFullscreen(window: PlatformWindow, windowState: WindowState, undecorated: Boolean) {
    }

    fun setPreventScreenSaver(prevent: Boolean) {
    }

    fun isCursorVisible(window: ComposeWindow): Boolean

    fun setCursorVisible(window: ComposeWindow, visible: Boolean) {
    }

    companion object : WindowUtils by (when (me.him188.ani.utils.platform.currentPlatformDesktop()) {
        is Platform.MacOS -> MacosWindowUtils()
        is Platform.Windows -> WindowsWindowUtils()
        is Platform.Linux -> LinuxWindowUtils()
    })
}

abstract class AwtWindowUtils : WindowUtils {
    companion object {
        val blankCursor: Cursor? by lazy {
            if (GraphicsEnvironment.isHeadless()) return@lazy null
            Toolkit.getDefaultToolkit().createCustomCursor(
                BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "blank cursor",
            )
        }
    }

    override fun isCursorVisible(window: ComposeWindow): Boolean = window.cursor != blankCursor

    override fun setCursorVisible(window: ComposeWindow, visible: Boolean) {
        if (GraphicsEnvironment.isHeadless()) return
        val cursor = if (visible) Cursor.getDefaultCursor() else blankCursor
        if (cursor != null) {
            window.cursor = cursor
            window.contentPane.cursor = cursor
        }
    }
}

fun ComposeWindow.setTitleBarColor(color: Color) {
    WindowUtils.setTitleBarColor(windowHandle, color)
}

