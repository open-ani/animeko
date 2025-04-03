/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.features

import me.him188.ani.app.platform.Context
import platform.UIKit.UIScreen

actual fun getComponentAccessorsImpl(context: Context): PlatformComponentAccessors =
    IosPlatformComponentAccessors

private object IosPlatformComponentAccessors : PlatformComponentAccessors {
    override val audioManager: AudioManager? get() = null // Ios does not allow adjusting system volume.
    override val brightnessManager: BrightnessManager? by lazy { IosBrightnessManager() }
}

private class IosBrightnessManager : BrightnessManager {
    override fun getBrightness(): Float {
        return UIScreen.mainScreen.brightness.toFloat()
    }

    override fun setBrightness(level: Float) {
        UIScreen.mainScreen.brightness = level.coerceIn(0f, 1f).toDouble()
    }
}
