/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.notification

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import me.him188.ani.app.data.models.preference.NotificationSettings
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.rememberTestSettingsState
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 评论被回复, 举报结果和账号安全各有一个独立的开关. 互动, 系统和公告三组之间有分隔线.
 */
@OptIn(TestOnly::class)
class NotificationSettingsGroupsTest {
    private fun ComposeUiTest.showGroups(initial: NotificationSettings): () -> NotificationSettings {
        lateinit var state: SettingsState<NotificationSettings>
        setContent {
            ProvideCompositionLocalsForPreview {
                SettingsTab {
                    state = rememberTestSettingsState(initial)
                    NotificationSettingsGroups(state)
                }
            }
        }
        return { state.value }
    }

    private fun ComposeUiTest.switchIn(itemTag: String) =
        onNode(isToggleable() and hasAnyAncestor(hasTestTag(itemTag)), useUnmergedTree = true)

    @Test
    fun `report results and account security are switched separately`() = runAniComposeUiTest {
        val settings = showGroups(NotificationSettings.Default)
        switchIn(NotificationSettingsTestTags.REPORT_RESULT).assertIsOn().assertIsEnabled()
        switchIn(NotificationSettingsTestTags.ACCOUNT_SECURITY).assertIsOn().assertIsEnabled()

        onNodeWithTag(NotificationSettingsTestTags.REPORT_RESULT).performClick()
        waitUntil { !settings().showReportResults }
        assertEquals(NotificationSettings(showReportResults = false), settings())
        switchIn(NotificationSettingsTestTags.REPORT_RESULT).assertIsOff()
        switchIn(NotificationSettingsTestTags.ACCOUNT_SECURITY).assertIsOn()

        onNodeWithTag(NotificationSettingsTestTags.ACCOUNT_SECURITY).performClick()
        waitUntil { !settings().showAccountSecurity }
        assertEquals(NotificationSettings(showReportResults = false, showAccountSecurity = false), settings())

        onNodeWithTag(NotificationSettingsTestTags.REPORT_RESULT).performClick()
        waitUntil { settings().showReportResults }
        assertEquals(NotificationSettings(showAccountSecurity = false), settings())
    }

    @Test
    fun `switches show the saved settings`() = runAniComposeUiTest {
        showGroups(NotificationSettings(showReportResults = true, showAccountSecurity = false, showCommentReplies = false))
        switchIn(NotificationSettingsTestTags.REPORT_RESULT).assertIsOn()
        switchIn(NotificationSettingsTestTags.ACCOUNT_SECURITY).assertIsOff()
        switchIn(NotificationSettingsTestTags.COMMENT_REPLY).assertIsOff()
    }

    @Test
    fun `comment replies have their own switch in the interaction group`() = runAniComposeUiTest {
        val settings = showGroups(NotificationSettings.Default)
        switchIn(NotificationSettingsTestTags.COMMENT_REPLY).assertIsOn().assertIsEnabled()

        onNodeWithTag(NotificationSettingsTestTags.COMMENT_REPLY).performClick()
        waitUntil { !settings().showCommentReplies }
        assertEquals(NotificationSettings(showCommentReplies = false), settings())
        switchIn(NotificationSettingsTestTags.REPORT_RESULT).assertIsOn()
        switchIn(NotificationSettingsTestTags.ACCOUNT_SECURITY).assertIsOn()

        // 互动组在系统组上方
        val commentReply = onNodeWithTag(NotificationSettingsTestTags.COMMENT_REPLY).fetchSemanticsNode().boundsInRoot
        val reportResult = onNodeWithTag(NotificationSettingsTestTags.REPORT_RESULT).fetchSemanticsNode().boundsInRoot
        assertTrue(commentReply.bottom <= reportResult.top, "comment reply switch should be above the system group")
    }

    @Test
    fun `dividers separate the interaction system and announcement groups`() = runAniComposeUiTest {
        showGroups(NotificationSettings.Default)
        val commentReply = onNodeWithTag(NotificationSettingsTestTags.COMMENT_REPLY).fetchSemanticsNode().boundsInRoot
        val reportResult = onNodeWithTag(NotificationSettingsTestTags.REPORT_RESULT).fetchSemanticsNode().boundsInRoot
        val accountSecurity = onNodeWithTag(NotificationSettingsTestTags.ACCOUNT_SECURITY).fetchSemanticsNode().boundsInRoot
        val announcement = onNodeWithTag(NotificationSettingsTestTags.ANNOUNCEMENT).fetchSemanticsNode().boundsInRoot
        val dividers = onAllNodesWithTag(NotificationSettingsTestTags.GROUP_DIVIDER).fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .sortedBy { it.top }
        assertEquals(2, dividers.size)
        assertTrue(dividers[0].top >= commentReply.bottom, "first divider should be below the interaction group")
        assertTrue(dividers[0].bottom <= reportResult.top, "first divider should be above the system group")
        assertTrue(dividers[1].top >= accountSecurity.bottom, "second divider should be below the system group")
        assertTrue(dividers[1].bottom <= announcement.top, "second divider should be above the announcement group")
        // 通栏
        for (divider in dividers) {
            assertEquals(accountSecurity.left, divider.left, 1f)
            assertEquals(accountSecurity.right, divider.right, 1f)
        }
    }
}
