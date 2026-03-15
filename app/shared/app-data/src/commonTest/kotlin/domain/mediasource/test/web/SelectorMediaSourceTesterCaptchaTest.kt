/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.test.web

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.domain.mediasource.web.DefaultSelectorMediaSourceEngine
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceEngine
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.domain.mediasource.web.WebCaptchaCoordinator
import me.him188.ani.app.domain.mediasource.web.WebCaptchaKind
import me.him188.ani.app.domain.mediasource.web.WebCaptchaLoadedPage
import me.him188.ani.app.domain.mediasource.web.WebCaptchaRequest
import me.him188.ani.app.domain.mediasource.web.WebCaptchaSolveResult
import me.him188.ani.app.domain.mediasource.web.WebPageCaptchaException
import me.him188.ani.app.domain.mediasource.web.WebSearchSubjectInfo
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.utils.ktor.asScopedHttpClient
import me.him188.ani.utils.xml.Document
import me.him188.ani.utils.xml.Element
import kotlin.time.Duration.Companion.ZERO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SelectorMediaSourceTesterCaptchaTest {
    @Test
    fun `interactive solve reuses the same session for subject and episode search`() = runTest {
        val coordinator = FakeWebCaptchaCoordinator()
        val tester = SelectorMediaSourceTester(
            engine = FakeSelectorMediaSourceEngine(),
            webCaptchaCoordinator = coordinator,
            mediaSourceId = "tester-source",
            flowContext = backgroundScope.coroutineContext,
            sharingStarted = SharingStarted.Eagerly,
        )

        tester.setSelectorSearchConfig(SelectorSearchConfig.Empty)
        tester.setEpisodeQuery(SelectorMediaSourceTester.EpisodeQuery(EpisodeSort(1)))
        tester.setSubjectQuery(
            SelectorMediaSourceTester.SubjectQuery(
                searchKeyword = "Frieren",
                searchUrl = "https://example.com/search/{keyword}",
                searchUseOnlyFirstWord = false,
                searchRemoveSpecial = false,
            ),
        )

        val blocked = tester.subjectSelectionResultFlow
            .filterIsInstance<SelectorTestSearchSubjectResult.CaptchaRequired>()
            .first()
        assertEquals(WebCaptchaKind.Cloudflare, blocked.request.kind)

        val solved = tester.solveCaptchaInteractively(blocked.request)
        assertIs<WebCaptchaSolveResult.Solved>(solved)
        tester.restartCurrentSubjectSearchForTest()

        val subjectResult = tester.subjectSelectionResultFlow
            .filterIsInstance<SelectorTestSearchSubjectResult.Success>()
            .first()
        assertEquals(1, subjectResult.subjects.size)

        tester.setSubjectIndex(0)
        val episodeResult = tester.episodeListSelectionResultFlow
            .filterIsInstance<SelectorTestEpisodeListResult.Success>()
            .first { it.episodes.isNotEmpty() }

        assertEquals(1, episodeResult.episodes.size)
        assertEquals(
            listOf(
                "https://example.com/search/Frieren",
                "https://example.com/subject/1",
            ),
            coordinator.loadedPageUrls,
        )
        assertTrue(tester.hasCaptchaSession.first())
    }

    @Test
    fun `reset session clears solved state and requires captcha again`() = runTest {
        val coordinator = FakeWebCaptchaCoordinator()
        val tester = SelectorMediaSourceTester(
            engine = FakeSelectorMediaSourceEngine(),
            webCaptchaCoordinator = coordinator,
            mediaSourceId = "tester-source",
            flowContext = backgroundScope.coroutineContext,
            sharingStarted = SharingStarted.Eagerly,
        )

        tester.setSelectorSearchConfig(SelectorSearchConfig.Empty)
        tester.setEpisodeQuery(SelectorMediaSourceTester.EpisodeQuery(EpisodeSort(1)))
        tester.setSubjectQuery(
            SelectorMediaSourceTester.SubjectQuery(
                searchKeyword = "Frieren",
                searchUrl = "https://example.com/search/{keyword}",
                searchUseOnlyFirstWord = false,
                searchRemoveSpecial = false,
            ),
        )

        val blocked = tester.subjectSelectionResultFlow
            .filterIsInstance<SelectorTestSearchSubjectResult.CaptchaRequired>()
            .first()
        tester.solveCaptchaInteractively(blocked.request)
        tester.restartCurrentSubjectSearchForTest()
        tester.subjectSelectionResultFlow
            .filterIsInstance<SelectorTestSearchSubjectResult.Success>()
            .first()

        tester.resetCaptchaSession()
        tester.restartCurrentSubjectSearchForTest()

        val blockedAgain = tester.subjectSelectionResultFlow
            .filterIsInstance<SelectorTestSearchSubjectResult.CaptchaRequired>()
            .first()
        assertEquals(WebCaptchaKind.Cloudflare, blockedAgain.request.kind)
        assertFalse(tester.hasCaptchaSession.first())
    }

    @Test
    fun `authorization error during subject search is treated as captcha required`() = runTest {
        val tester = SelectorMediaSourceTester(
            engine = AuthorizationFailingSelectorMediaSourceEngine(),
            webCaptchaCoordinator = FakeWebCaptchaCoordinator(),
            mediaSourceId = "tester-source",
            flowContext = backgroundScope.coroutineContext,
            sharingStarted = SharingStarted.Eagerly,
        )

        tester.setSelectorSearchConfig(SelectorSearchConfig.Empty)
        tester.setEpisodeQuery(SelectorMediaSourceTester.EpisodeQuery(EpisodeSort(1)))
        tester.setSubjectQuery(
            SelectorMediaSourceTester.SubjectQuery(
                searchKeyword = "Frieren",
                searchUrl = "https://example.com/search/{keyword}",
                searchUseOnlyFirstWord = false,
                searchRemoveSpecial = false,
            ),
        )

        val blocked = tester.subjectSelectionResultFlow
            .filterIsInstance<SelectorTestSearchSubjectResult.CaptchaRequired>()
            .first()
        assertEquals(WebCaptchaKind.Unknown, blocked.request.kind)
        assertEquals("https://example.com/search/Frieren", blocked.request.pageUrl)
    }

    @Test
    fun `rate limited error during subject search is treated as captcha required`() = runTest {
        val tester = SelectorMediaSourceTester(
            engine = RateLimitedSelectorMediaSourceEngine(),
            webCaptchaCoordinator = FakeWebCaptchaCoordinator(),
            mediaSourceId = "tester-source",
            flowContext = backgroundScope.coroutineContext,
            sharingStarted = SharingStarted.Eagerly,
        )

        tester.setSelectorSearchConfig(SelectorSearchConfig.Empty)
        tester.setEpisodeQuery(SelectorMediaSourceTester.EpisodeQuery(EpisodeSort(1)))
        tester.setSubjectQuery(
            SelectorMediaSourceTester.SubjectQuery(
                searchKeyword = "Frieren",
                searchUrl = "https://example.com/search/{keyword}",
                searchUseOnlyFirstWord = false,
                searchRemoveSpecial = false,
            ),
        )

        val blocked = tester.subjectSelectionResultFlow
            .filterIsInstance<SelectorTestSearchSubjectResult.CaptchaRequired>()
            .first()
        assertEquals(WebCaptchaKind.Unknown, blocked.request.kind)
        assertEquals("https://example.com/search/Frieren", blocked.request.pageUrl)
    }

    @Test
    fun `client request 403 during subject search is treated as captcha required`() = runTest {
        val tester = SelectorMediaSourceTester(
            engine = createDefaultEngine(
                status = HttpStatusCode.Forbidden,
                expectSuccess = true,
            ),
            webCaptchaCoordinator = FakeWebCaptchaCoordinator(),
            mediaSourceId = "tester-source",
            flowContext = backgroundScope.coroutineContext,
            sharingStarted = SharingStarted.Eagerly,
        )

        tester.setSelectorSearchConfig(SelectorSearchConfig.Empty)
        tester.setEpisodeQuery(SelectorMediaSourceTester.EpisodeQuery(EpisodeSort(1)))
        tester.setSubjectQuery(
            SelectorMediaSourceTester.SubjectQuery(
                searchKeyword = "Frieren",
                searchUrl = "https://example.com/search/{keyword}",
                searchUseOnlyFirstWord = false,
                searchRemoveSpecial = false,
            ),
        )

        val blocked = tester.subjectSelectionResultFlow
            .filterIsInstance<SelectorTestSearchSubjectResult.CaptchaRequired>()
            .first()
        assertEquals(WebCaptchaKind.Unknown, blocked.request.kind)
        assertEquals("https://example.com/search/Frieren", blocked.request.pageUrl)
    }

    @Test
    fun `auto solved empty subject page still stays captcha required`() = runTest {
        val tester = SelectorMediaSourceTester(
            engine = EmptySubjectSelectorMediaSourceEngine(),
            webCaptchaCoordinator = AutoSolvingButEmptyCaptchaCoordinator(),
            mediaSourceId = "tester-source",
            flowContext = backgroundScope.coroutineContext,
            sharingStarted = SharingStarted.Eagerly,
        )

        tester.setSelectorSearchConfig(SelectorSearchConfig.Empty)
        tester.setEpisodeQuery(SelectorMediaSourceTester.EpisodeQuery(EpisodeSort(1)))
        tester.setSubjectQuery(
            SelectorMediaSourceTester.SubjectQuery(
                searchKeyword = "Frieren",
                searchUrl = "https://example.com/search/{keyword}",
                searchUseOnlyFirstWord = false,
                searchRemoveSpecial = false,
            ),
        )

        val blocked = tester.subjectSelectionResultFlow
            .filterIsInstance<SelectorTestSearchSubjectResult.CaptchaRequired>()
            .first()
        assertEquals(WebCaptchaKind.Cloudflare, blocked.request.kind)
        assertEquals("https://example.com/search/Frieren", blocked.request.pageUrl)
    }

    @Test
    fun `search cooldown page retries once and returns subjects`() = runTest {
        val tester = SelectorMediaSourceTester(
            engine = SearchCooldownSelectorMediaSourceEngine(),
            webCaptchaCoordinator = FakeWebCaptchaCoordinator(),
            mediaSourceId = "tester-source",
            flowContext = backgroundScope.coroutineContext,
            sharingStarted = SharingStarted.Eagerly,
        )

        tester.setSelectorSearchConfig(
            SelectorSearchConfig(
                searchUrl = "https://example.com/search/{keyword}",
                requestInterval = ZERO,
            ),
        )
        tester.setEpisodeQuery(SelectorMediaSourceTester.EpisodeQuery(EpisodeSort(1)))
        tester.setSubjectQuery(
            SelectorMediaSourceTester.SubjectQuery(
                searchKeyword = "Frieren",
                searchUrl = "https://example.com/search/{keyword}",
                searchUseOnlyFirstWord = false,
                searchRemoveSpecial = false,
            ),
        )

        val result = tester.subjectSelectionResultFlow
            .filterIsInstance<SelectorTestSearchSubjectResult.Success>()
            .first()
        assertEquals(1, result.subjects.size)
    }

    private fun createDefaultEngine(
        status: HttpStatusCode,
        expectSuccess: Boolean,
    ): DefaultSelectorMediaSourceEngine {
        val client = HttpClient(MockEngine { request ->
            respond(
                content = "<html><body>Forbidden</body></html>",
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
            )
        }) {
            this.expectSuccess = expectSuccess
        }
        return DefaultSelectorMediaSourceEngine(client.asScopedHttpClient())
    }

    private class FakeSelectorMediaSourceEngine : SelectorMediaSourceEngine() {
        override suspend fun searchImpl(finalUrl: Url): SearchSubjectResult {
            return SearchSubjectResult(
                url = finalUrl,
                document = null,
                captchaKind = WebCaptchaKind.Cloudflare,
            )
        }

        override fun selectSubjects(
            document: Element,
            config: SelectorSearchConfig,
        ): List<WebSearchSubjectInfo> {
            return listOf(
                WebSearchSubjectInfo(
                    internalId = "subject-1",
                    name = "Frieren",
                    fullUrl = "https://example.com/subject/1",
                    partialUrl = "/subject/1",
                    origin = null,
                ),
            )
        }

        override suspend fun doHttpGet(uri: String): Document {
            throw WebPageCaptchaException(uri, WebCaptchaKind.Cloudflare)
        }
    }

    private class AuthorizationFailingSelectorMediaSourceEngine : SelectorMediaSourceEngine() {
        override suspend fun searchImpl(finalUrl: Url): SearchSubjectResult {
            throw RepositoryAuthorizationException("403 Forbidden")
        }

        override suspend fun doHttpGet(uri: String): Document {
            error("unused")
        }
    }

    private class EmptySubjectSelectorMediaSourceEngine : SelectorMediaSourceEngine() {
        override suspend fun searchImpl(finalUrl: Url): SearchSubjectResult {
            return SearchSubjectResult(
                url = finalUrl,
                document = null,
                captchaKind = WebCaptchaKind.Cloudflare,
            )
        }

        override fun selectSubjects(
            document: Element,
            config: SelectorSearchConfig,
        ): List<WebSearchSubjectInfo> {
            return emptyList()
        }

        override suspend fun doHttpGet(uri: String): Document {
            error("unused")
        }
    }

    private class RateLimitedSelectorMediaSourceEngine : SelectorMediaSourceEngine() {
        override suspend fun searchImpl(finalUrl: Url): SearchSubjectResult {
            throw RepositoryRateLimitedException("429 Too Many Requests")
        }

        override suspend fun doHttpGet(uri: String): Document {
            error("unused")
        }
    }

    private class SearchCooldownSelectorMediaSourceEngine : SelectorMediaSourceEngine() {
        private var searchCount = 0

        override suspend fun searchImpl(finalUrl: Url): SearchSubjectResult {
            searchCount++
            val html = if (searchCount == 1) {
                """
                    <html>
                      <body>
                        <div class="msg-jump">
                          <p>親愛的：請不要頻繁操作，搜索時間間隔爲3秒前</p>
                          <p><a id="href" href="javascript:history.back(-1);">跳轉</a></p>
                        </div>
                      </body>
                    </html>
                """.trimIndent()
            } else {
                """
                    <html>
                      <body>
                        <div class="video-info-header">
                          <a href="/subject/1">Frieren</a>
                        </div>
                      </body>
                    </html>
                """.trimIndent()
            }
            return parseSearchResult(finalUrl, html)
        }

        override fun selectSubjects(
            document: Element,
            config: SelectorSearchConfig,
        ): List<WebSearchSubjectInfo> {
            if (document.select(".video-info-header a").isEmpty()) {
                return emptyList()
            }
            return listOf(
                WebSearchSubjectInfo(
                    internalId = "subject-1",
                    name = "Frieren",
                    fullUrl = "https://example.com/subject/1",
                    partialUrl = "/subject/1",
                    origin = null,
                ),
            )
        }

        override suspend fun doHttpGet(uri: String): Document {
            error("unused")
        }
    }

    private class FakeWebCaptchaCoordinator : WebCaptchaCoordinator {
        private val solved = mutableSetOf<String>()
        val loadedPageUrls = mutableListOf<String>()

        override suspend fun loadPageInSolvedSession(
            mediaSourceId: String,
            pageUrl: String,
        ): WebCaptchaLoadedPage? {
            if (mediaSourceId !in solved) return null
            loadedPageUrls += pageUrl
            return when {
                pageUrl.contains("/search/") -> WebCaptchaLoadedPage(
                    finalUrl = pageUrl,
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

                pageUrl.contains("/subject/1") -> WebCaptchaLoadedPage(
                    finalUrl = pageUrl,
                    html = """
                        <html>
                          <body>
                            <div id="glist-1">
                              <div class="module-blocklist scroll-box scroll-box-y">
                                <div>
                                  <a href="https://example.com/play/1">Episode 1</a>
                                </div>
                              </div>
                            </div>
                          </body>
                        </html>
                    """.trimIndent(),
                )

                else -> null
            }
        }

        override suspend fun tryAutoSolve(request: WebCaptchaRequest): WebCaptchaSolveResult {
            return WebCaptchaSolveResult.StillBlocked(request.kind)
        }

        override suspend fun solveInteractively(request: WebCaptchaRequest): WebCaptchaSolveResult {
            solved += request.mediaSourceId
            return WebCaptchaSolveResult.Solved(request.pageUrl, emptyList())
        }

        override fun resetSolvedSession(mediaSourceId: String) {
            solved -= mediaSourceId
        }
    }

    private class AutoSolvingButEmptyCaptchaCoordinator : WebCaptchaCoordinator {
        private var solved = false

        override suspend fun loadPageInSolvedSession(
            mediaSourceId: String,
            pageUrl: String,
        ): WebCaptchaLoadedPage? {
            if (!solved) return null
            return WebCaptchaLoadedPage(
                finalUrl = pageUrl,
                html = "<html><body><div class=\"empty\"></div></body></html>",
            )
        }

        override suspend fun tryAutoSolve(request: WebCaptchaRequest): WebCaptchaSolveResult {
            solved = true
            return WebCaptchaSolveResult.Solved(request.pageUrl, emptyList())
        }

        override suspend fun solveInteractively(request: WebCaptchaRequest): WebCaptchaSolveResult {
            return WebCaptchaSolveResult.Unsupported
        }
    }
}

private fun SelectorMediaSourceTester.restartCurrentSubjectSearchForTest() {
    subjectSearchLifecycle.restart()
}
