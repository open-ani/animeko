package me.him188.ani.app.platform

/**
 * 公共的 Window 抽象
 * @property isUndecoratedFullscreen 当前窗口是否处于全屏模式
 * @property isLandscape 当前窗口是否处于横屏模式，PC 上一定处于横屏模式，Android 从 configuration 里读
 */
expect class PlatformWindow {
    val isUndecoratedFullscreen: Boolean
    val isLandscape: Boolean
}
