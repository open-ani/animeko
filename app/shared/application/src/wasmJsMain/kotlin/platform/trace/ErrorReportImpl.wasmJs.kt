package me.him188.ani.app.platform.trace

import me.him188.ani.app.trace.ErrorReportScope
import me.him188.ani.app.trace.IErrorReport

actual object SentryErrorReport : IErrorReport {
    override fun captureMessage(message: String, config: ErrorReportScope.() -> Unit) {}
    override fun captureException(throwable: Throwable, config: ErrorReportScope.() -> Unit) {}
}
