/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.trace

import kotlin.concurrent.Volatile

interface ErrorReportScope {
    fun setTag(key: String, value: String)
}

interface IErrorReport {
    fun captureMessage(
        message: String,
//        level: SentryLevel = SentryLevel.INFO,
        config: ErrorReportScope.() -> Unit = {}
    )

//    fun breadcrumb(
//        breadcrumb: Breadcrumb,
//        config: Breadcrumb.() -> Unit = {}
//    ) = Sentry.addBreadcrumb(breadcrumb.apply(config))

    fun captureException(
        throwable: Throwable,
//        level: SentryLevel = SentryLevel.WARNING,
        config: ErrorReportScope.() -> Unit = {}
    )
}

val ErrorReport get() = ErrorReportHolder._errorReport

object ErrorReportHolder {
    @Suppress("ObjectPropertyName")
    @Volatile
    internal var _errorReport: IErrorReport = NoopErrorReport

    fun init(errorReport: IErrorReport) {
        _errorReport = errorReport
    }
}

private object NoopErrorReport : IErrorReport {
    override fun captureMessage(message: String, config: ErrorReportScope.() -> Unit) {
        // no-op
    }

    override fun captureException(throwable: Throwable, config: ErrorReportScope.() -> Unit) {
        // no-op
    }
}
