/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.layout

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import me.him188.ani.app.platform.Context
import me.him188.ani.app.platform.LocalContext


@Suppress("USELESS_CAST") // compiler bug
actual suspend fun Context.setRequestFullScreen(window: PlatformWindowMP, fullscreen: Boolean) {
    android.util.Log.i("setRequestFullScreen", "Requesting fullscreen: $fullscreen, context=$this")
    if (this is Activity) {
        if (fullscreen) {
            // go landscape
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

            // keep screen on
            this.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            // cancel landscape
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

            // don't keep screen on
            this.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    } else {
        val orientation = if (fullscreen) {
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        } else {
            android.content.res.Configuration.ORIENTATION_PORTRAIT
        }
        resources.configuration.orientation = orientation
    }
}

// TODO: isSystemInFullscreen is written by ChatGPT, not tested
@Composable
actual fun isSystemInFullscreenImpl(): Boolean {
    val context = LocalContext.current
    var isFullscreen by remember { mutableStateOf(isInFullscreenMode(context)) }

    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        val decorView = window?.decorView

        val listener = View.OnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val isFullscreenNow = !insets.isVisible(WindowInsets.Type.systemBars())
                if (isFullscreenNow != isFullscreen) {
                    isFullscreen = isFullscreenNow
                }
            } else {
                @Suppress("DEPRECATION")
                val isFullscreenNow = (insets.systemWindowInsetTop == 0)
                if (isFullscreenNow != isFullscreen) {
                    isFullscreen = isFullscreenNow
                }
            }
            insets
        }

        if (decorView != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                decorView.setOnApplyWindowInsetsListener(listener)
            } else {
                ViewCompat.setOnApplyWindowInsetsListener(decorView) { v, insets ->
                    val toWindowInsets = insets.toWindowInsets()!!
                    listener.onApplyWindowInsets(v, toWindowInsets)
                    WindowInsetsCompat.toWindowInsetsCompat(toWindowInsets)
                }
            }
        }

        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                decorView?.setOnApplyWindowInsetsListener(null)
            } else {
                if (decorView != null) {
                    ViewCompat.setOnApplyWindowInsetsListener(decorView, null)
                }
            }
        }
    }

    return isFullscreen
}

@Suppress("DEPRECATION")
private fun isInFullscreenMode(context: Context): Boolean {
    val window = (context as? Activity)?.window ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val insetsController = window.insetsController
        insetsController != null && insetsController.systemBarsBehavior == BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    } else {
        val decorView = window.decorView
        (decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN) != 0
    }
}

actual fun Context.setSystemBarVisible(visible: Boolean) {
    if (this !is Activity) return
    if (visible) {
        // show bars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            this.window.insetsController?.show(
                WindowInsets.Type.statusBars().or(WindowInsets.Type.navigationBars()),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                this.window.insetsController?.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_DEFAULT
            }
        } else {
            val decorView = this.window.decorView
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE
            @Suppress("DEPRECATION")
            this.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    } else {
        // hide bars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            this.window.insetsController?.hide(
                WindowInsets.Type.statusBars().or(WindowInsets.Type.navigationBars()),
            )
            this.window.insetsController?.systemBarsBehavior =
                BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            val decorView = this.window.decorView
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_IMMERSIVE // Set the content to appear under the system bars so that the
                        // content doesn't resize when the system bars hide and show.
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN // Hide the nav bar and status bar
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN)
            @Suppress("DEPRECATION")
            this.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }
}