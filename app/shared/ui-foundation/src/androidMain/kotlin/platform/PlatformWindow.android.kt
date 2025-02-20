package me.him188.ani.app.platform

import android.app.Activity
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * PC 上的 Window. Android 上没有
 */
actual class PlatformWindow(private val context: Context) : AutoCloseable {
    private val activity = context.findActivity()
    private val decorView = activity?.window?.decorView
    
    private var _deviceOrientation: DeviceOrientation by mutableStateOf(context.resources.configuration.deviceOrientation)
    actual val deviceOrientation: DeviceOrientation get() = _deviceOrientation
    
    private var _isUndecoratedFullscreen: Boolean by mutableStateOf(isInFullscreenMode(context))
    actual val isUndecoratedFullscreen: Boolean get() = _isUndecoratedFullscreen

    private val insetListener = View.OnApplyWindowInsetsListener { _, insets ->
        @Suppress("DEPRECATION")
        val isFullscreenNow = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> !insets.isVisible(android.view.WindowInsets.Type.systemBars())
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

    init {
        
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
    
    override fun close() {
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
    get() = when(orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> DeviceOrientation.LANDSCAPE
        else -> DeviceOrientation.PORTRAIT
    }