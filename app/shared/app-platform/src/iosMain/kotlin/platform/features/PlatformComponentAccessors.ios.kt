/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.features

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import me.him188.ani.app.platform.Context
import me.him188.ani.app.tools.MonoTasker
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSNotificationCenter
import platform.MediaPlayer.MPVolumeView
import platform.UIKit.UIApplication
import platform.UIKit.UIScreen
import platform.UIKit.UIScreenBrightnessDidChangeNotification
import platform.UIKit.UISlider
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue


actual fun getComponentAccessorsImpl(context: Context): PlatformComponentAccessors =
    IosPlatformComponentAccessors

private object IosPlatformComponentAccessors : PlatformComponentAccessors {
    override val audioManager: AudioManager? by lazy { IosAudioManager() }
    override val brightnessManager: BrightnessManager? by lazy { IosBrightnessManager() }
}

private class IosBrightnessManager : BrightnessManager {
    /**
     * 屏幕亮度
     *
     *  UIScreen.mainScreen.brightness 设置后不能马上生效所以get的时候需要一个临时值
     */
    var brightness = UIScreen.mainScreen.brightness.toFloat()

    init {
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIScreenBrightnessDidChangeNotification,
            `object` = null,
            queue = null,
        ) { _ ->
            brightness = UIScreen.mainScreen.brightness.toFloat()
        }
    }

    override fun getBrightness(): Float = brightness

    override fun setBrightness(level: Float) =
        level.coerceIn(0f, 1f).let { savedLevel ->
            brightness = savedLevel
            UIScreen.mainScreen.brightness = savedLevel.toDouble()
        }
}

@OptIn(ExperimentalForeignApi::class)
internal class IosAudioManager : AudioManager {

    @OptIn(ExperimentalForeignApi::class)
    val volumeView = MPVolumeView(CGRectMake(-2000.0, -2000.0, 0.0, 0.0))

    val tasker = MonoTasker(CoroutineScope(Dispatchers.Main))

    val slider by lazy {
        // 在 MPVolumeView 中找到 UISlider 组件
        volumeView.subviews.first { it is UISlider } as UISlider
    }

    init {
        AVAudioSession.sharedInstance().run {
            setCategory(AVAudioSessionCategoryPlayback, null)
            setActive(true, null)
        }
    }

    override fun getVolume(streamType: StreamType): Float {
        return slider.value
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun setVolume(streamType: StreamType, levelPercentage: Float) {
        if (volumeView.superview == null) {
            UIApplication.sharedApplication.keyWindow?.addSubview(volumeView)
        }
        dispatch_async(queue = dispatch_get_main_queue()) {
            // 设置音量
            slider.setValue(levelPercentage.coerceIn(0f, 1f), animated = false)
        }
        // 防抖移除 MPVolumeView
        tasker.launch {
            delay(3000)
            // 移除 MPVolumeView
            volumeView.removeFromSuperview()
        }
    }

}
