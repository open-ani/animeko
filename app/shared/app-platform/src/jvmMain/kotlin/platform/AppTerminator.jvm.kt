/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import kotlin.system.exitProcess

actual val DefaultAppTerminator: AppTerminator get() = NativeAppTerminator

/**
 * Default app terminator which invokes [exitProcess] to exit process.
 */
private object NativeAppTerminator : AppTerminator {
    override fun exitApp(context: ContextMP, status: Int): Nothing {
        exitProcess(status)
    }
}
