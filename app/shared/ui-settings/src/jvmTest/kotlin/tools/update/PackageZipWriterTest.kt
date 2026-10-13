/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PackageZipWriterTest {
    private val tempDir: File = Files.createTempDirectory("package-zip-writer-test").toFile()

    private val entries = listOf(
        TestZipEntry("Ani/", null),
        TestZipEntry("Ani/app/x.bin", patternBytes(20_000, 1)),
        TestZipEntry("Ani/app/y.txt", "stored text".toByteArray(), stored = true),
        TestZipEntry("Ani/z.bin", randomBytes(70_000, 2)),
        TestZipEntry("Ani/empty", ByteArray(0)),
    )

    private val source: File by lazy { writeTestZip(tempDir.resolve("source.zip"), entries) }
    private val manifest: DesktopPackageManifest by lazy { manifestOf(source) }

    private fun localFile(name: String): File = tempDir.resolve("local/" + name.substringAfter('/')).apply {
        parentFile.mkdirs()
        writeBytes(entries.first { it.name == name }.content!!)
    }

    private fun rawData(entry: DesktopPackageEntry): ByteArray =
        source.readBytes().copyOfRange(entry.dataOffset.toInt(), (entry.dataOffset + entry.compressedSize).toInt())

    @Test
    fun `raw and local entries produce an equivalent zip`() = runTest {
        val output = tempDir.resolve("output.zip")
        PackageZipWriter(output.outputStream()).use { writer ->
            for (entry in manifest.entries) {
                when (entry.name) {
                    "Ani/" -> writer.writeDirectory(entry)
                    "Ani/app/x.bin", "Ani/empty" -> writer.writeRawEntry(entry) { it.write(rawData(entry)) }
                    else -> writer.writeLocalEntry(entry, localFile(entry.name))
                }
            }
            writer.finish()
        }

        assertEquals(zipContents(source).mapValues { it.value.toList() }, zipContents(output).mapValues { it.value.toList() })
        ZipFile(output).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertEquals(manifest.entries.map { it.name }, names)
            for (expected in manifest.entries.filterNot { it.isDirectory }) {
                val actual = zip.getEntry(expected.name)
                assertEquals(expected.size, actual.size, expected.name)
                assertEquals(expected.crc32, actual.crc, expected.name)
                assertEquals(expected.method, actual.method, expected.name)
            }
            assertTrue(zip.getEntry("Ani/").isDirectory)
        }

        // 外部属性和时间原样写入 central directory
        val written = manifestOf(output)
        for ((expected, actual) in manifest.entries.zip(written.entries)) {
            assertEquals(expected.externalAttributes, actual.externalAttributes, expected.name)
            assertEquals(expected.dosTime, actual.dosTime, expected.name)
            assertEquals(expected.sha256, actual.sha256, expected.name)
        }
        // 原样写入的 entry 压缩数据不变
        val x = manifest.entries.first { it.name == "Ani/app/x.bin" }
        val writtenX = written.entries.first { it.name == "Ani/app/x.bin" }
        assertContentEquals(rawData(x), output.readBytes().copyOfRange(writtenX.dataOffset.toInt(), (writtenX.dataOffset + writtenX.compressedSize).toInt()))
    }

    @Test
    fun `local file that no longer matches the entry is rejected`() {
        val entry = manifest.entries.first { it.name == "Ani/z.bin" }
        val changed = tempDir.resolve("changed.bin").apply { writeBytes(randomBytes(70_000, 3)) }
        PackageZipWriter(tempDir.resolve("rejected.zip").outputStream()).use { writer ->
            assertFailsWith<IllegalStateException> { writer.writeLocalEntry(entry, changed) }
        }
    }

    @Test
    fun `raw entry with the wrong number of bytes is rejected`() = runTest {
        val entry = manifest.entries.first { it.name == "Ani/app/x.bin" }
        PackageZipWriter(tempDir.resolve("short.zip").outputStream()).use { writer ->
            assertFailsWith<IllegalStateException> {
                writer.writeRawEntry(entry) { it.write(rawData(entry), 0, 10) }
            }
        }
    }

    @Test
    fun `stored entry is written without compression`() = runTest {
        val output = tempDir.resolve("stored.zip")
        val entry = manifest.entries.first { it.name == "Ani/app/y.txt" }
        assertEquals(ZipEntry.STORED, entry.method)
        PackageZipWriter(output.outputStream()).use { writer ->
            writer.writeLocalEntry(entry, localFile(entry.name))
            writer.finish()
        }
        val written = manifestOf(output).entries.single()
        assertEquals(entry.size, written.compressedSize)
        assertEquals(ZipEntry.STORED, written.method)
        assertContentEquals("stored text".toByteArray(), zipContents(output).getValue(entry.name))
    }

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }
}
