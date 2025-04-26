/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import platform.UIKit.UINavigationController
import platform.UIKit.UIViewController

// This interface is implemented in Swift.
interface ISwiftBridge {
    val UIViewController.navigationController: UINavigationController?
}

lateinit var SwiftBridge: ISwiftBridge // set from Swift

// Expose extensions as top-level in Kotlin. This is lazily-init in Kotlin code.
object SwiftBridgeGlobalHolder : ISwiftBridge by SwiftBridge
