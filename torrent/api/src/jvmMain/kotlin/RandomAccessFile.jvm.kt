/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.io

import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.toFile
import java.io.RandomAccessFile as JavaRandomAccessFile

actual class RandomAccessFile internal constructor(
    private val delegate: JavaRandomAccessFile,
) : AutoCloseable {
    actual fun seek(position: Long) {
        delegate.seek(position)
    }

    actual fun read(buffer: ByteArray, offset: Int, length: Int): Int = delegate.read(buffer, offset, length)

    actual fun write(buffer: ByteArray, offset: Int, length: Int) {
        delegate.write(buffer, offset, length)
    }

    // java.io.RandomAccessFile has no user-space buffer: every write already reached the kernel.
    actual fun flush() = Unit

    // false: only the file contents have to be durable, the modification time may stay in cache.
    actual fun sync() {
        delegate.channel.force(false)
    }

    actual fun setLength(newLength: Long) {
        delegate.setLength(newLength)
    }

    actual fun length(): Long = delegate.length()

    actual override fun close() {
        delegate.close()
    }
}

@Suppress("FunctionName")
actual fun RandomAccessFile(file: SystemPath, mode: String): RandomAccessFile =
    RandomAccessFile(JavaRandomAccessFile(file.toFile(), mode))
