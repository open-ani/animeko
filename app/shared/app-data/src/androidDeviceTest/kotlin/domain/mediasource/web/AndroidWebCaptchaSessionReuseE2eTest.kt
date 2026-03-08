/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.media.resolver.WebResource
import me.him188.ani.app.domain.media.resolver.WebViewVideoExtractor
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.datasources.api.source.MediaMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AndroidWebCaptchaSessionReuseE2eTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun `solveInteractively keeps the same session for search and details`() = runBlocking {
        LocalCaptchaSite().use { site ->
            val coordinator = AndroidWebCaptchaCoordinator(context)
            val request = WebCaptchaRequest(
                mediaSourceId = "e2e-interactive",
                pageUrl = site.searchUrl("test"),
                kind = WebCaptchaKind.Cloudflare,
            )

            val solved = assertIs<WebCaptchaSolveResult.Solved>(coordinator.solveInteractively(request))
            assertEquals(request.pageUrl, solved.finalUrl)

            val searchPage = coordinator.loadPageInSolvedSession(
                mediaSourceId = request.mediaSourceId,
                pageUrl = request.pageUrl,
            )
            val subjectPage = coordinator.loadPageInSolvedSession(
                mediaSourceId = request.mediaSourceId,
                pageUrl = site.subjectUrl(),
            )

            assertTrue(searchPage != null && "Test Subject" in searchPage.html)
            assertTrue(subjectPage != null && "Episode 1" in subjectPage.html)
            assertTrue(site.searchRequests.any { it.hasClearanceCookie })
            assertTrue(site.subjectRequests.any { it.hasClearanceCookie })
        }
    }

    @Test
    fun `selector fetch reuses solved webview session across search and subject pages`() = runBlocking {
        LocalCaptchaSite().use { site ->
            val coordinator = AndroidWebCaptchaCoordinator(context)
            val source = createDummySelectorMediaSource(
                mediaSourceId = "e2e-selector",
                baseUrl = site.baseUrl,
                coordinator = coordinator,
            )

            val matches = source.fetch(
                dummyFetchRequest(),
            ).results.toList()

            assertEquals(
                1,
                matches.size,
                "searchRequests=${site.searchRequests}, subjectRequests=${site.subjectRequests}, matches=${matches.map { it.media.originalUrl }}",
            )
            assertEquals("${site.baseUrl}/play/1", matches.single().media.originalUrl)
            assertTrue(site.searchRequests.any { !it.hasClearanceCookie })
            assertTrue(site.searchRequests.any { it.hasClearanceCookie })
            assertTrue(site.subjectRequests.any { it.hasClearanceCookie })
        }
    }

    @Test
    fun `auto solve with ui-attached coordinator reuses webview session for search and video extraction`() = runAniComposeUiTest {
        LocalCaptchaSite().use { site ->
            val coordinator = AndroidWebCaptchaCoordinator(context)
            val source = createDummySelectorMediaSource(
                mediaSourceId = "e2e-interactive-selector",
                baseUrl = site.baseUrl,
                coordinator = coordinator,
            )
            val scope = CoroutineScope(Dispatchers.Default)

            var solveResult: WebCaptchaSolveResult? = null
            var fetchResults: List<MediaMatch>? = null
            var extracted: WebResource? = null
            var failure: Throwable? = null

            setContent {
                coordinator.ComposeContent()
            }
            waitForIdle()

            scope.launch {
                runCatching {
                    coordinator.tryAutoSolve(
                        WebCaptchaRequest(
                            mediaSourceId = "e2e-interactive-selector",
                            pageUrl = site.searchUrl("Test Subject"),
                            kind = WebCaptchaKind.Cloudflare,
                        ),
                    )
                }.onSuccess {
                    solveResult = it
                }.onFailure {
                    failure = it
                }
            }
            waitUntil(timeoutMillis = 10_000) {
                solveResult != null || failure != null
            }
            failure?.let { throw it }
            assertIs<WebCaptchaSolveResult.Solved>(solveResult)

            scope.launch {
                runCatching {
                    source.fetch(dummyFetchRequest()).results.toList()
                }.onSuccess {
                    fetchResults = it
                }.onFailure {
                    failure = it
                }
            }
            waitUntil(timeoutMillis = 10_000) {
                fetchResults != null || failure != null
            }
            failure?.let { throw it }

            val matches = fetchResults ?: error("fetchResults is null")
            assertEquals(1, matches.size)
            assertEquals("${site.baseUrl}/play/1", matches.single().media.originalUrl)
            assertTrue(site.searchRequests.any { it.hasClearanceCookie })
            assertTrue(site.subjectRequests.any { it.hasClearanceCookie })

            scope.launch {
                runCatching {
                    coordinator.extractVideoResourceInSolvedSession(
                        mediaSourceId = "e2e-interactive-selector",
                        pageUrl = matches.single().media.originalUrl,
                        timeoutMillis = 5_000,
                    ) { url ->
                        when {
                            url.endsWith("/stream/1.m3u8") -> WebViewVideoExtractor.Instruction.FoundResource
                            else -> WebViewVideoExtractor.Instruction.Continue
                        }
                    }
                }.onSuccess {
                    extracted = it
                }.onFailure {
                    failure = it
                }
            }
            waitUntil(timeoutMillis = 10_000) {
                extracted != null || failure != null
            }
            failure?.let { throw it }

            assertEquals("${site.baseUrl}/stream/1.m3u8", extracted?.url)
            assertTrue(site.playRequests.isNotEmpty())
            assertTrue(site.playRequests.any { it.hasClearanceCookie })
        }
    }

    @Test
    fun `selector fetch retries original search when solved session lands on home page first`() = runBlocking {
        LocalCaptchaSite(redirectSearchHomeOnceAfterClearance = true).use { site ->
            val coordinator = AndroidWebCaptchaCoordinator(context)
            val source = createDummySelectorMediaSource(
                mediaSourceId = "e2e-home-retry",
                baseUrl = site.baseUrl,
                coordinator = coordinator,
            )

            val matches = source.fetch(
                dummyFetchRequest(),
            ).results.toList()

            assertEquals(1, matches.size)
            assertEquals("${site.baseUrl}/play/1", matches.single().media.originalUrl)
            assertTrue(site.redirectedHomeCount.get() >= 1, "expected the solved session to hit home page once")
            assertTrue(site.searchRequests.count { it.hasClearanceCookie } >= 2)
            assertTrue(site.subjectRequests.any { it.hasClearanceCookie })
        }
    }

    @Test
    fun `auto solve reuses session storage for video extraction`() = runBlocking {
        LocalCaptchaSite(
            challengeSetsLocalStorage = false,
            challengeSetsSessionStorage = true,
            playRequiresLocalStorage = false,
            playRequiresSessionStorage = true,
        ).use { site ->
            val coordinator = AndroidWebCaptchaCoordinator(context)
            val source = createDummySelectorMediaSource(
                mediaSourceId = "e2e-session-storage",
                baseUrl = site.baseUrl,
                coordinator = coordinator,
            )

            val solved = coordinator.tryAutoSolve(
                WebCaptchaRequest(
                    mediaSourceId = "e2e-session-storage",
                    pageUrl = site.searchUrl("Test Subject"),
                    kind = WebCaptchaKind.Cloudflare,
                ),
            )
            assertIs<WebCaptchaSolveResult.Solved>(solved)

            val matches = source.fetch(dummyFetchRequest()).results.toList()
            assertEquals(1, matches.size)

            val extracted = coordinator.extractVideoResourceInSolvedSession(
                mediaSourceId = "e2e-session-storage",
                pageUrl = matches.single().media.originalUrl,
                timeoutMillis = 5_000,
            ) { url ->
                when {
                    url.endsWith("/stream/1.m3u8") -> WebViewVideoExtractor.Instruction.FoundResource
                    else -> WebViewVideoExtractor.Instruction.Continue
                }
            }

            assertEquals("${site.baseUrl}/stream/1.m3u8", extracted?.url)
            assertTrue(site.challengeSolvedCount.get() >= 1)
            assertTrue(site.playRequests.any { it.hasClearanceCookie })
            assertTrue(site.streamRequests.isNotEmpty())
        }
    }

    @Test
    fun `manual confirm can continue search when solved state stays on challenge page`() = runAniComposeUiTest {
        LocalCaptchaSite(
            challengeAutoNavigateBack = false,
            challengeSetsSessionStorage = true,
            playRequiresSessionStorage = true,
        ).use { site ->
            val coordinator = AndroidWebCaptchaCoordinator(context)
            val source = createDummySelectorMediaSource(
                mediaSourceId = "e2e-manual-confirm",
                baseUrl = site.baseUrl,
                coordinator = coordinator,
            )
            val scope = CoroutineScope(Dispatchers.Default)

            var solveResult: WebCaptchaSolveResult? = null
            var fetchResults: List<MediaMatch>? = null
            var extracted: WebResource? = null
            var failure: Throwable? = null

            setContent {
                coordinator.ComposeContent()
            }
            waitForIdle()

            scope.launch {
                runCatching {
                    coordinator.solveInteractively(
                        WebCaptchaRequest(
                            mediaSourceId = "e2e-manual-confirm",
                            pageUrl = site.searchUrl("Test Subject"),
                            kind = WebCaptchaKind.Cloudflare,
                        ),
                    )
                }.onSuccess {
                    solveResult = it
                }.onFailure {
                    failure = it
                }
            }

            waitUntil(timeoutMillis = 10_000) {
                site.challengeSolvedCount.get() >= 1
            }
            waitForIdle()
            onNodeWithText("✓").performClick()

            waitUntil(timeoutMillis = 10_000) {
                solveResult != null || failure != null
            }
            failure?.let { throw it }
            assertIs<WebCaptchaSolveResult.Solved>(solveResult)

            scope.launch {
                runCatching {
                    source.fetch(dummyFetchRequest()).results.toList()
                }.onSuccess {
                    fetchResults = it
                }.onFailure {
                    failure = it
                }
            }
            waitUntil(timeoutMillis = 10_000) {
                fetchResults != null || failure != null
            }
            failure?.let { throw it }

            val matches = fetchResults ?: error("fetchResults is null")
            assertEquals(1, matches.size)

            scope.launch {
                runCatching {
                    coordinator.extractVideoResourceInSolvedSession(
                        mediaSourceId = "e2e-manual-confirm",
                        pageUrl = matches.single().media.originalUrl,
                        timeoutMillis = 5_000,
                    ) { url ->
                        when {
                            url.endsWith("/stream/1.m3u8") -> WebViewVideoExtractor.Instruction.FoundResource
                            else -> WebViewVideoExtractor.Instruction.Continue
                        }
                    }
                }.onSuccess {
                    extracted = it
                }.onFailure {
                    failure = it
                }
            }
            waitUntil(timeoutMillis = 10_000) {
                extracted != null || failure != null
            }
            failure?.let { throw it }

            assertEquals("${site.baseUrl}/stream/1.m3u8", extracted?.url)
            assertTrue(site.playRequests.any { it.hasClearanceCookie })
            assertTrue(site.streamRequests.isNotEmpty())
        }
    }
}
