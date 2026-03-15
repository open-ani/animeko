/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatIndexed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.him188.ani.utils.xml.Html

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
        assertTrue(page.matchesRequestedUrl(request.pageUrl))
    }

    @Test
    fun `requested search page can be reused directly after captcha`() {
        val request = WebCaptchaRequest(
            mediaSourceId = "source-a",
            pageUrl = "https://example.com/search/-------------/?wd=frieren",
            kind = WebCaptchaKind.Cloudflare,
        )
        val page = WebCaptchaLoadedPage(
            finalUrl = "https://www.example.com/search/-------------/?wd=frieren",
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

        assertTrue(page.matchesRequestedUrl(request.pageUrl))
        assertTrue(page.isUsableSolvedPage(request))
    }

    @Test
    fun `search page with inline captcha cannot be reused directly`() {
        val request = WebCaptchaRequest(
            mediaSourceId = "source-a",
            pageUrl = "https://example.com/search/-------------/?wd=frieren",
            kind = WebCaptchaKind.Cloudflare,
        )
        val page = WebCaptchaLoadedPage(
            finalUrl = "https://example.com/search/-------------/?wd=frieren",
            html = """
                <div class="msg-jump cor4 pop-box">
                  <input placeholder="请输入验证码" name="verify" />
                  <img class="ds-verify-img" src="/verify/index.html">
                  <button class="verify-submit" data-type="search">提交驗證</button>
                </div>
            """.trimIndent(),
        )

        assertTrue(page.matchesRequestedUrl(request.pageUrl))
        assertEquals(WebCaptchaKind.Image, page.detectMeaningfulCaptcha(request))
        assertFalse(page.isUsableSolvedPage(request))
    }

    @Test
    fun `interactive solve only auto completes when search page has parsed subjects`() {
        val searchConfig = SelectorSearchConfig(
            searchUrl = "https://example.com/search/-------------/?wd={keyword}",
            subjectFormatId = SelectorSubjectFormatIndexed.id,
        )
        val request = WebCaptchaRequest(
            mediaSourceId = "source-a",
            pageUrl = "https://example.com/search/-------------/?wd=frieren",
            kind = WebCaptchaKind.Cloudflare,
            searchProbe = WebCaptchaSearchProbe(searchConfig),
        )
        val page = WebCaptchaLoadedPage(
            finalUrl = "https://example.com/search/-------------/?wd=frieren",
            html = """
                <html>
                  <body>
                    <div class="search-box">
                      <div class="thumb-content">
                        <span class="thumb-txt">Frieren</span>
                      </div>
                      <div class="thumb-menu">
                        <a href="/detail/1.html">查看详情</a>
                      </div>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertTrue(page.shouldAutoCompleteInteractiveSolve(request))
        assertTrue(page.shouldMarkAutoSolveAsSolved(request))
    }

    @Test
    fun `interactive solve auto completes when canonicalized search page has parsed subjects`() {
        val searchConfig = SelectorSearchConfig(
            searchUrl = "https://example.com/search/-------------/?wd={keyword}",
            subjectFormatId = SelectorSubjectFormatIndexed.id,
        )
        val request = WebCaptchaRequest(
            mediaSourceId = "source-a",
            pageUrl = "https://example.com/search/-------------/?wd=frieren",
            kind = WebCaptchaKind.Cloudflare,
            searchProbe = WebCaptchaSearchProbe(searchConfig),
        )
        val page = WebCaptchaLoadedPage(
            finalUrl = "https://example.com/search/index.html?wd=frieren&from=cf",
            html = """
                <html>
                  <body>
                    <div class="search-box">
                      <div class="thumb-content">
                        <span class="thumb-txt">Frieren</span>
                      </div>
                      <div class="thumb-menu">
                        <a href="/detail/1.html">查看详情</a>
                      </div>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertFalse(page.matchesRequestedUrl(request.pageUrl))
        assertTrue(page.shouldAutoCompleteInteractiveSolve(request))
        assertTrue(page.shouldMarkAutoSolveAsSolved(request))
    }

    @Test
    fun `interactive solve does not auto complete on captcha free page without parsed subjects`() {
        val searchConfig = SelectorSearchConfig(
            searchUrl = "https://example.com/search/-------------/?wd={keyword}",
            subjectFormatId = SelectorSubjectFormatIndexed.id,
        )
        val request = WebCaptchaRequest(
            mediaSourceId = "source-a",
            pageUrl = "https://example.com/search/-------------/?wd=frieren",
            kind = WebCaptchaKind.Cloudflare,
            searchProbe = WebCaptchaSearchProbe(searchConfig),
        )
        val page = WebCaptchaLoadedPage(
            finalUrl = "https://example.com/search/-------------/?wd=frieren",
            html = """
                <html>
                  <body>
                    <div class="search-box empty-state">
                      <p>No matches yet</p>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertFalse(page.shouldAutoCompleteInteractiveSolve(request))
        assertFalse(page.shouldMarkAutoSolveAsSolved(request))
    }

    @Test
    fun `search cooldown page is detected from html content`() {
        val document = Html.parse(
            """
                <html>
                  <body>
                    <div class="msg-jump">
                      <p>親愛的：請不要頻繁操作，搜索時間間隔爲3秒前</p>
                      <p><a id="href" href="javascript:history.back(-1);">跳轉</a></p>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertTrue(document.isSearchCooldownPage())
    }

    @Test
    fun `normal search result page is not treated as cooldown page`() {
        val document = Html.parse(
            """
                <html>
                  <body>
                    <div class="video-info-header">
                      <a href="/subject/1">Frieren</a>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertFalse(document.isSearchCooldownPage())
    }
}
