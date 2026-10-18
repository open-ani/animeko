/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationRowTimeTextTest {
    private val times = listOf(
        NotificationRowTime.JustNow,
        NotificationRowTime.MinutesAgo(5),
        NotificationRowTime.HoursAgo(3),
        NotificationRowTime.Yesterday,
        NotificationRowTime.ThisYear(LocalDate(2026, 10, 11)),
        NotificationRowTime.ThisYear(LocalDate(2026, 1, 5)),
        NotificationRowTime.OtherYear(LocalDate(2025, 10, 11)),
    )

    private fun resolveAll(locale: Locale): List<String> = withLocale(locale) {
        runBlocking { times.map { notificationRowTimeText(it).resolveString() } }
    }

    @Test
    fun `simplified chinese`() {
        assertEquals(
            listOf("刚刚", "5 分钟前", "3 小时前", "昨天", "10 月 11 日", "1 月 5 日", "2025/10/11"),
            resolveAll(Locale.SIMPLIFIED_CHINESE),
        )
    }

    @Test
    fun `traditional chinese`() {
        val expected = listOf("剛剛", "5 分鐘前", "3 小時前", "昨天", "10 月 11 日", "1 月 5 日", "2025/10/11")
        assertEquals(expected, resolveAll(Locale.TRADITIONAL_CHINESE))
        assertEquals(expected, resolveAll(Locale.forLanguageTag("zh-HK")))
    }

    @Test
    fun `english`() {
        assertEquals(
            listOf("Just now", "5 min ago", "3 hr ago", "Yesterday", "Oct 11", "Jan 5", "Oct 11, 2025"),
            resolveAll(Locale.US),
        )
    }
}
