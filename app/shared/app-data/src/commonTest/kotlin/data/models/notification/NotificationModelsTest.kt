/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.notification

import me.him188.ani.app.data.models.preference.NotificationSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationModelsTest {
    @Test
    fun `tabs are interaction then notice`() {
        assertEquals(listOf(NotificationGroup.INTERACTION, NotificationGroup.NOTICE), NotificationGroup.TABS)
    }

    @Test
    fun `group wire values are the server categories`() {
        assertEquals("interaction", NotificationGroup.INTERACTION.wireValue)
        assertEquals("notice", NotificationGroup.NOTICE.wireValue)
        for (group in NotificationGroup.entries) {
            assertEquals(group, NotificationGroup.fromWireValue(group.wireValue))
        }
        // 没有标签页的分类
        assertNull(NotificationGroup.fromWireValue("direct_message"))
        assertNull(NotificationGroup.fromWireValue("future_category"))
    }

    @Test
    fun `only types with their own rendering are known`() {
        assertEquals(
            listOf("system", "report_result", "account_security"),
            NotificationType.entries.map { it.wireValue },
        )
        for (type in NotificationType.entries) {
            assertEquals(type, NotificationType.fromWireValue(type.wireValue))
        }
        for (value in listOf("comment_reply", "mention", "reaction", "follow", "direct_message", "future_type")) {
            assertNull(NotificationType.fromWireValue(value), value)
        }
    }

    @Test
    fun `notification type and group are parsed from the raw values`() {
        val known = notification(rawType = "report_result", rawCategory = "notice")
        assertEquals(NotificationType.REPORT_RESULT, known.type)
        assertEquals(NotificationGroup.NOTICE, known.group)

        val unknown = notification(rawType = "comment_reply", rawCategory = "interaction")
        assertNull(unknown.type)
        assertEquals(NotificationGroup.INTERACTION, unknown.group)

        val unknownCategory = notification(rawType = "direct_message", rawCategory = "direct_message")
        assertNull(unknownCategory.type)
        assertNull(unknownCategory.group)
    }

    @Test
    fun `icon wire values`() {
        for (icon in NotificationIcon.entries) {
            assertEquals(icon, NotificationIcon.fromWireValue(icon.wireValue))
        }
        assertEquals(NotificationIcon.REPLY, NotificationIcon.fromWireValue("reply"))
        assertNull(NotificationIcon.fromWireValue("sparkles"))
        assertNull(NotificationIcon.fromWireValue(null))
    }

    @Test
    fun `target destination prefers the most specific place`() {
        val all = NotificationTarget(
            subjectId = 1,
            episodeId = 2,
            personId = 3,
            characterId = 4,
            url = "https://example.com",
        )
        assertEquals(NotificationDestination.Episode(subjectId = 1, episodeId = 2), all.destination)
        assertEquals(NotificationDestination.Subject(1), all.copy(episodeId = null).destination)
        // 剧集需要条目
        assertEquals(NotificationDestination.Person(3), all.copy(subjectId = null).destination)
        assertEquals(NotificationDestination.Character(4), all.copy(subjectId = null, personId = null).destination)
        assertEquals(
            NotificationDestination.Url("https://example.com"),
            NotificationTarget(episodeId = 2, url = "https://example.com").destination,
        )
        assertNull(NotificationTarget().destination)
    }

    @Test
    fun `target url must be http or https`() {
        assertEquals(
            NotificationDestination.Url("http://example.com/a?b=1"),
            NotificationTarget(url = "http://example.com/a?b=1").destination,
        )
        assertEquals(
            NotificationDestination.Url("HTTPS://example.com"),
            NotificationTarget(url = " HTTPS://example.com ").destination,
        )
        val rejected = listOf("javascript:alert(1)", "ftp://example.com", "example.com", "https://", "", "ani://subject/1")
        for (url in rejected) {
            assertNull(NotificationTarget(url = url).destination, url)
        }
    }

    @Test
    fun `unknown account security event maps to UNKNOWN`() {
        assertEquals(
            AccountSecurityEvent.THIRD_PARTY_UNBOUND,
            AccountSecurityEvent.fromWireValue("third_party_unbound"),
        )
        assertEquals(AccountSecurityEvent.UNKNOWN, AccountSecurityEvent.fromWireValue("password_changed"))
    }

    @Test
    fun `unknown or missing system event maps to null`() {
        assertEquals(
            SystemNotificationEvent.BANGUMI_AUTHORIZATION_EXPIRED,
            SystemNotificationEvent.fromWireValue("bangumi_authorization_expired"),
        )
        assertNull(SystemNotificationEvent.fromWireValue("future_event"))
        assertNull(SystemNotificationEvent.fromWireValue(null))
    }

    @Test
    fun `settings exclude only the switched off types`() {
        assertEquals(emptySet(), NotificationSettings.Default.excludedTypes)
        assertEquals(
            setOf(NotificationType.REPORT_RESULT),
            NotificationSettings(showReportResults = false).excludedTypes,
        )
        assertEquals(
            setOf(NotificationType.ACCOUNT_SECURITY),
            NotificationSettings(showAccountSecurity = false).excludedTypes,
        )
        // 系统消息没有开关
        assertEquals(
            setOf(NotificationType.REPORT_RESULT, NotificationType.ACCOUNT_SECURITY),
            NotificationSettings(showReportResults = false, showAccountSecurity = false).excludedTypes,
        )
        assertEquals(emptySet(), NotificationSettings(showAnnouncementDot = false).excludedTypes)
    }

    @Test
    fun `badge state sums the tab counts`() {
        val state = NotificationBadgeState.Empty.copy(
            unreadCounts = mapOf(NotificationGroup.INTERACTION to 2, NotificationGroup.NOTICE to 5),
        )
        assertEquals(7, state.unreadCount)
        assertEquals(2, state.unreadCountOf(NotificationGroup.INTERACTION))
        assertEquals(5, state.unreadCountOf(NotificationGroup.NOTICE))

        val noticeOnly = NotificationBadgeState.Empty.copy(unreadCounts = mapOf(NotificationGroup.NOTICE to 3))
        assertEquals(3, noticeOnly.unreadCount)
        assertEquals(0, noticeOnly.unreadCountOf(NotificationGroup.INTERACTION))

        assertEquals(0, NotificationBadgeState.Empty.unreadCount)
    }

    @Test
    fun `unknown announcement tag maps to UNKNOWN`() {
        assertEquals(AnnouncementTag.MAINTENANCE, AnnouncementTag.fromWireValue("maintenance"))
        assertEquals(AnnouncementTag.GENERAL, AnnouncementTag.fromWireValue("general"))
        assertEquals(AnnouncementTag.UNKNOWN, AnnouncementTag.fromWireValue("event"))
    }

    private fun notification(rawType: String, rawCategory: String) = AppNotification(
        id = "n1",
        rawType = rawType,
        rawCategory = rawCategory,
        createdAtMillis = 1,
        read = false,
        display = NotificationDisplay(title = "t"),
        content = AppNotificationContent.Unknown,
    )
}
