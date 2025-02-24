/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.window

fun interface WindowsWindowHitTestOwner {
    fun hitTest(x: Float, y: Float): WindowsWindowHitResult
}

/**
 * Hit result of the window in Windows.
 *
 * See more at [WM_NCHITTEST message](https://learn.microsoft.com/en-us/windows/win32/inputdev/wm-nchittest).
 */
enum class WindowsWindowHitResult(val value: Int) {
    // pass the hit test to parent window
    TRANSPARENT(-1),

    // no hit test
    NOWHERE(0),

    // client area
    CLIENT(1),

    // title bar
    CAPTION(2),

    // minimize button
    CAPTION_MIN(8),

    // maximize button
    CAPTION_MAX(9),

    // close button
    CAPTION_CLOSE(20),

    // window edges
    BORDER_LEFT(10),
    BORDER_RIGHT(11),
    BORDER_TOP(12),
    BORDER_TOP_LEFT(13),
    BORDER_TOP_RIGHT(14),
    BORDER_BOTTOM(15),
    BORDER_BOTTOM_LEFT(16),
    BORDER_BOTTOM_RIGHT(17);
}