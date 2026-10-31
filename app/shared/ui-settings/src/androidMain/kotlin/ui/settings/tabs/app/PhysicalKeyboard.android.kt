/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.app

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import me.him188.ani.app.platform.LocalContext

@Composable
internal actual fun rememberHasPhysicalKeyboard(): Boolean {
    val context = LocalContext.current
    var present by remember { mutableStateOf(hasPhysicalKeyboard()) }
    DisposableEffect(context) {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
            ?: return@DisposableEffect onDispose {}
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                present = hasPhysicalKeyboard()
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                present = hasPhysicalKeyboard()
            }

            override fun onInputDeviceChanged(deviceId: Int) {
                present = hasPhysicalKeyboard()
            }
        }
        inputManager.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
        onDispose {
            inputManager.unregisterInputDeviceListener(listener)
        }
    }
    return present
}

private fun hasPhysicalKeyboard(): Boolean = InputDevice.getDeviceIds().any { id ->
    val device = InputDevice.getDevice(id) ?: return@any false
    isPhysicalKeyboard(device.sources, device.keyboardType, device.isVirtual)
}

/**
 * 字母键盘才算实体键盘: 遥控器和游戏手柄也带 `SOURCE_KEYBOARD`, 但 `keyboardType` 不是 `KEYBOARD_TYPE_ALPHABETIC`;
 * 软键盘对应的设备是虚拟的.
 */
internal fun isPhysicalKeyboard(sources: Int, keyboardType: Int, isVirtual: Boolean): Boolean {
    if (isVirtual) return false
    if (sources and InputDevice.SOURCE_KEYBOARD != InputDevice.SOURCE_KEYBOARD) return false
    return keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
}
