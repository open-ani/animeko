/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.updatee2e

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipEntry

/**
 * 清单作为发布包的伴随资产发布, 文件名是发布包资产名加这个后缀. 与 Linux 的 `.zsync` 是同一种约定:
 * 客户端从版本 API 返回的发布包地址派生清单地址, 版本 API 不需要知道它. build-logic 里有同一个常量给 CI 上传任务用.
 */
const val DESKTOP_PACKAGE_MANIFEST_SUFFIX = ".manifest.json"

/**
 * 清单格式版本. 字段含义变化时递增, 客户端只接受自己认识的版本.
 */
const val DESKTOP_PACKAGE_MANIFEST_FORMAT_VERSION = 1

/**
 * 桌面发布包 (zip) 的清单: 每个 entry 的内容哈希和它的压缩数据在 zip 中的位置.
 *
 * 桌面增量更新用它决定哪些文件需要下载: 已安装目录里 sha256 相同的文件直接复用, 其余 entry 按
 * [DesktopPackageEntry.dataOffset] 和 [DesktopPackageEntry.compressedSize] 用 HTTP Range 从发布包取出
 * 原始压缩数据, 在本地拼回与发布包等价的 zip, 再走整包安装. 因此清单记录的是 zip 的原始字段,
 * 足够客户端不解压就重新写出 local header 和 central directory.
 *
 * [packageSize] 让客户端核对镜像返回的文件与清单描述的是同一个包.
 */
@Serializable
data class DesktopPackageManifest(
    val formatVersion: Int,
    val packageSize: Long,
    val entries: List<DesktopPackageEntry>,
)

/**
 * 发布包中的一个 entry, 顺序与 central directory 一致.
 *
 * @param name zip 内的名字, 目录以 `/` 结尾.
 * @param method 压缩方法, [ZipEntry.STORED] 或 [ZipEntry.DEFLATED].
 * @param crc32 解压后内容的 CRC-32, 无符号.
 * @param dataOffset 压缩数据在 zip 文件中的绝对偏移, 即 local header 之后的第一个字节.
 * @param dosTime zip 记录的 DOS 日期时间, 高 16 位是日期, 低 16 位是时间.
 * @param externalAttributes central directory 的外部属性, 高 16 位是 unix mode, 可执行位在其中.
 * @param sha256 解压后内容的 SHA-256 十六进制; 目录没有内容, 为 `null`.
 */
@Serializable
data class DesktopPackageEntry(
    val name: String,
    val method: Int,
    val crc32: Long,
    val compressedSize: Long,
    val size: Long,
    val dataOffset: Long,
    val dosTime: Long,
    val externalAttributes: Long,
    val sha256: String?,
) {
    val isDirectory: Boolean get() = name.endsWith("/")
}

/**
 * 从 [packageFile] 的 central directory 读出清单, 并解压每个 entry 计算 sha256.
 *
 * 解压出的大小和 CRC 会与 central directory 核对, 所以偏移算错时这里就会失败, 而不是在客户端.
 * 只支持没有 ZIP64 扩展的 zip; 发布包远小于 4 GB.
 */
fun readDesktopPackageManifest(packageFile: File): DesktopPackageManifest {
    RandomAccessFile(packageFile, "r").use { file ->
        val eocd = readEndOfCentralDirectory(file)
        val entries = readCentralDirectory(file, eocd).map { record ->
            val dataOffset = record.localHeaderOffset + readLocalHeaderSize(file, record)
            DesktopPackageEntry(
                name = record.name,
                method = record.method,
                crc32 = record.crc32,
                compressedSize = record.compressedSize,
                size = record.size,
                dataOffset = dataOffset,
                dosTime = record.dosTime,
                externalAttributes = record.externalAttributes,
                sha256 = if (record.name.endsWith("/")) null else digestEntry(file, record, dataOffset),
            )
        }
        return DesktopPackageManifest(
            formatVersion = DESKTOP_PACKAGE_MANIFEST_FORMAT_VERSION,
            packageSize = file.length(),
            entries = entries,
        )
    }
}

fun DesktopPackageManifest.writeTo(output: File) {
    output.writeText(toJson())
}

/**
 * 字段名就是 [DesktopPackageManifest] 和 [DesktopPackageEntry] 的属性名, 客户端按名解析.
 */
fun DesktopPackageManifest.toJson(): String = Json.encodeToString(DesktopPackageManifest.serializer(), this)

// region zip 格式

private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50
private const val CENTRAL_DIRECTORY_HEADER_SIGNATURE = 0x02014b50
private const val LOCAL_HEADER_SIGNATURE = 0x04034b50
private const val END_OF_CENTRAL_DIRECTORY_SIZE = 22
private const val MAX_COMMENT_LENGTH = 0xFFFF
private const val CENTRAL_DIRECTORY_HEADER_SIZE = 46
private const val LOCAL_HEADER_SIZE = 30
private const val ZIP64_MARKER_16 = 0xFFFF
private const val ZIP64_MARKER_32 = 0xFFFFFFFFL
private const val UTF8_NAME_FLAG = 1 shl 11

private class EndOfCentralDirectory(
    val entryCount: Int,
    val centralDirectorySize: Long,
    val centralDirectoryOffset: Long,
)

private class CentralDirectoryRecord(
    val name: String,
    val method: Int,
    val crc32: Long,
    val compressedSize: Long,
    val size: Long,
    val dosTime: Long,
    val externalAttributes: Long,
    val localHeaderOffset: Long,
)

private fun readEndOfCentralDirectory(file: RandomAccessFile): EndOfCentralDirectory {
    val length = file.length()
    val tailLength = minOf(length, (END_OF_CENTRAL_DIRECTORY_SIZE + MAX_COMMENT_LENGTH).toLong()).toInt()
    val tail = ByteBuffer.wrap(file.readAt(length - tailLength, tailLength)).order(ByteOrder.LITTLE_ENDIAN)
    // 注释可能含有与签名相同的字节, 从后往前找第一个自洽的记录.
    for (position in tailLength - END_OF_CENTRAL_DIRECTORY_SIZE downTo 0) {
        if (tail.getInt(position) != END_OF_CENTRAL_DIRECTORY_SIGNATURE) continue
        val commentLength = tail.getUnsignedShort(position + 20)
        if (position + END_OF_CENTRAL_DIRECTORY_SIZE + commentLength != tailLength) continue
        val entryCount = tail.getUnsignedShort(position + 10)
        val centralDirectorySize = tail.getUnsignedInt(position + 12)
        val centralDirectoryOffset = tail.getUnsignedInt(position + 16)
        check(
            entryCount != ZIP64_MARKER_16 &&
                    centralDirectorySize != ZIP64_MARKER_32 &&
                    centralDirectoryOffset != ZIP64_MARKER_32,
        ) { "ZIP64 archives are not supported: $file" }
        return EndOfCentralDirectory(entryCount, centralDirectorySize, centralDirectoryOffset)
    }
    error("End of central directory record not found; not a zip file?")
}

private fun readCentralDirectory(file: RandomAccessFile, eocd: EndOfCentralDirectory): List<CentralDirectoryRecord> {
    val directory = ByteBuffer.wrap(file.readAt(eocd.centralDirectoryOffset, eocd.centralDirectorySize.toInt()))
        .order(ByteOrder.LITTLE_ENDIAN)
    val records = ArrayList<CentralDirectoryRecord>(eocd.entryCount)
    var position = 0
    repeat(eocd.entryCount) {
        check(directory.getInt(position) == CENTRAL_DIRECTORY_HEADER_SIGNATURE) {
            "Central directory header signature mismatch at offset ${eocd.centralDirectoryOffset + position}"
        }
        val flags = directory.getUnsignedShort(position + 8)
        val method = directory.getUnsignedShort(position + 10)
        val dosTime = directory.getUnsignedInt(position + 12)
        val crc32 = directory.getUnsignedInt(position + 16)
        val compressedSize = directory.getUnsignedInt(position + 20)
        val size = directory.getUnsignedInt(position + 24)
        val nameLength = directory.getUnsignedShort(position + 28)
        val extraLength = directory.getUnsignedShort(position + 30)
        val commentLength = directory.getUnsignedShort(position + 32)
        val externalAttributes = directory.getUnsignedInt(position + 38)
        val localHeaderOffset = directory.getUnsignedInt(position + 42)
        val nameBytes = ByteArray(nameLength).also { directory.get(position + CENTRAL_DIRECTORY_HEADER_SIZE, it) }
        // 发布包由 Gradle 写出, 名字总是 UTF-8; 没有标 UTF-8 的名字只含 ASCII, 两种解码结果相同.
        val name = String(nameBytes, Charsets.UTF_8)
        check(flags and UTF8_NAME_FLAG != 0 || nameBytes.all { it >= 0 }) {
            "Entry name is not UTF-8 and not ASCII: $name"
        }
        check(compressedSize != ZIP64_MARKER_32 && size != ZIP64_MARKER_32 && localHeaderOffset != ZIP64_MARKER_32) {
            "ZIP64 entries are not supported: $name"
        }
        check(method == ZipEntry.STORED || method == ZipEntry.DEFLATED) {
            "Unsupported compression method $method: $name"
        }
        records += CentralDirectoryRecord(
            name = name,
            method = method,
            crc32 = crc32,
            compressedSize = compressedSize,
            size = size,
            dosTime = dosTime,
            externalAttributes = externalAttributes,
            localHeaderOffset = localHeaderOffset,
        )
        position += CENTRAL_DIRECTORY_HEADER_SIZE + nameLength + extraLength + commentLength
    }
    return records
}

/**
 * local header 的 extra 字段长度可以与 central directory 的不同, 数据偏移必须按 local header 算.
 */
private fun readLocalHeaderSize(file: RandomAccessFile, record: CentralDirectoryRecord): Long {
    val header = ByteBuffer.wrap(file.readAt(record.localHeaderOffset, LOCAL_HEADER_SIZE)).order(ByteOrder.LITTLE_ENDIAN)
    check(header.getInt(0) == LOCAL_HEADER_SIGNATURE) {
        "Local header signature mismatch for ${record.name} at offset ${record.localHeaderOffset}"
    }
    val nameLength = header.getUnsignedShort(26)
    val extraLength = header.getUnsignedShort(28)
    return (LOCAL_HEADER_SIZE + nameLength + extraLength).toLong()
}

private fun digestEntry(file: RandomAccessFile, record: CentralDirectoryRecord, dataOffset: Long): String {
    val compressed = RangeInputStream(file, dataOffset, record.compressedSize)
    val content = when (record.method) {
        ZipEntry.STORED -> compressed
        else -> InflaterInputStream(compressed, Inflater(true))
    }
    val sha256 = MessageDigest.getInstance("SHA-256")
    val crc = CRC32()
    var total = 0L
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    content.use { input ->
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            sha256.update(buffer, 0, read)
            crc.update(buffer, 0, read)
            total += read
        }
    }
    check(total == record.size) { "Decompressed size of ${record.name} is $total, central directory says ${record.size}" }
    check(crc.value == record.crc32) { "CRC-32 of ${record.name} does not match the central directory" }
    return sha256.digest().joinToString("") { "%02x".format(it) }
}

/**
 * 顺序读 [file] 中从 [offset] 起的 [length] 个字节. 同一个 [RandomAccessFile] 上一次只能有一个在用.
 */
private class RangeInputStream(
    private val file: RandomAccessFile,
    offset: Long,
    length: Long,
) : InputStream() {
    private var position = offset
    private val end = offset + length

    override fun read(): Int {
        if (position >= end) return -1
        file.seek(position)
        val byte = file.read()
        if (byte >= 0) position++
        return byte
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (position >= end) return -1
        file.seek(position)
        val read = file.read(b, off, minOf(len.toLong(), end - position).toInt())
        if (read > 0) position += read
        return read
    }
}

private fun RandomAccessFile.readAt(offset: Long, length: Int): ByteArray {
    seek(offset)
    return ByteArray(length).also { readFully(it) }
}

private fun ByteBuffer.getUnsignedShort(index: Int): Int = getShort(index).toInt() and 0xFFFF

private fun ByteBuffer.getUnsignedInt(index: Int): Long = getInt(index).toLong() and 0xFFFFFFFFL

// endregion
