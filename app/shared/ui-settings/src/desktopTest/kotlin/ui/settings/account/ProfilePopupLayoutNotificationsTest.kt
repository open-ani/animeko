/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.account

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.ui.foundation.ProvideFoundationCompositionLocalsForTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.notification_has_unread
import me.him188.ani.app.ui.lang.notification_title
import me.him188.ani.app.ui.lang.playback_history_title
import me.him188.ani.app.ui.lang.settings_account_popup_edit_profile
import me.him188.ani.app.ui.lang.settings_account_popup_login_register
import me.him188.ani.app.ui.user.SelfInfoUiState
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(TestOnly::class)
class ProfilePopupLayoutNotificationsTest {
    private val notificationsText = runBlocking { getString(Lang.notification_title) }
    private val unreadText = runBlocking { getString(Lang.notification_has_unread) }

    private val guestState = AccountSettingsState(
        SelfInfoUiState(selfInfo = null, isLoading = false, isSessionValid = false, bangumiConnected = null),
        boundBangumi = false,
        avatarUploadState = EditProfileState.UploadAvatarState.Default,
    )

    private class Clicks {
        var notifications = 0
        var others = 0
    }

    private fun ComposeUiTest.setPopup(
        state: AccountSettingsState,
        clicks: Clicks,
        showDot: () -> Boolean = { false },
    ) {
        setContent {
            ProvideFoundationCompositionLocalsForTest {
                Surface {
                    ProfilePopupLayout(
                        state,
                        onClickLogin = { clicks.others++ },
                        onClickEditAvatar = { clicks.others++ },
                        onClickEditProfile = { clicks.others++ },
                        showNotificationDot = showDot(),
                        onClickNotifications = { clicks.notifications++ },
                        onClickPlaybackHistory = { clicks.others++ },
                        onClickSettings = { clicks.others++ },
                        onClickLogout = { clicks.others++ },
                        modifier = Modifier.widthIn(max = 360.dp),
                    )
                }
            }
        }
    }

    private fun ComposeUiTest.notificationsDescriptions(): List<String> =
        onNodeWithTag(ProfilePopupTestTags.NOTIFICATIONS).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

    private fun ComposeUiTest.dotCount(): Int =
        onAllNodesWithTag(ProfilePopupTestTags.NOTIFICATIONS_UNREAD_DOT, useUnmergedTree = true)
            .fetchSemanticsNodes().size

    private fun ComposeUiTest.assertNotificationsBetween(aboveText: String) {
        val above = onNodeWithText(aboveText).getUnclippedBoundsInRoot()
        val notifications = onNodeWithTag(ProfilePopupTestTags.NOTIFICATIONS).getUnclippedBoundsInRoot()
        val history = onNodeWithText(runBlocking { getString(Lang.playback_history_title) })
            .getUnclippedBoundsInRoot()
        assertTrue(above.bottom <= notifications.top, "$aboveText should be above notifications")
        assertTrue(notifications.bottom <= history.top, "notifications should be above playback history")
    }

    @Test
    fun `logged-in users open notifications from the menu`() = runAniComposeUiTest {
        val clicks = Clicks()
        setPopup(TestAccountSettingsState, clicks)

        onNodeWithTag(ProfilePopupTestTags.NOTIFICATIONS).assertTextEquals(notificationsText)
        assertNotificationsBetween(runBlocking { getString(Lang.settings_account_popup_edit_profile) })

        onNodeWithTag(ProfilePopupTestTags.NOTIFICATIONS).performClick()
        runOnIdle {
            assertEquals(1, clicks.notifications)
            assertEquals(0, clicks.others)
        }
    }

    @Test
    fun `guests also open notifications from the menu`() = runAniComposeUiTest {
        val clicks = Clicks()
        setPopup(guestState, clicks)

        onNodeWithTag(ProfilePopupTestTags.NOTIFICATIONS).assertTextEquals(notificationsText)
        assertNotificationsBetween(runBlocking { getString(Lang.settings_account_popup_login_register) })

        onNodeWithTag(ProfilePopupTestTags.NOTIFICATIONS).performClick()
        runOnIdle {
            assertEquals(1, clicks.notifications)
            assertEquals(0, clicks.others)
        }
    }

    @Test
    fun `dot and unread announcement follow the flag`() = runAniComposeUiTest {
        var showDot by mutableStateOf(true)
        setPopup(TestAccountSettingsState, Clicks(), showDot = { showDot })

        assertEquals(1, dotCount())
        assertTrue(unreadText in notificationsDescriptions(), notificationsDescriptions().toString())

        runOnIdle { showDot = false }
        waitForIdle()
        assertEquals(0, dotCount())
        assertFalse(unreadText in notificationsDescriptions(), notificationsDescriptions().toString())
    }
}
