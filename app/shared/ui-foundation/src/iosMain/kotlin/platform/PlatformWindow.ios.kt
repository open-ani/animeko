package me.him188.ani.app.platform

/**
 * PC 上的 Window. Android 上没有
 */
actual class PlatformWindow {
    actual val isUndecoratedFullscreen: Boolean = TODO("isUndecoratedFullscreen")
    actual val isLandscape: Boolean = TODO("isLandscape")
}