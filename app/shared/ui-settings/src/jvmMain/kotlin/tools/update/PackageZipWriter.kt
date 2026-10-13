/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipEntry

/**
 * 按清单写出与发布包等价的 zip.
 *
 * 从发布包取来的 entry 原样写入压缩数据; 本地文件按 entry 的压缩方法重新压缩, 压缩后的字节与发布包不一定
 * 相同, 但 entry 的名字、顺序、内容、权限和时间都相同, 安装器解压后得到同样的目录. 重新压缩的 entry 大小事先
 * 不知道, 用 data descriptor 记录; 其余 entry 的大小写在 local header 里. 不写 ZIP64, 发布包远小于 4 GB.
 *
 * 调用方按清单顺序写入每个 entry, 最后调用 [finish].
 */
internal class PackageZipWriter(output: OutputStream) : PackageWriter {
    private val out = PositionedOutputStream(BufferedOutputStream(output, BUFFER_SIZE))
    private val records = ArrayList<CentralRecord>()

    override fun writeDirectory(entry: DesktopPackageEntry) {
        require(entry.isDirectory) { "Not a directory entry: ${entry.name}" }
        val offset = out.position
        writeLocalHeader(entry.name, UTF8_FLAG, ZipEntry.STORED, entry.dosTime, crc = 0, compressedSize = 0, size = 0)
        records += CentralRecord(entry, UTF8_FLAG, ZipEntry.STORED, crc = 0, compressedSize = 0, size = 0, offset)
    }

    /**
     * 写入发布包中该 entry 的原始压缩数据. [writeData] 必须恰好写 [DesktopPackageEntry.compressedSize] 字节.
     */
    override suspend fun writeRawEntry(entry: DesktopPackageEntry, writeData: suspend (OutputStream) -> Unit) {
        require(!entry.isDirectory) { "Directory entry has no data: ${entry.name}" }
        val offset = out.position
        writeLocalHeader(
            entry.name, UTF8_FLAG, entry.method, entry.dosTime,
            crc = entry.crc32, compressedSize = entry.compressedSize, size = entry.size,
        )
        val dataStart = out.position
        writeData(NonClosingOutputStream(out))
        val written = out.position - dataStart
        check(written == entry.compressedSize) {
            "Wrote $written bytes for ${entry.name}, expected ${entry.compressedSize}"
        }
        records += CentralRecord(entry, UTF8_FLAG, entry.method, entry.crc32, entry.compressedSize, entry.size, offset)
    }

    /**
     * 以本地文件 [content] 作为 entry 的内容写入, 按 entry 的压缩方法压缩. 内容必须与 entry 相同 (调用方已按
     * sha256 匹配); 读到的大小或 CRC 与清单不符说明文件在索引后被改过, 抛 [IllegalStateException].
     */
    override fun writeLocalEntry(entry: DesktopPackageEntry, content: File) {
        require(!entry.isDirectory) { "Directory entry has no content: ${entry.name}" }
        val offset = out.position
        when (entry.method) {
            ZipEntry.STORED -> {
                writeLocalHeader(
                    entry.name, UTF8_FLAG, ZipEntry.STORED, entry.dosTime,
                    crc = entry.crc32, compressedSize = entry.size, size = entry.size,
                )
                val (crc, size) = copyContent(content, NonClosingOutputStream(out))
                checkContent(entry, content, crc, size)
                records += CentralRecord(entry, UTF8_FLAG, ZipEntry.STORED, crc, size, size, offset)
            }

            ZipEntry.DEFLATED -> {
                val flags = UTF8_FLAG or DATA_DESCRIPTOR_FLAG
                writeLocalHeader(entry.name, flags, ZipEntry.DEFLATED, entry.dosTime, crc = 0, compressedSize = 0, size = 0)
                val dataStart = out.position
                // 内容与发布包相同, 压缩只影响拼出的包的大小; 最快的级别就够.
                val deflater = Deflater(Deflater.BEST_SPEED, true)
                val (crc, size) = try {
                    DeflaterOutputStream(NonClosingOutputStream(out), deflater, BUFFER_SIZE).use { deflated ->
                        copyContent(content, deflated)
                    }
                } finally {
                    deflater.end()
                }
                checkContent(entry, content, crc, size)
                val compressedSize = out.position - dataStart
                writeDataDescriptor(crc, compressedSize, size)
                records += CentralRecord(entry, flags, ZipEntry.DEFLATED, crc, compressedSize, size, offset)
            }

            else -> throw IllegalArgumentException("Unsupported compression method ${entry.method}: ${entry.name}")
        }
    }

    /**
     * 写 central directory 和 end of central directory, 之后不能再写 entry.
     */
    override fun finish() {
        check(records.size < ZIP64_ENTRY_COUNT) { "Too many entries for a non-ZIP64 archive: ${records.size}" }
        val centralDirectoryOffset = out.position
        for (record in records) {
            checkNoZip64(record.compressedSize, record.size, record.localHeaderOffset)
            val name = record.entry.name.toByteArray(Charsets.UTF_8)
            out.writeInt(CENTRAL_DIRECTORY_HEADER_SIGNATURE)
            out.writeShort(VERSION_MADE_BY)
            out.writeShort(VERSION_NEEDED)
            out.writeShort(record.flags)
            out.writeShort(record.method)
            out.writeInt(record.entry.dosTime)
            out.writeInt(record.crc)
            out.writeInt(record.compressedSize)
            out.writeInt(record.size)
            out.writeShort(name.size)
            out.writeShort(0) // extra
            out.writeShort(0) // comment
            out.writeShort(0) // disk number start
            out.writeShort(0) // internal attributes
            out.writeInt(record.entry.externalAttributes)
            out.writeInt(record.localHeaderOffset)
            out.write(name)
        }
        val centralDirectorySize = out.position - centralDirectoryOffset
        checkNoZip64(centralDirectorySize, centralDirectoryOffset)
        out.writeInt(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
        out.writeShort(0) // this disk
        out.writeShort(0) // disk with central directory
        out.writeShort(records.size)
        out.writeShort(records.size)
        out.writeInt(centralDirectorySize)
        out.writeInt(centralDirectoryOffset)
        out.writeShort(0) // comment
        out.flush()
    }

    override fun close() {
        out.close()
    }

    private fun writeLocalHeader(
        name: String,
        flags: Int,
        method: Int,
        dosTime: Long,
        crc: Long,
        compressedSize: Long,
        size: Long,
    ) {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        check(nameBytes.size <= 0xFFFF) { "Entry name too long: $name" }
        checkNoZip64(compressedSize, size)
        out.writeInt(LOCAL_HEADER_SIGNATURE)
        out.writeShort(VERSION_NEEDED)
        out.writeShort(flags)
        out.writeShort(method)
        out.writeInt(dosTime)
        out.writeInt(crc)
        out.writeInt(compressedSize)
        out.writeInt(size)
        out.writeShort(nameBytes.size)
        out.writeShort(0) // extra
        out.write(nameBytes)
    }

    private fun writeDataDescriptor(crc: Long, compressedSize: Long, size: Long) {
        checkNoZip64(compressedSize, size)
        out.writeInt(DATA_DESCRIPTOR_SIGNATURE)
        out.writeInt(crc)
        out.writeInt(compressedSize)
        out.writeInt(size)
    }

    /**
     * 把 [content] 复制到 [target], 返回内容的 CRC-32 和字节数.
     */
    private fun copyContent(content: File, target: OutputStream): Pair<Long, Long> {
        val crc = CRC32()
        var size = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        content.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                crc.update(buffer, 0, read)
                target.write(buffer, 0, read)
                size += read
            }
        }
        return crc.value to size
    }

    /**
     * 大小和偏移达到 0xFFFFFFFF 就要写 ZIP64 字段, 这里不支持.
     */
    private fun checkNoZip64(vararg values: Long) {
        for (value in values) check(value < ZIP64_LIMIT) { "Value requires ZIP64: $value" }
    }

    private fun checkContent(entry: DesktopPackageEntry, content: File, crc: Long, size: Long) {
        check(size == entry.size && crc == entry.crc32) {
            "Local file $content no longer matches ${entry.name}: size=$size crc=$crc, " +
                    "expected size=${entry.size} crc=${entry.crc32}"
        }
    }

    private class CentralRecord(
        val entry: DesktopPackageEntry,
        val flags: Int,
        val method: Int,
        val crc: Long,
        val compressedSize: Long,
        val size: Long,
        val localHeaderOffset: Long,
    )

    private class PositionedOutputStream(private val delegate: OutputStream) : OutputStream() {
        var position: Long = 0
            private set

        override fun write(b: Int) {
            delegate.write(b)
            position++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            position += len
        }

        override fun flush() = delegate.flush()

        override fun close() = delegate.close()

        fun writeShort(value: Int) {
            require(value in 0..0xFFFF) { "Value out of range for a 16-bit field: $value" }
            write(value and 0xFF)
            write((value ushr 8) and 0xFF)
        }

        fun writeInt(value: Long) {
            require(value in 0..0xFFFFFFFFL) { "Value out of range for a 32-bit field: $value" }
            write((value and 0xFF).toInt())
            write(((value ushr 8) and 0xFF).toInt())
            write(((value ushr 16) and 0xFF).toInt())
            write(((value ushr 24) and 0xFF).toInt())
        }
    }

    private class NonClosingOutputStream(private val delegate: OutputStream) : OutputStream() {
        override fun write(b: Int) = delegate.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)
        override fun flush() = delegate.flush()
        override fun close() = delegate.flush()
    }

    private companion object {
        const val BUFFER_SIZE = 256 * 1024
        const val LOCAL_HEADER_SIGNATURE = 0x04034b50L
        const val DATA_DESCRIPTOR_SIGNATURE = 0x08074b50L
        const val CENTRAL_DIRECTORY_HEADER_SIGNATURE = 0x02014b50L
        const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50L

        /** 主机系统 UNIX (3), 解压工具才会按外部属性里的 unix mode 恢复可执行位. */
        const val VERSION_MADE_BY = (3 shl 8) or 20
        const val VERSION_NEEDED = 20
        const val DATA_DESCRIPTOR_FLAG = 1 shl 3
        const val UTF8_FLAG = 1 shl 11
        const val ZIP64_LIMIT = 0xFFFFFFFFL
        const val ZIP64_ENTRY_COUNT = 0xFFFF
    }
}
