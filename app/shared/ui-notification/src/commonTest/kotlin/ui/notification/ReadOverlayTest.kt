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

import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadOverlayTest {
    @Test
    fun `server read state is kept`() {
        assertTrue(ReadOverlay.Empty.isRead(createTestReportResult(read = true)))
        assertFalse(ReadOverlay.Empty.isRead(createTestReportResult(read = false)))
    }

    @Test
    fun `locally read ids are read`() {
        val overlay = ReadOverlay.Empty.withRead("report-1")
        assertTrue(overlay.isRead(createTestReportResult(id = "report-1")))
        assertFalse(overlay.isRead(createTestReportResult(id = "report-2")))
        assertFalse(overlay.withoutRead("report-1").isRead(createTestReportResult(id = "report-1")))
    }

    @Test
    fun `mark all read covers items not newer than the time point`() {
        val overlay = ReadOverlay(allReadUpToMillis = 1000)
        assertTrue(overlay.isRead(createTestReportResult(createdAtMillis = 1000)))
        assertTrue(overlay.isRead(createTestReportResult(createdAtMillis = 999)))
        assertFalse(overlay.isRead(createTestReportResult(createdAtMillis = 1001)))
    }

    @Test
    fun `guest announcements have no read state`() {
        assertNull(ReadOverlay.Empty.isRead(createTestAnnouncement(read = null)))
        assertEquals(false, ReadOverlay.Empty.isRead(createTestAnnouncement(read = false)))
        assertEquals(true, ReadOverlay.Empty.withRead("announcement-1").isRead(createTestAnnouncement(read = false)))
    }
}
