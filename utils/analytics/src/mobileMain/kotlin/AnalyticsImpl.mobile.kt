/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.analytics

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics

class AnalyticsImpl(
    config: AnalyticsConfig,
) : CommonAnalyticsImpl(config), IAnalytics {

    fun init() {
        val analytics = Firebase.analytics
        analytics.setAnalyticsCollectionEnabled(true)
        analytics.setDefaultEventParameters(
            intrinsicProperties.map { sanitizeParamKey(it.key) to it.value.toString() }.toMap(),
        )
    }

    override fun recordEventImpl(event: AnalyticsEvent, properties: Map<String, Any>) {
        val name = sanitizeEventName(event.event)
        val payload: Map<String, Any> = properties.map { (k, v) ->
            sanitizeParamKey(k) to v
        }.toMap()
        Firebase.analytics.logEvent(name, payload)
    }

    override fun onAppStart() {
    }
}

