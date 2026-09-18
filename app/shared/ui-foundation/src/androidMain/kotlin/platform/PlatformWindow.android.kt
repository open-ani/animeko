/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import android.app.Activity
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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


actual class PlatformWindow private constructor(
    initialDeviceOrientation: DeviceOrientation,
    initialUndecoratedFullscreen: Boolean,
    private val sensorManager: SensorManager?,
    private val activity: Activity?,
) {
    constructor(context: Context) : this(
        initialDeviceOrientation = context.resources.configuration.deviceOrientation,
        initialUndecoratedFullscreen = isInFullscreenMode(context),
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager,
        activity = context.findActivity(),
    )

    // Sensor.TYPE_HINGE_ANGLE 自 API 30 起存在, 更低版本的设备上没有折叠屏, 视为非折叠屏.
    private val hingeAngleSensor: Sensor? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        } else {
            null
        }

    actual val isExactlyMaximized: Boolean get() = false

    private var _deviceOrientation: DeviceOrientation by mutableStateOf(initialDeviceOrientation)
    actual val deviceOrientation: DeviceOrientation get() = _deviceOrientation

    private var _isUndecoratedFullscreen: Boolean by mutableStateOf(initialUndecoratedFullscreen)
    actual val isUndecoratedFullscreen: Boolean get() = _isUndecoratedFullscreen

    actual val isFoldable: Boolean get() = hingeAngleSensor != null

    private var _isInMultiWindowMode: Boolean by mutableStateOf(activity?.isInMultiWindowMode == true)
    actual val isInMultiWindowMode: Boolean get() = _isInMultiWindowMode

    private var _hingeAngle: Float? by mutableStateOf(null)
    actual val hingeAngle: Float? get() = _hingeAngle

    private val hingeAngleListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            _hingeAngle = event.values[0]
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        }
    }

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
            // 进入/退出小窗与分屏也会触发配置变化, 这里一并刷新
            _isInMultiWindowMode = activity?.isInMultiWindowMode == true
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

        hingeAngleSensor?.let { sensor ->
            sensorManager?.registerListener(hingeAngleListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
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

        if (hingeAngleSensor != null) {
            sensorManager?.unregisterListener(hingeAngleListener)
        }
    }

    actual fun maximize() {
    }

    actual fun floating() {
    }

    actual val isAlwaysOnTop: Boolean get() = false

    actual fun setAlwaysOnTop(alwaysOnTop: Boolean) {
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
