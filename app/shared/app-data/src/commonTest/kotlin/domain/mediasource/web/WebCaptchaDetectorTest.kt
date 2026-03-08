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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebCaptchaDetectorTest {
    @Test
    fun `detects cloudflare challenge`() {
        assertEquals(
            WebCaptchaKind.Cloudflare,
            WebCaptchaDetector.detect(
                "https://example.com/cdn-cgi/challenge-platform/h/b/orchestrate/jsch/v1",
                "<html><title>Just a moment...</title><div>Checking your browser before accessing</div></html>",
            ),
        )
    }

    @Test
    fun `detects managed cloudflare challenge page sample`() {
        assertEquals(
            WebCaptchaKind.Cloudflare,
            WebCaptchaDetector.detect(
                "https://anime.girigirilove.icu/",
                """
                <!DOCTYPE html>
                <html lang="en-US">
                  <head>
                    <title>Just a moment...</title>
                  </head>
                  <body>
                    <div id="challenge-error-text">Enable JavaScript and cookies to continue</div>
                    <script>
                      window._cf_chl_opt = { cType: 'managed', cUPMDTk: "/?__cf_chl_tk=abc" };
                    </script>
                    <script src="/cdn-cgi/challenge-platform/h/g/orchestrate/chl_page/v1?ray=123"></script>
                  </body>
                </html>
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `detects safeline challenge page as unknown captcha`() {
        assertEquals(
            WebCaptchaKind.Unknown,
            WebCaptchaDetector.detect(
                "https://www.cycani.org/search.html?wd=test",
                """
                <!DOCTYPE html>
                <html>
                  <head>
                    <link rel="icon" href="/.safeline/static/favicon.png" type="image/png">
                    <title id="slg-title"></title>
                  </head>
                  <body>
                    <div id="slg-bg"></div>
                    <div id="slg-box"></div>
                    <script>window.product_data = {"favicon":"test"};</script>
                  </body>
                </html>
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `detects turnstile challenge`() {
        assertEquals(
            WebCaptchaKind.CloudflareTurnstile,
            WebCaptchaDetector.detect(
                "https://example.com/search",
                "<div class='cf-turnstile'></div><script src='https://challenges.cloudflare.com/turnstile/v0/api.js'></script>",
            ),
        )
    }

    @Test
    fun `detects image captcha`() {
        assertEquals(
            WebCaptchaKind.Image,
            WebCaptchaDetector.detect(
                "https://example.com/search",
                "<html><img src='/captcha.png' alt='captcha'><label>verification code</label></html>",
            ),
        )
    }

    @Test
    fun `ignores normal page`() {
        assertNull(
            WebCaptchaDetector.detect(
                "https://example.com/search?q=test",
                "<html><body><a href='/episode/1'>Episode 1</a></body></html>",
            ),
        )
    }

    @Test
    fun `ignores generic cloudflare mention without challenge markers`() {
        assertNull(
            WebCaptchaDetector.detect(
                "https://example.com/search?q=test",
                """
                <html>
                  <body>
                    <footer>Protected by Cloudflare CDN</footer>
                    <div>Search results here</div>
                  </body>
                </html>
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `ignores just a moment copy without challenge markers`() {
        assertNull(
            WebCaptchaDetector.detect(
                "https://example.com/search?q=test",
                """
                <html>
                  <head>
                    <title>Just a moment with Test Subject</title>
                  </head>
                  <body>
                    <div>Just a moment, loading recommendation cards.</div>
                    <div>Cloudflare cache warmup complete.</div>
                  </body>
                </html>
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `ignores normal page with injected cloudflare browser script`() {
        assertNull(
            WebCaptchaDetector.detect(
                "https://eacg.net/vodsearch/-------------.html?wd=test",
                """
                <html>
                  <head>
                    <title>Test Subject_搜索结果 - E-ACG</title>
                  </head>
                  <body>
                    <div>Search results here</div>
                    <script>
                      (function(){
                        function c(){
                          var d = document.createElement('script');
                          d.innerHTML = "window.__CF${'$'}cv${'$'}params={r:'123',t:'456'};";
                          var a = document.createElement('script');
                          a.src = '/cdn-cgi/challenge-platform/scripts/jsd/main.js';
                          document.getElementsByTagName('head')[0].appendChild(a);
                        }
                        c();
                      }());
                    </script>
                  </body>
                </html>
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `storage key normalizes www host`() {
        val request = WebCaptchaRequest(
            mediaSourceId = "source-1",
            pageUrl = "https://www.example.com/search?q=test",
            kind = WebCaptchaKind.Cloudflare,
        )

        assertEquals("source-1@example.com", request.storageKey())
    }

    @Test
    fun `solved page relevance ignores about blank`() {
        val request = WebCaptchaRequest(
            mediaSourceId = "source-1",
            pageUrl = "https://example.com/search",
            kind = WebCaptchaKind.Cloudflare,
        )

        val blankPage = WebCaptchaLoadedPage(
            finalUrl = "about:blank",
            html = "",
        )

        assertFalse(blankPage.isRelevantFor(request))
        assertEquals(WebCaptchaKind.Cloudflare, blankPage.detectMeaningfulCaptcha(request))
    }

    @Test
    fun `solved page relevance ignores chrome error page`() {
        val request = WebCaptchaRequest(
            mediaSourceId = "source-1",
            pageUrl = "https://example.com/search",
            kind = WebCaptchaKind.Cloudflare,
        )

        val errorPage = WebCaptchaLoadedPage(
            finalUrl = "chrome-error://chromewebdata/",
            html = "<html></html>",
        )

        assertFalse(errorPage.isRelevantFor(request))
        assertEquals(WebCaptchaKind.Cloudflare, errorPage.detectMeaningfulCaptcha(request))
    }

    @Test
    fun `solved page relevance accepts www variant on same site`() {
        val request = WebCaptchaRequest(
            mediaSourceId = "source-1",
            pageUrl = "https://example.com/search",
            kind = WebCaptchaKind.Cloudflare,
        )
        val page = WebCaptchaLoadedPage(
            finalUrl = "https://www.example.com/result",
            html = "<html><body><div>ok</div></body></html>",
        )

        assertTrue(page.isRelevantFor(request))
        assertNull(page.detectMeaningfulCaptcha(request))
    }
}
