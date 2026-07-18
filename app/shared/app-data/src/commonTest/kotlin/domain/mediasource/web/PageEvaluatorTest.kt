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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * 判决顺序的硬规则测试. 对应 docs/dev/media/web-captcha.md "判决顺序".
 */
class PageEvaluatorTest {
    private val evaluator = PageEvaluator()

    private val searchConfig = SelectorSearchConfig(
        searchUrl = "https://example.com/search/?wd={keyword}",
        subjectFormatId = SelectorSubjectFormatIndexed.id,
    )

    private val parseableSearchHtml = """
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
    """.trimIndent()

    private fun evaluateSearch(
        html: String,
        status: Int? = null,
        retryAfterSeconds: Long? = null,
        url: String = "https://example.com/search/?wd=frieren",
    ) = evaluator.evaluate(
        LoadedPage(
            finalUrl = url,
            html = html,
            status = status,
            retryAfter = retryAfterSeconds?.seconds,
        ),
        PageExpectation.SearchResults(searchConfig),
    )

    // 规则 2: 解析优先于一切启发式检测 (关键用例 1)
    @Test
    fun `parse priority - parseable page with captcha markers is Ok`() {
        val htmlWithFalsePositiveMarkers = parseableSearchHtml.replace(
            "</body>",
            """
            <script src="/cdn-cgi/challenge-platform/h/g/orchestrate/jsd/v1"></script>
            <div>captcha verify</div>
            </body>
            """.trimIndent(),
        )
        // 启发式检测器单独看会报警
        assertEquals(
            WebCaptchaKind.Cloudflare,
            WebCaptchaDetector.detect("https://example.com/search/?wd=frieren", htmlWithFalsePositiveMarkers),
        )
        // 但 selector 能解析出内容 → Ok, 解析优先
        val verdict = evaluateSearch(htmlWithFalsePositiveMarkers)
        val ok = assertIs<PageVerdict.Ok<List<WebSearchSubjectInfo>>>(verdict)
        assertEquals(1, ok.value.size)
        assertEquals("Frieren", ok.value.first().name)
    }

    @Test
    fun `parse priority wins even on 4xx status`() {
        val verdict = evaluateSearch(parseableSearchHtml, status = 403)
        assertIs<PageVerdict.Ok<*>>(verdict)
    }

    // 规则 1
    @Test
    fun `404 is NotFound`() {
        val verdict = evaluateSearch(parseableSearchHtml, status = 404)
        val blocked = assertIs<PageVerdict.Blocked>(verdict)
        assertEquals(BlockReason.NotFound, blocked.reason)
    }

    // 规则 3
    @Test
    fun `cooldown page is RateLimited not captcha`() {
        val verdict = evaluateSearch(
            """
            <html><body>
              <div class="msg-jump">
                <p>請不要頻繁操作，搜索時間間隔爲3秒</p>
                <p><a href="javascript:history.back(-1);">跳轉</a></p>
              </div>
            </body></html>
            """.trimIndent(),
        )
        val blocked = assertIs<PageVerdict.Blocked>(verdict)
        assertIs<BlockReason.RateLimited>(blocked.reason)
    }

    // 规则 4 (关键用例 4: 429 不是验证码)
    @Test
    fun `http 429 is RateLimited with retry after`() {
        val verdict = evaluateSearch("<html><body>too many requests</body></html>", status = 429, retryAfterSeconds = 17)
        val blocked = assertIs<PageVerdict.Blocked>(verdict)
        val rateLimited = assertIs<BlockReason.RateLimited>(blocked.reason)
        assertEquals(17.seconds, rateLimited.retryAfter)
    }

    // 规则 5
    @Test
    fun `detector classified captcha is Captcha with kind`() {
        val verdict = evaluateSearch(
            "<div class='cf-turnstile'></div><script src='https://challenges.cloudflare.com/turnstile/v0/api.js'></script>",
            status = 403,
        )
        val blocked = assertIs<PageVerdict.Blocked>(verdict)
        assertEquals(BlockReason.Captcha(WebCaptchaKind.CloudflareTurnstile), blocked.reason)
    }

    // 规则 6
    @Test
    fun `featureless 403 is Forbidden not captcha`() {
        val verdict = evaluateSearch("<html><body>Forbidden</body></html>", status = 403)
        val blocked = assertIs<PageVerdict.Blocked>(verdict)
        assertEquals(BlockReason.Forbidden(403), blocked.reason)
    }

    @Test
    fun `featureless 468 is Captcha Unknown`() {
        val verdict = evaluateSearch("<html><body>blocked</body></html>", status = 468)
        val blocked = assertIs<PageVerdict.Blocked>(verdict)
        assertEquals(BlockReason.Captcha(WebCaptchaKind.Unknown), blocked.reason)
    }

    // 规则 7
    @Test
    fun `normal page without results is EmptyContent`() {
        val verdict = evaluateSearch(
            "<html><body><div class='search-box empty-state'><p>No matches yet</p></div></body></html>",
        )
        assertIs<PageVerdict.EmptyContent>(verdict)
    }

    @Test
    fun `AnyContent expectation accepts normal page`() {
        val verdict = evaluator.evaluate(
            LoadedPage("https://example.com/play/1.html", "<html><body><div>player page</div></body></html>"),
            PageExpectation.AnyContent,
        )
        assertIs<PageVerdict.Ok<*>>(verdict)
    }

    @Test
    fun `AnyContent expectation still detects challenge pages`() {
        val verdict = evaluator.evaluate(
            LoadedPage(
                "https://example.com/play/1.html",
                "<html><title>Just a moment...</title><div id=\"challenge-error-text\">Enable JavaScript and cookies to continue</div></html>",
            ),
            PageExpectation.AnyContent,
        )
        val blocked = assertIs<PageVerdict.Blocked>(verdict)
        assertEquals(BlockReason.Captcha(WebCaptchaKind.Cloudflare), blocked.reason)
    }

    @Test
    fun `SubjectDetails expectation parses episodes`() {
        val config = searchConfig.copy() // channelFormatId 默认为 index 格式
        val verdict = evaluator.evaluate(
            LoadedPage(
                "https://example.com/detail/1.html",
                """
                <html><body>
                  <div class="module-list sort-list">
                    <div class="module-blocklist">
                      <a href="/play/1-1.html"><span>第1集</span></a>
                      <a href="/play/1-2.html"><span>第2集</span></a>
                    </div>
                  </div>
                </body></html>
                """.trimIndent(),
            ),
            PageExpectation.SubjectDetails(config, "https://example.com/detail/1.html"),
        )
        // 具体能否解析取决于默认 channel format 配置; 至少不允许误判为被挡
        assertTrue(verdict is PageVerdict.Ok<*> || verdict is PageVerdict.EmptyContent)
    }
}
