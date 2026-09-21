/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.him188.ani.app.data.repository.user.DeveloperVerificationInfo
import me.him188.ani.app.data.repository.user.DeveloperVerificationRequestInfo
import me.him188.ani.app.data.repository.user.DeveloperVerificationRequestStatus
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GithubAccountTabTest {
    private class Callbacks {
        var applies = 0
        var retries = 0
    }

    private fun info(
        isDeveloper: Boolean = false,
        latestRequest: DeveloperVerificationRequestInfo? = null,
        nextApplyAt: Long? = null,
        enabled: Boolean = true,
    ) = DeveloperVerificationInfo(
        enabled = enabled,
        isDeveloper = isDeveloper,
        validUntil = if (isDeveloper) 1_800_000_000_000 else null,
        latestRequest = latestRequest,
        nextApplyAt = nextApplyAt,
    )

    private fun request(status: DeveloperVerificationRequestStatus, message: String? = null, url: String? = null) =
        DeveloperVerificationRequestInfo(status, createdAt = 1_790_000_000_000, message = message, pullRequestUrl = url)

    private fun AniComposeUiTest.render(
        verification: DeveloperVerificationInfo?,
        callbacks: Callbacks = Callbacks(),
        loadFailed: Boolean = false,
    ) {
        setContent {
            ProvideCompositionLocalsForPreview {
                GithubAccountTabImpl(
                    GithubAccountUiState(username = "octocat", verification = verification, loadFailed = loadFailed),
                    onApply = { callbacks.applies++ },
                    onRetry = { callbacks.retries++ },
                    isApplying = false,
                )
            }
        }
    }

    @Test
    fun `user who never applied can apply`() = runAniComposeUiTest {
        val callbacks = Callbacks()
        render(info(), callbacks)

        onNodeWithText("@octocat").assertIsDisplayed()
        onNodeWithTag("developerVerification-lastResult").assertDoesNotExist()
        onNodeWithTag("developerVerification-apply").assertIsEnabled().performClick()
        waitForIdle()

        assertEquals(1, callbacks.applies)
    }

    @Test
    fun `pending request shows progress instead of the apply button`() = runAniComposeUiTest {
        render(info(latestRequest = request(DeveloperVerificationRequestStatus.PENDING)))

        onNodeWithTag("developerVerification-status").assertIsDisplayed()
        onNodeWithTag("developerVerification-apply").assertDoesNotExist()
        onNodeWithTag("developerVerification-lastResult").assertDoesNotExist()
    }

    @Test
    fun `rejected request hides the server's reason and blocks applying until the limit resets`() = runAniComposeUiTest {
        render(
            info(
                latestRequest = request(DeveloperVerificationRequestStatus.REJECTED, message = "PR #20: 仅修复了拼写"),
                nextApplyAt = 1_790_086_400_000,
            ),
        )

        // 只告知暂未通过, 不展示对用户 PR 的评价
        onNodeWithTag("developerVerification-lastResult").assertIsDisplayed()
        onNodeWithText("仅修复了拼写", substring = true).assertDoesNotExist()
        onNodeWithTag("developerVerification-apply").assertIsNotEnabled()
        onNodeWithTag("developerVerification-limit").assertIsDisplayed()
    }

    @Test
    fun `certified developer sees the validity and cannot apply`() = runAniComposeUiTest {
        val callbacks = Callbacks()
        render(
            info(
                isDeveloper = true,
                latestRequest = request(
                    DeveloperVerificationRequestStatus.APPROVED,
                    url = "https://github.com/open-ani/animeko/pull/11",
                ),
            ),
            callbacks,
        )

        onNodeWithTag("developerVerification-validUntil").assertIsDisplayed()
        onNodeWithTag("developerVerification-apply").assertDoesNotExist()
        onNodeWithTag("developerVerification-limit").assertDoesNotExist()
    }

    @Test
    fun `load failure can be retried`() = runAniComposeUiTest {
        val callbacks = Callbacks()
        render(verification = null, callbacks, loadFailed = true)

        onNodeWithTag("developerVerification-loading").assertIsDisplayed().performClick()
        waitForIdle()

        assertEquals(1, callbacks.retries)
    }

    @Test
    fun `server without the feature hides the apply entry`() = runAniComposeUiTest {
        render(info(enabled = false))

        onNodeWithTag("developerVerification-status").assertDoesNotExist()
        onNodeWithTag("developerVerification-apply").assertDoesNotExist()
    }
}
