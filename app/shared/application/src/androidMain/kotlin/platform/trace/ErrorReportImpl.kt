package me.him188.ani.app.platform.trace

import io.sentry.kotlin.multiplatform.Scope
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryLevel
import io.sentry.kotlin.multiplatform.protocol.SentryId
import me.him188.ani.app.trace.ErrorReportScope
import me.him188.ani.app.trace.IErrorReport

actual object SentryErrorReport : IErrorReport {
    override fun captureMessage(message: String, config: ErrorReportScope.() -> Unit) {
        if (Sentry.isEnabled()) {
            Sentry.captureMessage(message) {
                it.level = SentryLevel.WARNING
                config(it.asErrorReportScope())
            }
        } else {
            SentryId.EMPTY_ID
        }
    }

    override fun captureException(throwable: Throwable, config: ErrorReportScope.() -> Unit) {
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
