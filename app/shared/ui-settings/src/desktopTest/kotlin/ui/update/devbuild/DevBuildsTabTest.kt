/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update.devbuild

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.resolve
import kotlin.test.Test
import kotlin.test.assertEquals

class DevBuildsTabTest {
    private val spec = DevBuildPackageSpec(listOf("ani-macos-dmg-aarch64"), DevBuildPackageKind.MACOS_DMG)

    @Test
    fun `lists commits and installs the selected one after confirmation`() = runAniComposeUiTest {
        val dir = SystemPaths.createTempDirectory("dev-builds-tab-test")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val installer = FakeInstaller()
            val client = fullGitHubMockClient("ani-macos-dmg-aarch64", zipBytes("Ani-4.12.0.dmg" to byteArrayOf(1)))
            val state = DevBuildsState(
                api = GitHubDevBuildApi(client),
                spec = spec,
                installer = installer,
                saveDir = dir.resolve("dev-builds"),
                getToken = { "token" },
                currentVersionName = "4.12.0-main-bbbbbbbb",
                backgroundScope = scope,
                installDispatcher = Dispatchers.Default,
            )
            setContent {
                ProvideCompositionLocalsForPreview {
                    DevBuildsTabContent(
                        state = state,
                        token = "token",
                        onTokenChange = {},
                        modifier = Modifier.fillMaxSize(),
                        currentVersion = "4.12.0-main-bbbbbbbb",
                    )
                }
            }

            // 列表由后台线程更新, waitForIdle 不能保证已经重组, 直接等待节点出现
            waitUntil(timeoutMillis = 10_000) {
                onAllNodesWithTag(DevBuildsTestTags.COMMIT_PREFIX + SHA_A).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(DevBuildsTestTags.COMMIT_PREFIX + SHA_A).assertIsDisplayed()
            onNodeWithTag(DevBuildsTestTags.INSTALL_BUTTON_PREFIX + SHA_A).assertIsEnabled()
            onNodeWithTag(DevBuildsTestTags.INSTALL_BUTTON_PREFIX + SHA_B).assertIsNotEnabled()

            onNodeWithTag(DevBuildsTestTags.INSTALL_BUTTON_PREFIX + SHA_A).performClick()
            waitForIdle()
            onNodeWithTag(DevBuildsTestTags.CONFIRM_CANCEL_BUTTON).assertIsDisplayed().performClick()
            waitForIdle()
            onNodeWithTag(DevBuildsTestTags.CONFIRM_BUTTON).assertDoesNotExist()
            assertEquals(emptyList(), installer.installed)

            onNodeWithTag(DevBuildsTestTags.INSTALL_BUTTON_PREFIX + SHA_A).performClick()
            waitForIdle()
            onNodeWithTag(DevBuildsTestTags.CONFIRM_BUTTON).assertIsDisplayed().performClick()

            waitUntil(timeoutMillis = 10_000) { installer.installed.isNotEmpty() }
            assertEquals(listOf(dir.resolve("dev-builds").resolve("ani-main-aaaaaaaa.dmg")), installer.installed)
        } finally {
            scope.cancel()
            dir.deleteRecursively()
        }
    }
}
