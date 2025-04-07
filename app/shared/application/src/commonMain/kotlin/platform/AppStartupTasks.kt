/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import io.sentry.kotlin.multiplatform.Sentry
import me.him188.ani.app.domain.session.AuthorizationCancelledException
import me.him188.ani.app.domain.session.AuthorizationFailedException
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.SessionStatus
import me.him188.ani.app.platform.trace.SentryErrorReport
import me.him188.ani.app.trace.ErrorReportHolder
import me.him188.ani.utils.analytics.AnalyticsConfig
import me.him188.ani.utils.analytics.AnalyticsHolder
import me.him188.ani.utils.analytics.IAnalytics
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.currentPlatform
import kotlin.coroutines.cancellation.CancellationException

object AppStartupTasks {
    fun initializeSentry(userId: String) {
        initializeErrorReport(userId = userId)
        if (!currentAniBuildConfig.isDebug && currentAniBuildConfig.sentryEnabled) {
            ErrorReportHolder.init(SentryErrorReport)
        } else {
            Sentry.init {
                it.beforeBreadcrumb = { null }
            }
            Sentry.close()
        }
    }

    fun initializeAnalytics(instance: () -> IAnalytics) {
        if (!currentAniBuildConfig.isDebug && currentAniBuildConfig.analyticsEnabled) {
            AnalyticsHolder.init(instance())
        }
    }

    fun printVersions() {
        logger.info { "Ani started. platform: ${currentPlatform()}, version: ${currentAniBuildConfig.versionName}, isDebug: ${currentAniBuildConfig.isDebug}" }
    }

    // only throws CancellationException
    suspend fun verifySession(sessionManager: SessionManager) {
        try {
            sessionManager.requireAuthorize(
                onLaunch = {
                    // 打开 welcome page 一定代表账号验证失败或者没有账号，直接取消协程是可以的
                    throw CancellationException("Navigates to welcome page on first launch.")
                },
                skipOnGuest = true,
            )
        } catch (e: AuthorizationCancelledException) {
            // 如果验证失败的原因是 CancellationException，那可能是用户手动取消了验证或是上方首次启动的抛出
            if (e.cause is CancellationException) return
            logger.warn { IllegalStateException("Failed to automatically log in on startup", e) }
        } catch (e: AuthorizationFailedException) {
            if (e.status == SessionStatus.NetworkError) {
                // 网络错误就别抛异常了
                logger.warn { "Failed to automatically log in on startup due to network error" }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn { IllegalStateException("Failed to automatically log in on startup due to unknown error", e) }
        }
    }

    private val logger = logger<AppStartupTasks>()
}

fun AnalyticsConfig.Companion.create(): AnalyticsConfig {
    return AnalyticsConfig(
        currentAniBuildConfig.versionName,
        currentAniBuildConfig.isDebug,
    )
}
