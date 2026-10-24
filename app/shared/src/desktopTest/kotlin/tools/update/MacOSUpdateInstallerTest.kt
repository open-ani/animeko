/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun `prepared directory is moved into place and launched`() {
        createSignedApp(targetApp, version = "old")
        val stagingDir = createPreparedDirectory { createSignedApp(it, version = "new") }
        val stagedVersionKey = stagingDir.resolve("Ani.app/Contents/Resources/version").fileKey()
        val process = runUpdateScript(stagingDir)
        assertEquals(0, process.exitValue(), process.errorStream.bufferedReader().readText())
        assertEquals("new", targetApp.resolve("Contents/Resources/version").readText())
        // 移动而不是复制: 文件还是原来的 inode.
        assertEquals(stagedVersionKey, targetApp.resolve("Contents/Resources/version").fileKey())
        assertFalse(stagingDir.exists(), "the emptied staging directory should be removed")
        assertTrue(codesignVerify(targetApp))
        assertEquals(listOf("Ani.app"), appsDir.list().orEmpty().toList())
        assertEquals(listOf(targetApp.absolutePath), openLog.readLines())
    }

    @Test
    fun `prepared directory without a launcher keeps and relaunches the current app`() {
        createSignedApp(targetApp, version = "old")
        val stagingDir = createPreparedDirectory {
            createSignedApp(it, version = "new")
            assertTrue(it.resolve("Contents/MacOS/Ani").delete())
        }
        val process = runUpdateScript(stagingDir)
        assertNotEquals(0, process.exitValue())
        assertEquals("old", targetApp.resolve("Contents/Resources/version").readText())
        assertEquals(listOf("Ani.app"), appsDir.list().orEmpty().toList())
        assertEquals(listOf(targetApp.absolutePath), openLog.readLines())
    }

    @Test
    fun `preparing a zip extracts and verifies the app`() {
        val zip = createReleaseShapedZipPackage { createSignedApp(it, version = "new") }
        val staging = runBlocking { DesktopPackagePreparation.prepareMacPackage(zip, "Ani.app") }
        assertEquals(File(zip.path + ".staging"), staging)
        val app = staging.resolve("Ani.app")
        assertEquals("new", app.resolve("Contents/Resources/version").readText())
        assertTrue(app.resolve("Contents/MacOS/Ani").canExecute())
        assertTrue(codesignVerify(app))
        assertFalse(File(staging.path + ".part").exists())
    }

    @Test
    fun `preparing a ditto zip extracts and verifies the app`() {
        val zip = createZipPackage { createSignedApp(it, version = "new") }
        val staging = runBlocking { DesktopPackagePreparation.prepareMacPackage(zip, "Ani.app") }
        val app = staging.resolve("Ani.app")
        assertTrue(app.resolve("Contents/MacOS/Ani").canExecute())
        assertTrue(codesignVerify(app))
    }

    @Test
    fun `preparing a zip with an invalid signature fails`() {
        val zip = createReleaseShapedZipPackage {
            createSignedApp(it, version = "new")
            it.resolve("Contents/Resources/version").writeText("tampered")
        }
        assertFailsWith<PackagePreparationException> {
            runBlocking { DesktopPackagePreparation.prepareMacPackage(zip, "Ani.app") }
        }
        assertFalse(File(zip.path + ".staging").exists())
        assertFalse(File(zip.path + ".staging.part").exists())
    }

    @Test
    fun `preparing a zip without the app fails`() {
        val zip = createZipPackage { createSignedApp(it, version = "new") }
        assertFailsWith<PackagePreparationException> {
            runBlocking { DesktopPackagePreparation.prepareMacPackage(zip, "Other.app") }
        }
    }

    /**
     * 应用在下载阶段准备好的目录: `<包名>.staging/Ani.app`.
     */
    private fun createPreparedDirectory(createApp: (File) -> Unit): File {
        val stagingDir = tempDir.resolve("updates/Ani.zip.staging").apply { mkdirs() }
        createApp(stagingDir.resolve("Ani.app"))
        return stagingDir
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

    /**
     * 与 Gradle 打的发布包同形的 zip: 顶层是 `Ani.app`, 外部属性里只有 unix 权限位 (没有文件类型位), 主机系统 UNIX.
     * `ditto -x -k` 不认这样的权限, 解出来的启动器不可执行; 解压工具必须按权限位恢复.
     */
    private fun createReleaseShapedZipPackage(createApp: (File) -> Unit): File {
        val packageDir = tempDir.resolve("package").apply { mkdirs() }
        val app = packageDir.resolve("Ani.app")
        createApp(app)
        val zip = tempDir.resolve("Ani.zip")
        val modes = LinkedHashMap<String, Int>()
        ZipOutputStream(zip.outputStream()).use { out ->
            for (file in app.walkTopDown().sortedBy { it.path }) {
                val relative = file.relativeTo(packageDir).invariantSeparatorsPath
                val name = if (file.isDirectory) "$relative/" else relative
                modes[name] = when {
                    file.isDirectory -> "755".toInt(8)
                    file.canExecute() -> "755".toInt(8)
                    else -> "644".toInt(8)
                }
                out.putNextEntry(ZipEntry(name))
                if (file.isFile) file.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        patchCentralDirectory(zip, modes)
        return zip
    }

    /**
     * 把 central directory 里每个 entry 的 "version made by" 改成 UNIX, 外部属性改成 `mode shl 16`.
     */
    private fun patchCentralDirectory(zip: File, modes: Map<String, Int>) {
        RandomAccessFile(zip, "rw").use { file ->
            val bytes = ByteArray(file.length().toInt())
            file.readFully(bytes)
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var eocd = bytes.size - 22
            while (buf.getInt(eocd) != 0x06054b50) eocd--
            var position = buf.getInt(eocd + 16)
            val count = buf.getShort(eocd + 10).toInt() and 0xFFFF
            repeat(count) {
                check(buf.getInt(position) == 0x02014b50)
                val nameLength = buf.getShort(position + 28).toInt() and 0xFFFF
                val extraLength = buf.getShort(position + 30).toInt() and 0xFFFF
                val commentLength = buf.getShort(position + 32).toInt() and 0xFFFF
                val name = String(bytes, position + 46, nameLength, Charsets.UTF_8)
                bytes[position + 5] = 3 // version made by: UNIX
                buf.putInt(position + 38, modes.getValue(name) shl 16)
                position += 46 + nameLength + extraLength + commentLength
            }
            file.seek(0)
            file.write(bytes)
        }
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
