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
    fun `supported types are the notice group types`() {
        assertEquals(
            setOf(NotificationType.SYSTEM, NotificationType.REPORT_RESULT, NotificationType.ACCOUNT_SECURITY),
            NotificationType.SUPPORTED,
        )
        assertEquals(listOf(NotificationGroup.NOTICE), NotificationGroup.SUPPORTED)
    }

    @Test
    fun `notification type wire values`() {
        for (type in NotificationType.entries) {
            assertEquals(type, NotificationType.fromWireValue(type.wireValue))
        }
        assertEquals(NotificationType.REPORT_RESULT, NotificationType.fromWireValue("report_result"))
        assertNull(NotificationType.fromWireValue("future_type"))
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
    fun `settings hide only the switched off types`() {
        assertEquals(NotificationType.SUPPORTED, NotificationSettings.Default.shownTypes(NotificationType.SUPPORTED))
        assertEquals(
            setOf(NotificationType.SYSTEM, NotificationType.ACCOUNT_SECURITY),
            NotificationSettings(showReportResults = false).shownTypes(NotificationType.SUPPORTED),
        )
        assertEquals(
            setOf(NotificationType.SYSTEM, NotificationType.REPORT_RESULT),
            NotificationSettings(showAccountSecurity = false).shownTypes(NotificationType.SUPPORTED),
        )
        // 系统消息没有开关
        assertEquals(
            setOf(NotificationType.SYSTEM),
            NotificationSettings(showReportResults = false, showAccountSecurity = false)
                .shownTypes(NotificationGroup.NOTICE.types),
        )
        assertEquals(
            NotificationType.SUPPORTED,
            NotificationSettings(showAnnouncementDot = false).shownTypes(NotificationType.SUPPORTED),
        )
    }

    @Test
    fun `unknown announcement tag maps to UNKNOWN`() {
        assertEquals(AnnouncementTag.MAINTENANCE, AnnouncementTag.fromWireValue("maintenance"))
        assertEquals(AnnouncementTag.GENERAL, AnnouncementTag.fromWireValue("general"))
        assertEquals(AnnouncementTag.UNKNOWN, AnnouncementTag.fromWireValue("event"))
    }
}
