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
 * 举报结果和账号安全各有一个独立的开关. 系统和公告两组之间有分隔线.
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
        showGroups(NotificationSettings(showReportResults = true, showAccountSecurity = false))
        switchIn(NotificationSettingsTestTags.REPORT_RESULT).assertIsOn()
        switchIn(NotificationSettingsTestTags.ACCOUNT_SECURITY).assertIsOff()
    }

    @Test
    fun `a divider separates the system and announcement groups`() = runAniComposeUiTest {
        showGroups(NotificationSettings.Default)
        val accountSecurity = onNodeWithTag(NotificationSettingsTestTags.ACCOUNT_SECURITY).fetchSemanticsNode().boundsInRoot
        val divider = onNodeWithTag(NotificationSettingsTestTags.GROUP_DIVIDER).fetchSemanticsNode().boundsInRoot
        val announcement = onNodeWithTag(NotificationSettingsTestTags.ANNOUNCEMENT).fetchSemanticsNode().boundsInRoot
        assertTrue(divider.top >= accountSecurity.bottom, "divider should be below the system group")
        assertTrue(divider.bottom <= announcement.top, "divider should be above the announcement group")
        // 通栏
        assertEquals(accountSecurity.left, divider.left, 1f)
        assertEquals(accountSecurity.right, divider.right, 1f)
    }
}
