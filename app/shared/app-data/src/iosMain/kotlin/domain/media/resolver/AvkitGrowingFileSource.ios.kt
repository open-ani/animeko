/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(ExperimentalForeignApi::class)

package me.him188.ani.app.domain.media.resolver

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSource
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import platform.posix.EINTR
import platform.posix.O_RDONLY
import platform.posix.errno
import platform.posix.close as closeFile
import platform.posix.open as openFile
import platform.posix.read as readFile

/** POSIX read 在暂时 EOF 后可以读取追加数据；stdio 的 EOF 状态会阻止增长文件继续读取。 */
internal actual fun avkitGrowingFileSource(path: SystemPath): RawSource {
    val descriptor = openFile(path.absolutePath, O_RDONLY)
    if (descriptor < 0) throw IOException("Cannot open streaming file (errno $errno)")
    return object : RawSource {
        private var closed = false
        private val bytes = ByteArray(64 * 1024)

        override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
            require(byteCount >= 0)
            check(!closed)
            if (byteCount == 0L) return 0
            val requested = minOf(byteCount, bytes.size.toLong()).toInt()
            var count: Long
            do {
                count = bytes.usePinned { readFile(descriptor, it.addressOf(0), requested.convert()) }
            } while (count < 0 && errno == EINTR)
            if (count < 0) throw IOException("Cannot read streaming file (errno $errno)")
            if (count == 0L) return -1
            sink.write(bytes, 0, count.toInt())
            return count
        }

        override fun close() {
            if (closed) return
            closed = true
            closeFile(descriptor)
        }
    }
}
