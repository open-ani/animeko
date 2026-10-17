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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.CompletableDeferred
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
 * 设置页的注销账号: 输入 yes 才能确认, 注销期间弹窗等待且不能关闭.
 */
class ProfileGroupDeleteAccountTest {
    private val selfInfo = SelfInfo(
        id = Uuid.parse("0e4b1e4a-6c1e-4f3f-9d0d-1b2c3d4e5f60"),
        nickname = "nick",
        email = "a@example.com",
        hasPassword = false,
        avatarUrl = null,
        bangumiUsername = null,
    )

    private fun state(isSessionValid: Boolean) = AccountSettingsState(
        selfInfo = SelfInfoUiState(
            selfInfo.takeIf { isSessionValid },
            isLoading = false,
            isSessionValid = isSessionValid,
            bangumiConnected = false,
        ),
        boundBangumi = false,
        avatarUploadState = EditProfileState.UploadAvatarState.Default,
    )

    private fun AniComposeUiTest.render(
        state: AccountSettingsState = state(isSessionValid = true),
        onDeleteAccount: suspend () -> Unit,
    ) {
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
                        onExternalAccountClick = {},
                        onGithubAccountClick = {},
                        onUnbindExternalAccount = {},
                        onUnbindEmail = {},
                        onDeleteAccount = onDeleteAccount,
                    )
                }
            }
        }
    }

    private fun AniComposeUiTest.openDialog() {
        onNodeWithTag("deleteAccount").assertIsDisplayed().performClick()
        waitForIdle()
    }

    @Test
    fun `entry is hidden when not logged in`() = runAniComposeUiTest {
        render(state(isSessionValid = false)) {}

        onNodeWithTag("deleteAccount").assertDoesNotExist()
    }

    @Test
    fun `confirm requires typing yes`() = runAniComposeUiTest {
        var deleted = 0
        render { deleted++ }
        openDialog()

        onNodeWithTag("deleteAccountConfirm").assertIsNotEnabled()
        onNodeWithTag("deleteAccountInput").performTextInput("no")
        waitForIdle()
        onNodeWithTag("deleteAccountConfirm").assertIsNotEnabled()

        // 手机键盘可能自动大写首字母或补空格
        onNodeWithTag("deleteAccountInput").performTextClearance()
        onNodeWithTag("deleteAccountInput").performTextInput("Yes ")
        waitForIdle()
        onNodeWithTag("deleteAccountConfirm").assertIsEnabled().performClick()
        waitForIdle()

        assertEquals(1, deleted)
        onNodeWithTag("deleteAccountInput").assertDoesNotExist()
    }

    @Test
    fun `dialog waits until deletion completes`() = runAniComposeUiTest {
        val deletion = CompletableDeferred<Unit>()
        render { deletion.await() }
        openDialog()

        onNodeWithTag("deleteAccountInput").performTextInput(DELETE_ACCOUNT_CONFIRMATION_TEXT)
        waitForIdle()
        onNodeWithTag("deleteAccountConfirm").performClick()
        waitForIdle()

        onNodeWithTag("deleteAccountProgress", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("deleteAccountConfirm").assertIsNotEnabled()
        onNodeWithTag("deleteAccountCancel").assertIsNotEnabled()

        deletion.complete(Unit)
        // 恢复的协程在 UI 线程上调度, waitForIdle 不等它
        waitUntil { onAllNodesWithTag("deleteAccountInput").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `failed deletion keeps the dialog open for retry`() = runAniComposeUiTest {
        var attempts = 0
        render {
            attempts++
            throw IllegalStateException("network")
        }
        openDialog()

        onNodeWithTag("deleteAccountInput").performTextInput(DELETE_ACCOUNT_CONFIRMATION_TEXT)
        waitForIdle()
        onNodeWithTag("deleteAccountConfirm").performClick()
        waitForIdle()

        assertEquals(1, attempts)
        onNodeWithTag("deleteAccountProgress", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("deleteAccountConfirm").assertIsEnabled()
    }

    @Test
    fun `cancel closes the dialog without deleting`() = runAniComposeUiTest {
        var deleted = 0
        render { deleted++ }
        openDialog()

        onNodeWithTag("deleteAccountCancel").performClick()
        waitForIdle()

        onNodeWithTag("deleteAccountInput").assertDoesNotExist()
        assertEquals(0, deleted)
    }
}
