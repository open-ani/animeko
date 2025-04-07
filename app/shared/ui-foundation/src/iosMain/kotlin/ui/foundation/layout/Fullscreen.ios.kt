/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.layout

import me.him188.ani.app.platform.Context
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.OSVersion
import org.jetbrains.skiko.available
import platform.Foundation.NSThread
import platform.Foundation.setValue
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UINavigationController
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.UIKit.attemptRotationToDeviceOrientation
import platform.UIKit.setStatusBarHidden
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual suspend fun Context.setRequestFullScreen(window: PlatformWindowMP, fullscreen: Boolean) {
    ensureMainThread {
        val orientation = if (fullscreen) {
            UIDeviceOrientation.UIDeviceOrientationLandscapeLeft
        } else {
            UIDeviceOrientation.UIDeviceOrientationPortrait
        }

        if (available(OS.Ios to OSVersion(16))) {
            // Get a UIWindowScene from the PlatformWindowMP if available
            val scene = window.uiViewController.view.window?.windowScene
            if (scene != null) {
                val geometryPreferences = UIWindowSceneGeometryPreferencesIOS().apply {
                    interfaceOrientations = if (fullscreen) {
                        UIInterfaceOrientationMaskLandscape
                    } else {
                        UIInterfaceOrientationMaskPortrait
                    }
                }
                scene.requestGeometryUpdateWithPreferences(geometryPreferences) { error ->
                    // If there's an error or the request fails, fall back
                    if (error != null) {
                        UIDevice.currentDevice.setValue(orientation, forKey = "orientation")
                        UINavigationController.attemptRotationToDeviceOrientation()
                    }
                }
            } else {
                // No scene? Fallback
                UIDevice.currentDevice.setValue(orientation, forKey = "orientation")
                UINavigationController.attemptRotationToDeviceOrientation()
            }
        } else {
            if (fullscreen) {
                // Attempt to force orientation to landscape
                UIDevice.currentDevice.setValue(
                    UIDeviceOrientation.UIDeviceOrientationLandscapeRight,
                    forKey = "orientation",
                )
                // Prevent auto-lock (keep screen on)
                UIApplication.sharedApplication.idleTimerDisabled = true
            } else {
                // Attempt to restore orientation “auto” or portrait
                UIDevice.currentDevice.setValue(
                    UIDeviceOrientation.UIDeviceOrientationPortrait,
                    forKey = "orientation",
                )
                // Allow screen to lock
                UIApplication.sharedApplication.idleTimerDisabled = false
            }
        }

        // Trigger a rotation update
        UINavigationController.attemptRotationToDeviceOrientation()
    }
}

actual fun Context.setSystemBarVisible(window: PlatformWindowMP, visible: Boolean) {
    ensureMainThread {
        UIApplication.sharedApplication.setStatusBarHidden(!visible, animated = true)
        window.uiViewController.setNeedsStatusBarAppearanceUpdate()
    }
}

/**
 * Utility to ensure the given block runs on the main thread.
 */
private inline fun ensureMainThread(crossinline block: () -> Unit) {
    if (NSThread.isMainThread()) {
        block()
    } else {
        dispatch_async(dispatch_get_main_queue()) {
            block()
        }
    }
}
