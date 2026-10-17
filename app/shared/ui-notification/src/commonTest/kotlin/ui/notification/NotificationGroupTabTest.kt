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

import me.him188.ani.app.data.models.notification.NotificationGroup
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationGroupTabTest {
    @Test
    fun `badge text caps at 99`() {
        assertEquals("1", notificationBadgeText(1))
        assertEquals("99", notificationBadgeText(99))
        assertEquals("99+", notificationBadgeText(100))
        assertEquals("99+", notificationBadgeText(1234))
    }

    @Test
    fun `each tab shows the unread count of its own group`() {
        val state = TestNotificationBadgeState.copy(
            unreadCounts = mapOf(NotificationGroup.INTERACTION to 2, NotificationGroup.NOTICE to 5),
        )
        assertEquals(5, state.unreadCountOf(NotificationGroup.NOTICE))
        assertEquals(2, state.unreadCountOf(NotificationGroup.INTERACTION))

        val noticeOnly = TestNotificationBadgeState.copy(unreadCounts = mapOf(NotificationGroup.NOTICE to 5))
        assertEquals(0, noticeOnly.unreadCountOf(NotificationGroup.INTERACTION))
    }
}
