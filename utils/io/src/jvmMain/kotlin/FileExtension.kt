/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.io

import java.io.File
import java.io.RandomAccessFile
import java.util.ArrayDeque

/**
 * Reads the last [n] lines from [file].
 *
 * @param file The file to read from.
 * @param n The number of lines to read from the end.
 * @return A list of the last [n] lines in the file (or fewer if the file has fewer than [n] lines).
 */
fun File.readLastNLines(n: Int): List<String> {
    if (!exists() || !isFile || n <= 0) {
        return emptyList()
    }

    RandomAccessFile(this, "r").use { raf ->
        val fileLength = raf.length()
        if (fileLength == 0L) return emptyList()

        // Position the pointer at the last character in the file
        var pos = fileLength - 1
        raf.seek(pos)

        // Skip trailing newlines (if any)
        while (pos > 0) {
            val c = raf.readByte()
            if (c != '\n'.code.toByte() && c != '\r'.code.toByte()) {
                raf.seek(++pos)
                break
            }
            raf.seek(--pos)
        }

        val lines = ArrayDeque<String>(n)
        val sb = StringBuilder()
        var lineCount = 0

        // Move backwards through the file until we collect n lines or reach the start
        while (pos >= 0 && lineCount < n) {
            raf.seek(pos--)
            val c = raf.readByte().toInt().toChar()

            if (c == '\n' || c == '\r') {
                // We have a new line if there's something in the buffer
                if (sb.isNotEmpty()) {
                    sb.reverse() // Reverse the current line (because we built it backwards)
                    lines.addFirst(sb.toString())
                    sb.setLength(0)
                    lineCount++
                }
            } else {
                // Collect the characters of the current line
                sb.append(c)
            }
        }

        // If we reached the start of the file and still have text in the buffer
        if (sb.isNotEmpty()) {
            sb.reverse()
            lines.addFirst(sb.toString())
        }

        return lines.toList()
    }
}
