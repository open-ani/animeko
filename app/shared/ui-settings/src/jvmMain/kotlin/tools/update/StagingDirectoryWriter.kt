/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import java.io.Closeable
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.InflaterOutputStream
import java.util.zip.ZipEntry

/**
 * 接收按清单顺序写入的 entry, 拼出一个包. 实现决定包的形态: [PackageZipWriter] 拼 zip, [StagingDirectoryWriter] 拼目录.
 */
internal interface PackageWriter : Closeable {
    fun writeDirectory(entry: DesktopPackageEntry)

    /**
     * 写入发布包中该 entry 的原始压缩数据. [writeData] 必须恰好写 [DesktopPackageEntry.compressedSize] 字节.
     */
    suspend fun writeRawEntry(entry: DesktopPackageEntry, writeData: suspend (OutputStream) -> Unit)

    /**
     * 以本地文件 [content] 作为 entry 的内容写入. 内容必须与 entry 相同 (调用方已按 sha256 匹配).
     */
    fun writeLocalEntry(entry: DesktopPackageEntry, content: File)

    /**
     * 所有 entry 写完后调用.
     */
    fun finish()
}

/**
 * 把发布包的内容直接解到 [root] 目录, 得到解压好的新版本, 安装器可以整个目录换进安装目录, 不用再经过 zip.
 *
 * 发布包里的文件都在顶层的应用目录 (如 `Ani/`) 下, 写出时去掉这一层. 本地文件尽量用硬链接, 不在同一卷时复制.
 * 远程 entry 边解压边算 CRC 和 sha256, 与清单不符立即失败, 不会留下错误的文件.
 */
internal class StagingDirectoryWriter(private val root: File) : PackageWriter {
    override fun writeDirectory(entry: DesktopPackageEntry) {
        require(entry.isDirectory) { "Not a directory entry: ${entry.name}" }
        val target = targetOf(entry) ?: return
        check(target.isDirectory || target.mkdirs()) { "Cannot create directory $target" }
    }

    override suspend fun writeRawEntry(entry: DesktopPackageEntry, writeData: suspend (OutputStream) -> Unit) {
        require(!entry.isDirectory) { "Directory entry has no data: ${entry.name}" }
        val target = requireNotNull(targetOf(entry)) { "Entry has no file name: ${entry.name}" }
        target.parentFile?.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        val crc = CRC32()
        var size = 0L
        val fileOutput = target.outputStream()
        val checked = object : OutputStream() {
            override fun write(b: Int) {
                fileOutput.write(b)
                digest.update(b.toByte())
                crc.update(b)
                size++
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                fileOutput.write(b, off, len)
                digest.update(b, off, len)
                crc.update(b, off, len)
                size += len
            }

            override fun close() = fileOutput.close()
        }
        when (entry.method) {
            ZipEntry.STORED -> checked.use { writeData(it) }
            ZipEntry.DEFLATED -> {
                val inflater = Inflater(true)
                try {
                    InflaterOutputStream(checked, inflater, BUFFER_SIZE).use { inflating ->
                        writeData(inflating)
                        inflating.finish()
                    }
                } finally {
                    inflater.end()
                }
            }

            else -> throw IllegalArgumentException("Unsupported compression method ${entry.method}: ${entry.name}")
        }
        checkContent(entry, size, crc.value, digest.hex())
    }

    override fun writeLocalEntry(entry: DesktopPackageEntry, content: File) {
        require(!entry.isDirectory) { "Directory entry has no content: ${entry.name}" }
        val target = requireNotNull(targetOf(entry)) { "Entry has no file name: ${entry.name}" }
        target.parentFile?.mkdirs()
        target.delete()
        val linked = runCatching { Files.createLink(target.toPath(), content.toPath()) }.isSuccess
        if (!linked) {
            content.copyTo(target, overwrite = true)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val crc = CRC32()
        var size = 0L
        target.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                crc.update(buffer, 0, read)
                size += read
            }
        }
        checkContent(entry, size, crc.value, digest.hex())
    }

    override fun finish() {}

    override fun close() {}

    /**
     * entry 在 [root] 下的位置; 顶层目录本身没有对应的文件, 返回 `null`.
     */
    private fun targetOf(entry: DesktopPackageEntry): File? {
        val relative = entry.name.substringAfter('/', "").trimEnd('/')
        if (relative.isEmpty()) return null
        val target = File(root, relative)
        check(target.canonicalPath.startsWith(root.canonicalPath + File.separator)) {
            "Entry escapes the staging directory: ${entry.name}"
        }
        return target
    }

    private fun checkContent(entry: DesktopPackageEntry, size: Long, crc: Long, sha256: String) {
        check(size == entry.size && crc == entry.crc32 && sha256 == entry.sha256) {
            "Content of ${entry.name} does not match the manifest: size=$size crc=$crc, " +
                    "expected size=${entry.size} crc=${entry.crc32}"
        }
    }

    private fun MessageDigest.hex(): String = digest().joinToString("") { "%02x".format(it) }

    companion object {
        const val BUFFER_SIZE = 64 * 1024

        /**
         * 逐 entry 核对 [root] 与清单: 目录存在, 文件大小和 sha256 一致. 不一致时抛 [IllegalStateException].
         */
        fun verify(root: File, manifest: DesktopPackageManifest) {
            val writer = StagingDirectoryWriter(root)
            for (entry in manifest.entries) {
                val target = writer.targetOf(entry) ?: continue
                if (entry.isDirectory) {
                    check(target.isDirectory) { "Missing directory ${entry.name}" }
                    continue
                }
                check(target.isFile && target.length() == entry.size) { "Missing or wrong size: ${entry.name}" }
                val sha256 = target.inputStream().use { it.sha256Hex() }
                check(sha256 == entry.sha256) { "Content of ${entry.name} does not match the manifest" }
            }
        }
    }
}
