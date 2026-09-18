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
     * 设备是否为折叠屏 (具有铰链角度传感器). 非折叠屏设备恒为 `false`.
     */
    val isFoldable: Boolean

    /**
     * 当前是否处于多窗口模式 (小窗/分屏). 非 Android 平台恒为 `false`.
     */
    val isInMultiWindowMode: Boolean

    /**
     * 当前铰链夹角, 单位为度, 180 表示完全展开. 设备无铰链角度传感器时恒为 `null`.
     */
    val hingeAngle: Float?

    /**
     * 将窗口最大化. 注意, 这不是全屏.
     *
     * 仅在桌面端有效.
     */
    fun maximize()
    fun floating()

    /**
     * 窗口是否置顶 (always on top). 这是运行时状态, 不会持久化, 关闭应用后自动清除.
     *
     * 仅在桌面端有效, 其他平台始终为 `false`.
     */
    val isAlwaysOnTop: Boolean

    /**
     * 设置窗口置顶. 仅在桌面端有效.
     */
    fun setAlwaysOnTop(alwaysOnTop: Boolean)
}

enum class DeviceOrientation {
    PORTRAIT,
    LANDSCAPE,
}