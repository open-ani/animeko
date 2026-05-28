package me.him188.ani.app.ui.foundation.layout

import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.toSize
import androidx.window.core.layout.WindowSizeClass
import androidx.compose.material3.adaptive.Posture
import me.him188.ani.app.platform.Context
import me.him188.ani.app.platform.PlatformWindow

actual suspend fun Context.setRequestFullScreen(window: PlatformWindowMP, fullscreen: Boolean) {
    (window as? PlatformWindow)?.setFullscreen(fullscreen)
}

actual fun Context.setSystemBarVisible(window: PlatformWindowMP, visible: Boolean) {}

@Composable
@Suppress("DEPRECATION")
actual fun currentWindowAdaptiveInfo1(): WindowAdaptiveInfo {
    val density = LocalDensity.current
    val size = with(density) { LocalWindowInfo.current.containerSize.toSize().toDpSize() }
    return WindowAdaptiveInfo(WindowSizeClass.compute(size.width.value, size.height.value), Posture())
}
