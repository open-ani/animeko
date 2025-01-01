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
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

private val logger = logger("IpSeqRange")

internal actual fun createIpSeqRange(ipSeqPattern: String): IpSeqRange {
    return object : IpSeqRange {
        private val range = try {
            IPAddressString(ipSeqPattern).address
        } catch (ex: AddressStringException) {
            logger.warn(ex) { "failed to parse ip range $ipSeqPattern" }
            null
        }

        override fun contains(address: String): Boolean {
            if (range == null) return false
            val ipAddress: IPAddress = try {
                NoValidationIPAddressString(address).address
                    ?: return false
            } catch (_: AddressStringException) {
                return false
            }

            return range.contains(ipAddress)
        }
    }
}

private class NoValidationIPAddressString(addr: String) : IPAddressString(addr) {
    override fun validate() {
        // NO-OP
    }

    override fun validateIPv4() {
        // NO-OP
    }

    override fun validateIPv6() {
        // NO-OP
    }
}
