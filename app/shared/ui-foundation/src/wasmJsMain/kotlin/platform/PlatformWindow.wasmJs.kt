package me.him188.ani.app.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.window

actual class PlatformWindow {
    actual val isExactlyMaximized: Boolean get() = false

    private var fullscreen by mutableStateOf(false)
    actual val isUndecoratedFullscreen: Boolean get() = fullscreen

    actual val deviceOrientation: DeviceOrientation
        get() = if (window.innerWidth >= window.innerHeight) DeviceOrientation.LANDSCAPE else DeviceOrientation.PORTRAIT

    actual fun maximize() {}
    actual fun floating() {}

    internal fun setFullscreen(value: Boolean) {
        fullscreen = value
    }
}
