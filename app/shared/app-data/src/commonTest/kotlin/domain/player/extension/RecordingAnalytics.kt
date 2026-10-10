/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import me.him188.ani.utils.analytics.AnalyticsEvent
import me.him188.ani.utils.analytics.IAnalytics

/**
 * 记下所有事件的 [IAnalytics]. 与正式实现一样丢弃值为 `null` 的属性.
 */
class RecordingAnalytics : IAnalytics {
    private val events = mutableListOf<Pair<AnalyticsEvent, Map<String, Any>>>()

    override fun recordEvent(event: AnalyticsEvent, properties: Map<String, Any?>) {
        @Suppress("UNCHECKED_CAST")
        events += event to (properties.filterValues { it != null } as Map<String, Any>)
    }

    fun propertiesOf(event: AnalyticsEvent): List<Map<String, Any>> =
        events.filter { it.first == event }.map { it.second }
}
