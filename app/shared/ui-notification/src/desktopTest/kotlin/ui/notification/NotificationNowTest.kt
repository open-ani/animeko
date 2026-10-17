/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.notification

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class NotificationNowTest {
    private class MutableClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    @Test
    fun `row times follow the passing time`() = runAniComposeUiTest {
        val start = Instant.fromEpochMilliseconds(TestNotificationNowMillis)
        val clock = MutableClock(start)
        val context = NotificationTimeContext(clock, TimeZone.UTC)
        val justNow = runBlocking { notificationRowTimeText(NotificationRowTime.JustNow).resolveString() }
        val fiveMinutesAgo = runBlocking { notificationRowTimeText(NotificationRowTime.MinutesAgo(5)).resolveString() }
        setContent {
            CompositionLocalProvider(LocalNotificationTimeContext provides context) {
                ProvideNotificationNow {
                    Text(formatNotificationRowTime(TestNotificationNowMillis - 30.seconds.inWholeMilliseconds))
                }
            }
        }
        onNodeWithText(justNow).assertExists()

        clock.now = start + 5.minutes
        mainClock.advanceTimeBy(1.minutes.inWholeMilliseconds)
        waitUntil { onAllNodesWithText(fiveMinutesAgo).fetchSemanticsNodes().isNotEmpty() }
    }
}
