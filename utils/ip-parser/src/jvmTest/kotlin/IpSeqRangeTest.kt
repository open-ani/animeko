/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.ipparser

import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IpSeqRangeTest {

    // --- Existing tests ---

    @Test
    fun `invalid range`() {
        assertThrows<IllegalArgumentException> {
            IpSeqRange("invalid")
        }
    }

    @Test
    fun `ipv4 range`() {
        assertEquals(true, IpSeqRange("192.168.1.0/24").contains("192.168.1.252"))
    }

    // --- Additional IPv4 Tests ---

    @Test
    fun `ipv4 single address`() {
        // Single IP as a "range"
        val ipRange = IpSeqRange("10.0.0.1")
        assertTrue(ipRange.contains("10.0.0.1"), "Single IP range should contain itself")
        assertFalse(ipRange.contains("10.0.0.2"), "Single IP range should not contain another address")
    }

    @Test
    fun `ipv4 cidr boundaries`() {
        // /30 means we have addresses from 10.0.0.0 to 10.0.0.3
        val ipRange = IpSeqRange("10.0.0.0/30")
        // Within
        assertTrue(ipRange.contains("10.0.0.0"))
        assertTrue(ipRange.contains("10.0.0.1"))
        assertTrue(ipRange.contains("10.0.0.2"))
        assertTrue(ipRange.contains("10.0.0.3"))
        // Outside
        assertFalse(ipRange.contains("10.0.0.4"))
        assertFalse(ipRange.contains("9.255.255.255"))
        assertFalse(ipRange.contains("10.0.1.0"))
    }

    @Test
    fun `ipv4 large cidr`() {
        // /8 range from 10.0.0.0 to 10.255.255.255
        val ipRange = IpSeqRange("10.0.0.0/8")
        assertTrue(ipRange.contains("10.0.0.1"))
        assertTrue(ipRange.contains("10.255.255.255"))
        // Just outside
        assertFalse(ipRange.contains("11.0.0.0"))
        assertFalse(ipRange.contains("9.255.255.255"))
    }

    @Test
    fun `ipv4 broadcast address`() {
        // /24 from 192.168.0.0 to 192.168.0.255
        val ipRange = IpSeqRange("192.168.0.0/24")
        assertTrue(ipRange.contains("192.168.0.255"), "Should contain broadcast address in that CIDR")
        assertFalse(ipRange.contains("192.168.1.255"))
    }

    @Test
    fun `ipv4 edge cases`() {
        // Test the 0.0.0.0 address
        val ipRange1 = IpSeqRange("0.0.0.0")
        assertTrue(ipRange1.contains("0.0.0.0"))
        assertFalse(ipRange1.contains("0.0.0.1"))

        // Test the maximum IPv4 address as a single IP
        val ipRange2 = IpSeqRange("255.255.255.255")
        assertTrue(ipRange2.contains("255.255.255.255"))
        assertFalse(ipRange2.contains("255.255.255.254"))
    }

    @Test
    fun `ipv4 invalid addresses`() {
        // Ensure that invalid IP addresses throw or simply return false
        val ipRange = IpSeqRange("192.168.1.0/24")

        // These should return false from .contains()
        assertFalse(ipRange.contains("999.999.999.999"))
        assertFalse(ipRange.contains("256.256.256.256"))
        assertFalse(ipRange.contains("not_an_ip"))
    }

    // --- Additional IPv6 Tests ---

    @Test
    fun `ipv6 single address`() {
        // Loopback as single IP
        val ipRange = IpSeqRange("::1")
        assertTrue(ipRange.contains("::1"), "Should contain the loopback address itself")
        assertFalse(ipRange.contains("::2"), "Should not contain another address")
    }

    @Test
    fun `ipv6 short notation`() {
        // Short notation example: fe80::/64 covers fe80:0000:0000:0000:xxxx:xxxx:xxxx:xxxx
        val ipRange = IpSeqRange("fe80::/64")

        // Check within range
        assertTrue(ipRange.contains("fe80::"), "Network address itself")
        assertTrue(ipRange.contains("fe80::1"), "Slight increment")
        assertTrue(ipRange.contains("fe80::abcd:1234"), "Some random short within fe80::/64")

        // Outside
        assertFalse(ipRange.contains("fe80:1::"), "fe80:0001:: is not in fe80::/64")
        assertFalse(ipRange.contains("fe81::"), "Different prefix")
    }

    @Test
    fun `ipv6 long notation`() {
        // A /120 for IPv6 is a fairly small range
        val ipRange = IpSeqRange("2001:db8::/120")

        // This range covers from 2001:db8::0 to 2001:db8::ff for the last 8 bits.
        assertTrue(ipRange.contains("2001:db8::0"))
        assertTrue(ipRange.contains("2001:db8::1"))
        assertTrue(ipRange.contains("2001:db8::ff"))
        assertFalse(ipRange.contains("2001:db8::100")) // 0x100 (256) is outside /120
    }

    @Test
    fun `ipv6 full range`() {
        // The largest possible range for IPv6 is /0, which covers all IPv6 addresses
        val ipRange = IpSeqRange("::/0")
        // Should contain anything
        assertTrue(ipRange.contains("::1"))
        assertTrue(ipRange.contains("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"))
        assertTrue(ipRange.contains("abcd::1234"))
    }

    @Test
    fun `ipv6 edge addresses`() {
        // Single address for the unspecified address
        val ipRange1 = IpSeqRange("::")
        assertTrue(ipRange1.contains("::"), "Unspecified address should contain itself")
        assertFalse(ipRange1.contains("::1"), "Unspecified address does not cover ::1")

        // Maximum possible address in IPv6: ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff
        val ipRange2 = IpSeqRange("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff")
        assertTrue(ipRange2.contains("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"))
        assertFalse(ipRange2.contains("ffff:ffff:ffff:ffff:ffff:ffff:ffff:fffe"))
    }

    @Test
    fun `ipv6 invalid addresses`() {
        val ipRange = IpSeqRange("2001:db8::/32")

        // Check invalid inputs
        assertFalse(ipRange.contains("not_an_ipv6"))
        assertFalse(ipRange.contains("2001:::db8")) // triple colon is invalid
        assertFalse(ipRange.contains("2001:db8:xyz::1")) // invalid hex
        assertFalse(ipRange.contains("2001:db8::ffff:gggg")) // invalid hex block
    }

    // --- Mixed Tests (optional scenarios) ---

    @Test
    fun `mixing ipv4 with ipv6 is invalid pattern`() {
        // Depending on your use case or parser behavior, mixing might throw an exception
        assertThrows<IllegalArgumentException> {
            IpSeqRange("192.168.0.1-2001:db8::")
        }
    }

    @Test
    fun `mixing ipv4 mapped ipv6 - check parser ability`() {
        // Some libraries allow IPv4-mapped IPv6 addresses (e.g. ::ffff:192.168.0.1)
        // But our library does not support this.
        val ipRange = IpSeqRange("::ffff:192.168.0.0/112")
        assertFalse(ipRange.contains("::ffff:192.168.0.1")) // should actually be true
        assertFalse(ipRange.contains("::ffff:192.168.1.1"))
    }
}
