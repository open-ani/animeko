/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import me.him188.ani.app.platform.trace.SentryErrorReport
import me.him188.ani.app.trace.ErrorReportHolder
import me.him188.ani.utils.analytics.AnalyticsConfig
import me.him188.ani.utils.analytics.AnalyticsHolder
import me.him188.ani.utils.analytics.IAnalytics
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.currentPlatform

object AppStartupTasks {
    fun initializeSentry(userId: String) {
        if (!currentAniBuildConfig.isDebug && currentAniBuildConfig.sentryEnabled) {
            ErrorReportHolder.init(SentryErrorReport)
            me.him188.ani.app.platform.initializeSentry(userId = userId)
        } else {
//            if (currentPlatform().isIos()) {
//                // 初始化一下然后关闭, 否则 ios 上 sentry 会捕获 crash, 导致 debug 看不到堆栈.
//                Sentry.init {
//                    it.beforeBreadcrumb = { null }
//                }
//                Sentry.close()
//            }
        }
    }

    inline fun initializeAnalytics(instance: () -> IAnalytics) {
        if (currentAniBuildConfig.analyticsEnabled) {
            AnalyticsHolder.init(instance())
        }
    }

    fun printVersions() {
        logger.info { "Ani started. platform: ${currentPlatform()}, version: ${currentAniBuildConfig.versionName}, isDebug: ${currentAniBuildConfig.isDebug}" }
    }

    private val logger = logger<AppStartupTasks>()
}

fun AnalyticsConfig.Companion.create(): AnalyticsConfig {
    return AnalyticsConfig(
        currentAniBuildConfig.versionName,
        currentAniBuildConfig.isDebug,
    )
}
