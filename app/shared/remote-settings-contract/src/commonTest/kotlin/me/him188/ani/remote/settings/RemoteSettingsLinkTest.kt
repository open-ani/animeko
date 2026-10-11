/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.remote.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class RemoteSettingsLinkTest {
    private val key = "54bb81b5-fd77-4b44-9937-ec7d643e16a2"

    private fun link() = RemoteSettingsLink("192.168.10.5", 43511, key, "6.1.0-beta01", "")

    @Test
    fun roundTripRetainsAllParameters() {
        val parsed = RemoteSettingsLink.parse(link().toUri())
        assertEquals("192.168.10.5", parsed.ip)
        assertEquals(43511, parsed.port)
        assertEquals(key, parsed.accessKey)
        assertEquals("6.1.0-beta01", parsed.appVersion)
        assertEquals("", parsed.userUuid)
        assertEquals(1, parsed.protocolVersion)
    }

    @Test
    fun rejectsPublicLoopbackHostnameAndAmbiguousNumericAddresses() {
        for (ip in
            listOf(
                "127.0.0.1",
                "0.0.0.0",
                "8.8.8.8",
                "tv.local",
                "192.168.001.2",
                "192.168.1.256",
                "::1",
                "2130706433",
                "169.254.1.2",
            )) {
            assertFailsWith<IllegalArgumentException>(ip) {
                RemoteSettingsLink(ip, 12345, key, "6.1", "")
            }
        }
    }

    @Test
    fun rejectsDuplicateCredentialsAndForeignLinks() {
        assertFailsWith<IllegalArgumentException> {
            RemoteSettingsLink.parse(link().toUri() + "&accessKey=$key")
        }
        assertFailsWith<IllegalArgumentException> {
            RemoteSettingsLink.parse(link().toUri().replace("ani:", "http:"))
        }
        assertFailsWith<IllegalArgumentException> {
            RemoteSettingsLink.parse(link().toUri().replace("remote-settings", "qr-login"))
        }
        assertFailsWith<IllegalArgumentException> {
            RemoteSettingsLink("10.1.1.1", 65536, key, "6.1", "")
        }
    }

    @Test
    fun diagnosticsDoNotRevealKey() {
        assertFalse(link().toString().contains(key))
    }
}
