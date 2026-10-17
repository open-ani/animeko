/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

@EnabledOnOs(OS.MAC)
class MacOSUpdateInstallerTest {
    private val tempDir = createTempDirectory("macos-update-installer-test").toFile()
    private val appsDir = tempDir.resolve("Applications").apply { mkdirs() }
    private val targetApp = appsDir.resolve("Ani.app")
    private val openLog = tempDir.resolve("open.log")

    @Test
    fun `update replaces the whole app bundle and launches it`() {
        createSignedApp(targetApp, version = "old")
        targetApp.resolve("Contents/Resources/only-in-old-version").apply {
            parentFile.mkdirs()
            writeText("stale")
        }
        val oldExecutableKey = targetApp.resolve("Contents/MacOS/Ani").fileKey()
        val zip = createZipPackage { createSignedApp(it, version = "new") }

        val process = runUpdateScript(zip)

        assertEquals(0, process.exitValue(), process.errorStream.bufferedReader().readText())
        assertEquals("new", targetApp.resolve("Contents/Resources/version").readText())
        assertFalse(targetApp.resolve("Contents/Resources/only-in-old-version").exists())
        // 可执行文件必须是新的 inode; 原地覆盖会让内核按旧签名校验, 新版本启动即被杀死.
        assertNotEquals(oldExecutableKey, targetApp.resolve("Contents/MacOS/Ani").fileKey())
        assertTrue(codesignVerify(targetApp))
        assertEquals(listOf("Ani.app"), appsDir.list().orEmpty().toList())
        assertEquals(listOf(targetApp.absolutePath), openLog.readLines())
    }

    @Test
    fun `update with invalid signature keeps and relaunches the current app`() {
        createSignedApp(targetApp, version = "old")
        val zip = createZipPackage {
            createSignedApp(it, version = "new")
            it.resolve("Contents/Resources/version").writeText("tampered")
        }

        val process = runUpdateScript(zip)

        assertNotEquals(0, process.exitValue())
        assertEquals("old", targetApp.resolve("Contents/Resources/version").readText())
        assertTrue(codesignVerify(targetApp))
        assertEquals(listOf("Ani.app"), appsDir.list().orEmpty().toList())
        assertEquals(listOf(targetApp.absolutePath), openLog.readLines())
    }

    private fun runUpdateScript(packageFile: File): Process {
        val script = tempDir.resolve("macos-update.command").apply {
            writeText(MACOS_UPDATE_SCRIPT)
            assertTrue(setExecutable(true))
        }
        val fakeBin = tempDir.resolve("bin").apply { mkdirs() }
        fakeBin.resolve("open").apply {
            writeText(
                """
                    #!/usr/bin/env bash
                    echo "${'$'}1" >> "${openLog.absolutePath}"
                """.trimIndent(),
            )
            assertTrue(setExecutable(true))
        }
        val exitedPid = ProcessBuilder("true").start().also { it.waitFor() }.pid()
        val workDir = tempDir.resolve("work").apply { mkdirs() }

        val process = ProcessBuilder(
            script.absolutePath,
            exitedPid.toString(),
            packageFile.absolutePath,
            targetApp.absolutePath,
            workDir.absolutePath,
        ).apply {
            environment()["PATH"] = "${fakeBin.absolutePath}:${environment()["PATH"]}"
        }.start()
        assertTrue(process.waitFor(60, TimeUnit.SECONDS))
        return process
    }

    private fun createZipPackage(createApp: (File) -> Unit): File {
        val packageDir = tempDir.resolve("package").apply { mkdirs() }
        val app = packageDir.resolve("Ani.app")
        createApp(app)
        val zip = tempDir.resolve("Ani.zip")
        exec("ditto", "-c", "-k", "--keepParent", app.absolutePath, zip.absolutePath)
        return zip
    }

    private fun createSignedApp(app: File, version: String) {
        val executable = app.resolve("Contents/MacOS/Ani")
        executable.parentFile.mkdirs()
        File("/usr/bin/true").copyTo(executable)
        assertTrue(executable.setExecutable(true))
        app.resolve("Contents/Info.plist").writeText(
            """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>CFBundleExecutable</key>
                    <string>Ani</string>
                    <key>CFBundleIdentifier</key>
                    <string>me.him188.ani.test</string>
                </dict>
                </plist>
            """.trimIndent(),
        )
        app.resolve("Contents/Resources").mkdirs()
        app.resolve("Contents/Resources/version").writeText(version)
        exec("codesign", "--force", "--sign", "-", app.absolutePath)
    }

    private fun codesignVerify(app: File): Boolean =
        ProcessBuilder("codesign", "--verify", "--deep", "--strict", app.absolutePath).start().waitFor() == 0

    private fun File.fileKey(): Any = Files.readAttributes(toPath(), BasicFileAttributes::class.java).fileKey()

    private fun exec(vararg command: String) {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(30, TimeUnit.SECONDS))
        assertEquals(0, process.exitValue(), output)
    }
}
