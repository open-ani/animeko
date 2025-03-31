/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

/**
 * 公共的 Window 抽象
 * @property isUndecoratedFullscreen 当前窗口是否处于全屏模式
 * @property deviceOrientation 当前窗口的方向，PC 上一定处于横屏模式，Android 从 configuration 里读
 */
expect class PlatformWindow {
    /**
     * 当前窗口是否处于最大化模式. 注意, 这不包含全屏模式 [isUndecoratedFullscreen].
     */
    val isExactlyMaximized: Boolean

    val isUndecoratedFullscreen: Boolean
    val deviceOrientation: DeviceOrientation

    /**
     * 将窗口最大化. 注意, 这不是全屏.
     *
     * 仅在桌面端有效.
     */
    fun maximize()
    fun floating()
}

enum class DeviceOrientation {
    PORTRAIT,
    LANDSCAPE,
}