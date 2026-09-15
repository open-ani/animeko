/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.io.files.Path
import me.him188.ani.app.platform.Context
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.tools.update.InstallationFailureReason
import me.him188.ani.app.tools.update.InstallationResult
import me.him188.ani.app.tools.update.UpdateInstaller
import me.him188.ani.app.ui.foundation.DragAndDropContent
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(TestOnly::class)
class DropInstallPackageHostTest {
    private class FakeInstaller(
        private val result: InstallationResult = InstallationResult.Succeed,
    ) : UpdateInstaller {
        override val installablePackageExtensions: Set<String> = setOf("dmg")
        val installed = mutableListOf<SystemPath>()

        override fun install(file: SystemPath, context: Context): InstallationResult {
            installed += file
            return result
        }
    }

    private val droppedPackage = Path("/downloads/Ani-test.dmg")

    @Test
    fun `confirming the dialog installs the dropped package`() = runAniComposeUiTest {
        val installer = FakeInstaller()
        val state = DropInstallPackageState(installer)
        setContent {
            ProvideCompositionLocalsForPreview {
                DropInstallPackageHost(enabled = true, state = state) {
                    Text("content")
                }
            }
        }

        state.offer(DragAndDropContent.FileList(listOf(droppedPackage)))
        waitForIdle()

        onNodeWithTag(DropInstallPackageTestTags.CONFIRM_BUTTON).assertIsDisplayed().performClick()

        waitUntil { installer.installed == listOf(droppedPackage.inSystem) }
        assertNull(state.pendingPackage)
    }

    @Test
    fun `overlay shows the dragged package and disappears when the drag ends`() = runAniComposeUiTest {
        val state = DropInstallPackageState(FakeInstaller())
        setContent {
            ProvideCompositionLocalsForPreview {
                // 与桌面端主窗口一致, 覆盖层需要占满可用空间才能把卡片放在中间
                DropInstallPackageHost(enabled = true, modifier = Modifier.fillMaxSize(), state = state) {
                    Text("content")
                }
            }
        }
        onNodeWithTag(DropInstallPackageTestTags.OVERLAY).assertDoesNotExist()

        state.onDragStarted(DragAndDropContent.FileList(listOf(droppedPackage)))
        waitForIdle()

        onNodeWithTag(DropInstallPackageTestTags.OVERLAY).assertIsDisplayed()
        onNodeWithText("Ani-test.dmg").assertIsDisplayed()
        onNodeWithText("content").assertIsDisplayed()

        state.onDragStarted(DragAndDropContent.FileList(listOf(Path("/downloads/readme.txt"))))
        waitForIdle()
        onNodeWithText("readme.txt").assertIsDisplayed()

        state.onDragEnded()
        waitForIdle()
        onNodeWithTag(DropInstallPackageTestTags.OVERLAY).assertDoesNotExist()
        assertNull(state.pendingPackage)
    }

    @Test
    fun `cancelling the dialog does not install`() = runAniComposeUiTest {
        val installer = FakeInstaller()
        val state = DropInstallPackageState(installer)
        setContent {
            ProvideCompositionLocalsForPreview {
                DropInstallPackageHost(enabled = true, state = state) {
                    Text("content")
                }
            }
        }

        state.offer(DragAndDropContent.FileList(listOf(droppedPackage)))
        waitForIdle()

        onNodeWithTag(DropInstallPackageTestTags.CANCEL_BUTTON).performClick()
        waitForIdle()

        assertNull(state.pendingPackage)
        assertEquals(emptyList(), installer.installed)
        onNodeWithTag(DropInstallPackageTestTags.CONFIRM_BUTTON).assertDoesNotExist()
    }

    @Test
    fun `installation failure is shown and can be dismissed`() = runAniComposeUiTest {
        val installer = FakeInstaller(
            result = InstallationResult.Failed(InstallationFailureReason.UNSUPPORTED_FILE_STRUCTURE, "Not an app bundle"),
        )
        val state = DropInstallPackageState(installer)
        setContent {
            ProvideCompositionLocalsForPreview {
                DropInstallPackageHost(enabled = true, state = state) {
                    Text("content")
                }
            }
        }

        state.offer(DragAndDropContent.FileList(listOf(droppedPackage)))
        waitForIdle()
        onNodeWithTag(DropInstallPackageTestTags.CONFIRM_BUTTON).performClick()
        waitForIdle()

        onNodeWithText("Not an app bundle").assertIsDisplayed()
        onNodeWithTag(FailedToInstallDialogTestTags.DISMISS_BUTTON).performClick()
        waitForIdle()

        onNodeWithText("Not an app bundle").assertDoesNotExist()
    }
}
