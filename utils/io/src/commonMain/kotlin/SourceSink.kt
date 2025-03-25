/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.io

import kotlinx.io.Sink
import kotlinx.io.Source

fun Source.copyTo(out: Sink) {
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val bytesRead = this.readAtMostTo(buffer, 0, buffer.size)
        if (bytesRead == -1) break
        out.write(buffer, startIndex = 0, endIndex = bytesRead)
    }
}
