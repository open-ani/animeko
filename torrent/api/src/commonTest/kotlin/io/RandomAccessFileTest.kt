/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.io

import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.resolve
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RandomAccessFileTest {
    private val file = SystemPaths.createTempDirectory("randomAccessFileTest").resolve("data.bin")

    @Test
    fun `preallocate then write at offset then read back`() {
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(1024)
            assertEquals(1024, raf.length())

            raf.seek(500)
            raf.write(byteArrayOf(1, 2, 3, 4, 5), 1, 3)
        }

        RandomAccessFile(file, "r").use { raf ->
            assertEquals(1024, raf.length())
            raf.seek(500)
            val buffer = ByteArray(3)
            raf.readFully(buffer, 0, 3)
            assertContentEquals(byteArrayOf(2, 3, 4), buffer)
        }
    }

    @Test
    fun `read returns -1 at EOF`() {
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(4)
        }

        RandomAccessFile(file, "r").use { raf ->
            raf.seek(4)
            assertEquals(-1, raf.read(ByteArray(8), 0, 8))
        }
    }

    @Test
    fun `readFully past EOF fails`() {
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(4)
        }

        RandomAccessFile(file, "r").use { raf ->
            assertFailsWith<kotlinx.io.IOException> {
                raf.readFully(ByteArray(8), 0, 8)
            }
        }
    }
}
