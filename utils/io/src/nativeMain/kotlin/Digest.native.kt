/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(ExperimentalForeignApi::class)

package me.him188.ani.utils.io

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.io.Source
import platform.CoreCrypto.CC_MD5_CTX
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import platform.CoreCrypto.CC_MD5_Final
import platform.CoreCrypto.CC_MD5_Init
import platform.CoreCrypto.CC_MD5_Update
import platform.CoreCrypto.CC_SHA1_CTX
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA1_Final
import platform.CoreCrypto.CC_SHA1_Init
import platform.CoreCrypto.CC_SHA1_Update
import platform.CoreCrypto.CC_SHA256_CTX
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256_Final
import platform.CoreCrypto.CC_SHA256_Init
import platform.CoreCrypto.CC_SHA256_Update

actual fun Source.readAndDigest(algorithm: DigestAlgorithm): ByteArray = memScoped {
    when (algorithm) {
        DigestAlgorithm.MD5 -> digestMD5()
        DigestAlgorithm.SHA256 -> digestSHA256()
        DigestAlgorithm.SHA1 -> digestSHA1()
    }
}

/** Uses CC_MD5 to process input from `this` [Source]. */
private fun Source.digestMD5(): ByteArray = memScoped {
    val context = alloc<CC_MD5_CTX>()
    CC_MD5_Init(context.ptr)

    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = readAtMostTo(buffer)
        if (read == -1) break

        buffer.usePinned {
            CC_MD5_Update(context.ptr, it.addressOf(0), read.convert())
        }
    }

    val digest = UByteArray(CC_MD5_DIGEST_LENGTH)
    digest.usePinned {
        CC_MD5_Final(it.addressOf(0), context.ptr)
    }
    digest.asByteArray()
}

/** Uses CC_SHA1 to process input from `this` [Source]. */
private fun Source.digestSHA1(): ByteArray = memScoped {
    val context = alloc<CC_SHA1_CTX>()
    CC_SHA1_Init(context.ptr)

    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = readAtMostTo(buffer)
        if (read == -1) break

        buffer.usePinned {
            CC_SHA1_Update(context.ptr, it.addressOf(0), read.convert())
        }
    }

    val digest = UByteArray(CC_SHA1_DIGEST_LENGTH)
    digest.usePinned {
        CC_SHA1_Final(it.addressOf(0), context.ptr)
    }
    digest.asByteArray()
}

/** Uses CC_SHA256 to process input from `this` [Source]. */
private fun Source.digestSHA256(): ByteArray = memScoped {
    val context = alloc<CC_SHA256_CTX>()
    CC_SHA256_Init(context.ptr)

    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = readAtMostTo(buffer)
        if (read == -1) break

        buffer.usePinned {
            CC_SHA256_Update(context.ptr, it.addressOf(0), read.convert())
        }
    }

    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    digest.usePinned {
        CC_SHA256_Final(it.addressOf(0), context.ptr)
    }
    digest.asByteArray()
}
