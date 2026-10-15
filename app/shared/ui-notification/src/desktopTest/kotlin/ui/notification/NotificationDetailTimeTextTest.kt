/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationDetailTimeTextTest {
    private val times = listOf(
        NotificationDetailTime(LocalDateTime.parse("2026-10-11T19:02"), withYear = false),
        NotificationDetailTime(LocalDateTime.parse("2026-01-05T09:05"), withYear = false),
        NotificationDetailTime(LocalDateTime.parse("2025-10-11T19:02"), withYear = true),
    )

    private fun resolveAll(locale: Locale): List<String> = withLocale(locale) {
        runBlocking { times.map { notificationDetailTimeText(it).resolveString() } }
    }

    @Test
    fun `simplified chinese`() {
        assertEquals(
            listOf("10 月 11 日 19:02", "1 月 5 日 09:05", "2025 年 10 月 11 日 19:02"),
            resolveAll(Locale.SIMPLIFIED_CHINESE),
        )
    }

    @Test
    fun `traditional chinese`() {
        val expected = listOf("10 月 11 日 19:02", "1 月 5 日 09:05", "2025 年 10 月 11 日 19:02")
        assertEquals(expected, resolveAll(Locale.TRADITIONAL_CHINESE))
        assertEquals(expected, resolveAll(Locale.forLanguageTag("zh-HK")))
    }

    @Test
    fun `english`() {
        assertEquals(
            listOf("Oct 11, 19:02", "Jan 5, 09:05", "Oct 11, 2025, 19:02"),
            resolveAll(Locale.US),
        )
    }
}
