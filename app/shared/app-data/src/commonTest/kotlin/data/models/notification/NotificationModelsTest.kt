/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.notification

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
        assertEquals(AccountSecurityEvent.NEW_LOGIN, AccountSecurityEvent.fromWireValue("new_login"))
        assertEquals(
            AccountSecurityEvent.BANGUMI_AUTHORIZATION_EXPIRED,
            AccountSecurityEvent.fromWireValue("bangumi_authorization_expired"),
        )
        assertEquals(AccountSecurityEvent.UNKNOWN, AccountSecurityEvent.fromWireValue("password_changed"))
    }

    @Test
    fun `unknown login method maps to UNKNOWN and missing stays null`() {
        assertEquals(LoginMethod.QR_CODE, LoginMethod.fromWireValue("qr_code"))
        assertEquals(LoginMethod.EMAIL_OTP, LoginMethod.fromWireValue("email_otp"))
        assertEquals(LoginMethod.UNKNOWN, LoginMethod.fromWireValue("passkey"))
        assertNull(LoginMethod.fromWireValue(null))
    }

    @Test
    fun `unknown announcement tag maps to UNKNOWN`() {
        assertEquals(AnnouncementTag.MAINTENANCE, AnnouncementTag.fromWireValue("maintenance"))
        assertEquals(AnnouncementTag.GENERAL, AnnouncementTag.fromWireValue("general"))
        assertEquals(AnnouncementTag.UNKNOWN, AnnouncementTag.fromWireValue("event"))
    }
}
