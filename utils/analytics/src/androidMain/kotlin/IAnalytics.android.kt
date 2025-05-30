/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.analytics

import android.content.Context
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import java.util.UUID

class AnalyticsImpl(
    config: AnalyticsConfig,
    private val userId: String, // anonymous id
) : CommonAnalyticsImpl(config), IAnalytics {
    fun init(
        context: Context,
        apiKey: String,
        host: String,
    ) {
        val config = PostHogAndroidConfig(
            apiKey = apiKey,
            host = host,
        ).apply {
            debug = config.debugLogging
            this.captureApplicationLifecycleEvents = false
            this.captureScreenViews = false
            this.getAnonymousId = { UUID.fromString(userId) }
        }
        PostHogAndroid.setup(context, config)
        for (entry in intrinsicProperties) {
            PostHog.register(entry.key, entry.value)
        }
    }

    override fun recordEventImpl(event: AnalyticsEvent, properties: Map<String, Any>) {
        PostHog.capture(event = event.event, properties = properties)
    }

    override fun onAppStart() {
//        PostHog.identify(userId, userProperties = intrinsicProperties)
    }
}

// Countly implementation

//class AnalyticsImpl(config: AnalyticsConfig) : CommonAnalyticsImpl(config), IAnalytics {
//    fun init(
//        application: Application,
//        appKey: String,
//        serverUrl: String,
//    ) {
//        Countly.sharedInstance().init(
//            CountlyConfig(application, appKey, serverUrl).apply {
//                if (config.debugLogging) {
//                    setLoggingEnabled(true)
//                } else {
//                    setLoggingEnabled(false)
//                }
//                enableAutomaticViewTracking()
//                setDisableLocation()
//            },
//        )
//    }
//
//    override fun recordEventImpl(event: AnalyticsEvent, extras: Map<String, Any?>) {
//        Countly.sharedInstance().events().recordEvent(event.event, extras)
//    }
//
//    override fun onAppStart() {
//        Countly.sharedInstance().sessions().beginSession()
//    }
//}
