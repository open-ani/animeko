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

class DomMediaUrlCollectorTest {
    @Test
    fun `web view config keeps DOM scanning disabled by default`() {
        assertFalse(WebViewConfig().scanDomMediaUrls)
    }

    @Test
    fun `resolves relative DOM URL against document URL`() {
        val collector = DomMediaUrlCollector()

        assertEquals(
            "https://example.com/player/media/video.m3u8",
            collector.collect(
                baseUrl = "https://example.com/player/index.html",
                rawUrl = "media/video.m3u8",
            ),
        )
    }

    @Test
    fun `deduplicates equivalent absolute and relative DOM URLs`() {
        val collector = DomMediaUrlCollector()

        assertEquals(
            "https://example.com/player/video.mp4",
            collector.collect("https://example.com/player/index.html", "video.mp4"),
        )
        assertNull(
            collector.collect(
                "https://example.com/another-page.html",
                "https://example.com/player/video.mp4",
            ),
        )
    }
}
