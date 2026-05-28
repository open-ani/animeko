/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.logging

actual interface Logger {
    actual fun isTraceEnabled(): Boolean
    actual fun trace(message: String?, throwable: Throwable?)
    actual fun isDebugEnabled(): Boolean
    actual fun debug(message: String?, throwable: Throwable?)
    actual fun isInfoEnabled(): Boolean
    actual fun info(message: String?, throwable: Throwable?)
    actual fun isWarnEnabled(): Boolean
    actual fun warn(message: String?, throwable: Throwable?)
    actual fun isErrorEnabled(): Boolean
    actual fun error(message: String?, throwable: Throwable?)
}

private class ConsoleLogger(private val name: String) : Logger {
    override fun isTraceEnabled(): Boolean = true
    override fun isDebugEnabled(): Boolean = true
    override fun isInfoEnabled(): Boolean = true
    override fun isWarnEnabled(): Boolean = true
    override fun isErrorEnabled(): Boolean = true

    override fun trace(message: String?, throwable: Throwable?) = log("TRACE", message, throwable)
    override fun debug(message: String?, throwable: Throwable?) = log("DEBUG", message, throwable)
    override fun info(message: String?, throwable: Throwable?) = log("INFO", message, throwable)
    override fun warn(message: String?, throwable: Throwable?) = log("WARN", message, throwable)
    override fun error(message: String?, throwable: Throwable?) = log("ERROR", message, throwable)

    private fun log(level: String, message: String?, throwable: Throwable?) {
        val text = buildString {
            append('[').append(level).append("] ").append(name)
            if (message != null) append(": ").append(message)
            if (throwable != null) append("\n").append(throwable.stackTraceToString())
        }
        println(text)
    }
}

actual fun logger(name: String): Logger = ConsoleLogger(name)

actual fun Any.thisLogger(): Logger = logger("anonymous")

actual inline fun <reified T : Any> logger(): Logger = logger(T::class.simpleName ?: "anonymous")

actual val SilentLogger: Logger = object : Logger {
    override fun isTraceEnabled(): Boolean = false
    override fun trace(message: String?, throwable: Throwable?) {}
    override fun isDebugEnabled(): Boolean = false
    override fun debug(message: String?, throwable: Throwable?) {}
    override fun isInfoEnabled(): Boolean = false
    override fun info(message: String?, throwable: Throwable?) {}
    override fun isWarnEnabled(): Boolean = false
    override fun warn(message: String?, throwable: Throwable?) {}
    override fun isErrorEnabled(): Boolean = false
    override fun error(message: String?, throwable: Throwable?) {}
}
