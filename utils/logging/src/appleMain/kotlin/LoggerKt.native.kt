/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.logging

import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.reflect.KClass

@OptIn(ExperimentalForeignApi::class)
actual fun logger(name: String): Logger =
    IosLoggingConfigurator.factory.createLogger(name)

@PublishedApi
internal fun logger(clazz: KClass<out Any>): Logger {
    return logger(clazz.qualifiedName ?: clazz.simpleName ?: clazz.toString())
}

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

abstract class AbstractLogger(
    protected val tag: String,
) : Logger {
    protected abstract fun writeLog(
        level: LogLevel,
        tag: String,
        message: String?,
        throwable: Throwable?
    )

    protected abstract fun isLoggingEnabled(
        level: LogLevel
    ): Boolean

    final override fun trace(message: String?, throwable: Throwable?) {
        writeLog(LogLevel.TRACE, tag, message, throwable)
    }

    final override fun debug(message: String?, throwable: Throwable?) {
        writeLog(LogLevel.DEBUG, tag, message, throwable)
    }

    final override fun info(message: String?, throwable: Throwable?) {
        writeLog(LogLevel.INFO, tag, message, throwable)
    }

    final override fun warn(message: String?, throwable: Throwable?) {
        writeLog(LogLevel.WARN, tag, message, throwable)
    }

    final override fun error(message: String?, throwable: Throwable?) {
        writeLog(LogLevel.ERROR, tag, message, throwable)
    }

    final override fun isTraceEnabled(): Boolean {
        return isLoggingEnabled(LogLevel.TRACE)
    }

    final override fun isDebugEnabled(): Boolean {
        return isLoggingEnabled(LogLevel.DEBUG)
    }

    final override fun isInfoEnabled(): Boolean {
        return isLoggingEnabled(LogLevel.INFO)
    }

    final override fun isWarnEnabled(): Boolean {
        return isLoggingEnabled(LogLevel.WARN)
    }

    final override fun isErrorEnabled(): Boolean {
        return isLoggingEnabled(LogLevel.ERROR)
    }
}

actual inline fun <reified T : Any> logger(): Logger {
    return logger(T::class)
}

actual fun Any.thisLogger(): Logger {
    return logger(this::class)
}

actual val SilentLogger: Logger get() = SilentLoggerImpl

private object SilentLoggerImpl : Logger {
    override fun isTraceEnabled(): Boolean = false
    override fun trace(message: String?, throwable: Throwable?) {
    }

    override fun isDebugEnabled(): Boolean = false
    override fun debug(message: String?, throwable: Throwable?) {
    }

    override fun isInfoEnabled(): Boolean = false
    override fun info(message: String?, throwable: Throwable?) {
    }

    override fun isWarnEnabled(): Boolean = false
    override fun warn(message: String?, throwable: Throwable?) {
    }

    override fun isErrorEnabled(): Boolean = false
    override fun error(message: String?, throwable: Throwable?) {
    }
}
