/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import me.him188.ani.app.data.models.user.ExternalAccount
import me.him188.ani.app.data.models.user.SelfInfo
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.user.SelfInfoUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * 设置页邮箱一栏的绑定、换绑与解绑入口. 邮箱是唯一登录方式时不能解绑.
 */
class ProfileGroupEmailTest {
    private class Callbacks {
        var navigateToEmail = 0
        var unbindEmail = 0
    }

    private fun selfInfo(
        email: String?,
        bangumiUsername: String? = null,
        vararg accounts: ExternalAccount,
    ) = SelfInfo(
        id = Uuid.parse("0e4b1e4a-6c1e-4f3f-9d0d-1b2c3d4e5f60"),
        nickname = "nick",
        email = email,
        hasPassword = false,
        avatarUrl = null,
        bangumiUsername = bangumiUsername,
        externalAccounts = accounts.toList(),
    )

    private fun state(selfInfo: SelfInfo) = AccountSettingsState(
        selfInfo = SelfInfoUiState(selfInfo, isLoading = false, isSessionValid = true, bangumiConnected = false),
        boundBangumi = false,
        avatarUploadState = EditProfileState.UploadAvatarState.Default,
    )

    private fun AniComposeUiTest.render(state: AccountSettingsState, callbacks: Callbacks) {
        setContent {
            ProvideCompositionLocalsForPreview {
                SettingsTab {
                    ProfileGroupImpl(
                        state,
                        isNicknameErrorProvider = { false },
                        onSaveNickname = {},
                        onAvatarUpload = { true },
                        onAvatarUploadBytes = { true },
                        onResetAvatarUploadState = {},
                        onLogout = {},
                        onNavigateToEmail = { callbacks.navigateToEmail++ },
                        onBangumiClick = {},
                        onUnbindBangumi = {},
                        onExternalAccountClick = {},
                        onGithubAccountClick = {},
                        onUnbindExternalAccount = {},
                        onUnbindEmail = { callbacks.unbindEmail++ },
                    )
                }
            }
        }
    }

    @Test
    fun `unbound email navigates to binding and has no unbind button`() = runAniComposeUiTest {
        val callbacks = Callbacks()
        render(state(selfInfo(email = null, bangumiUsername = "bgm")), callbacks)

        onNodeWithTag("email-unbind").assertDoesNotExist()
        onNodeWithTag("email").assertIsDisplayed().performClick()
        waitForIdle()
        assertEquals(1, callbacks.navigateToEmail)
    }

    @Test
    fun `bound email navigates to rebinding when clicked`() = runAniComposeUiTest {
        val callbacks = Callbacks()
        render(state(selfInfo(email = "a@example.com")), callbacks)

        onNodeWithTag("email").assertIsDisplayed().performClick()
        waitForIdle()
        assertEquals(1, callbacks.navigateToEmail)

        onNodeWithTag("email-edit").assertIsDisplayed().performClick()
        waitForIdle()
        assertEquals(2, callbacks.navigateToEmail)
        assertEquals(0, callbacks.unbindEmail)
    }

    @Test
    fun `email unbinds after confirmation when bangumi is bound`() = runAniComposeUiTest {
        val callbacks = Callbacks()
        render(state(selfInfo(email = "a@example.com", bangumiUsername = "bgm")), callbacks)

        onNodeWithTag("email-unbind").assertIsDisplayed().performClick()
        waitForIdle()
        assertEquals(0, callbacks.unbindEmail) // 需要确认
        onNodeWithTag("unbindEmailLastLoginMethod").assertDoesNotExist()

        onNodeWithTag("unbindEmailConfirm").assertIsDisplayed().performClick()
        waitForIdle()
        assertEquals(1, callbacks.unbindEmail)
        assertEquals(0, callbacks.navigateToEmail)
    }

    @Test
    fun `email unbinds after confirmation when an external account is bound`() = runAniComposeUiTest {
        val callbacks = Callbacks()
        render(state(selfInfo(email = "a@example.com", null, ExternalAccount("github", "octocat"))), callbacks)

        onNodeWithTag("email-unbind").assertIsDisplayed().performClick()
        waitForIdle()
        onNodeWithTag("unbindEmailConfirm").assertIsDisplayed().performClick()
        waitForIdle()
        assertEquals(1, callbacks.unbindEmail)
    }

    @Test
    fun `email that is the only login method cannot be unbound`() = runAniComposeUiTest {
        val callbacks = Callbacks()
        render(state(selfInfo(email = "a@example.com")), callbacks)

        onNodeWithTag("email-unbind").assertIsDisplayed().performClick()
        waitForIdle()

        onNodeWithTag("unbindEmailLastLoginMethod").assertIsDisplayed()
        onNodeWithTag("unbindEmailConfirm").assertDoesNotExist()
        assertEquals(0, callbacks.unbindEmail)
    }
}
