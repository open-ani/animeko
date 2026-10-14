/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsUpdaterCommandTest {
    private val tempDir: File = Files.createTempDirectory("windows-updater-command-test").toFile()
    private val updater = tempDir.resolve("ani_update.exe")
    private val appDir = tempDir.resolve("Ani").apply { mkdirs() }

    @Test
    fun `staged directory is installed with --staging`() {
        val staging = tempDir.resolve("ani-6.4.0-windows-x86_64.zip.staging").apply { mkdirs() }
        assertEquals(
            listOf(
                "cmd", "/c", "start", "",
                updater.absolutePath,
                "--pid", "123",
                "--app-dir", appDir.absolutePath,
                "--staging", staging.absolutePath,
            ),
            WindowsUpdateInstaller.updaterCommand(updater, appDir, staging, pid = 123),
        )
    }

    @Test
    fun `zip package is installed with --archive`() {
        val zip = tempDir.resolve("ani-6.4.0-windows-x86_64.zip").apply { writeBytes(ByteArray(0)) }
        assertEquals(
            listOf(
                "cmd", "/c", "start", "",
                updater.absolutePath,
                "--pid", "123",
                "--app-dir", appDir.absolutePath,
                "--archive", zip.absolutePath,
            ),
            WindowsUpdateInstaller.updaterCommand(updater, appDir, zip, pid = 123),
        )
    }

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }
}
