/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import me.him188.ani.app.domain.player.analytics.PlayFlowStep
import me.him188.ani.utils.analytics.AnalyticsEvent
import me.him188.ani.utils.analytics.IAnalytics

/**
 * 记下所有事件的 [IAnalytics]. 与正式实现一样丢弃值为 `null` 的属性.
 */
class RecordingAnalytics : IAnalytics {
    private val lock = SynchronizedObject()
    private val events = mutableListOf<Pair<AnalyticsEvent, Map<String, Any>>>()

    override fun recordEvent(event: AnalyticsEvent, properties: Map<String, Any?>) {
        @Suppress("UNCHECKED_CAST")
        val recorded = event to (properties.filterValues { it != null } as Map<String, Any>)
        synchronized(lock) { events += recorded }
    }

    fun propertiesOf(event: AnalyticsEvent): List<Map<String, Any>> =
        synchronized(lock) { events.filter { it.first == event }.map { it.second } }

    /**
     * `play_flow` 事件里 `step` 为 [step] 的那些, 按记录顺序.
     */
    fun stepsOf(step: PlayFlowStep): List<Map<String, Any>> =
        propertiesOf(AnalyticsEvent.PlayFlow).filter { it["step"] == step.value }

    /**
     * 全部 `play_flow` 事件的 `step` 序列.
     */
    fun playFlowSteps(): List<String> = propertiesOf(AnalyticsEvent.PlayFlow).map { it["step"] as String }
}
