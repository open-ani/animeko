package me.him188.ani.app.platform

import android.app.Activity
import android.content.ComponentCallbacks
import android.content.res.Configuration
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


actual class PlatformWindow(
    initialDeviceOrientation: DeviceOrientation,
    initialUndecoratedFullscreen: Boolean
) {
    constructor(context: Context) : this(
        initialDeviceOrientation = context.resources.configuration.deviceOrientation,
        initialUndecoratedFullscreen = isInFullscreenMode(context),
    )

    private var _deviceOrientation: DeviceOrientation by mutableStateOf(initialDeviceOrientation)
    actual val deviceOrientation: DeviceOrientation get() = _deviceOrientation

    private var _isUndecoratedFullscreen: Boolean by mutableStateOf(initialUndecoratedFullscreen)
    actual val isUndecoratedFullscreen: Boolean get() = _isUndecoratedFullscreen

    private val insetListener = View.OnApplyWindowInsetsListener { _, insets ->
        @Suppress("DEPRECATION")
        val isFullscreenNow = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // 只有在导航栏和状态栏都隐藏的时候才认为是无修饰全屏 (undecorated fullscreen)
                // 有些系统 (例如 MIUI) 在开启全面屏手势后, 会隐藏导航栏并且设置 visible = false
                // 我们不管包含在 systemBars 里的 systemOverlays 和 captionBar, 这两个东西的实现在各个系统都不一样
                !insets.isVisible(WindowInsets.Type.statusBars()) &&
                        !insets.isVisible(WindowInsets.Type.navigationBars())
            }

            else -> insets.systemWindowInsetTop == 0
        }
        if (isFullscreenNow != _isUndecoratedFullscreen) {
            _isUndecoratedFullscreen = isFullscreenNow
        }

        insets
    }

    private val configurationListener = object : ComponentCallbacks {
        override fun onLowMemory() {
        }

        override fun onConfigurationChanged(newConfig: Configuration) {
            _deviceOrientation = newConfig.deviceOrientation
        }
    }

    internal fun register(context: Context) {
        val activity = context.findActivity()
        val decorView = activity?.window?.decorView
        //register window inset listener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            decorView?.setOnApplyWindowInsetsListener(insetListener)
        } else if (decorView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(decorView) { v, insets ->
                val toWindowInsets = insets.toWindowInsets()!!
                insetListener.onApplyWindowInsets(v, toWindowInsets)
                WindowInsetsCompat.toWindowInsetsCompat(toWindowInsets)
            }
        }

        //register resource change listener
        context.registerComponentCallbacks(configurationListener)
    }

    internal fun dispose(context: Context) {
        val activity = context.findActivity()
        val decorView = activity?.window?.decorView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            decorView?.setOnApplyWindowInsetsListener(null)
        } else if (decorView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(decorView, null)
        }
        context.unregisterComponentCallbacks(configurationListener)
    }
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

private val Configuration.deviceOrientation
    get() = when (orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> DeviceOrientation.LANDSCAPE
        else -> DeviceOrientation.PORTRAIT
    }

@Composable
fun rememberPlatformWindow(context: Context = LocalContext.current): PlatformWindow {
    val platformWindow = remember(context) { PlatformWindow(context) }
    DisposableEffect(platformWindow) {
        platformWindow.register(context)
        onDispose { platformWindow.dispose(context) }
    }
    return platformWindow
}