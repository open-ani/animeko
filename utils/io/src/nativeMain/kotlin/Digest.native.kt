/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.io

import korlibs.crypto.Hasher
import korlibs.crypto.MD5
import korlibs.crypto.SHA1
import korlibs.crypto.SHA256
import kotlinx.io.Source

actual fun Source.readAndDigest(algorithm: DigestAlgorithm): ByteArray {
    return when (algorithm) {
        DigestAlgorithm.MD5 -> {
            digest(MD5())
        }

        DigestAlgorithm.SHA256 -> {
            digest(SHA256())
        }

        DigestAlgorithm.SHA1 -> {
            digest(SHA1())
        }
    }
}

@Suppress("SpellCheckingInspection")
private fun Source.digest(hasher: Hasher): ByteArray {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var read = 0
    while (read != -1) {
        read = readAtMostTo(buffer)
        if (read != -1) {
            hasher.update(buffer, 0, read)
        }
    }
    return hasher.digest().bytes
}
