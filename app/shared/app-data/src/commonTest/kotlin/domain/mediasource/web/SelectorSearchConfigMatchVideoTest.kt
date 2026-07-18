/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectorSearchConfigMatchVideoTest {
    @Test
    fun `old JSON defaults optional scanners to disabled`() {
        val decoded = Json.decodeFromString<SelectorSearchConfig>("""{"matchVideo":{}}""")

        assertFalse(decoded.matchVideo.scanDomMediaUrls)
        assertFalse(decoded.matchVideo.scanInlineScriptUrls)
    }

    @Test
    fun `DOM scanning survives JSON round trip`() {
        val original = SelectorSearchConfig.MatchVideoConfig(scanDomMediaUrls = true)

        val encoded = Json.encodeToString(original)
        val decoded = Json.decodeFromString<SelectorSearchConfig.MatchVideoConfig>(encoded)

        assertTrue("\"scanDomMediaUrls\":true" in encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `inline script scanning survives JSON round trip`() {
        val original = SelectorSearchConfig.MatchVideoConfig(scanInlineScriptUrls = true)

        val encoded = Json.encodeToString(original)
        val decoded = Json.decodeFromString<SelectorSearchConfig.MatchVideoConfig>(encoded)

        assertTrue("\"scanInlineScriptUrls\":true" in encoded)
        assertEquals(original, decoded)
    }
}
