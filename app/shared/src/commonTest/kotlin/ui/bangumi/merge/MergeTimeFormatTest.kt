/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.bangumi.merge

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class MergeTimeFormatTest {
    private val timeZone = TimeZone.of("Asia/Shanghai")
    private val now = LocalDateTime(2026, 7, 29, 14, 10).toInstant(timeZone)

    private fun format(dateTime: LocalDateTime): String =
        formatMergeTime(dateTime.toInstant(timeZone), now, timeZone, yesterdayLabel = "昨")

    @Test
    fun `TIME-01 今天显示时分`() {
        assertEquals("13:40", format(LocalDateTime(2026, 7, 29, 13, 40)))
    }

    @Test
    fun `TIME-02 时分都补零`() {
        assertEquals("09:05", format(LocalDateTime(2026, 7, 29, 9, 5)))
    }

    @Test
    fun `TIME-03 昨天显示昨字前缀`() {
        assertEquals("昨 22:10", format(LocalDateTime(2026, 7, 28, 22, 10)))
    }

    @Test
    fun `TIME-04 今年显示月日`() {
        assertEquals("7/25", format(LocalDateTime(2026, 7, 25, 8, 0)))
    }

    @Test
    fun `TIME-05 往年显示年月日`() {
        assertEquals("2024/7/25", format(LocalDateTime(2024, 7, 25, 8, 0)))
    }

    @Test
    fun `TIME-06 跨年边界 去年12月31日不按今年格式`() {
        assertEquals("2025/12/31", format(LocalDateTime(2025, 12, 31, 23, 59)))
    }

    @Test
    fun `TIME-07 今天零点与今天最后一分钟都按今天`() {
        assertEquals("00:00", format(LocalDateTime(2026, 7, 29, 0, 0)))
        assertEquals("23:59", format(LocalDateTime(2026, 7, 29, 23, 59)))
    }

    @Test
    fun `TIME-08 昨天按时区判断`() {
        // UTC 2026-07-28 17:00 = 上海 2026-07-29 01:00 → 今天.
        val utc = TimeZone.UTC
        val instant = LocalDateTime(2026, 7, 28, 17, 0).toInstant(utc)
        assertEquals("01:00", formatMergeTime(instant, now, timeZone, "昨"))
        // 按 UTC 看则是昨天.
        val nowUtc = LocalDateTime(2026, 7, 29, 6, 10).toInstant(utc)
        assertEquals("昨 17:00", formatMergeTime(instant, nowUtc, utc, "昨"))
    }
}
