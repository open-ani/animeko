/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import kotlinx.io.bytestring.encodeToByteString
import me.him188.ani.utils.io.DigestAlgorithm
import me.him188.ani.utils.io.digest

internal fun sourceKeyFor(uri: String): String {
    magnetInfoHash(uri)?.let { return it }
    torrentFileNameInfoHash(uri)?.let { return it }

    return "h-" + sha256HexShort(uri)
}

private fun magnetInfoHash(uri: String): String? =
    Regex("xt=urn:btih:([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)
        .find(uri)?.groupValues?.get(1)?.let(::canonicalizeBtih)

private fun torrentFileNameInfoHash(uri: String): String? {
    if (!uri.startsWith("http", ignoreCase = true)) return null
    val fileName = uri.substringBefore('?').substringBefore('#').substringAfterLast('/')
    return canonicalizeBtih(fileName.substringBeforeLast('.'))
}

private fun canonicalizeBtih(raw: String): String? {
    val upper = raw.uppercase()
    return when {
        upper.length == 40 && upper.all { it in HEX_ALPHABET } -> upper
        upper.length == 64 && upper.all { it in HEX_ALPHABET } -> upper
        upper.length == 32 && upper.all { it in BASE32_ALPHABET } -> base32Decode(upper).toHexString().uppercase()
        else -> null
    }
}

private const val HEX_ALPHABET = "0123456789ABCDEF"
private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

private fun base32Decode(s: String): ByteArray {
    val out = ByteArray(s.length * 5 / 8)
    var buffer = 0
    var bitsInBuffer = 0
    var next = 0
    for (c in s) {
        buffer = (buffer shl 5) or BASE32_ALPHABET.indexOf(c)
        bitsInBuffer += 5
        if (bitsInBuffer >= 8) {
            bitsInBuffer -= 8
            out[next++] = ((buffer shr bitsInBuffer) and 0xFF).toByte()
        }
    }
    return out
}

private fun sha256HexShort(input: String): String =
    input.encodeToByteString().digest(DigestAlgorithm.SHA256).toHexString().take(16)
