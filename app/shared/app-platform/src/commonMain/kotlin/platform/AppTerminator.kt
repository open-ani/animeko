/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

/**
 * 用于终止 app 运行
 *
 * 通常在 UI 尝试关闭 App 时使用.
 *
 * * Desktop 只需要 exitProcess.
 * * Android 需要 exitProcess 的同时关闭 torrent 服务.
 */
interface AppTerminator {
    fun exitApp(context: ContextMP, status: Int): Nothing
}

expect val DefaultAppTerminator: AppTerminator
