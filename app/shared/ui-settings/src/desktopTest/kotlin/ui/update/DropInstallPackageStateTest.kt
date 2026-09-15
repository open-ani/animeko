/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import me.him188.ani.app.platform.Context
import me.him188.ani.app.tools.update.InstallationFailureReason
import me.him188.ani.app.tools.update.InstallationResult
import me.him188.ani.app.tools.update.UpdateInstallationState
import me.him188.ani.app.tools.update.UpdateInstaller
import me.him188.ani.app.ui.foundation.DragAndDropContent
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.inSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DropInstallPackageStateTest {
    private class FakeInstaller(
        override val installablePackageExtensions: Set<String> = setOf("dmg", "zip"),
        private val result: InstallationResult = InstallationResult.Succeed,
        private val beforeReturn: suspend () -> Unit = {},
    ) : UpdateInstaller {
        val installed = mutableListOf<SystemPath>()

        override fun install(file: SystemPath, context: Context): InstallationResult = result

        override suspend fun install(
            file: SystemPath,
            packageUrls: List<String>,
            context: Context,
        ): InstallationResult {
            installed += file
            beforeReturn()
            return result
        }
    }

    private val context = object : Context() {}

    @Test
    fun `ignores plain text and empty file lists`() {
        val state = DropInstallPackageState(FakeInstaller())

        assertEquals(DropInstallPackageOutcome.IGNORED, state.offer(DragAndDropContent.PlainText("hello")))
        assertEquals(DropInstallPackageOutcome.IGNORED, state.offer(DragAndDropContent.FileList(emptyList())))
        assertEquals(DropInstallPackageOutcome.IGNORED, state.offer(DragAndDropContent.Unsupported))
        assertNull(state.pendingPackage)
    }

    @Test
    fun `rejects files that are not installable packages`() {
        val state = DropInstallPackageState(FakeInstaller())

        assertEquals(
            DropInstallPackageOutcome.UNSUPPORTED,
            state.offer(DragAndDropContent.FileList(listOf(Path("/downloads/ani.exe"), Path("/downloads/readme.txt")))),
        )
        assertNull(state.pendingPackage)
    }

    @Test
    fun `records the first installable package as pending`() {
        val state = DropInstallPackageState(FakeInstaller())

        assertEquals(
            DropInstallPackageOutcome.PENDING_CONFIRMATION,
            state.offer(
                DragAndDropContent.FileList(
                    listOf(Path("/downloads/notes.txt"), Path("/downloads/Ani-4.0.0.DMG"), Path("/downloads/b.zip")),
                ),
            ),
        )
        assertEquals(Path("/downloads/Ani-4.0.0.DMG").inSystem, state.pendingPackage)

        state.dismissPending()
        assertNull(state.pendingPackage)
    }

    @Test
    fun `installs the pending package and clears it`() = runTest {
        val installer = FakeInstaller()
        val state = DropInstallPackageState(installer)
        state.offer(DragAndDropContent.FileList(listOf(Path("/downloads/ani.zip"))))

        state.installPending(context)

        assertEquals(listOf(Path("/downloads/ani.zip").inSystem), installer.installed)
        assertNull(state.pendingPackage)
        assertEquals(Path("/downloads/ani.zip").inSystem, state.lastInstalledPackage)
        assertEquals(UpdateInstallationState.Succeed, state.installationState.value)
    }

    @Test
    fun `does nothing when there is no pending package`() = runTest {
        val installer = FakeInstaller()
        val state = DropInstallPackageState(installer)

        state.installPending(context)

        assertEquals(emptyList(), installer.installed)
        assertEquals(UpdateInstallationState.Idle, state.installationState.value)
    }

    @Test
    fun `exposes installation failure and dismisses it`() = runTest {
        val failure = InstallationResult.Failed(InstallationFailureReason.UNSUPPORTED_FILE_STRUCTURE, "not an app")
        val state = DropInstallPackageState(FakeInstaller(result = failure))
        state.offer(DragAndDropContent.FileList(listOf(Path("/downloads/ani.dmg"))))

        state.installPending(context)

        assertEquals(failure, assertIs<UpdateInstallationState.Failed>(state.installationState.value).result)
        assertEquals(Path("/downloads/ani.dmg").inSystem, state.lastInstalledPackage)

        state.dismissFailure()
        assertEquals(UpdateInstallationState.Idle, state.installationState.value)
    }

    @Test
    fun `drag preview classifies the dragged content`() {
        val state = DropInstallPackageState(FakeInstaller())

        state.onDragStarted(DragAndDropContent.FileList(listOf(Path("/downloads/notes.txt"), Path("/downloads/ani.dmg"))))
        assertEquals(DraggingPackage.Installable(Path("/downloads/ani.dmg").inSystem), state.dragging)

        state.onDragStarted(DragAndDropContent.FileList(listOf(Path("/downloads/readme.txt"), Path("/downloads/ani.exe"))))
        assertEquals(DraggingPackage.Unsupported("readme.txt"), state.dragging)

        state.onDragStarted(null)
        assertEquals(DraggingPackage.Unknown, state.dragging)

        state.onDragStarted(DragAndDropContent.PlainText("hello"))
        assertNull(state.dragging)

        state.onDragStarted(DragAndDropContent.FileList(listOf(Path("/downloads/ani.zip"))))
        state.onDragEnded()
        assertNull(state.dragging)
    }

    @Test
    fun `parses the version from the package file name`() {
        assertEquals("4.12.0", parsePackageVersion("Ani-4.12.0-macos-aarch64.dmg"))
        assertEquals("4.12.0-beta02", parsePackageVersion("ani-4.12.0-beta02-windows-x86_64.zip"))
        assertEquals("4.12.0-alpha01", parsePackageVersion("Ani-4.12.0-alpha01.dmg"))
        assertNull(parsePackageVersion("package.dmg"))
    }

    @Test
    fun `ignores drops while an installation is running`() = runTest {
        val installationStarted = CompletableDeferred<Unit>()
        val finishInstallation = CompletableDeferred<Unit>()
        val state = DropInstallPackageState(
            FakeInstaller(
                beforeReturn = {
                    installationStarted.complete(Unit)
                    finishInstallation.await()
                },
            ),
        )
        state.offer(DragAndDropContent.FileList(listOf(Path("/downloads/ani.dmg"))))
        val installation = async { state.installPending(context) }
        installationStarted.await()

        assertEquals(
            DropInstallPackageOutcome.IGNORED,
            state.offer(DragAndDropContent.FileList(listOf(Path("/downloads/other.dmg")))),
        )
        assertNull(state.pendingPackage)
        state.onDragStarted(DragAndDropContent.FileList(listOf(Path("/downloads/other.dmg"))))
        assertNull(state.dragging)

        finishInstallation.complete(Unit)
        installation.await()
    }
}
