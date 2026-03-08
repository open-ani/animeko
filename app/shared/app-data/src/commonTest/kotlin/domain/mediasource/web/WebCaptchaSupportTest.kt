/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebCaptchaSupportTest {
    @Test
    fun `storageKey is stable for same source and host`() {
        val first = WebCaptchaRequest(
            mediaSourceId = "source-a",
            pageUrl = "https://Example.com/search?q=1",
            kind = WebCaptchaKind.Cloudflare,
        )
        val second = WebCaptchaRequest(
            mediaSourceId = "source-a",
            pageUrl = "https://example.com/detail/2",
            kind = WebCaptchaKind.Image,
        )
        val third = WebCaptchaRequest(
            mediaSourceId = "source-b",
            pageUrl = "https://example.com/detail/2",
            kind = WebCaptchaKind.Image,
        )

        assertEquals(first.storageKey(), second.storageKey())
        assertNotEquals(first.storageKey(), third.storageKey())
    }

    @Test
    fun `displayName matches ui copy`() {
        assertEquals("图片验证码", WebCaptchaKind.Image.displayName())
        assertEquals("Cloudflare 验证", WebCaptchaKind.Cloudflare.displayName())
        assertEquals("Cloudflare Turnstile 验证", WebCaptchaKind.CloudflareTurnstile.displayName())
        assertEquals("验证码", WebCaptchaKind.Unknown.displayName())
    }

    @Test
    fun `selectSolvedSessionKey prefers exact host match`() {
        val selected = selectSolvedSessionKey(
            mediaSourceId = "source-a",
            pageUrl = "https://exact.example.com/play",
            solvedKeys = setOf(
                "source-a@exact.example.com",
                "source-a@fallback.example.com",
            ),
            solvedByMediaSource = mapOf(
                "source-a" to "source-a@fallback.example.com",
            ),
        )

        assertEquals("source-a@exact.example.com", selected)
    }

    @Test
    fun `selectSolvedSessionKey falls back to media source session`() {
        val selected = selectSolvedSessionKey(
            mediaSourceId = "source-a",
            pageUrl = "https://play.example.com/embed/1",
            solvedKeys = setOf("source-a@search.example.com"),
            solvedByMediaSource = mapOf(
                "source-a" to "source-a@search.example.com",
            ),
        )

        assertEquals("source-a@search.example.com", selected)
    }

    @Test
    fun `selectSolvedSessionKey returns null when fallback session is stale`() {
        val selected = selectSolvedSessionKey(
            mediaSourceId = "source-a",
            pageUrl = "https://play.example.com/embed/1",
            solvedKeys = emptySet(),
            solvedByMediaSource = mapOf(
                "source-a" to "source-a@search.example.com",
            ),
        )

        assertNull(selected)
    }

    @Test
    fun `fallback home page is not treated as solved search page`() {
        val request = WebCaptchaRequest(
            mediaSourceId = "source-a",
            pageUrl = "https://example.com/search/-------------/?wd=frieren",
            kind = WebCaptchaKind.Cloudflare,
        )
        val page = WebCaptchaLoadedPage(
            finalUrl = "https://example.com/",
            html = "<html><body><h1>home</h1></body></html>",
        )

        assertTrue(page.isFallbackHomePageFor(request))
        assertEquals(WebCaptchaKind.Cloudflare, page.detectMeaningfulCaptcha(request))
        assertEquals(false, page.isUsableSolvedPage(request))
    }

    @Test
    fun `same host search result is treated as usable solved page`() {
        val request = WebCaptchaRequest(
            mediaSourceId = "source-a",
            pageUrl = "https://example.com/search/-------------/?wd=frieren",
            kind = WebCaptchaKind.Cloudflare,
        )
        val page = WebCaptchaLoadedPage(
            finalUrl = "https://example.com/search/-------------/?wd=frieren",
            html = """
                <html>
                  <body>
                    <div class="video-info-header">
                      <a href="/subject/1">Frieren</a>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertEquals(null, page.detectMeaningfulCaptcha(request))
        assertTrue(page.isUsableSolvedPage(request))
    }
}
