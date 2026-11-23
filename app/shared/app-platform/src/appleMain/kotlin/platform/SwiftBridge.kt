/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import platform.AVFoundation.AVPlayer
import platform.UIKit.UINavigationController
import platform.UIKit.UIView
import platform.UIKit.UIViewController

// This interface is implemented in Swift.
interface ISwiftBridge {
    fun createEnhancedPlayerView(player: AVPlayer, onUnavailable: () -> Unit): UIView
    /** mode: off=0, performance=1, quality=2; aspect: fit=0, stretch=1, crop=2. */
    fun updateEnhancedPlayerView(view: UIView, mode: Int, aspect: Int)
    fun releaseEnhancedPlayerView(view: UIView)

    val UIViewController.navigationController: UINavigationController?

    fun setDeviceOrientation(
        uiViewController: AniHostingUIViewController,
        fullscreen: Boolean
    )
}

lateinit var SwiftBridge: ISwiftBridge // set from Swift

// Expose extensions as top-level in Kotlin. This is lazily-init in Kotlin code.
object SwiftBridgeGlobalHolder : ISwiftBridge by SwiftBridge
