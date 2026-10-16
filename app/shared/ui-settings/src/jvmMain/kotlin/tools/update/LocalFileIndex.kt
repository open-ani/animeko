/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * 已安装目录中文件的 SHA-256 索引, 按内容而不是路径匹配清单里的 entry, 所以改名的文件也能复用.
 *
 * 只对大小与清单中某个文件相同的文件计算哈希, 其余文件不可能匹配, 不用读.
 */
internal class LocalFileIndex private constructor(
    private val filesBySha256: Map<String, File>,
) {
    val size: Int get() = filesBySha256.size

    operator fun get(sha256: String): File? = filesBySha256[sha256]

    companion object {
        fun build(root: File, manifest: DesktopPackageManifest): LocalFileIndex {
            val sizes = manifest.entries.asSequence()
                .filterNot { it.isDirectory }
                .mapTo(HashSet()) { it.size }
            val filesBySha256 = HashMap<String, File>()
            root.walkTopDown()
                .filter { it.isFile && it.length() in sizes }
                .forEach { file ->
                    val sha256 = file.inputStream().use { it.sha256Hex() }
                    filesBySha256.putIfAbsent(sha256, file)
                }
            return LocalFileIndex(filesBySha256)
        }
    }
}

/**
 * 读完整个流并返回内容的 SHA-256 十六进制, 与清单里的 [DesktopPackageEntry.sha256] 同一格式.
 */
internal fun InputStream.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
