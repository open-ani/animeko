/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.persistent.database.dao.WebSearchEpisodeInfoDao
import me.him188.ani.app.data.persistent.database.dao.WebSearchEpisodeInfoEntity
import me.him188.ani.app.data.persistent.database.dao.WebSearchSubjectInfoAndEpisodes
import me.him188.ani.app.data.persistent.database.dao.WebSearchSubjectInfoDao
import me.him188.ani.app.data.persistent.database.dao.WebSearchSubjectInfoEntity
import me.him188.ani.app.data.repository.media.SelectorMediaSourceEpisodeCacheRepository
import me.him188.ani.app.domain.media.resolver.WebResource
import me.him188.ani.app.domain.media.resolver.WebViewVideoExtractor
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatNoChannel
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatA
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.serializeArguments
import me.him188.ani.utils.ktor.asScopedHttpClient
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

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
            val source = SelectorMediaSource(
                mediaSourceId = "e2e-selector",
                config = selectorConfig(site.baseUrl),
                repository = SelectorMediaSourceEpisodeCacheRepository(
                    webSubjectInfoDao = InMemoryWebSearchSubjectInfoDao(),
                    webEpisodeInfoDao = InMemoryWebSearchEpisodeInfoDao(),
                ),
                client = HttpClient().asScopedHttpClient(),
                webCaptchaCoordinator = coordinator,
            )

            val matches = source.fetch(
                MediaFetchRequest(
                    subjectId = "",
                    episodeId = "",
                    subjectNameCN = "Test Subject",
                    subjectNames = listOf("Test Subject"),
                    episodeSort = EpisodeSort("1"),
                    episodeName = "Episode 1",
                    episodeEp = EpisodeSort("1"),
                ),
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
            val source = SelectorMediaSource(
                mediaSourceId = "e2e-interactive-selector",
                config = selectorConfig(site.baseUrl),
                repository = SelectorMediaSourceEpisodeCacheRepository(
                    webSubjectInfoDao = InMemoryWebSearchSubjectInfoDao(),
                    webEpisodeInfoDao = InMemoryWebSearchEpisodeInfoDao(),
                ),
                client = HttpClient().asScopedHttpClient(),
                webCaptchaCoordinator = coordinator,
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
                    source.fetch(
                        MediaFetchRequest(
                            subjectId = "",
                            episodeId = "",
                            subjectNameCN = "Test Subject",
                            subjectNames = listOf("Test Subject"),
                            episodeSort = EpisodeSort("1"),
                            episodeName = "Episode 1",
                            episodeEp = EpisodeSort("1"),
                        ),
                    ).results.toList()
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
        }
    }

    private fun selectorConfig(baseUrl: String): MediaSourceConfig {
        return MediaSourceConfig(
            serializedArguments = MediaSourceConfig.Companion.serializeArguments(
                SelectorMediaSourceArguments.serializer(),
                SelectorMediaSourceArguments(
                    name = "Loopback Selector",
                    description = "android device test",
                    iconUrl = "",
                    searchConfig = SelectorSearchConfig(
                        searchUrl = "$baseUrl/search/{keyword}",
                        searchUseOnlyFirstWord = false,
                        searchRemoveSpecial = false,
                        requestInterval = 0.milliseconds,
                        selectorSubjectFormatA = SelectorSubjectFormatA.Config(
                            selectLists = "div.video-info-header > a",
                        ),
                        selectorChannelFormatNoChannel = SelectorChannelFormatNoChannel.Config(
                            selectEpisodes = "#glist-1 > div.module-blocklist.scroll-box.scroll-box-y > div > a",
                            matchEpisodeSortFromName = "Episode\\s*(?<ep>\\d+)",
                        ),
                    ),
                ),
            ),
        )
    }

    private class InMemoryWebSearchSubjectInfoDao : WebSearchSubjectInfoDao {
        private val ids = AtomicLong(1L)
        private val subjects = Collections.synchronizedList(mutableListOf<WebSearchSubjectInfoEntity>())

        override suspend fun insert(item: WebSearchSubjectInfoEntity): Long {
            val id = if (item.id == 0L) ids.getAndIncrement() else item.id
            subjects.removeAll { it.mediaSourceId == item.mediaSourceId && it.subjectName == item.subjectName }
            subjects += item.copy(id = id)
            return id
        }

        override suspend fun upsert(item: List<WebSearchSubjectInfoEntity>) {
            item.forEach { insert(it) }
        }

        override suspend fun filterByMediaSourceIdAndSubjectName(
            mediaSourceId: String,
            subjectName: String,
        ): List<WebSearchSubjectInfoAndEpisodes> {
            return subjects
                .filter { it.mediaSourceId == mediaSourceId && it.subjectName == subjectName }
                .map { WebSearchSubjectInfoAndEpisodes(it, emptyList()) }
        }

        override suspend fun deleteAll() {
            subjects.clear()
        }
    }

    private class InMemoryWebSearchEpisodeInfoDao : WebSearchEpisodeInfoDao {
        private val episodes = Collections.synchronizedList(mutableListOf<WebSearchEpisodeInfoEntity>())

        override suspend fun upsert(item: WebSearchEpisodeInfoEntity) {
            upsert(listOf(item))
        }

        override suspend fun upsert(item: List<WebSearchEpisodeInfoEntity>) {
            episodes.removeAll { existing ->
                item.any {
                    it.parentId == existing.parentId &&
                        it.channel == existing.channel &&
                        it.name == existing.name
                }
            }
            episodes += item
        }
    }

    private data class SiteRequest(
        val path: String,
        val hasClearanceCookie: Boolean,
    )

    private class LocalCaptchaSite : AutoCloseable {
        private val serverSocket = ServerSocket(0)
        private val acceptThread: Thread
        private val running = AtomicBoolean(true)

        val baseUrl = "http://127.0.0.1:${serverSocket.localPort}"
        val searchRequests = CopyOnWriteArrayList<SiteRequest>()
        val subjectRequests = CopyOnWriteArrayList<SiteRequest>()
        val playRequests = CopyOnWriteArrayList<SiteRequest>()
        val embedRequests = CopyOnWriteArrayList<SiteRequest>()
        val streamRequests = CopyOnWriteArrayList<SiteRequest>()

        init {
            acceptThread = thread(start = true, name = "local-captcha-site") {
                while (running.get()) {
                    val socket = runCatching { serverSocket.accept() }.getOrNull() ?: break
                    thread(start = true, name = "local-captcha-site-client") {
                        socket.use(::handle)
                    }
                }
            }
        }

        fun searchUrl(keyword: String): String = "$baseUrl/search/$keyword"

        fun subjectUrl(): String = "$baseUrl/subject/1"

        override fun close() {
            running.set(false)
            runCatching { serverSocket.close() }
            acceptThread.join(2_000)
        }

        private fun handle(socket: Socket) {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val path = requestLine.substringAfter(' ').substringBefore(" HTTP/")
            var cookieHeader = ""
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) {
                    break
                }
                if (line.startsWith("Cookie:", ignoreCase = true)) {
                    cookieHeader = line.substringAfter(':').trim()
                }
            }

            val hasClearanceCookie = "cf_clearance=ok" in cookieHeader
            when {
                path.startsWith("/search/") -> {
                    searchRequests += SiteRequest(path, hasClearanceCookie)
                    if (hasClearanceCookie) {
                        respond(socket, okSearchPage())
                    } else {
                        respond(socket, cloudflareChallengePage(path))
                    }
                }

                path == "/subject/1" -> {
                    subjectRequests += SiteRequest(path, hasClearanceCookie)
                    if (hasClearanceCookie) {
                        respond(socket, okSubjectPage())
                    } else {
                        respond(socket, cloudflareChallengePage(path))
                    }
                }

                path == "/play/1" -> {
                    playRequests += SiteRequest(path, hasClearanceCookie)
                    respond(socket, okPlayPage())
                }

                path == "/embed/1" -> {
                    embedRequests += SiteRequest(path, hasClearanceCookie)
                    respond(socket, okEmbedPage())
                }

                path == "/stream/1.m3u8" -> {
                    streamRequests += SiteRequest(path, hasClearanceCookie)
                    respond(
                        socket,
                        "#EXTM3U\n#EXT-X-VERSION:3\n",
                        contentType = "application/vnd.apple.mpegurl",
                    )
                }

                else -> respond(socket, "<html><body>not found</body></html>", status = "404 Not Found")
            }
        }

        private fun respond(
            socket: Socket,
            body: String,
            status: String = "200 OK",
            contentType: String = "text/html; charset=utf-8",
        ) {
            val bodyBytes = body.toByteArray(Charsets.UTF_8)
            val output = socket.getOutputStream()
            output.write(
                buildString {
                    append("HTTP/1.1 ").append(status).append("\r\n")
                    append("Content-Type: ").append(contentType).append("\r\n")
                    append("Content-Length: ").append(bodyBytes.size).append("\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                }.toByteArray(Charsets.UTF_8),
            )
            output.write(bodyBytes)
            output.flush()
        }

        private fun cloudflareChallengePage(returnPath: String): String {
            return """
                <!DOCTYPE html>
                <html lang="en-US">
                  <head>
                    <title>Just a moment...</title>
                    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
                    <meta http-equiv="X-UA-Compatible" content="IE=Edge">
                    <meta name="robots" content="noindex,nofollow">
                    <meta name="viewport" content="width=device-width,initial-scale=1">
                    <script src="/cdn-cgi/challenge-platform/scripts/jsd/main.js"></script>
                  </head>
                  <body>
                    <div class="main-wrapper" role="main">
                      <div class="main-content">
                        <div id="challenge-error-text">Enable JavaScript and cookies to continue</div>
                        <div id="cf-challenge">Checking your browser before accessing this website.</div>
                      </div>
                    </div>
                    <script>
                      window._cf_chl_opt = {
                        cZone: "127.0.0.1",
                        cType: "managed",
                        cUPMDTk: "$returnPath",
                      };
                      setTimeout(function() {
                        localStorage.setItem("captcha_state", "ok");
                        document.cookie = "cf_clearance=ok; path=/";
                        window.location.href = "$returnPath";
                      }, 600);
                    </script>
                  </body>
                </html>
            """.trimIndent()
        }

        private fun okSearchPage(): String {
            return """
                <html>
                  <body>
                    <div class="video-info-header">
                      <a href="/subject/1" title="Test Subject">Test Subject</a>
                    </div>
                  </body>
                </html>
            """.trimIndent()
        }

        private fun okSubjectPage(): String {
            return """
                <html>
                  <body>
                    <div id="glist-1">
                      <div class="module-blocklist scroll-box scroll-box-y">
                        <div>
                          <a href="$baseUrl/play/1">Episode 1</a>
                        </div>
                      </div>
                    </div>
                  </body>
                </html>
            """.trimIndent()
        }

        private fun okPlayPage(): String {
            return """
                <html>
                  <body>
                    <script>
                      if (localStorage.getItem("captcha_state") === "ok") {
                        document.body.innerText = "storage ok";
                        fetch("$baseUrl/stream/1.m3u8");
                      } else {
                        document.body.innerText = "missing local storage gate";
                      }
                    </script>
                  </body>
                </html>
            """.trimIndent()
        }

        private fun okEmbedPage(): String {
            return """
                <html>
                  <body>
                    <script>
                      fetch("$baseUrl/stream/1.m3u8");
                    </script>
                  </body>
                </html>
            """.trimIndent()
        }
    }
}
