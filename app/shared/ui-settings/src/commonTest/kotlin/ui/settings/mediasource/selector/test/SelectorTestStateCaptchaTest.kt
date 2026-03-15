/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.selector.test

import androidx.compose.runtime.mutableStateOf
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.domain.mediasource.test.web.SelectorMediaSourceTester
import me.him188.ani.app.domain.mediasource.test.web.SelectorTestEpisodeListResult
import me.him188.ani.app.domain.mediasource.test.web.SelectorTestSearchSubjectResult
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceEngine
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.domain.mediasource.web.WebCaptchaCoordinator
import me.him188.ani.app.domain.mediasource.web.WebCaptchaKind
import me.him188.ani.app.domain.mediasource.web.WebCaptchaLoadedPage
import me.him188.ani.app.domain.mediasource.web.WebCaptchaRequest
import me.him188.ani.app.domain.mediasource.web.WebCaptchaSolveResult
import me.him188.ani.app.domain.mediasource.web.WebPageCaptchaException
import me.him188.ani.app.domain.mediasource.web.WebSearchSubjectInfo
import me.him188.ani.utils.xml.Document
import me.him188.ani.utils.xml.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SelectorTestStateCaptchaTest {
    @Test
    fun `solve captcha continues settings selector test with the same session`() = runTest {
        val coordinator = FakeWebCaptchaCoordinator()
        val state = createState(coordinator, backgroundScope)

        val observeJob = backgroundScope.launch {
            state.observeChanges()
        }
        state.searchKeyword = "Frieren"

        advanceTimeBy(600)
        advanceUntilIdle()

        val blocked = state.presentation
            .first { it.subjectCaptchaRequest != null }
        val request = assertNotNull(blocked.subjectCaptchaRequest)
        assertEquals(WebCaptchaKind.Cloudflare, request.kind)

        state.solveCaptcha(request, forEpisodeSearch = false)
        advanceUntilIdle()

        val solved = state.presentation.first {
            val result = it.episodeListSearchResult as? SelectorTestEpisodeListResult.Success
            result != null && result.episodes.isNotEmpty()
        }

        assertTrue(solved.hasCaptchaSession)
        assertIs<SelectorTestSearchSubjectResult.Success>(solved.subjectSearchResult)
        val episodes = assertIs<SelectorTestEpisodeListResult.Success>(solved.episodeListSearchResult)
        assertEquals(1, episodes.episodes.size)
        assertEquals(
            listOf(
                "https://example.com/search/Frieren",
                "https://example.com/subject/1",
            ),
            coordinator.loadedPageUrls,
        )

        observeJob.cancel()
    }

    @Test
    fun `reset captcha session makes settings selector test require captcha again`() = runTest {
        val coordinator = FakeWebCaptchaCoordinator()
        val state = createState(coordinator, backgroundScope)

        val observeJob = backgroundScope.launch {
            state.observeChanges()
        }
        state.searchKeyword = "Frieren"

        advanceTimeBy(600)
        advanceUntilIdle()

        val firstBlocked = state.presentation
            .first { it.subjectCaptchaRequest != null }
        val request = assertNotNull(firstBlocked.subjectCaptchaRequest)

        state.solveCaptcha(request, forEpisodeSearch = false)
        advanceUntilIdle()
        state.presentation.first { it.hasCaptchaSession }

        state.resetCaptchaSession()
        advanceUntilIdle()

        val blockedAgain = state.presentation.first {
            it.subjectCaptchaRequest != null && !it.hasCaptchaSession
        }
        assertEquals(WebCaptchaKind.Cloudflare, blockedAgain.subjectCaptchaRequest?.kind)
        assertFalse(blockedAgain.hasCaptchaSession)

        observeJob.cancel()
    }

    @Test
    fun `authorization api error is normalized into captcha required in settings state`() = runTest {
        val state = createState(
            coordinator = FakeWebCaptchaCoordinator(),
            backgroundScope = backgroundScope,
            engine = AuthorizationFailingSelectorMediaSourceEngine(),
        )

        val observeJob = backgroundScope.launch {
            state.observeChanges()
        }
        state.searchKeyword = "命运石之门"

        advanceTimeBy(600)
        advanceUntilIdle()

        val presentation = state.presentation.first { it.subjectCaptchaRequest != null }
        val result = assertIs<SelectorTestSearchSubjectResult.CaptchaRequired>(presentation.subjectSearchResult)
        assertEquals("https://example.com/search/%E5%91%BD%E8%BF%90%E7%9F%B3%E4%B9%8B%E9%97%A8", result.request.pageUrl)
        assertEquals(WebCaptchaKind.Unknown, result.request.kind)

        observeJob.cancel()
    }

    private fun createState(
        coordinator: FakeWebCaptchaCoordinator,
        backgroundScope: CoroutineScope,
        engine: SelectorMediaSourceEngine = FakeSelectorMediaSourceEngine(),
    ): SelectorTestState {
        return SelectorTestState(
            searchConfigState = mutableStateOf(
                SelectorSearchConfig.Empty.copy(
                    searchUrl = "https://example.com/search/{keyword}",
                    searchUseOnlyFirstWord = false,
                    searchRemoveSpecial = false,
                ),
            ),
            tester = SelectorMediaSourceTester(
                engine = engine,
                webCaptchaCoordinator = coordinator,
                mediaSourceId = "selector-test-state",
                flowContext = backgroundScope.coroutineContext,
                sharingStarted = SharingStarted.Eagerly,
            ),
            backgroundScope = backgroundScope,
        )
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
}
