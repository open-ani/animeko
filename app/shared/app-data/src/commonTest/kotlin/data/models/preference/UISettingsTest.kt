/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import me.him188.ani.app.data.persistent.DataStoreJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 覆盖 [UISettings] 中新番时间表时区偏好 [UISettings.scheduleTimeZoneId] 的持久化:
 * 它决定时间表按哪个时区划分日期, 读不出来会导致用户在页面上选择的时区在重启后失效.
 */
class UISettingsTest {
    /**
     * 早于该偏好存在的存档不含这个字段, 必须回退到"跟随系统时区"而不是反序列化失败.
     */
    @Test
    fun `missing schedule time zone falls back to following the system`() {
        val settings = DataStoreJson.decodeFromString(UISettings.serializer(), "{}")

        assertNull(settings.scheduleTimeZoneId)
        assertEquals(UISettings.Default.scheduleTimeZoneId, settings.scheduleTimeZoneId)
    }

    @Test
    fun `schedule time zone survives serialization`() {
        val settings = UISettings.Default.copy(scheduleTimeZoneId = "Asia/Tokyo")

        val decoded = DataStoreJson.decodeFromString(
            UISettings.serializer(),
            DataStoreJson.encodeToString(UISettings.serializer(), settings),
        )

        assertEquals("Asia/Tokyo", decoded.scheduleTimeZoneId)
    }

    /**
     * `null` 是"跟随系统时区"这一有效状态, 不能被写成其他值或被省略成默认值.
     */
    @Test
    fun `following the system time zone survives serialization`() {
        val settings = UISettings.Default.copy(scheduleTimeZoneId = null)

        val decoded = DataStoreJson.decodeFromString(
            UISettings.serializer(),
            DataStoreJson.encodeToString(UISettings.serializer(), settings),
        )

        assertNull(decoded.scheduleTimeZoneId)
    }
}
