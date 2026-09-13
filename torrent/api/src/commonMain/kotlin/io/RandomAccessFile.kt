/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.io

import kotlinx.io.IOException
import me.him188.ani.utils.io.SystemPath

// Preallocation and out-of-order pieces require random writes unavailable in kotlinx-io.
// A handle is not thread-safe: seek and its subsequent I/O must be serialized together.
expect class RandomAccessFile : AutoCloseable {
    fun seek(position: Long)

    fun read(buffer: ByteArray, offset: Int, length: Int): Int

    // Must write the full range or throw; callers publish piece completion after this returns.
    fun write(buffer: ByteArray, offset: Int, length: Int)

    /**
     * Hands buffered bytes to the kernel, so other handles on the same file read them back.
     * They live in the page cache from there on: a power loss or kernel crash still loses them.
     */
    fun flush()

    /**
     * Waits for the kernel to put those bytes on the storage medium; once this returns they
     * survive a power loss. Costs a real device round trip, so it belongs where a record written
     * elsewhere would otherwise claim more than the file can back up.
     */
    fun sync()

    fun setLength(newLength: Long)

    fun length(): Long

    override fun close()
}

@Suppress("FunctionName")
expect fun RandomAccessFile(file: SystemPath, mode: String): RandomAccessFile

fun RandomAccessFile.readFully(buffer: ByteArray, offset: Int, length: Int) {
    var read = 0
    while (read < length) {
        val n = read(buffer, offset + read, length - read)
        if (n < 0) throw IOException("Unexpected EOF after reading $read of $length bytes")
        read += n
    }
}
