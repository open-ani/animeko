/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent

import kotlinx.io.EOFException
import org.openani.mediamp.io.SeekableInput


/**
 * Reads max [maxLength] bytes from this [SeekableInput], and advances the current position by the number of bytes read.
 *
 * Returns the bytes read.
 *
 * See [SeekableInput.read] for more details about the behaviour of asynchronous reading.
 */
fun SeekableInput.readBytes(maxLength: Int = 4096): ByteArray {
    val buffer = ByteArray(maxLength)
    val actualLength = read(buffer, 0, maxLength)
    if (actualLength == -1) return ByteArray(0)
    return if (actualLength != buffer.size) {
        buffer.copyOf(newSize = actualLength)
    } else {
        buffer
    }
}

/**
 * 读取所剩的所有字节. 如果文件已经关闭, 会抛出异常 [IllegalStateException]
 */
fun SeekableInput.readAllBytes(): ByteArray {
    val buffer = ByteArray(bytesRemaining.toInt())
    var offset = 0
    while (true) {
        val read = read(buffer, offset)
        if (read == -1) break
        offset += read
    }
    if (offset == buffer.size) return buffer
    return buffer.copyOf(newSize = offset)
}

/**
 * 读取 [n] 个字节.
 *
 * @throws EOFException when the file is shorter than [n] bytes.
 * @throws IllegalStateException if the input source is closed.
 */
@Throws(EOFException::class)
fun SeekableInput.readExactBytes(
    n: Int
): ByteArray {
    val buffer = ByteArray(n)
    var offset = 0
    while (offset != buffer.size) {
        val read = read(buffer, offset)
        if (read == -1) break
        offset += read
    }
    if (offset == buffer.size) return buffer
    throw EOFException("Expected $n bytes, but only read $offset bytes")
}
