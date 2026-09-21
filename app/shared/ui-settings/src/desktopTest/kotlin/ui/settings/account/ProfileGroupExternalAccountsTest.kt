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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.him188.ani.app.data.models.user.ExternalAccount
import me.him188.ani.app.data.models.user.SelfInfo
import me.him188.ani.app.domain.session.auth.OAuthPlatform
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.user.SelfInfoUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * 设置页 "第三方账号" 中除 Bangumi 以外的平台的展示、绑定入口与解绑.
 */
class ProfileGroupExternalAccountsTest {
    private class Callbacks {
        val bindClicks = mutableListOf<OAuthPlatform>()
        val unbinds = mutableListOf<String>()
        var githubAccountClicks = 0
    }

    private fun selfInfo(vararg accounts: ExternalAccount) = SelfInfo(
        id = Uuid.parse("0e4b1e4a-6c1e-4f3f-9d0d-1b2c3d4e5f60"),
        nickname = "nick",
        email = "a@example.com",
        hasPassword = false,
        avatarUrl = null,
        bangumiUsername = null,
        externalAccounts = accounts.toList(),
    )

    private fun state(selfInfo: SelfInfo, platforms: List<OAuthPlatform>) = AccountSettingsState(
        selfInfo = SelfInfoUiState(selfInfo, isLoading = false, isSessionValid = true, bangumiConnected = false),
        boundBangumi = false,
        avatarUploadState = EditProfileState.UploadAvatarState.Default,
        externalPlatforms = platforms,
    )

    private fun me.him188.ani.app.ui.framework.AniComposeUiTest.render(state: AccountSettingsState, callbacks: Callbacks) {
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
                        onNavigateToEmail = {},
                        onBangumiClick = {},
                        onUnbindBangumi = {},
                        onExternalAccountClick = { callbacks.bindClicks += it },
                        onGithubAccountClick = { callbacks.githubAccountClicks++ },
                        onUnbindExternalAccount = { callbacks.unbinds += it },
                    )
                }
            }
        }
    }

    @Test
    fun `bound account shows its username and unbinds after confirmation`() = runAniComposeUiTest {
        val callbacks = Callbacks()
        render(state(selfInfo(ExternalAccount("github", "octocat")), listOf(OAuthPlatform.GITHUB)), callbacks)

        onNodeWithTag("externalAccount-github").assertIsDisplayed()
        onNodeWithText("octocat", substring = true).assertIsDisplayed()
        onNodeWithTag("externalAccount-github-unbind").assertIsDisplayed().performClick()
        waitForIdle()
        assertEquals(emptyList(), callbacks.unbinds) // 需要确认

        onNodeWithTag("unbindExternalAccountConfirm").assertIsDisplayed().performClick()
        waitForIdle()

        assertEquals(listOf("github"), callbacks.unbinds)
        assertEquals(emptyList(), callbacks.bindClicks)
    }

    @Test
    fun `bound github account navigates to its details when clicked`() = runAniComposeUiTest {
        val callbacks = Callbacks()
        render(state(selfInfo(ExternalAccount("github", "octocat")), listOf(OAuthPlatform.GITHUB)), callbacks)

        onNodeWithTag("externalAccount-github").assertIsDisplayed().performClick()
        waitForIdle()

        assertEquals(1, callbacks.githubAccountClicks)
        assertEquals(emptyList(), callbacks.bindClicks)
    }

    @Test
    fun `enabled but unbound platform navigates to binding when clicked`() = runAniComposeUiTest {
        val callbacks = Callbacks()
        render(state(selfInfo(), listOf(OAuthPlatform.GITHUB)), callbacks)

        onNodeWithTag("externalAccount-github-unbind").assertDoesNotExist()
        onNodeWithTag("externalAccount-github").assertIsDisplayed().performClick()
        waitForIdle()

        assertEquals(listOf(OAuthPlatform.GITHUB), callbacks.bindClicks)
        assertEquals(0, callbacks.githubAccountClicks)
    }

    @Test
    fun `platform disabled on the server is hidden unless bound`() = runAniComposeUiTest {
        val callbacks = Callbacks()
        render(state(selfInfo(), emptyList()), callbacks)
        onNodeWithTag("externalAccount-github").assertDoesNotExist()
    }

    @Test
    fun `bound account of a disabled or unknown platform can still be unbound`() = runAniComposeUiTest {
        val callbacks = Callbacks()
        render(
            state(selfInfo(ExternalAccount("github", "octocat"), ExternalAccount("google", "g@example.com")), emptyList()),
            callbacks,
        )

        onNodeWithTag("externalAccount-github-unbind").assertIsDisplayed()
        // 客户端不认识的平台以 ID 展示
        onNodeWithTag("externalAccount-google").assertIsDisplayed()
        onNodeWithText("google", substring = true).assertIsDisplayed()
        onNodeWithTag("externalAccount-google-unbind").assertIsDisplayed().performClick()
        waitForIdle()
        onNodeWithTag("unbindExternalAccountConfirm").performClick()
        waitForIdle()

        assertEquals(listOf("google"), callbacks.unbinds)
    }
}
