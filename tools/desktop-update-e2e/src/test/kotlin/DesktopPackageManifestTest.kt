/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.updatee2e

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopPackageManifestTest {
    private val tempDir: File = Files.createTempDirectory("desktop-package-manifest-test").toFile()

    private val contents = linkedMapOf(
        "Ani/app/a.jar" to ByteArray(20_000) { (it % 97).toByte() },
        "Ani/app/stored.bin" to "stored, not compressed".toByteArray(),
        "Ani/runtime/bin/中文名.dat" to "non-ascii name".toByteArray(),
        "Ani/empty.txt" to ByteArray(0),
    )

    private fun writePackage(name: String = "package.zip"): File {
        val file = tempDir.resolve(name)
        ZipOutputStream(file.outputStream()).use { out ->
            out.setComment("release package")
            out.putNextEntry(ZipEntry("Ani/").apply { time = 1_700_000_000_000L })
            out.closeEntry()
            for ((entryName, bytes) in contents) {
                val entry = ZipEntry(entryName).apply {
                    time = 1_700_000_000_000L
                    if (entryName.endsWith(".bin")) {
                        method = ZipEntry.STORED
                        size = bytes.size.toLong()
                        compressedSize = bytes.size.toLong()
                        crc = CRC32().apply { update(bytes) }.value
                    }
                    if (entryName.endsWith(".jar")) extra = byteArrayOf(0x55, 0x54, 5, 0, 1, 1, 2, 3, 4)
                }
                out.putNextEntry(entry)
                out.write(bytes)
                out.closeEntry()
            }
        }
        return file
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun inflate(bytes: ByteArray, size: Int): ByteArray {
        val inflater = Inflater(true).apply { setInput(bytes) }
        val out = ByteArray(size)
        var total = 0
        while (!inflater.finished() && total < size) total += inflater.inflate(out, total, size - total)
        inflater.end()
        return out
    }

    @Test
    fun `entries follow the central directory order of the zip`() {
        val zip = writePackage()
        val manifest = readDesktopPackageManifest(zip)

        val expected = ZipFile(zip).use { it.entries().toList().map { entry -> entry.name } }
        assertEquals(expected, manifest.entries.map { it.name })
        assertEquals(zip.length(), manifest.packageSize)
        assertEquals(DESKTOP_PACKAGE_MANIFEST_FORMAT_VERSION, manifest.formatVersion)
    }

    @Test
    fun `data offsets point at the compressed bytes of each entry`() {
        val zip = writePackage()
        val bytes = zip.readBytes()
        val manifest = readDesktopPackageManifest(zip)

        for (entry in manifest.entries.filterNot { it.isDirectory }) {
            val raw = bytes.copyOfRange(entry.dataOffset.toInt(), (entry.dataOffset + entry.compressedSize).toInt())
            val content = if (entry.method == ZipEntry.STORED) raw else inflate(raw, entry.size.toInt())
            assertContentEquals(contents.getValue(entry.name), content, entry.name)
            assertEquals(sha256(content), entry.sha256, entry.name)
            assertEquals(CRC32().apply { update(content) }.value, entry.crc32, entry.name)
            assertEquals(content.size.toLong(), entry.size, entry.name)
        }
    }

    @Test
    fun `keeps raw zip fields`() {
        val manifest = readDesktopPackageManifest(writePackage())
        val byName = manifest.entries.associateBy { it.name }

        assertEquals(ZipEntry.STORED, byName.getValue("Ani/app/stored.bin").method)
        assertEquals(ZipEntry.DEFLATED, byName.getValue("Ani/app/a.jar").method)
        val directory = byName.getValue("Ani/")
        assertTrue(directory.isDirectory)
        assertNull(directory.sha256)
        // 目录没有内容, 但 ZipOutputStream 仍会为它写一段空的 deflate 流, compressedSize 不一定是 0.
        assertEquals(0L, directory.size)
        // DOS 日期在高 16 位, 年份从 1980 起算; 1_700_000_000_000 是 2023 年.
        for (entry in manifest.entries) {
            assertEquals(2023, (entry.dosTime shr 25).toInt() + 1980, entry.name)
        }
    }

    @Test
    fun `json round trip keeps every field`() {
        val manifest = readDesktopPackageManifest(writePackage())
        val json = tempDir.resolve("package.zip$DESKTOP_PACKAGE_MANIFEST_SUFFIX")
        manifest.writeTo(json)

        val parsed = Json.parseToJsonElement(json.readText()).jsonObject
        assertEquals(manifest.formatVersion, parsed.getValue("formatVersion").jsonPrimitive.int)
        assertEquals(manifest.packageSize, parsed.getValue("packageSize").jsonPrimitive.long)

        val entries = parsed.getValue("entries").jsonArray.map { it.jsonObject }
        assertEquals(manifest.entries.size, entries.size)
        for ((expected, actual) in manifest.entries.zip(entries)) {
            assertEquals(expected.name, actual.getValue("name").jsonPrimitive.content)
            assertEquals(expected.method, actual.getValue("method").jsonPrimitive.int)
            assertEquals(expected.crc32, actual.getValue("crc32").jsonPrimitive.long)
            assertEquals(expected.compressedSize, actual.getValue("compressedSize").jsonPrimitive.long)
            assertEquals(expected.size, actual.getValue("size").jsonPrimitive.long)
            assertEquals(expected.dataOffset, actual.getValue("dataOffset").jsonPrimitive.long)
            assertEquals(expected.dosTime, actual.getValue("dosTime").jsonPrimitive.long)
            assertEquals(expected.externalAttributes, actual.getValue("externalAttributes").jsonPrimitive.long)
            assertEquals(expected.sha256, actual.getValue("sha256").jsonPrimitive.contentOrNull)
        }
    }

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }
}
