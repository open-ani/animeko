/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:JvmName("LoggerKt")
@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER", "KotlinRedundantDiagnosticSuppress")

package me.him188.ani.utils.logging

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmName

expect interface Logger {
    fun isTraceEnabled(): Boolean
    fun trace(message: String?, throwable: Throwable?)

    fun isDebugEnabled(): Boolean
    fun debug(message: String?, throwable: Throwable?)

    fun isInfoEnabled(): Boolean
    fun info(message: String?, throwable: Throwable?)

    fun isWarnEnabled(): Boolean
    fun warn(message: String?, throwable: Throwable?)

    fun isErrorEnabled(): Boolean
    fun error(message: String?, throwable: Throwable?)
}

enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

fun Logger.trace(message: String?) = trace(message, null)
fun Logger.trace(throwable: Throwable?) = trace(null, throwable)

fun Logger.debug(message: String?) = debug(message, null)
fun Logger.debug(throwable: Throwable?) = debug(null, throwable)

fun Logger.info(message: String?) = info(message, null)
fun Logger.info(throwable: Throwable?) = info(null, throwable)

fun Logger.warn(message: String?) = warn(message, null)
fun Logger.warn(throwable: Throwable?) = warn(null, throwable)

fun Logger.error(message: String?) = error(message, null)
fun Logger.error(throwable: Throwable?) = error(null, throwable)

expect fun logger(name: String): Logger

/**
 * Equivalent to `logger(this::class)`, but faster.
 */
expect fun Any.thisLogger(): Logger

expect inline fun <@Suppress("unused") reified T : Any> logger(): Logger

/**
 * 忽略所有日志
 */
expect val SilentLogger: Logger

inline fun Logger.trace(message: () -> String) {
    if (isTraceEnabled()) {
        trace(message())
    }
}

inline fun Logger.debug(message: () -> String) {
    if (isDebugEnabled()) {
        debug(message())
    }
}

inline fun Logger.info(message: () -> String) {
    if (isInfoEnabled()) {
        info(message())
    }
}

@OverloadResolutionByLambdaReturnType
inline fun Logger.warn(message: () -> String) {
    if (isWarnEnabled()) {
        warn(message())
    }
}

@JvmName("warnThrowable")
@OverloadResolutionByLambdaReturnType
inline fun Logger.warn(e: () -> Throwable) {
    contract { callsInPlace(e, InvocationKind.AT_MOST_ONCE) }
    if (isErrorEnabled()) {
        val exception = e()
        warn(exception.message, exception)
    }
}

@OverloadResolutionByLambdaReturnType
inline fun Logger.error(message: () -> String) {
    error(message())
}

@JvmName("errorThrowable")
@OverloadResolutionByLambdaReturnType
inline fun Logger.error(e: () -> Throwable) {
    contract { callsInPlace(e, InvocationKind.AT_MOST_ONCE) }
    if (isErrorEnabled()) {
        val exception = e()
        error(exception.message, exception)
    }
}

inline fun Logger.trace(exception: Throwable? = null, message: () -> String) {
    if (isTraceEnabled()) {
        trace(message(), exception)
    }
}

inline fun Logger.debug(exception: Throwable? = null, message: () -> String) {
    if (isDebugEnabled()) {
        debug(message(), exception)
    }
}

inline fun Logger.info(exception: Throwable? = null, message: () -> String) {
    if (isInfoEnabled()) {
        info(message(), exception)
    }
}

inline fun Logger.warn(exception: Throwable? = null, message: () -> String) {
    if (isWarnEnabled()) {
        warn(message(), exception)
    }
}

inline fun Logger.error(exception: Throwable? = null, message: () -> String) {
    if (isErrorEnabled()) {
        error(message(), exception)
    }
}
