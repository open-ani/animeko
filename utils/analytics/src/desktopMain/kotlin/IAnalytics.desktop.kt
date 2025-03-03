/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.analytics

import com.posthog.java.PostHog
import com.posthog.java.PostHogLogger
import me.him188.ani.utils.logging.Logger
import me.him188.ani.utils.logging.logger
import java.util.*

class AnalyticsImpl(
    config: AnalyticsConfig,
    private val userId: String, // anonymous id
) : IAnalytics, CommonAnalyticsImpl(config) {
    private lateinit var postHog: PostHog

    fun init(
        apiKey: String,
        host: String,
    ) {
        postHog = PostHog.Builder(apiKey).host(host).logger(
            logger.asPosthogLogger(),
        ).build()
    }

    private val logger = logger<AnalyticsImpl>()
    override fun recordEventImpl(event: AnalyticsEvent, properties: Map<String, Any?>) {
        postHog.capture(UUID.randomUUID().toString(), event.event, properties)
    }

    private fun Logger.asPosthogLogger() = object : PostHogLogger {
        override fun debug(message: String?) {
            this@asPosthogLogger.debug(message)
        }

        override fun info(message: String?) {
            this@asPosthogLogger.info(message)
        }

        override fun warn(message: String?) {
            this@asPosthogLogger.warn(message)
        }

        override fun error(message: String?) {
            this@asPosthogLogger.error(message)
        }

        override fun error(message: String?, throwable: Throwable?) {
            this@asPosthogLogger.error(message, throwable)
        }

    }

    override fun onAppStart() {
        postHog.identify(userId, intrinsicProperties)
    }
}

//class AnalyticsImpl(
//    config: AnalyticsConfig,
//) : IAnalytics, CommonAnalyticsImpl(config) {
//    private val logger = logger<AnalyticsImpl>()
//
//    fun init(
//        dir: File,
//        appKey: String,
//        serverUrl: String,
//    ) {
//        Countly.instance().init(
//            Config(serverUrl, appKey, dir).apply {
//                enableFeatures(
//                    Config.Feature.Events,
//                    Config.Feature.Views,
//                    Config.Feature.Sessions,
//                    Config.Feature.UserProfiles
//                )
//                setDeviceIdStrategy(Config.DeviceIdStrategy.UUID)
//                if (config.debugLogging) {
//                    setLogListener(logger.asLogCallback())
//                }
//
//                disableUnhandledCrashReporting()
//                disableLocation()
//            },
//        )
//        logger.info { "Device ID: ${Countly.instance().deviceId().id}" }
//    }
//
//    private fun Logger.asLogCallback() = LogCallback { logMessage, logLevel ->
//        when (logLevel) {
//            Config.LoggingLevel.VERBOSE -> this@asLogCallback.trace(logMessage)
//            Config.LoggingLevel.DEBUG -> this@asLogCallback.debug(logMessage)
//            Config.LoggingLevel.INFO -> this@asLogCallback.info(logMessage)
//            Config.LoggingLevel.WARN -> this@asLogCallback.warn(logMessage)
//            Config.LoggingLevel.ERROR -> this@asLogCallback.error(logMessage)
//            null,
//            Config.LoggingLevel.OFF,
//                -> {
//            }
//        }
//    }
//
//    override fun recordEventImpl(event: AnalyticsEvent, extras: Map<String, Any?>) {
//        Countly.instance().events().recordEvent(event.event, extras)
//    }
//
//    override fun onAppStart() {
//        Countly.session().begin()
//    }
//}

