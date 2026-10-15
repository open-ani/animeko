/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/**
 * 测试用的发布包内容. [content] 为 `null` 表示目录.
 */
internal class TestZipEntry(
    val name: String,
    val content: ByteArray?,
    val stored: Boolean = false,
)

internal fun randomBytes(size: Int, seed: Int): ByteArray = Random(seed).nextBytes(size)

internal fun patternBytes(size: Int, seed: Int): ByteArray = ByteArray(size) { ((it + seed) % 97).toByte() }

/**
 * 用 [ZipOutputStream] 写一个发布包. 它为 DEFLATED entry 写 data descriptor, 与 Gradle 的 zip 不同, 清单解析和
 * 拼包都必须能处理这两种.
 */
internal fun writeTestZip(file: File, entries: List<TestZipEntry>): File {
    ZipOutputStream(file.outputStream()).use { out ->
        for (spec in entries) {
            val entry = ZipEntry(spec.name).apply {
                time = 1_700_000_000_000L
                if (spec.stored && spec.content != null) {
                    method = ZipEntry.STORED
                    size = spec.content.size.toLong()
                    compressedSize = spec.content.size.toLong()
                    crc = CRC32().apply { update(spec.content) }.value
                }
            }
            out.putNextEntry(entry)
            spec.content?.let { out.write(it) }
            out.closeEntry()
        }
    }
    return file
}

/**
 * 按与 build-logic 相同的方式从 zip 读出清单: central directory 给出每个 entry 的字段, local header 给出数据偏移.
 */
internal fun manifestOf(zip: File): DesktopPackageManifest {
    val bytes = zip.readBytes()
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    fun u16(at: Int) = buf.getShort(at).toInt() and 0xFFFF
    fun u32(at: Int) = buf.getInt(at).toLong() and 0xFFFFFFFFL

    var eocd = bytes.size - 22
    while (buf.getInt(eocd) != 0x06054b50) eocd--
    val count = u16(eocd + 10)
    var position = u32(eocd + 16).toInt()
    val entries = ArrayList<DesktopPackageEntry>()
    ZipFile(zip).use { zipFile ->
        repeat(count) {
            check(buf.getInt(position) == 0x02014b50)
            val nameLength = u16(position + 28)
            val extraLength = u16(position + 30)
            val commentLength = u16(position + 32)
            val name = String(bytes, position + 46, nameLength, Charsets.UTF_8)
            val localHeaderOffset = u32(position + 42).toInt()
            val dataOffset = localHeaderOffset + 30 + u16(localHeaderOffset + 26) + u16(localHeaderOffset + 28)
            val sha256 = if (name.endsWith("/")) {
                null
            } else {
                zipFile.getInputStream(zipFile.getEntry(name)).use { it.sha256Hex() }
            }
            entries += DesktopPackageEntry(
                name = name,
                method = u16(position + 10),
                crc32 = u32(position + 16),
                compressedSize = u32(position + 20),
                size = u32(position + 24),
                dataOffset = dataOffset.toLong(),
                dosTime = u32(position + 12),
                externalAttributes = u32(position + 38),
                sha256 = sha256,
            )
            position += 46 + nameLength + extraLength + commentLength
        }
    }
    return DesktopPackageManifest(
        formatVersion = DesktopPackageManifest.SUPPORTED_FORMAT_VERSION,
        packageSize = bytes.size.toLong(),
        entries = entries,
    )
}

internal fun DesktopPackageManifest.toJson(): String = Json.encodeToString(DesktopPackageManifest.serializer(), this)

/**
 * 读出 zip 里每个文件 entry 的内容.
 */
internal fun zipContents(zip: File): Map<String, ByteArray> = ZipFile(zip).use { zipFile ->
    zipFile.entries().asSequence()
        .filterNot { it.isDirectory }
        .associate { it.name to zipFile.getInputStream(it).use { input -> input.readBytes() } }
}

/**
 * 把 [entries] 里有内容的文件写到 [root] 下, 作为已安装目录. 名字的第一段 (如 `Ani/`) 去掉.
 */
internal fun writeInstalledFiles(root: File, entries: List<TestZipEntry>) {
    for (entry in entries) {
        val content = entry.content ?: continue
        val file = root.resolve(entry.name.substringAfter('/'))
        file.parentFile.mkdirs()
        file.writeBytes(content)
    }
}
