/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import io.sentry.kotlin.multiplatform.Scope
import io.sentry.kotlin.multiplatform.SentryOptions
import me.him188.ani.app.trace.ErrorReport
import me.him188.ani.utils.platform.currentPlatform


/**
 * Shares initialization logic across platforms. Only used by [initializeErrorReport].
 */
internal object CommonTracingInitializer {
    fun configureSentryOptions(options: SentryOptions) {
        val buildConfig = currentAniBuildConfig
        options.dsn = buildConfig.sentryDsn
        options.debug = buildConfig.isDebug
        options.release = "me.him188.ani@${buildConfig.versionName}"
    }

    fun configureGlobalScope(scope: Scope) {
        val platform = currentPlatform()
        scope.setContext("os", platform.name)
        scope.setContext("arch", platform.arch.name)
        scope.setContext("version", currentAniBuildConfig.versionName)
    }
}

/**
 * Initializes [ErrorReport] for the platform.
 */
internal expect fun initializeErrorReport()
