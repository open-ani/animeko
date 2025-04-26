/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.uikit.LocalUIViewController
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation
import platform.UIKit.UIDeviceOrientationDidChangeNotification
import platform.darwin.NSObjectProtocol

actual class PlatformWindow(
    val uiViewController: AniHostingUIViewController,
) {
    private var _deviceOrientation by mutableStateOf(getCurrentOrientation())
    actual val deviceOrientation: DeviceOrientation get() = _deviceOrientation

    private var _isUndecoratedFullscreen by mutableStateOf(false)
    actual val isUndecoratedFullscreen: Boolean get() = _isUndecoratedFullscreen

    private var orientationObserver: NSObjectProtocol? = null

    actual val isExactlyMaximized: Boolean get() = false

    internal fun register() {
        // Start listening to device orientation changes
        UIDevice.currentDevice.beginGeneratingDeviceOrientationNotifications()
        orientationObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIDeviceOrientationDidChangeNotification,
            `object` = null,
            queue = null,
        ) { _ ->
            _deviceOrientation = getCurrentOrientation()
        }
    }

    internal fun dispose() {
        // Stop listening
        orientationObserver?.let {
            NSNotificationCenter.defaultCenter.removeObserver(it)
        }
        UIDevice.currentDevice.endGeneratingDeviceOrientationNotifications()
    }

    private fun getCurrentOrientation(): DeviceOrientation {
        return when (UIDevice.currentDevice.orientation) {
            UIDeviceOrientation.UIDeviceOrientationLandscapeLeft,
            UIDeviceOrientation.UIDeviceOrientationLandscapeRight -> DeviceOrientation.LANDSCAPE

            else -> DeviceOrientation.PORTRAIT
        }
    }

    actual fun maximize() {
    }

    actual fun floating() {
    }
}

@Composable
fun rememberPlatformWindow(): PlatformWindow {
    val uiViewController = LocalUIViewController.current
    val platformWindow = remember(uiViewController) {
        PlatformWindow(uiViewController.parentViewController!! as AniHostingUIViewController)
    }
    DisposableEffect(platformWindow) {
        platformWindow.register()
        onDispose { platformWindow.dispose() }
    }
    return platformWindow
}

