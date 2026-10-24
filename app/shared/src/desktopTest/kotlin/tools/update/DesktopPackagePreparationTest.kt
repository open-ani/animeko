/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopPackagePreparationTest {
    private val tempDir = createTempDirectory("desktop-package-preparation-test").toFile()

    private fun zip(name: String, entries: Map<String, ByteArray?>, stored: Boolean = false): File {
        val file = tempDir.resolve(name)
        ZipOutputStream(file.outputStream()).use { out ->
            for ((entryName, content) in entries) {
                val entry = ZipEntry(entryName)
                if (stored && content != null) {
                    entry.method = ZipEntry.STORED
                    entry.size = content.size.toLong()
                    entry.compressedSize = content.size.toLong()
                    entry.crc = CRC32().apply { update(content) }.value
                }
                out.putNextEntry(entry)
                content?.let { out.write(it) }
                out.closeEntry()
            }
        }
        return file
    }

    private val windowsPackage = linkedMapOf<String, ByteArray?>(
        "Ani/" to null,
        "Ani/Ani.exe" to "exe".toByteArray(),
        "Ani/app/" to null,
        "Ani/app/a.jar" to ByteArray(100_000) { it.toByte() },
    )

    @Test
    fun `windows package is extracted without the top-level directory`() {
        val zip = zip("pkg.zip", windowsPackage)
        val staging = runBlocking { DesktopPackagePreparation.prepareWindowsPackage(zip, "Ani.exe") }
        assertEquals(tempDir.resolve("pkg.zip.staging"), staging)
        assertEquals("exe", staging.resolve("Ani.exe").readText())
        assertEquals(windowsPackage.getValue("Ani/app/a.jar")!!.toList(), staging.resolve("app/a.jar").readBytes().toList())
        assertFalse(staging.resolve("Ani").exists())
        assertFalse(tempDir.resolve("pkg.zip.staging.part").exists())
    }

    @Test
    fun `windows package without the executable is rejected`() {
        val zip = zip("pkg.zip", linkedMapOf("Ani/" to null, "Ani/app/a.jar" to "jar".toByteArray()))
        assertFailsWith<PackagePreparationException> {
            runBlocking { DesktopPackagePreparation.prepareWindowsPackage(zip, "Ani.exe") }
        }
        assertFalse(tempDir.resolve("pkg.zip.staging").exists())
        assertFalse(tempDir.resolve("pkg.zip.staging.part").exists())
    }

    @Test
    fun `an existing staging directory is replaced`() {
        val zip = zip("pkg.zip", windowsPackage)
        tempDir.resolve("pkg.zip.staging/stale.txt").apply {
            parentFile.mkdirs()
            writeText("stale")
        }
        val staging = runBlocking { DesktopPackagePreparation.prepareWindowsPackage(zip, "Ani.exe") }
        assertFalse(staging.resolve("stale.txt").exists())
        assertTrue(staging.resolve("Ani.exe").isFile)
    }

    @Test
    fun `entries escaping the target directory are rejected`() {
        val zip = zip("evil.zip", linkedMapOf("Ani/Ani.exe" to "exe".toByteArray(), "Ani/../../evil.txt" to "evil".toByteArray()))
        val into = tempDir.resolve("into").apply { mkdirs() }
        assertFailsWith<IllegalStateException> { DesktopPackagePreparation.extractZip(zip, into, stripCommonRoot = true) }
        assertFalse(tempDir.resolve("evil.txt").exists())
    }

    @Test
    fun `corrupted entries are detected`() {
        val content = ByteArray(1000) { 1 }
        val zip = zip("corrupt.zip", linkedMapOf("Ani/data.bin" to content), stored = true)
        // STORED entry 的数据原样在 zip 里, 改一个字节就与 CRC 不符
        val bytes = zip.readBytes()
        val dataStart = bytes.indexOfData(content)
        bytes[dataStart + 10] = 2
        zip.writeBytes(bytes)
        val into = tempDir.resolve("into").apply { mkdirs() }
        assertFailsWith<IllegalStateException> { DesktopPackagePreparation.extractZip(zip, into, stripCommonRoot = true) }
    }

    @Test
    fun `common root requires every entry to share the first segment`() {
        assertEquals("Ani", DesktopPackagePreparation.commonRoot(listOf("Ani/", "Ani/Ani.exe", "Ani/app/a.jar")))
        assertNull(DesktopPackagePreparation.commonRoot(listOf("Ani/Ani.exe", "Other/b.txt")))
        assertNull(DesktopPackagePreparation.commonRoot(listOf("readme.txt", "Ani/Ani.exe")))
        assertNull(DesktopPackagePreparation.commonRoot(emptyList()))
    }

    private fun ByteArray.indexOfData(data: ByteArray): Int {
        outer@ for (start in indices) {
            if (start + data.size > size) break
            for (i in data.indices) if (this[start + i] != data[i]) continue@outer
            return start
        }
        error("data not found")
    }

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }
}
