/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.logging

import org.slf4j.ILoggerFactory
import org.slf4j.LoggerFactory

actual typealias Logger = org.slf4j.Logger

fun ILoggerFactory.getLogger(clazz: Class<out Any>): Logger =
    getLogger(
        clazz.canonicalName?.let {
            when {
                it.startsWith("me.him188.ani.") -> {
                    it.removePrefix("me.him188.ani.")
                }

                else -> {
                    it
                }
            }
        },
    )

actual fun logger(name: String): Logger {
    return LoggerFactory.getILoggerFactory().getLogger(name)
}

@JvmName("logger1")
actual inline fun <reified T : Any> logger(): Logger {
    return LoggerFactory.getILoggerFactory().getLogger(T::class.java)
}

actual fun Any.thisLogger(): Logger {
    return LoggerFactory.getILoggerFactory().getLogger(this::class.java)
}
