/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.analytics

actual class AnalyticsImpl(config: AnalyticsConfig) : IAnalytics, CommonAnalyticsImpl(config) {

    fun init(apiKey: String, host: String) {

    }

    override fun recordEventImpl(event: AnalyticsEvent, properties: Map<String, Any?>) {
        // TODO: ios analytics
    }
}


//
//actual class AnalyticsImpl(config: AnalyticsConfig) : IAnalytics, CommonAnalyticsImpl(config) {
//    @OptIn(ExperimentalForeignApi::class)
//    fun init(
//        appKey: String,
//        serverUrl: String,
//    ) {
//        Countly.sharedInstance().startWithConfig(
//            CountlyConfig().apply {
//                this.appKey = appKey
//                this.host = serverUrl
//            },
//        )
//    }
//
//    @OptIn(ExperimentalForeignApi::class)
//    override fun recordEventImpl(event: AnalyticsEvent, extras: Map<String, Any?>) {
//        @Suppress("UNCHECKED_CAST")
//        Countly.sharedInstance().recordEvent(event.event, extras as Map<Any?, Any?>)
//    }
//
//    @OptIn(ExperimentalForeignApi::class)
//    override fun onAppStart() {
//        Countly.sharedInstance().beginSession()
//    }
//}
