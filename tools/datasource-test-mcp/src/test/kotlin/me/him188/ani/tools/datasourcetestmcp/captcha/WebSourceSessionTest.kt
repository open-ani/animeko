/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.captcha

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.mediasource.web.BlockReason
import me.him188.ani.app.domain.mediasource.web.PageEvaluator
import me.him188.ani.app.domain.mediasource.web.PageExpectation
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.domain.mediasource.web.WebCaptchaKind
import me.him188.ani.app.domain.mediasource.web.captcha.CaptchaSolver
import me.him188.ani.app.domain.mediasource.web.captcha.SolveContext
import me.him188.ani.app.domain.mediasource.web.captcha.SolveOutcome
import me.him188.ani.app.domain.mediasource.web.captcha.UnsupportedCaptchaBrowserFactory
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceCookieJar
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceIdentityRegistry
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/**
 * MCP 是无人值守的评测工具, 所以 [WebSourceSession.fetchPage] 只有两种结局:
 * 验证码被自动解掉后拿到真页面, 或者抛 [CaptchaUnsolvedException] 让调用方终止这个源的流程 ——
 * 绝不会把验证页当成正常页面返回.
 */
class WebSourceSessionTest {
    private val searchUrl = "https://example.com/search?wd=x"
    private val expectation = PageExpectation.SearchResults(SelectorSearchConfig.Empty)

    /**
     * @param backgroundScope 必须用 `runTest` 的 backgroundScope: [WebSessionManager] 会常驻一个清扫协程.
     */
    private fun session(
        backgroundScope: CoroutineScope,
        solvers: List<CaptchaSolver> = emptyList(),
        handler: MockRequestHandler,
    ): WebSourceSession {
        val cookieJar = WebSourceCookieJar()
        return WebSourceSession(
            cookieJar,
            WebSessionManager(
                browserFactory = UnsupportedCaptchaBrowserFactory,
                evaluator = PageEvaluator(),
                cookieJar = cookieJar,
                identityRegistry = WebSourceIdentityRegistry(),
                client = HttpClient(MockEngine(handler)).asScopedHttpClient(),
                backgroundScope = backgroundScope,
                solvers = solvers,
            ),
        )
    }

    @Test
    fun `unsolvable captcha aborts instead of returning the blocked page`() = runTest {
        val session = session(backgroundScope) {
            respond("<html><body>blocked</body></html>", HttpStatusCode.Forbidden)
        }
        val exception = assertFailsWith<CaptchaUnsolvedException> {
            session.fetchPage("test", searchUrl, expectation, 1.seconds)
        }
        assertEquals("example.com", exception.host)
        assertEquals(WebCaptchaKind.Unknown, exception.kind)
    }

    @Test
    fun `solved captcha is reported alongside the retried page`() = runTest {
        var requestCount = 0
        val session = session(backgroundScope, solvers = listOf(AlwaysSolvingSolver)) {
            requestCount++
            if (requestCount == 1) {
                respond("<html><body>blocked</body></html>", HttpStatusCode.Forbidden)
            } else {
                respond("<html><body>content</body></html>", HttpStatusCode.OK)
            }
        }
        val result = session.fetchPage("test", searchUrl, expectation, 1.seconds)
        assertEquals(WebCaptchaKind.Unknown, result.autoSolvedCaptcha)
        assertNull(result.blockReason)
    }

    @Test
    fun `rate limited page is retried once, without treating it as a captcha`() = runTest {
        var requestCount = 0
        val session = session(backgroundScope) {
            requestCount++
            if (requestCount == 1) {
                respond("", HttpStatusCode.TooManyRequests, headersOf("Retry-After", "1"))
            } else {
                respond("<html><body>content</body></html>", HttpStatusCode.OK)
            }
        }
        val result = session.fetchPage("test", searchUrl, expectation, 1.seconds)
        assertEquals(2, requestCount)
        assertNull(result.blockReason)
        assertNull(result.autoSolvedCaptcha)
    }

    private object AlwaysSolvingSolver : CaptchaSolver {
        override val id: String get() = "always-solving"
        override fun canAttempt(reason: BlockReason.Captcha, host: String): Boolean = true
        override suspend fun attempt(ctx: SolveContext): SolveOutcome = SolveOutcome.Solved
    }
}
