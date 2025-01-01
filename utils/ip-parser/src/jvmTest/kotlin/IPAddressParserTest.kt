/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.ipparser

import kotlin.test.Test
import kotlin.test.assertEquals

class IPAddressParserTest {
    @Test
    fun `parse ipv4`() {
        val ip = "192.168.1.1"
        assertEquals(ip, IPAddressParser.parse(ip)?.toNormalizedString())
    }

    @Test
    fun `parse ipv6`() {
        val ip = "2001:0db8:85a3:0000:0000:8a2e:0370:7334"
        assertEquals(ip, IPAddressParser.parse(ip)?.toFullString())
    }

    @Test
    fun `parse ipv6 missing segment`() {
        val ip = "2001:0db8:85a3:0000:0000::0370:7334"
        assertEquals("2001:0db8:85a3:0000:0000:0000:0370:7334", IPAddressParser.parse(ip)?.toFullString())
    }

    @Test
    fun `parse ipv6 too many colons`() {
        val ip = "2001:0db8:85a3:::0370:7334"
        assertEquals(null, IPAddressParser.parse(ip)?.toFullString())
    }

    @Test
    fun `parse ipv6 missing 2 segments`() {
        val ip = "2001:0db8:85a3::0370:7334"
        assertEquals("2001:0db8:85a3:0000:0000:0000:0370:7334", IPAddressParser.parse(ip)?.toFullString())
    }

    @Test
    fun `parse ipv6 two compressed`() {
        val ip = "2001::85a3::7334"
        assertEquals(null, IPAddressParser.parse(ip)?.toFullString())
    }

    @Test
    fun `parse ipv6 invalid hex`() {
        val ip = "2001:0db8:85a3:0000:0000:8a2q:0370:7334"
        assertEquals(null, IPAddressParser.parse(ip)?.toFullString())
    }

    @Test
    fun `parse v4 on v6`() {
        val ip = "::ffff:192.168.1.1"
        assertEquals("192.168.1.1", IPAddressParser.parse(ip)?.toNormalizedString())
    }

    @Test
    fun `parse invalid v4 on v6`() {
        val ip = "::ffff:192.168.1.256"
        assertEquals(null, IPAddressParser.parse(ip)?.toNormalizedString())
    }

    @Test
    fun `parse empty`() {
        val ip = ""
        assertEquals(null, IPAddressParser.parse(ip)?.toNormalizedString())
    }

    @Test
    fun `parse invalid ipv4`() {
        val ip = "192.168.1.256"
        assertEquals(null, IPAddressParser.parse(ip)?.toNormalizedString())
    }

    @Test
    fun `parse invalid ipv4 missing segment`() {
        val ip = "192.168.1."
        assertEquals(null, IPAddressParser.parse(ip)?.toNormalizedString())
    }


    @Test
    fun `parse ipv4 edge zero`() {
        val ip = "0.0.0.0"
        assertEquals(ip, IPAddressParser.parse(ip)?.toNormalizedString())
    }

    @Test
    fun `parse ipv4 edge broadcast`() {
        val ip = "255.255.255.255"
        assertEquals(ip, IPAddressParser.parse(ip)?.toNormalizedString())
    }

    @Test
    fun `parse ipv4 localhost`() {
        val ip = "127.0.0.1"
        assertEquals(ip, IPAddressParser.parse(ip)?.toNormalizedString())
    }

    @Test
    fun `parse ipv6 unspecified`() {
        val ip = "::"
        // The fully expanded form is all-zero segments
        assertEquals("0000:0000:0000:0000:0000:0000:0000:0000", IPAddressParser.parse(ip)?.toFullString())
    }

    @Test
    fun `parse ipv6 loopback`() {
        val ip = "::1"
        assertEquals("0000:0000:0000:0000:0000:0000:0000:0001", IPAddressParser.parse(ip)?.toFullString())
    }

    @Test
    fun `parse ipv6 link local start`() {
        val ip = "fe80::"
        // Expands the remaining segments to zero
        assertEquals("fe80:0000:0000:0000:0000:0000:0000:0000", IPAddressParser.parse(ip)?.toFullString())
    }

    @Test
    fun `parse ipv6 all ones`() {
        val ip = "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"
        // This is already fully expanded
        assertEquals(ip, IPAddressParser.parse(ip)?.toFullString())
    }

    @Test
    fun `parse ipv6 compressed typical`() {
        val ip = "2001:db8::8a2e:370:7334"
        // Should expand the omitted segments in the middle
        assertEquals("2001:0db8:0000:0000:0000:8a2e:0370:7334", IPAddressParser.parse(ip)?.toFullString())
    }

    @Test
    fun `parse ipv6 uppercase`() {
        val ip = "2001:0DB8:85A3::8A2E:0370:7334"
        // Parser should handle case-insensitivity and expand double-colon
        assertEquals("2001:0db8:85a3:0000:0000:8a2e:0370:7334", IPAddressParser.parse(ip)?.toFullString())
    }

    @Test
    fun `parse invalid short ipv4`() {
        val ip = "1.2.3"
        // Missing one octet
        assertEquals(null, IPAddressParser.parse(ip)?.toNormalizedString())
    }

    @Test
    fun `parse invalid long ipv4`() {
        val ip = "1.2.3.4.5"
        // One octet too many
        assertEquals(null, IPAddressParser.parse(ip)?.toNormalizedString())
    }

    @Test
    fun `parse invalid embedded ipv4 in ipv6`() {
        val ip = "::ffff:192.168.1.1.5"
        // The portion after ffff: should be a valid IPv4, but has 5 groups
        assertEquals(null, IPAddressParser.parse(ip)?.toNormalizedString())
    }
}
