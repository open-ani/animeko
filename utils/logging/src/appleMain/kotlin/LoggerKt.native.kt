/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.logging

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv
import kotlin.reflect.KClass

@OptIn(ExperimentalForeignApi::class)
private val isDebug = runCatching {
    val value = getenv("kotlinx.coroutines.debug")?.toKString() ?: "off"
    value.equals("on", ignoreCase = true)
    true // TODO: ios logging debug. KLogger 在 test 中不会打印任何日志, 所以现在先临时使用 stdout
}

@OptIn(ExperimentalForeignApi::class)
actual fun logger(name: String): Logger {
    if (isDebug.getOrThrow()) {
        return StdoutLogger(name)
    }
    return IosLoggerByKLogger(KotlinLogging.logger(name))
}

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

private class IosLoggerByKLogger(
    private val delegate: KLogger,
) : Logger {
    override fun isTraceEnabled(): Boolean = delegate.isTraceEnabled()

    override fun trace(message: String?, throwable: Throwable?) {
        return delegate.trace(throwable) { message }
    }

    override fun isDebugEnabled(): Boolean {
        return delegate.isDebugEnabled()
    }

    override fun debug(message: String?, throwable: Throwable?) {
        return delegate.debug(throwable) { message }
    }

    override fun isInfoEnabled(): Boolean {
        return delegate.isInfoEnabled()
    }

    override fun info(message: String?, throwable: Throwable?) {
        return delegate.info(throwable) { message }
    }

    override fun isWarnEnabled(): Boolean {
        return delegate.isWarnEnabled()
    }

    override fun warn(message: String?, throwable: Throwable?) {
        return delegate.warn(throwable) { message }
    }

    override fun isErrorEnabled(): Boolean {
        return delegate.isErrorEnabled()
    }

    override fun error(message: String?, throwable: Throwable?) {
        return delegate.error(throwable) { message }
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

private class StdoutLogger(
    private val name: String,
) : Logger {
    override fun isTraceEnabled(): Boolean = true
    override fun trace(message: String?, throwable: Throwable?) {
        println("[$name] TRACE: $message")
        throwable?.printStackTrace()
    }

    override fun isDebugEnabled(): Boolean = true
    override fun debug(message: String?, throwable: Throwable?) {
        println("[$name] DEBUG: $message")
        throwable?.printStackTrace()
    }

    override fun isInfoEnabled(): Boolean = true
    override fun info(message: String?, throwable: Throwable?) {
        println("[$name] INFO: $message")
        throwable?.printStackTrace()
    }

    override fun isWarnEnabled(): Boolean = true
    override fun warn(message: String?, throwable: Throwable?) {
        println("[$name] WARN: $message")
        throwable?.printStackTrace()
    }

    override fun isErrorEnabled(): Boolean = true
    override fun error(message: String?, throwable: Throwable?) {
        println("[$name] ERROR: $message")
        throwable?.printStackTrace()
    }
}
