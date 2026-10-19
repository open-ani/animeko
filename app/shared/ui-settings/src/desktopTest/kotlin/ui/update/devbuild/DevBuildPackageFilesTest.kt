/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update.devbuild

import kotlinx.coroutines.test.runTest
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.toFile
import me.him188.ani.utils.io.writeBytes
import kotlin.test.Test
import kotlin.test.assertTrue

class DevBuildPackageFilesTest {
    private inline fun withTempDir(block: (SystemPath) -> Unit) {
        val dir = SystemPaths.createTempDirectory("dev-build-package-files-test")
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `marks the file executable`() = runTest {
        withTempDir { dir ->
            val file = dir.resolve("Animeko.AppImage")
            file.writeBytes(byteArrayOf(1))
            file.toFile().setExecutable(false, false)
            markExecutable(file)
            assertTrue(file.toFile().canExecute())
        }
    }
}
