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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.resolve
import kotlin.test.Test
import kotlin.test.assertEquals

class DevBuildsTabTest {
    private val spec = DevBuildPackageSpec(listOf("ani-macos-dmg-aarch64"), DevBuildPackageKind.MACOS_DMG)

    private data class Fixture(val saveDir: SystemPath, val installer: FakeInstaller)

    /**
     * 在 [block] 中显示页面, 结束后回收临时目录和后台协程.
     */
    private fun withTab(
        client: HttpClient,
        block: AniComposeUiTest.(Fixture) -> Unit,
    ) = runAniComposeUiTest {
        val dir = SystemPaths.createTempDirectory("dev-builds-tab-test")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val fixture = Fixture(dir.resolve("dev-builds"), FakeInstaller())
            val state = DevBuildsState(
                api = GitHubDevBuildApi(client),
                spec = spec,
                installer = fixture.installer,
                saveDir = fixture.saveDir,
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
            block(fixture)
        } finally {
            scope.cancel()
            dir.deleteRecursively()
        }
    }

    @Test
    fun `lists commits and installs the selected one after confirmation`() = withTab(
        fullGitHubMockClient("ani-macos-dmg-aarch64", zipBytes("Ani-4.12.0.dmg" to byteArrayOf(1))),
    ) { (saveDir, installer) ->
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
        assertEquals(listOf(saveDir.resolve("ani-main-aaaaaaaa.dmg")), installer.installed)
    }

    @Test
    fun `looks up a pasted commit link and installs it from the result card`() = withTab(
        fullGitHubMockClient("ani-macos-dmg-aarch64", zipBytes("Ani-4.12.0.dmg" to byteArrayOf(1))),
    ) { (saveDir, installer) ->
        onNodeWithTag(DevBuildsTestTags.LOOKUP_BUTTON).assertIsNotEnabled()
        onNodeWithTag(DevBuildsTestTags.LOOKUP_FIELD).performTextInput("https://github.com/open-ani/animeko/commit/$SHA_A")
        onNodeWithTag(DevBuildsTestTags.LOOKUP_BUTTON).assertIsEnabled().performClick()

        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(DevBuildsTestTags.LOOKUP_RESULT).fetchSemanticsNodes().isNotEmpty()
        }
        val inResult = hasAnyAncestor(hasTestTag(DevBuildsTestTags.LOOKUP_RESULT))
        onNode(hasTestTag(DevBuildsTestTags.COMMIT_PREFIX + SHA_A) and inResult).assertIsDisplayed()
        onNode(hasTestTag(DevBuildsTestTags.INSTALL_BUTTON_PREFIX + SHA_A) and inResult).assertIsEnabled().performClick()
        waitForIdle()
        onNodeWithTag(DevBuildsTestTags.CONFIRM_BUTTON).assertIsDisplayed().performClick()

        waitUntil(timeoutMillis = 10_000) { installer.installed.isNotEmpty() }
        assertEquals(listOf(saveDir.resolve("ani-main-aaaaaaaa.dmg")), installer.installed)
    }

    @Test
    fun `shows an error for unrecognized input and clears it`() = withTab(
        fullGitHubMockClient("ani-macos-dmg-aarch64", zipBytes()),
    ) {
        onNodeWithTag(DevBuildsTestTags.LOOKUP_FIELD).performTextInput("what is this")
        onNodeWithTag(DevBuildsTestTags.LOOKUP_BUTTON).performClick()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(DevBuildsTestTags.LOOKUP_ERROR).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(DevBuildsTestTags.LOOKUP_CLEAR_BUTTON).performClick()
        waitForIdle()
        onNodeWithTag(DevBuildsTestTags.LOOKUP_ERROR).assertDoesNotExist()
    }

    @Test
    fun `installs a package from a direct link`() = withTab(
        gitHubMockClient { request ->
            when (request.url.host) {
                "example.com" -> respond(byteArrayOf(9), HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "1"))
                else -> fullGitHubMockHandler("ani-macos-dmg-aarch64", zipBytes())(request)
            }
        },
    ) { (saveDir, installer) ->
        onNodeWithTag(DevBuildsTestTags.LOOKUP_FIELD).performTextInput("https://example.com/dl/Ani-dev.dmg")
        onNodeWithTag(DevBuildsTestTags.LOOKUP_BUTTON).performClick()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(DevBuildsTestTags.PACKAGE_ROW).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(DevBuildsTestTags.PACKAGE_INSTALL_BUTTON).assertIsEnabled().performClick()
        waitForIdle()
        onNodeWithTag(DevBuildsTestTags.CONFIRM_BUTTON).assertIsDisplayed().performClick()

        waitUntil(timeoutMillis = 10_000) { installer.installed.isNotEmpty() }
        assertEquals(listOf(saveDir.resolve("Ani-dev.dmg")), installer.installed)
    }
}
