/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.window

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import me.him188.ani.app.platform.Context
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.findActivity

@Composable
actual fun collectWindowConfiguration(): State<WindowConfiguration> {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val windowConfigurationState = remember {
        mutableStateOf(
            WindowConfiguration(
                isFullScreen = isInFullscreenMode(context),
                isLandscape = isLandscape(configuration),
            )
        )
    }

    // TODO: isSystemInFullscreen is written by ChatGPT, not tested
    DisposableEffect(configuration, context) {
        val window = context.findActivity()?.window
        val decorView = window?.decorView
        windowConfigurationState.value = WindowConfiguration(
            isFullScreen = isInFullscreenMode(context),
            isLandscape = isLandscape(configuration),
        )
        val listener = View.OnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            val isFullscreenNow = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> !insets.isVisible(android.view.WindowInsets.Type.systemBars())
                else -> insets.systemWindowInsetTop == 0
            }
            if (isFullscreenNow != windowConfigurationState.value.isFullScreen) {
                windowConfigurationState.value.copy(isFullScreen = isFullscreenNow)
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
    return windowConfigurationState
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

private fun isLandscape(configuration: Configuration) = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE