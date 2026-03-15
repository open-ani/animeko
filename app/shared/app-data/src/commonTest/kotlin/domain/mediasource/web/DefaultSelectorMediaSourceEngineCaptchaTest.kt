/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DefaultSelectorMediaSourceEngineCaptchaTest {
    private fun createEngine(
        status: HttpStatusCode = HttpStatusCode.OK,
        expectSuccess: Boolean = false,
        responder: (url: String) -> String,
    ): DefaultSelectorMediaSourceEngine {
        val client = HttpClient(MockEngine { request ->
            respond(
                content = responder(request.url.toString()),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
            )
        }) {
            this.expectSuccess = expectSuccess
        }
        return DefaultSelectorMediaSourceEngine(client.asScopedHttpClient())
    }

    @Test
    fun `searchSubjects returns captcha kind instead of document`() = runTest {
        val engine = createEngine { _ ->
            """
            <html>
              <head><title>Just a moment...</title></head>
              <body>
                <div id="challenge-error-text">Enable JavaScript and cookies to continue</div>
                <script>
                  window._cf_chl_opt = { cType: "managed" };
                </script>
              </body>
            </html>
            """.trimIndent()
        }

        val result = engine.searchSubjects(
            searchUrl = "https://example.com/search/{keyword}",
            subjectName = "test subject",
            useOnlyFirstWord = false,
            removeSpecial = false,
        )

        assertEquals(WebCaptchaKind.Cloudflare, result.captchaKind)
        assertNull(result.document)
    }

    @Test
    fun `searchSubjects decodes quoted html body before detecting captcha`() = runTest {
        val engine = createEngine { _ ->
            "\"<html><body><div class=\\\"cf-turnstile\\\"></div></body></html>\""
        }

        val result = engine.searchSubjects(
            searchUrl = "https://example.com/search/{keyword}",
            subjectName = "test",
            useOnlyFirstWord = false,
            removeSpecial = false,
        )

        assertEquals(WebCaptchaKind.CloudflareTurnstile, result.captchaKind)
        assertNull(result.document)
    }

    @Test
    fun `searchSubjects treats 403 as captcha even without body markers`() = runTest {
        val engine = createEngine(status = HttpStatusCode.Forbidden) { _ ->
            "<html><body>Forbidden</body></html>"
        }

        val result = engine.searchSubjects(
            searchUrl = "https://example.com/search/{keyword}",
            subjectName = "test",
            useOnlyFirstWord = false,
            removeSpecial = false,
        )

        assertEquals(WebCaptchaKind.Unknown, result.captchaKind)
        assertNull(result.document)
    }

    @Test
    fun `searchSubjects treats 403 as captcha when client throws before execute`() = runTest {
        val engine = createEngine(
            status = HttpStatusCode.Forbidden,
            expectSuccess = true,
        ) { _ ->
            "<html><body>Forbidden</body></html>"
        }

        val result = engine.searchSubjects(
            searchUrl = "https://example.com/search/{keyword}",
            subjectName = "test",
            useOnlyFirstWord = false,
            removeSpecial = false,
        )

        assertEquals(WebCaptchaKind.Unknown, result.captchaKind)
        assertNull(result.document)
    }

    @Test
    fun `searchSubjects treats 468 as captcha when client throws before execute`() = runTest {
        val engine = createEngine(
            status = HttpStatusCode(468, "Captcha Required"),
            expectSuccess = true,
        ) { _ ->
            """
            <html>
              <head>
                <link rel="icon" href="/.safeline/static/favicon.png" type="image/png">
              </head>
              <body>
                <div id="slg-box"></div>
                <script>window.product_data = {};</script>
              </body>
            </html>
            """.trimIndent()
        }

        val result = engine.searchSubjects(
            searchUrl = "https://example.com/search/{keyword}",
            subjectName = "test",
            useOnlyFirstWord = false,
            removeSpecial = false,
        )

        assertEquals(WebCaptchaKind.Unknown, result.captchaKind)
        assertNull(result.document)
    }

    @Test
    fun `searchSubjects treats 404 as empty result when client throws before execute`() = runTest {
        val engine = createEngine(
            status = HttpStatusCode.NotFound,
            expectSuccess = true,
        ) { _ ->
            "<html><body>Not found</body></html>"
        }

        val result = engine.searchSubjects(
            searchUrl = "https://example.com/search/{keyword}",
            subjectName = "test",
            useOnlyFirstWord = false,
            removeSpecial = false,
        )

        assertNull(result.captchaKind)
        assertNull(result.document)
    }

    @Test
    fun `doHttpGet throws captcha exception when detail page is blocked`() = runTest {
        val engine = createEngine { _ ->
            """
            <html>
              <body>
                <form id="login">
                  <img src="/captcha.png" alt="captcha" />
                  <input name="captcha" />
                </form>
              </body>
            </html>
            """.trimIndent()
        }

        val exception = kotlin.test.assertFailsWith<RepositoryException> {
            engine.doHttpGet("https://example.com/detail")
        }
        val cause = generateSequence(exception as Throwable) { it.cause }
            .filterIsInstance<WebPageCaptchaException>()
            .first()

        assertEquals("https://example.com/detail", cause.url)
        assertEquals(WebCaptchaKind.Image, cause.kind)
    }

    @Test
    fun `doHttpGet treats 403 as captcha even without body markers`() = runTest {
        val engine = createEngine(status = HttpStatusCode.Forbidden) { _ ->
            "<html><body>Forbidden</body></html>"
        }

        val exception = kotlin.test.assertFailsWith<RepositoryException> {
            engine.doHttpGet("https://example.com/detail")
        }
        val cause = generateSequence(exception as Throwable) { it.cause }
            .filterIsInstance<WebPageCaptchaException>()
            .first()

        assertEquals("https://example.com/detail", cause.url)
        assertEquals(WebCaptchaKind.Unknown, cause.kind)
    }

    @Test
    fun `doHttpGet treats 468 as captcha when client throws before execute`() = runTest {
        val engine = createEngine(
            status = HttpStatusCode(468, "Captcha Required"),
            expectSuccess = true,
        ) { _ ->
            """
            <html>
              <head>
                <link rel="icon" href="/.safeline/static/favicon.png" type="image/png">
              </head>
              <body>
                <div id="slg-box"></div>
                <script>window.product_data = {};</script>
              </body>
            </html>
            """.trimIndent()
        }

        val exception = kotlin.test.assertFails {
            engine.doHttpGet("https://example.com/detail")
        }
        val cause = generateSequence(exception as Throwable) { it.cause }
            .filterIsInstance<WebPageCaptchaException>()
            .first()

        assertEquals("https://example.com/detail", cause.url)
        assertEquals(WebCaptchaKind.Unknown, cause.kind)
    }

    @Test
    fun `searchSubjects still parses normal html`() = runTest {
        val engine = createEngine { url ->
            """
            <html>
              <body>
                <a class="subject-link" href="$url">subject</a>
              </body>
            </html>
            """.trimIndent()
        }

        val result = engine.searchSubjects(
            searchUrl = "https://example.com/search/{keyword}",
            subjectName = "test",
            useOnlyFirstWord = false,
            removeSpecial = false,
        )

        assertNull(result.captchaKind)
        assertNotNull(result.document)
        assertEquals("subject", result.document.selectFirst(".subject-link")?.text())
    }
}
