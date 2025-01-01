/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.ipparser

import inet.ipaddr.AddressStringException
import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString

/**
 * Creates a new IpSeqRange instance using the [ipSeqPattern].
 * @throws IllegalArgumentException if the pattern is invalid.
 */
internal actual fun IpSeqRange(ipSeqPattern: String): IpSeqRange {
    val range: IPAddress? = try {
        IPAddressString(ipSeqPattern).address
    } catch (ex: AddressStringException) {
        throw IllegalArgumentException(ex)
    }

    if (range == null) {
        throw IllegalArgumentException("Invalid IP range pattern: '$ipSeqPattern'")
    }

    return JvmIpSeqRange(range)
}

private class JvmIpSeqRange(
    private val range: IPAddress
) : IpSeqRange {
    override fun contains(address: String): Boolean {
        val ipAddress: IPAddress = try {
            IPAddressParser.parse(address)
                ?: return false
        } catch (_: Exception) {
            return false
        }

        return range.contains(ipAddress)
    }

    override fun toString(): String {
        return "JvmIpSeqRange(range=$range)"
    }
}