/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.io

import kotlinx.io.Buffer
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.io.write
import kotlin.test.Test
import kotlin.test.assertEquals

class DigestTest {

    @Test
    fun `md5 - hello world`() {
        val data = "Hello, world!"
        val expectedHex = "6cd3556deb0da54bca060b4c39479839"

        // Approach 1: ByteString.digest(...)
        val byteString = data.encodeToByteString()
        val actualByteStringDigest = byteString.digest(DigestAlgorithm.MD5).toHexString()
        assertEquals(expectedHex, actualByteStringDigest)

        // Approach 2: Source.readAndDigest(...)
        val buffer = Buffer().apply { write(byteString) }
        val actualSourceDigest = buffer.readAndDigest(DigestAlgorithm.MD5).toHexString()
        assertEquals(expectedHex, actualSourceDigest)
    }

    @Test
    fun `sha1 - hello world`() {
        val data = "Hello, world!"
        val expectedHex = "943a702d06f34599aee1f8da8ef9f7296031d699"

        val byteString = data.encodeToByteString()
        assertEquals(expectedHex, byteString.digest(DigestAlgorithm.SHA1).toHexString())

        val buffer = Buffer().apply { write(byteString) }
        assertEquals(expectedHex, buffer.readAndDigest(DigestAlgorithm.SHA1).toHexString())
    }

    @Test
    fun `sha256 - hello world`() {
        val data = "Hello, world!"
        val expectedHex = "315f5bdb76d078c43b8ac0064e4a0164612b1fce77c869345bfc94c75894edd3"

        val byteString = data.encodeToByteString()
        assertEquals(expectedHex, byteString.digest(DigestAlgorithm.SHA256).toHexString())

        val buffer = Buffer().apply { write(byteString) }
        assertEquals(expectedHex, buffer.readAndDigest(DigestAlgorithm.SHA256).toHexString())
    }
}
