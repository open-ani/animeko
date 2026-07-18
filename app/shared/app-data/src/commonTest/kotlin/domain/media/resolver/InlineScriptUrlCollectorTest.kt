/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import me.him188.ani.datasources.api.matcher.WebViewConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InlineScriptUrlCollectorTest {
    @Test
    fun `web view config keeps inline script scanning disabled by default`() {
        assertFalse(WebViewConfig().scanInlineScriptUrls)
    }

    @Test
    fun `normalizes escaped slash URL and makes it absolute`() {
        val collector = InlineScriptUrlCollector()

        assertEquals(
            "https://media.example.com/video/index.m3u8",
            collector.collect(
                baseUrl = "https://example.com/player/index.html",
                rawUrl = "https:\\/\\/media.example.com\\/video\\/index.m3u8",
            ),
        )
    }

    @Test
    fun `deduplicates escaped and plain forms`() {
        val collector = InlineScriptUrlCollector()

        assertEquals(
            "https://media.example.com/video.mp4",
            collector.collect("https://example.com", "https:\\/\\/media.example.com\\/video.mp4"),
        )
        assertNull(
            collector.collect("https://example.com", "https://media.example.com/video.mp4"),
        )
    }

    @Test
    fun `bounds candidate count and URL length`() {
        val collector = InlineScriptUrlCollector(maxCandidates = 2, maxUrlLength = 64)

        assertEquals(
            "https://example.com/1.m3u8",
            collector.collect("https://example.com", "https://example.com/1.m3u8"),
        )
        assertEquals(
            "https://example.com/2.m3u8",
            collector.collect("https://example.com", "https://example.com/2.m3u8"),
        )
        assertNull(collector.collect("https://example.com", "https://example.com/3.m3u8"))

        val lengthLimited = InlineScriptUrlCollector(maxCandidates = 2, maxUrlLength = 32)
        assertNull(
            lengthLimited.collect(
                "https://example.com",
                "https://example.com/" + "x".repeat(64) + ".m3u8",
            ),
        )
    }

    @Test
    fun `scanner script observes initial and rewritten inline scripts with limits`() {
        val script = inlineScriptUrlScannerScript("bridge.report(url);")

        assertTrue("script:not([src])" in script)
        assertTrue("new MutationObserver" in script)
        assertTrue("characterData: true" in script)
        assertTrue("bridge.report(url);" in script)
        assertTrue("const maxScriptLength = $INLINE_SCRIPT_URL_MAX_SCRIPT_LENGTH;" in script)
        assertTrue("const maxScriptScans = $INLINE_SCRIPT_URL_MAX_SCRIPT_SCANS;" in script)
        assertTrue("const maxCandidates = $INLINE_SCRIPT_URL_MAX_CANDIDATES;" in script)
        assertTrue("__aniInlineScriptUrlScanner" in stopInlineScriptUrlScannerScript())
    }
}
