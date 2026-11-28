/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.resolve
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class AvkitGrowingFileSourceTest {
    @Test
    fun `a growing file remains readable after temporary EOF`() {
        val directory = SystemPaths.createTempDirectory("avkit-growing-test-")
        val file = directory.resolve("stream.bin")
        try {
            SystemFileSystem.sink(file.path).close()
            avkitGrowingFileSource(file).buffered().use { input ->
                repeat(3) { index ->
                    assertTrue(input.exhausted())
                    val bytes = byteArrayOf(index.toByte(), 1, 2, 3)
                    SystemFileSystem.sink(file.path, append = true).buffered().use { it.write(bytes) }
                    assertTrue(input.request(bytes.size.toLong()))
                    assertContentEquals(bytes, input.readByteArray(bytes.size))
                }
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
