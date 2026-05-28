/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.ktor

import io.ktor.utils.io.ByteReadChannel
import kotlinx.io.Buffer
import kotlinx.io.RawSource

actual fun ByteReadChannel.toRawSource(): RawSource {
    return object : RawSource {
        override fun close() {
        }

        override fun readAtMostTo(sink: Buffer, byteCount: Long): Long = -1
    }
}
