/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(ExperimentalForeignApi::class)

package me.him188.ani.app.ios

import cocoapods.PostHog.PostHogConfig
import cocoapods.PostHog.PostHogSDK
import kotlinx.cinterop.ExperimentalForeignApi
import me.him188.ani.utils.analytics.AnalyticsConfig
import me.him188.ani.utils.analytics.AnalyticsEvent
import me.him188.ani.utils.analytics.CommonAnalyticsImpl
import me.him188.ani.utils.analytics.IAnalytics
import platform.Foundation.NSUUID

class IosAnalyticsImpl(
    config: AnalyticsConfig,
    private val userId: String,
) : IAnalytics, CommonAnalyticsImpl(config) {
    fun init(
        apiKey: String,
        host: String,
    ) {
        println("PostHogSDK initialized")
        PostHogSDK.shared().setup(
            PostHogConfig(apiKey = apiKey, host = host).apply {
                setCaptureApplicationLifecycleEvents(false)
                setCaptureElementInteractions(false)
                setCaptureScreenViews(true)
                setDebug(true)
                setGetAnonymousId {
                    NSUUID(userId)
                }
            },
        )
    }

    override fun recordEventImpl(
        event: AnalyticsEvent,
        properties: Map<String, Any>,
    ) {
        @Suppress("UNCHECKED_CAST")
        PostHogSDK.shared().screenWithTitle(
            "Main",
            properties as Map<Any?, String>,
        )
    }

    override fun onAppStart() {
        recordEvent(AnalyticsEvent.Screen)
    }
}
