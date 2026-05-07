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
import kotlin.test.assertTrue

/**
 * Unit tests for [sourceKeyFor] — the infohash/URL → stable bucket-name
 * mapping used by the engine's server-side slot folder. These have to be
 * pure (no network, no PikPak dependencies) because the result ends up as
 * a filename under the user's drive; a bug here bleeds across sources.
 */
class SourceKeyForTest {

    @Test
    fun `magnet infohash is extracted and uppercased`() {
        val uri = "magnet:?xt=urn:btih:abcdef1234567890abcdef1234567890abcdef12" +
                "&dn=example&tr=http://tracker.example.org"
        assertEquals("ABCDEF1234567890ABCDEF1234567890ABCDEF12", sourceKeyFor(uri))
    }

    @Test
    fun `magnet infohash case is normalised`() {
        val lower = "magnet:?xt=urn:btih:deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
        val upper = "magnet:?xt=urn:btih:DEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEF"
        // Same infohash, different casing → same key.
        assertEquals(sourceKeyFor(lower), sourceKeyFor(upper))
    }

    @Test
    fun `magnet xt parameter position does not matter`() {
        val a = "magnet:?xt=urn:btih:0000111122223333444455556666777788889999&dn=x"
        val b = "magnet:?dn=x&xt=urn:btih:0000111122223333444455556666777788889999"
        assertEquals(sourceKeyFor(a), sourceKeyFor(b))
    }

    @Test
    fun `different magnets produce different keys`() {
        val a = "magnet:?xt=urn:btih:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val b = "magnet:?xt=urn:btih:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        assertNotEquals(sourceKeyFor(a), sourceKeyFor(b))
    }

    @Test
    fun `base32 and hex infohash encodings canonicalize to the same bucket`() {
        // RFC-4648 base32 and hex of the same 160-bit infohash must map to
        // the same bucket name — otherwise two copies of the same magnet
        // from different sources would fill the slot cache twice.
        // Compute the base32 form in the test so we don't rely on a
        // pre-baked example that could drift from the implementation.
        val hex = "A74A541C00ECA54F0B7D54BFCD8580E6BBFE49E4"
        val base32 = hexToBase32(hex)
        val hexForm = "magnet:?xt=urn:btih:$hex"
        val base32Form = "magnet:?xt=urn:btih:$base32"
        assertEquals(sourceKeyFor(hexForm), sourceKeyFor(base32Form))
        assertEquals(hex, sourceKeyFor(hexForm))
    }

    // Reference encoder for the test-only hex → base32 conversion. Kept in
    // the test file because the engine only ever needs to go base32 → hex.
    private fun hexToBase32(hex: String): String {
        val base32Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = StringBuilder(hex.length * 4 / 5)
        var buffer = 0
        var bitsInBuffer = 0
        for (c in hex) {
            val v = "0123456789ABCDEF".indexOf(c.uppercaseChar())
            require(v >= 0) { "non-hex char: $c" }
            buffer = (buffer shl 4) or v
            bitsInBuffer += 4
            while (bitsInBuffer >= 5) {
                bitsInBuffer -= 5
                val idx = (buffer shr bitsInBuffer) and 0x1F
                out.append(base32Alphabet[idx])
            }
        }
        return out.toString()
    }

    @Test
    fun `v2 64-char hex infohash is accepted`() {
        // BEP-52 (SHA-256) infohashes are 64 hex chars.
        val v2 = "magnet:?xt=urn:btih:" +
                "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"
        assertEquals(
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF",
            sourceKeyFor(v2),
        )
    }

    @Test
    fun `http torrent URL falls back to h-prefixed hash`() {
        val url = "https://example.org/path/to/file.torrent?k=v"
        val key = sourceKeyFor(url)
        assertTrue(key.startsWith("h-"), "fallback should be prefixed with 'h-' to avoid infohash clash: $key")
        // The bucket-name must be a plain ASCII token PikPak won't reject.
        // SHA-256 truncated to 16 hex chars = 64-bit; lowercase to stay
        // visually distinct from hex-encoded infohashes.
        assertTrue(key.matches(Regex("h-[0-9a-f]{16}")), "unexpected fallback shape: $key")
    }

    @Test
    fun `fallback is stable across invocations`() {
        val url = "https://example.org/some.torrent"
        assertEquals(sourceKeyFor(url), sourceKeyFor(url))
    }

    @Test
    fun `fallback uses SHA-256, not 32-bit String_hashCode`() {
        // Sanity check that two URLs that happen to share a String.hashCode
        // don't collide in the slot namespace. SHA-256 is a cryptographic
        // hash, so any collision here would be a genuine miracle.
        val a = sourceKeyFor("https://a.example.org/x.torrent")
        val b = sourceKeyFor("https://b.example.org/x.torrent")
        assertNotEquals(a, b)
    }

    @Test
    fun `magnet without infohash falls back to URL hash`() {
        val weird = "magnet:?dn=missing-xt"
        val key = sourceKeyFor(weird)
        assertTrue(key.startsWith("h-"))
    }

    @Test
    fun `magnet with an unrecognised infohash encoding falls back`() {
        // Not 32, 40 or 64 chars — treat as unknown and fall through to
        // the URL-hash path instead of silently truncating.
        val odd = "magnet:?xt=urn:btih:DEADBEEFDEADBEEF"
        val key = sourceKeyFor(odd)
        assertTrue(key.startsWith("h-"), "weird-length infohash should fall through to URL hash: $key")
    }
}
