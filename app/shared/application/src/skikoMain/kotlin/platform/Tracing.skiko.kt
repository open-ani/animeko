package me.him188.ani.app.platform

import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.protocol.User
import me.him188.ani.utils.platform.currentPlatform

internal actual fun initializeSentry(userId: String) {
    Sentry.init { options ->
        val buildConfig = currentAniBuildConfig
        options.dsn = buildConfig.sentryDsn
        options.debug = buildConfig.isDebug
        options.release = "me.him188.ani@${buildConfig.versionName}"
    }
    Sentry.configureScope {
        val platform = currentPlatform()
        it.user = User(id = userId)
        it.setContext("os", platform.name)
        it.setContext("arch", platform.arch.name)
        it.setContext("version", currentAniBuildConfig.versionName)
    }
}
