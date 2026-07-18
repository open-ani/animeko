/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.anitorrent

import me.him188.ani.utils.platform.Arch
import me.him188.ani.utils.platform.Platform
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AnitorrentLibraryLoaderTest {
    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `can load in debug mode on Windows`() { // 注意, 这个 test 无法测试打包之后的
        assertDoesNotThrow {
            AnitorrentLibraryLoader.loadLibraries()
        }
    }

    @Test
    @EnabledOnOs(OS.MAC)
    fun `can load in debug mode when the working directory is macOS root`() {
        val originalWorkingDirectory = System.getProperty("user.dir")
        try {
            System.setProperty("user.dir", "/")
            assertDoesNotThrow {
                AnitorrentLibraryLoader.loadLibraries()
            }
        } finally {
            System.setProperty("user.dir", originalWorkingDirectory)
        }
    }

    @Test
    @EnabledOnOs(OS.MAC)
    fun `uses a writable temp directory instead of the working directory on macOS`() {
        val workingDirectory = Paths.get("/")
        val tempDirectory = AnitorrentLibraryLoader.getTempDirForPlatform(
            Platform.MacOS(Arch.AARCH64),
            workingDirectory,
        )

        try {
            assertNotEquals(workingDirectory, tempDirectory)
            assertTrue(tempDirectory.startsWith(Paths.get(System.getProperty("java.io.tmpdir"))))
            assertTrue(Files.isWritable(tempDirectory))
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }
}
