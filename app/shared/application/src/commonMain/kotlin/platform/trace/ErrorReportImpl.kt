/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.trace

import io.sentry.kotlin.multiplatform.Scope
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryLevel
import io.sentry.kotlin.multiplatform.protocol.SentryId
import me.him188.ani.app.trace.ErrorReportScope
import me.him188.ani.app.trace.IErrorReport


object SentryErrorReport : IErrorReport {
    override fun captureMessage(
        message: String,
        config: ErrorReportScope.() -> Unit
    ) {
        if (Sentry.isEnabled()) {
            Sentry.captureMessage(message) {
                it.level = SentryLevel.WARNING
                config(it.asErrorReportScope())
            }
        } else {
            SentryId.EMPTY_ID
        }
    }

    override fun captureException(
        throwable: Throwable,
        config: ErrorReportScope.() -> Unit
    ) {
        if (Sentry.isEnabled()) {
            Sentry.captureException(throwable) {
                it.level = SentryLevel.WARNING
                config(it.asErrorReportScope())
            }
        } else {
            SentryId.EMPTY_ID
        }
    }
}

private fun Scope.asErrorReportScope(): ErrorReportScope {
    val scope = this
    return object : ErrorReportScope {
        override fun setTag(key: String, value: String) {
            scope.setTag(key, value)
        }
    }
}
