/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.him188.ani.app.domain.mediasource.web.BlockReason
import me.him188.ani.app.domain.mediasource.web.LoadedPage
import me.him188.ani.app.domain.mediasource.web.PageEvaluator
import me.him188.ani.app.domain.mediasource.web.PageExpectation
import me.him188.ani.app.domain.mediasource.web.PageVerdict
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.domain.mediasource.web.SolveRequest
import me.him188.ani.app.domain.mediasource.web.WebCaptchaKind
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatIndexed
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * [WebSessionManager] 的 commonTest 覆盖, 用 [FakeCaptchaBrowser] (脚本化页面) 与 ktor MockEngine.
 * 用例编号对应 docs/dev/media/web-captcha.md "测试" 一节.
 */
class WebSessionManagerTest {
    private val searchConfig = SelectorSearchConfig(
        searchUrl = "https://example.com/search/?wd={keyword}",
        subjectFormatId = SelectorSubjectFormatIndexed.id,
    )

    private val searchUrl = "https://example.com/search/?wd=frieren"
    private val expectation get() = PageExpectation.SearchResults(searchConfig)

    private val parseableHtml = """
        <html><body>
          <div class="search-box">
            <div class="thumb-content"><span class="thumb-txt">Frieren</span></div>
            <div class="thumb-menu"><a href="/detail/1.html">查看详情</a></div>
          </div>
        </body></html>
    """.trimIndent()

    private val challengeHtml =
        "<html><title>Just a moment...</title><div id=\"challenge-error-text\">Enable JavaScript and cookies to continue</div></html>"

    private class Fixture(
        scope: TestScope,
        var httpResponder: (url: String) -> Pair<String, HttpStatusCode> = { "" to HttpStatusCode.OK },
    ) {
        val factory = FakeCaptchaBrowserFactory()
        val cookieJar = WebSourceCookieJar()
        val identityRegistry = WebSourceIdentityRegistry()
        val client = HttpClient(
            MockEngine { request ->
                val (content, status) = httpResponder(request.url.toString())
                respond(
                    content = content,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
                )
            },
        ) {
            expectSuccess = true
            install(HttpCookies)
        }.asScopedHttpClient()

        /**
         * 手动时钟. 不能用 `testScheduler.currentTime`: runTestWithMainDispatcher 在测试协程等待真实线程
         * (ktor 引擎) 时会跳过 sweep 循环的 delay 无限推进虚拟时间, 几毫秒真实等待就能
         * 推进数小时, 导致暖会话被闲置 TTL 误回收.
         */
        var nowMillis: Long = 1_000_000

        val manager = WebSessionManager(
            browserFactory = factory,
            evaluator = PageEvaluator(),
            cookieJar = cookieJar,
            identityRegistry = identityRegistry,
            client = client,
            backgroundScope = scope.backgroundScope,
            getTimeMillis = { nowMillis },
            // 留在测试调度器上, 让浏览器创建/关闭与断言确定性同步
            ioContext = EmptyCoroutineContext,
        )
    }

    private fun solveRequest() = SolveRequest(
        mediaSourceId = "test-source",
        pageUrl = searchUrl,
        kind = WebCaptchaKind.Cloudflare,
        expectation = expectation,
    )

    private fun runTestExt(block: suspend TestScope.() -> Unit): TestResult {
        return runTest {
            try {
                Dispatchers.setMain(StandardTestDispatcher(testScheduler))
                block()
            } finally {
                Dispatchers.resetMain()
            }
        }
    }

    // 关键用例 8 (v1): 空 solver 列表 → solve(auto) 立即失败, 不创建浏览器
    @Test
    fun `auto solve with empty solvers fails immediately without browser`() = runTestExt {
        val fixture = Fixture(this)

        val outcome = fixture.manager.solve(solveRequest(), interactive = false)

        assertIs<SolveOutcome.Failed>(outcome)
        assertEquals(0, fixture.factory.createCount)
        assertNull(fixture.manager.interactiveUi.value)
    }

    // 关键用例 4: HTTP 429 → RateLimited, 不创建浏览器
    @Test
    fun `http 429 yields RateLimited and does not create browser`() = runTestExt {
        val fixture = Fixture(this) { _ -> "too many" to HttpStatusCode.TooManyRequests }

        val verdict = fixture.manager.fetchPage(searchUrl, expectation)

        val blocked = assertIs<PageVerdict.Blocked>(verdict)
        assertIs<BlockReason.RateLimited>(blocked.reason)
        assertEquals(0, fixture.factory.createCount)
    }

    // 关键用例 2: interactive solve 必定弹框 (无陈旧缓存路径); 成功后 cookie 与 UA 同步
    @Test
    fun `interactive solve always presents dialog and syncs identity on success`() = runTestExt {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(this) { _ -> challengeHtml to HttpStatusCode.Forbidden }

        // 第一次 solve
        val result1 = async { fixture.manager.solve(solveRequest(), interactive = true) }
        val ui1 = fixture.manager.interactiveUi.first { it != null }!!
        val browser = fixture.factory.created.single()
        browser.script(finalUrl = searchUrl, html = parseableHtml)
        assertEquals(SolveOutcome.Solved, result1.await())
        assertNull(fixture.manager.interactiveUi.value) // 对话框已关闭

        // cookie 与 UA 已同步到 HTTP 侧
        assertEquals(listOf("cf_clearance=solved"), fixture.cookieJar.getCookieHeaderValues("https://example.com/"))
        assertEquals("FakeBrowserUA/1.0", fixture.identityRegistry.userAgentFor("example.com"))

        // 第二次 solve: 入口不查任何缓存, 必定再次弹框
        val result2 = async { fixture.manager.solve(solveRequest(), interactive = true) }
        val ui2 = fixture.manager.interactiveUi.first { it != null }!!
        assertTrue(ui1 !== ui2)
        ui2.onDismiss()
        assertEquals(SolveOutcome.Cancelled, result2.await())
    }

    // 关键用例 5: 同 host 并发 solve → single-flight, 只弹一个对话框, 只建一个浏览器
    @Test
    fun `concurrent solves for same host are single flight`() = runTestExt {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(this) { _ -> challengeHtml to HttpStatusCode.Forbidden }

        val result1 = async { fixture.manager.solve(solveRequest(), interactive = true) }
        fixture.manager.interactiveUi.first { it != null }
        val result2 = async { fixture.manager.solve(solveRequest(), interactive = true) }

        val browser = fixture.factory.created.single()
        browser.script(finalUrl = searchUrl, html = parseableHtml)

        assertEquals(SolveOutcome.Solved, result1.await())
        assertEquals(SolveOutcome.Solved, result2.await())
        assertEquals(1, fixture.factory.createCount)
    }

    // 关键用例 3: solve 成功后同 host 再次 Blocked → 自动失效, 丢弃暖会话与 cookie
    @Test
    fun `blocked again after solve invalidates warm session and cookies`() = runTestExt {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(this) { _ -> challengeHtml to HttpStatusCode.Forbidden }

        val result = async { fixture.manager.solve(solveRequest(), interactive = true) }
        fixture.manager.interactiveUi.first { it != null }
        val browser = fixture.factory.created.single()
        browser.script(finalUrl = searchUrl, html = parseableHtml)
        assertEquals(SolveOutcome.Solved, result.await())
        assertTrue(fixture.cookieJar.getCookieHeaderValues("https://example.com/").isNotEmpty())

        // 站点再次挑战: 直连被挡, 暖会话重载也被挡
        browser.script(finalUrl = searchUrl, html = challengeHtml)
        val verdict = fixture.manager.fetchPage(searchUrl, expectation)

        val blocked = assertIs<PageVerdict.Blocked>(verdict)
        assertIs<BlockReason.Captcha>(blocked.reason)
        // 自动失效: 浏览器已关闭, cookie 已清
        assertTrue(browser.closed)
        assertEquals(emptyList(), fixture.cookieJar.getCookieHeaderValues("https://example.com/"))
    }

    // 暖会话重载成功: 直连被挡时用浏览器拿到结果, 不上抛
    @Test
    fun `warm session browser reload rescues blocked http fetch`() = runTestExt {
        val fixture = Fixture(this) { _ -> challengeHtml to HttpStatusCode.Forbidden }

        val result = async { fixture.manager.solve(solveRequest(), interactive = true) }
        fixture.manager.interactiveUi.first { it != null }
        val browser = fixture.factory.created.single()
        browser.script(finalUrl = searchUrl, html = parseableHtml)
        assertEquals(SolveOutcome.Solved, result.await())

        // 直连仍被挡, 但暖会话里能加载出结果
        val verdict = fixture.manager.fetchPage(searchUrl, expectation)
        val ok = assertIs<PageVerdict.Ok<*>>(verdict)
        assertEquals(1, (ok.value as List<*>).size)
    }

    // 关键用例 6: 闲置 TTL 回收 → close() 被调用 (防泄漏回归)
    @Test
    fun `idle session is closed after ttl`() = runTestExt {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(this) { _ -> challengeHtml to HttpStatusCode.Forbidden }

        val result = async { fixture.manager.solve(solveRequest(), interactive = true) }
        fixture.manager.interactiveUi.first { it != null }
        val browser = fixture.factory.created.single()
        browser.script(finalUrl = searchUrl, html = parseableHtml)
        assertEquals(SolveOutcome.Solved, result.await())
        assertTrue(!browser.closed)

        // 手动时钟前进 6 分钟 (> 5min TTL), 再推进虚拟时间让 30s 周期的 sweep 跑至少一轮
        fixture.nowMillis += 6.minutes.inWholeMilliseconds
        testScheduler.advanceTimeBy(1.minutes.inWholeMilliseconds)
        testScheduler.runCurrent()

        assertTrue(browser.closed)
    }

    // 直连正常时不碰浏览器
    @Test
    fun `direct http ok does not touch browser`() = runTestExt {
        val fixture = Fixture(this) { _ -> parseableHtml to HttpStatusCode.OK }

        val verdict = fixture.manager.fetchPage(searchUrl, expectation)

        assertIs<PageVerdict.Ok<*>>(verdict)
        assertEquals(0, fixture.factory.createCount)
    }

    // 手动确认 (✓): 以当前页面判决为准
    @Test
    fun `manual confirm records failure when page still blocked`() = runTestExt {
        val fixture = Fixture(this) { _ -> challengeHtml to HttpStatusCode.Forbidden }

        val result = async { fixture.manager.solve(solveRequest(), interactive = true) }
        val ui = fixture.manager.interactiveUi.first { it != null }!!
        val browser = fixture.factory.created.single()
        browser.current = LoadedPage(searchUrl, challengeHtml)
        ui.onConfirm()

        val outcome = result.await()
        val failed = assertIs<SolveOutcome.Failed>(outcome)
        assertNotNull(failed.reason)
        assertNull(fixture.manager.interactiveUi.value)
    }
}

private class FakeCaptchaBrowserFactory : CaptchaBrowserFactory {
    val created = mutableListOf<FakeCaptchaBrowser>()
    val createCount get() = created.size

    override val isSupported: Boolean get() = true

    override suspend fun create(): CaptchaBrowser {
        return FakeCaptchaBrowser().also { created.add(it) }
    }
}

private class FakeCaptchaBrowser : CaptchaBrowser {
    override val userAgent: String get() = "FakeBrowserUA/1.0"

    private val _pageLoads = MutableSharedFlow<LoadedPage>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val pageLoads: SharedFlow<LoadedPage> get() = _pageLoads

    override val isLoading: StateFlow<Boolean> = MutableStateFlow(false)

    /** 当前页面快照; [navigate] 后保持不变, 由测试脚本控制. */
    var current: LoadedPage? = null

    var closed = false
        private set

    var cookies: List<BrowserCookie> = listOf(
        BrowserCookie(name = "cf_clearance", value = "solved", domain = ".example.com", path = "/"),
    )

    /** 设置当前页面并广播加载完成事件. */
    fun script(finalUrl: String, html: String) {
        val page = LoadedPage(finalUrl, html)
        current = page
        _pageLoads.tryEmit(page)
    }

    override suspend fun navigate(url: String) {
    }

    override suspend fun currentPage(): LoadedPage? = current

    override suspend fun collectCookies(urls: List<String>): List<BrowserCookie> = cookies

    override fun setResourceInterceptor(handler: ((url: String) -> InterceptDecision)?) {
    }

    @Composable
    override fun View(modifier: Modifier) {
    }

    override fun close() {
        closed = true
    }
}
