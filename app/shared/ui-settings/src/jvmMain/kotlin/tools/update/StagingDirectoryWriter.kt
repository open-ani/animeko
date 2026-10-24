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
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
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
 * [stripTopLevel] 为 true 时去掉发布包顶层的应用目录 (如 `Ani/`) 这一层. 本地文件尽量用硬链接, 不在同一卷时复制.
 * 远程 entry 边解压边算 CRC 和 sha256, 与清单不符立即失败, 不会留下错误的文件.
 *
 * 支持 POSIX 权限的文件系统上按清单里的 unix mode 设置文件权限: macOS 的启动器和运行时里的可执行文件靠它保持可执行,
 * 安装脚本不再解压 zip, 没有别的地方恢复权限. 硬链接与安装目录里的文件共用权限, 权限不同时改为复制, 不改动安装目录.
 * 目录的权限不设置, 清单里的目录权限可能不可写, 设上之后后面的文件就写不进去.
 */
internal class StagingDirectoryWriter(
    private val root: File,
    private val stripTopLevel: Boolean = true,
) : PackageWriter {
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
        applyPosixMode(target, entry.posixMode)
    }

    override fun writeLocalEntry(entry: DesktopPackageEntry, content: File) {
        require(!entry.isDirectory) { "Directory entry has no content: ${entry.name}" }
        val target = requireNotNull(targetOf(entry)) { "Entry has no file name: ${entry.name}" }
        target.parentFile?.mkdirs()
        target.delete()
        val mode = entry.posixMode
        // 硬链接共用 inode, 也就共用权限; 权限不同时不能链接, 否则要么新版本权限不对, 要么改动了安装目录.
        val canLink = mode == null || runCatching { posixModeOf(content) }.getOrNull() == mode
        val linked = canLink && runCatching { Files.createLink(target.toPath(), content.toPath()) }.isSuccess
        if (!linked) {
            content.copyTo(target, overwrite = true)
            applyPosixMode(target, mode)
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
     * entry 在 [root] 下的位置; 去掉的顶层目录本身没有对应的文件, 返回 `null`.
     */
    private fun targetOf(entry: DesktopPackageEntry): File? {
        val relative = (if (stripTopLevel) entry.name.substringAfter('/', "") else entry.name).trimEnd('/')
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
         * 逐 entry 核对 [root] 与清单: 目录存在, 文件大小、sha256 和 (支持时) POSIX 权限一致. 不一致时抛 [IllegalStateException].
         */
        fun verify(root: File, manifest: DesktopPackageManifest, stripTopLevel: Boolean = true) {
            val writer = StagingDirectoryWriter(root, stripTopLevel)
            for (entry in manifest.entries) {
                val target = writer.targetOf(entry) ?: continue
                if (entry.isDirectory) {
                    check(target.isDirectory) { "Missing directory ${entry.name}" }
                    continue
                }
                check(target.isFile && target.length() == entry.size) { "Missing or wrong size: ${entry.name}" }
                val sha256 = target.inputStream().use { it.sha256Hex() }
                check(sha256 == entry.sha256) { "Content of ${entry.name} does not match the manifest" }
                entry.posixMode?.let { mode ->
                    val actual = posixModeOf(target)
                    check(actual == mode) {
                        "Permissions of ${entry.name} are ${Integer.toOctalString(actual)}, expected ${Integer.toOctalString(mode)}"
                    }
                }
            }
        }
    }
}

/**
 * 当前文件系统是否支持 POSIX 权限. Windows 上为 `false`, 既不设置也不核对权限.
 */
private val POSIX_SUPPORTED: Boolean = "posix" in FileSystems.getDefault().supportedFileAttributeViews()

/**
 * 清单里的 unix 权限位 (`rwxrwxrwx`), 来自 zip 外部属性的高 16 位. 不支持 POSIX 的文件系统上, 或 zip 没记录权限时为 `null`.
 */
internal val DesktopPackageEntry.posixMode: Int?
    get() = if (!POSIX_SUPPORTED) null else ((externalAttributes ushr 16) and 0x1FF).toInt().takeIf { it != 0 }

private val POSIX_PERMISSION_BITS = listOf(
    PosixFilePermission.OWNER_READ to 0b100_000_000,
    PosixFilePermission.OWNER_WRITE to 0b010_000_000,
    PosixFilePermission.OWNER_EXECUTE to 0b001_000_000,
    PosixFilePermission.GROUP_READ to 0b000_100_000,
    PosixFilePermission.GROUP_WRITE to 0b000_010_000,
    PosixFilePermission.GROUP_EXECUTE to 0b000_001_000,
    PosixFilePermission.OTHERS_READ to 0b000_000_100,
    PosixFilePermission.OTHERS_WRITE to 0b000_000_010,
    PosixFilePermission.OTHERS_EXECUTE to 0b000_000_001,
)

internal fun posixModeOf(file: File): Int {
    val permissions = Files.getPosixFilePermissions(file.toPath())
    return POSIX_PERMISSION_BITS.fold(0) { mode, (permission, bit) -> if (permission in permissions) mode or bit else mode }
}

internal fun applyPosixMode(file: File, mode: Int?) {
    if (mode == null) return
    val permissions = POSIX_PERMISSION_BITS.filter { (_, bit) -> mode and bit != 0 }.mapTo(HashSet()) { it.first }
    Files.setPosixFilePermissions(file.toPath(), permissions)
}
