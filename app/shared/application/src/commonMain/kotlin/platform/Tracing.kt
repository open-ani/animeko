package me.him188.ani.app.platform

import me.him188.ani.app.trace.ErrorReport

/**
 * Initializes [ErrorReport] for the platform.
 */
internal expect fun initializeSentry(userId: String)
