/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web.captcha

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.mediasource.web.LoadedPage
import me.him188.ani.app.domain.mediasource.web.PageEvaluator
import me.him188.ani.app.domain.mediasource.web.PageExpectation
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.domain.mediasource.web.SolveRequest
import me.him188.ani.app.domain.mediasource.web.WebCaptchaKind
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.list
import me.him188.ani.utils.io.readBytes
import me.him188.ani.utils.io.readText
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class ImageCaptchaSolverTest {
    @Test
    fun `accepts exactly four digits`() {
        assertTrue(isValidImageCaptchaAnswer("1234"))
        assertEquals(false, isValidImageCaptchaAnswer("123"))
        assertEquals(false, isValidImageCaptchaAnswer("12345"))
        assertEquals(false, isValidImageCaptchaAnswer("12a4"))
    }

    @Test
    fun `recognizes submits and validates solved page`() = runTest {
        val session = FakeImageCaptchaBrowser(correctAnswer = "1234")

        val result = attemptWithBrowser(session, ImageCaptchaRecognizer { " 1234 " })

        assertIs<SolveOutcome.Solved>(result)
        assertEquals(1, session.submitCount)
    }

    @Test
    fun `retries twice before reporting blocked`() = runTest {
        val session = FakeImageCaptchaBrowser(correctAnswer = "9876")

        val result = attemptWithBrowser(session, ImageCaptchaRecognizer { "1234" })

        assertIs<SolveOutcome.Failed>(result)
        assertEquals(3, session.submitCount)
        assertEquals(2, session.refreshCount)
    }

    @Test
    fun `retries recognition failures before submitting`() = runTest {
        val session = FakeImageCaptchaBrowser(correctAnswer = "1234")
        var recognitionCount = 0

        val result = attemptWithBrowser(
            session,
            ImageCaptchaRecognizer {
                recognitionCount++
                if (recognitionCount == 3) "1234" else null
            },
        )

        assertIs<SolveOutcome.Solved>(result)
        assertEquals(3, recognitionCount)
        assertEquals(2, session.refreshCount)
        assertEquals(1, session.submitCount)
    }

    @Test
    fun `accepts explicit empty result only at requested search location`() = runTest {
        val valid = FakeImageCaptchaBrowser(
            correctAnswer = "1234",
            solvedHtml = "<html><body>没有找到相关内容</body></html>",
        )
        val homeFallback = FakeImageCaptchaBrowser(
            correctAnswer = "1234",
            solvedFinalUrl = "https://example.com/",
            solvedHtml = "<html><body>没有找到相关内容</body></html>",
        )

        assertIs<SolveOutcome.Solved>(attemptWithBrowser(valid, ImageCaptchaRecognizer { "1234" }, searchRequest))
        assertIs<SolveOutcome.Failed>(
            attemptWithBrowser(homeFallback, ImageCaptchaRecognizer { "1234" }, searchRequest),
        )
    }

    @Test
    fun `waits for captcha bytes to change before retrying recognition`() = runTest {
        val session = FakeImageCaptchaBrowser(
            correctAnswer = "1234",
            staleRefreshesBeforeChange = 2,
        )
        var recognitionCount = 0

        val result = attemptWithBrowser(
            session,
            ImageCaptchaRecognizer {
                recognitionCount++
                if (recognitionCount == 1) "9999" else "1234"
            },
        )

        assertIs<SolveOutcome.Solved>(result)
        assertEquals(2, recognitionCount)
        assertEquals(3, session.refreshCount)
    }

    @Test
    fun `sample collection writes raw images and jsonl manifest`() = runTest {
        val directory = SystemPaths.createTempDirectory("captcha-samples-test")
        try {
            val session = FakeImageCaptchaBrowser(correctAnswer = "1234")

            val result = collectImageCaptchaSamplesToDirectory(
                browser = session,
                request = request,
                count = 2,
                outputDirectory = directory.absolutePath,
            )

            assertEquals(2, result.savedCount)
            assertEquals(3, directory.list().size)
            val manifest = directory.resolve("manifest.jsonl")
                .readText()
                .lineSequence()
                .filter(String::isNotBlank)
                .toList()
            assertEquals(2, manifest.size)
            assertTrue(manifest.all { "\"mediaSourceId\":\"test\"" in it })
            val samples = directory.list()
                .filter { it.name != "manifest.jsonl" }
                .map { it.inSystem.readBytes() }
                .toList()
            assertEquals(2, samples.size)
            assertEquals(false, samples[0].contentEquals(samples[1]))
            assertEquals(1, session.refreshCount)
        } finally {
            directory.deleteRecursively()
        }
    }

    private suspend fun attemptWithBrowser(
        browser: CaptchaBrowser,
        recognizer: ImageCaptchaRecognizer,
        solveRequest: SolveRequest = request,
    ): SolveOutcome {
        val evaluator = PageEvaluator()
        return BrowserImageCaptchaSolver(recognizer).attempt(
            SolveContext(
                request = solveRequest,
                http = HttpClient(MockEngine { error("HTTP is not used by browser solver") }).asScopedHttpClient(),
                acquireBrowser = { browser },
                evaluate = { evaluator.evaluate(it, solveRequest.expectation) },
            ),
        )
    }

    private class FakeImageCaptchaBrowser(
        private val correctAnswer: String,
        private val staleRefreshesBeforeChange: Int = 0,
        private val solvedFinalUrl: String = request.pageUrl,
        private val solvedHtml: String = "<html><body><div class='search-result'>ok</div></body></html>",
    ) : CaptchaBrowser {
        override val userAgent: String = "FakeCaptcha/1.0"
        override val pageLoads: SharedFlow<LoadedPage> = MutableSharedFlow()
        override val isLoading: StateFlow<Boolean> = MutableStateFlow(false)

        var submitCount = 0
        var refreshCount = 0
        private var solved = false
        private var captureReady = false

        override suspend fun navigate(url: String) {
        }

        override suspend fun currentPage(): LoadedPage {
            return if (solved) {
                LoadedPage(solvedFinalUrl, solvedHtml)
            } else {
                LoadedPage(request.pageUrl, blockedHtml(captureReady))
            }
        }

        override suspend fun executeJavaScript(script: String) {
            when {
                "data-state', 'loading'" in script -> captureReady = true
                "_ani_captcha=" in script -> {
                    captureReady = false
                    refreshCount++
                }

                "setter.call" in script -> {
                    submitCount++
                    solved = "'$correctAnswer'" in script
                }
            }
        }

        override suspend fun collectCookies(urls: List<String>): List<BrowserCookie> = emptyList()

        override fun setResourceInterceptor(handler: ((String) -> InterceptDecision)?) {
        }

        @Composable
        override fun View(modifier: Modifier) {
        }

        override fun close() {
        }

        private fun blockedHtml(captureReady: Boolean): String {
            val marker = if (captureReady) {
                val sampleVersion = (refreshCount - staleRefreshesBeforeChange).coerceAtLeast(0)
                val payload = Base64.encode("sample-$sampleVersion".encodeToByteArray())
                "<meta id='ani-image-captcha-sample' data-state='ready' " +
                        "data-source='/verify/index.html' data-image='data:image/png;base64,$payload'>"
            } else {
                ""
            }
            return """
                <html><body>
                  <input name="verify">
                  <img class="ds-verify-img" src="/verify/index.html">
                  <button class="verify-submit" data-type="search">submit</button>
                  $marker
                </body></html>
            """.trimIndent()
        }
    }

    private companion object {
        val request = SolveRequest(
            mediaSourceId = "test",
            pageUrl = "https://example.com/search?q=test",
            kind = WebCaptchaKind.Image,
            expectation = PageExpectation.AnyContent,
        )
        val searchRequest = request.copy(
            expectation = PageExpectation.SearchResults(
                SelectorSearchConfig(searchUrl = "https://example.com/search?q={keyword}"),
            ),
        )
    }
}
