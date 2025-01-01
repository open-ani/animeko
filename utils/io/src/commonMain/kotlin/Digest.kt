/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.io

import kotlinx.io.Source

enum class DigestAlgorithm {
    MD5, SHA256, SHA1
}

const val DEFAULT_BUFFER_SIZE: Int = 8 * 1024

expect fun Source.readAndDigest(algorithm: DigestAlgorithm): ByteArray
