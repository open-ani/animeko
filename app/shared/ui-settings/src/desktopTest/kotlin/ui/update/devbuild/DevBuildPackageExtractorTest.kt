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
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.readBytes
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.toFile
import me.him188.ani.utils.io.writeBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DevBuildPackageExtractorTest {
    private inline fun withTempDir(block: (SystemPath) -> Unit) {
        val dir = SystemPaths.createTempDirectory("dev-build-extractor-test")
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `extracts the first entry with the extension ignoring case and directories`() = runTest {
        withTempDir { dir ->
            val dmg = byteArrayOf(1, 2, 3)
            val archive = dir.resolve("artifact.zip")
            archive.writeBytes(
                zipBytes(
                    "readme.txt" to byteArrayOf(9),
                    "nested.DMG/" to byteArrayOf(),
                    "Ani-4.12.0.DMG" to dmg,
                    "other.dmg" to byteArrayOf(4),
                ),
            )
            val target = dir.resolve("out.dmg")
            assertTrue(extractZipEntryByExtension(archive, "dmg", target))
            assertContentEquals(dmg, target.readBytes())
        }
    }

    @Test
    fun `returns false without creating the target when nothing matches`() = runTest {
        withTempDir { dir ->
            val archive = dir.resolve("artifact.zip")
            archive.writeBytes(zipBytes("readme.txt" to byteArrayOf(9)))
            val target = dir.resolve("out.apk")
            assertFalse(extractZipEntryByExtension(archive, "apk", target))
            assertFalse(target.exists())
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
