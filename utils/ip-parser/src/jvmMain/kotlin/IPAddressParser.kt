/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.ipparser

import inet.ipaddr.IPAddress
import inet.ipaddr.ipv4.IPv4Address
import inet.ipaddr.ipv6.IPv6Address
import java.net.InetAddress

object IPAddressParser {
    private val ipv4Pattern = Regex(
        """^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$""",
    )

    // https://ihateregex.io/expr/ipv6/
    private val ipv6Pattern = Regex(
        """^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]+|::(ffff(:0{1,4})?:)?((25[0-5]|(2[0-4]|1?[0-9])?[0-9])\.){3}(25[0-5]|(2[0-4]|1?[0-9])?[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1?[0-9])?[0-9])\.){3}(25[0-5]|(2[0-4]|1?[0-9])?[0-9]))$""",
    )

    /**
     * Parse the [ip] string as an IPv4 or IPv6 address.
     *
     * All canonical forms of IPv4 and IPv6 addresses are supported.
     *
     * This function does not throw. If the [ip] string is not a valid IPv4 or IPv6 address, the function returns null.
     */
    fun parse(ip: String): IPAddress? {
        @Suppress("NAME_SHADOWING")
        val ip = ip.removePrefix("::ffff:")
        return try {
            when (val addr = InetAddress.getByName(ip)) {
                is java.net.Inet4Address -> {
                    if (!ipv4Pattern.matches(ip)) {
                        return null
                    }
                    IPv4Address(addr.address)
                }

                is java.net.Inet6Address -> {
                    if (!ipv6Pattern.matches(ip)) {
                        return null
                    }
                    IPv6Address(addr.address)
                }

                else -> null
            }
        } catch (e: Exception) {
            return null
        }
    }
}
