/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SourceKeyForTest {
    @Test
    fun `equivalent source URLs share one cache directory`() {
        val expected = "A74A541C00ECA54F0B7D54BFCD8580E6BBFE49E4"
        val urls = listOf(
            "magnet:?xt=urn:btih:$expected",
            "magnet:?dn=episode&xt=urn:btih:${expected.lowercase()}&tr=https://tracker.example.org",
            "magnet:?xt=urn:btih:U5FFIHAA5SSU6C35KS743BMA42574SPE",
            "https://mikanani.me/Download/20260911/${expected.lowercase()}.torrent",
        )
        for (url in urls) assertEquals(expected, sourceKeyFor(url), url)
    }

    @Test
    fun `URLs without a recognised infohash use a stable digest`() {
        val vectors = mapOf(
            "https://nyaa.si/download/2157637.torrent" to "h-3453a47ff8a7a4c9",
            "magnet:?dn=missing-xt" to "h-868c19a912c110e9",
            "magnet:?xt=urn:btih:DEADBEEFDEADBEEF" to "h-4675892f0793a0d9",
        )
        for ((url, expected) in vectors) assertEquals(expected, sourceKeyFor(url), url)
    }

    @Test
    fun `fallback distinguishes URLs with identical String hash codes`() {
        val first = "https://example.org/Aa.torrent"
        val second = "https://example.org/BB.torrent"
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(sourceKeyFor(first), sourceKeyFor(second))
    }
}
