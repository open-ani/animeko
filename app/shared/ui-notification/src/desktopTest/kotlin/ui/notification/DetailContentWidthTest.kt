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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 详情内容在宽屏中是居中的一列, 在手机上填满宽度.
 */
class DetailContentWidthTest {
    private companion object {
        const val CONTAINER = "container"
        val WideWidth = 1000.dp
        val NarrowWidth = 360.dp
        const val TOLERANCE_DP = 1f
    }

    private fun ComposeUiTest.showInContainer(width: Dp, content: @Composable () -> Unit) {
        setContent {
            NotificationTestHost {
                Box(Modifier.size(width, 600.dp).testTag(CONTAINER)) {
                    content()
                }
            }
        }
    }

    /**
     * [tag] 的宽度为 640dp, 左右两侧与容器的距离相等.
     */
    private fun ComposeUiTest.assertCentered(tag: String) {
        val container = onNodeWithTag(CONTAINER).getBoundsInRoot()
        val column = onNodeWithTag(tag).getBoundsInRoot()
        val leftGap = column.left - container.left
        val rightGap = container.right - column.right
        assertEquals(640f, (column.right - column.left).value, TOLERANCE_DP, "column width")
        assertTrue(leftGap > 0.dp, "column should not touch the left edge, gap: $leftGap")
        assertEquals(leftGap.value, rightGap.value, TOLERANCE_DP, "left gap $leftGap, right gap $rightGap")
    }

    private fun ComposeUiTest.assertFillsWidth(tag: String) {
        val container = onNodeWithTag(CONTAINER).getBoundsInRoot()
        val column = onNodeWithTag(tag).getBoundsInRoot()
        assertEquals(container.left.value, column.left.value, TOLERANCE_DP, "column left")
        assertEquals(container.right.value, column.right.value, TOLERANCE_DP, "column right")
    }

    @Test
    fun `report result is centered on wide screens`() = runAniComposeUiTest {
        showInContainer(WideWidth) {
            NotificationDetailContent(createTestReportResult(), NotificationActions.Noop)
        }
        assertCentered(NotificationTestTags.DETAIL)
    }

    @Test
    fun `report result fills the width on phones`() = runAniComposeUiTest {
        showInContainer(NarrowWidth) {
            NotificationDetailContent(createTestReportResult(), NotificationActions.Noop)
        }
        assertFillsWidth(NotificationTestTags.DETAIL)
    }

    @Test
    fun `short announcement is centered on wide screens`() = runAniComposeUiTest {
        showInContainer(WideWidth) {
            AnnouncementDetailContent(createTestAnnouncement(title = "维护", body = "很快恢复。"))
        }
        assertCentered(AnnouncementTestTags.DETAIL)
    }

    @Test
    fun `announcement fills the width on phones`() = runAniComposeUiTest {
        showInContainer(NarrowWidth) {
            AnnouncementDetailContent(createTestAnnouncement(pinned = true, linkUrl = "https://myani.org"))
        }
        assertFillsWidth(AnnouncementTestTags.DETAIL)
    }
}
