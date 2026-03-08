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
import me.him188.ani.app.data.persistent.database.dao.WebSearchEpisodeInfoDao
import me.him188.ani.app.data.persistent.database.dao.WebSearchEpisodeInfoEntity
import me.him188.ani.app.data.persistent.database.dao.WebSearchSubjectInfoAndEpisodes
import me.him188.ani.app.data.persistent.database.dao.WebSearchSubjectInfoDao
import me.him188.ani.app.data.persistent.database.dao.WebSearchSubjectInfoEntity
import me.him188.ani.app.data.repository.media.SelectorMediaSourceEpisodeCacheRepository
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatNoChannel
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatA
import me.him188.ani.datasources.api.EpisodeSort
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
import kotlin.time.Duration.Companion.milliseconds

internal fun createDummySelectorMediaSource(
    mediaSourceId: String,
    baseUrl: String,
    coordinator: WebCaptchaCoordinator,
): SelectorMediaSource {
    return SelectorMediaSource(
        mediaSourceId = mediaSourceId,
        config = selectorConfig(baseUrl),
        repository = SelectorMediaSourceEpisodeCacheRepository(
            webSubjectInfoDao = InMemoryWebSearchSubjectInfoDao(),
            webEpisodeInfoDao = InMemoryWebSearchEpisodeInfoDao(),
        ),
        client = HttpClient().asScopedHttpClient(),
        webCaptchaCoordinator = coordinator,
    )
}

internal fun dummyFetchRequest(
    subjectName: String = "Test Subject",
    episodeName: String = "Episode 1",
    episodeSort: String = "1",
): MediaFetchRequest {
    return MediaFetchRequest(
        subjectId = "",
        episodeId = "",
        subjectNameCN = subjectName,
        subjectNames = listOf(subjectName),
        episodeSort = EpisodeSort(episodeSort),
        episodeName = episodeName,
        episodeEp = EpisodeSort(episodeSort),
    )
}

internal fun selectorConfig(baseUrl: String): MediaSourceConfig {
    return MediaSourceConfig(
        serializedArguments = MediaSourceConfig.serializeArguments(
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

internal class InMemoryWebSearchSubjectInfoDao : WebSearchSubjectInfoDao {
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

internal class InMemoryWebSearchEpisodeInfoDao : WebSearchEpisodeInfoDao {
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

internal data class SiteRequest(
    val path: String,
    val hasClearanceCookie: Boolean,
)

internal class LocalCaptchaSite(
    private val redirectSearchHomeOnceAfterClearance: Boolean = false,
    private val searchReturns403WhenBlocked: Boolean = false,
    private val challengeSetsCookie: Boolean = true,
    private val challengeSetsLocalStorage: Boolean = true,
    private val challengeSetsSessionStorage: Boolean = false,
    private val challengeAutoNavigateBack: Boolean = true,
    private val playRequiresClearanceCookie: Boolean = true,
    private val playRequiresLocalStorage: Boolean = true,
    private val playRequiresSessionStorage: Boolean = false,
) : AutoCloseable {
    private val serverSocket = ServerSocket(0)
    private val acceptThread: Thread
    private val running = AtomicBoolean(true)

    val redirectedHomeCount = AtomicLong(0L)
    val challengeSolvedCount = AtomicLong(0L)

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
            path == "/challenge-solved" -> {
                challengeSolvedCount.incrementAndGet()
                respond(socket, "", contentType = "text/plain; charset=utf-8")
            }

            path.startsWith("/search/") -> {
                searchRequests += SiteRequest(path, hasClearanceCookie)
                if (hasClearanceCookie) {
                    if (
                        redirectSearchHomeOnceAfterClearance &&
                        redirectedHomeCount.compareAndSet(0L, 1L)
                    ) {
                        respond(socket, okHomePage())
                    } else {
                        respond(socket, okSearchPage())
                    }
                } else if (searchReturns403WhenBlocked) {
                    respond(socket, cloudflareChallengePage(path), status = "403 Forbidden")
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
                if (playRequiresClearanceCookie && !hasClearanceCookie) {
                    respond(socket, missingPlayGatePage("cookie"))
                } else {
                    respond(socket, okPlayPage())
                }
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
        val escapedReturnPath = returnPath.replace("\\", "\\\\").replace("\"", "\\\"")
        val solveScript = buildString {
            append("setTimeout(function(){")
            if (challengeSetsLocalStorage) {
                append("localStorage.setItem('captcha_state','ok');")
            }
            if (challengeSetsSessionStorage) {
                append("sessionStorage.setItem('captcha_secret','ok');")
            }
            if (challengeSetsCookie) {
                append("document.cookie='cf_clearance=ok; path=/';")
            }
            append("fetch('/challenge-solved').catch(function(){});")
            if (challengeAutoNavigateBack) {
                append("window.location.href=\"").append(escapedReturnPath).append("\";")
            }
            append("}, 600);")
        }
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
                    cUPMDTk: "$escapedReturnPath",
                  };
                  $solveScript
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

    private fun okHomePage(): String {
        return """
            <html>
              <body>
                <div class="site-home">Home</div>
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
        val localStorageCheck = if (playRequiresLocalStorage) {
            "localStorage.getItem('captcha_state') === 'ok'"
        } else {
            "true"
        }
        val sessionStorageCheck = if (playRequiresSessionStorage) {
            "sessionStorage.getItem('captcha_secret') === 'ok'"
        } else {
            "true"
        }
        return """
            <html>
              <body>
                <script>
                  if ($localStorageCheck && $sessionStorageCheck) {
                    document.body.innerText = "storage ok";
                    fetch("$baseUrl/stream/1.m3u8");
                  } else {
                    document.body.innerText = "missing storage gate";
                  }
                </script>
              </body>
            </html>
        """.trimIndent()
    }

    private fun missingPlayGatePage(reason: String): String {
        return """
            <html>
              <body>
                <div class="missing-gate">$reason</div>
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
