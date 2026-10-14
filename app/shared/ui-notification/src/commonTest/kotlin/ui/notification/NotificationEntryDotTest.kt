/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import me.him188.ani.app.data.models.notification.NotificationBadgeState
import me.him188.ani.app.data.models.notification.NotificationGroup
import me.him188.ani.app.data.models.preference.NotificationSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationEntryDotTest {
    private val dotOn = NotificationSettings(showAnnouncementDot = true)
    private val dotOff = NotificationSettings(showAnnouncementDot = false)

    @Test
    fun `unread notifications always show the dot`() {
        val state = NotificationBadgeState.Empty.copy(
            unreadCounts = mapOf(NotificationGroup.NOTICE to 5),
            hasUnreadAnnouncements = true,
        )
        assertTrue(state.showsEntryDot(dotOn))
        assertTrue(state.showsEntryDot(dotOff))
    }

    @Test
    fun `unread notifications in any tab show the dot`() {
        val state = NotificationBadgeState.Empty.copy(unreadCounts = mapOf(NotificationGroup.INTERACTION to 1))
        assertTrue(state.showsEntryDot(dotOff))
    }

    @Test
    fun `only unread announcements show the dot when enabled`() {
        val state = NotificationBadgeState.Empty.copy(hasUnreadAnnouncements = true, unreadAnnouncementCount = 2)
        assertTrue(state.showsEntryDot(dotOn))
        assertFalse(state.showsEntryDot(dotOff))
    }

    @Test
    fun `nothing unread shows no dot`() {
        assertFalse(NotificationBadgeState.Empty.showsEntryDot(dotOn))
    }
}
