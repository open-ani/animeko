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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.persistent.database.dao.WebSearchEpisodeInfoDao
import me.him188.ani.app.data.persistent.database.dao.WebSearchEpisodeInfoEntity
import me.him188.ani.app.data.persistent.database.dao.WebSearchSubjectInfoAndEpisodes
import me.him188.ani.app.data.persistent.database.dao.WebSearchSubjectInfoDao
import me.him188.ani.app.data.persistent.database.dao.WebSearchSubjectInfoEntity
import me.him188.ani.app.data.repository.media.SelectorMediaSourceEpisodeCacheRepository
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.serializeArguments
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SelectorMediaSourceCaptchaFlowTest {
    @Test
    fun `fetch keeps captcha required when auto-solved page still has no searchable subjects`() = runTest {
        val coordinator = FakeWebCaptchaCoordinator(
            solvedPage = WebCaptchaLoadedPage(
                finalUrl = "https://example.com/",
                html = "<html><body><h1>home</h1></body></html>",
            ),
        )
        val source = SelectorMediaSource(
            mediaSourceId = "source-1",
            config = MediaSourceConfig(
                serializedArguments = MediaSourceConfig.serializeArguments(
                    SelectorMediaSourceArguments.serializer(),
                    SelectorMediaSourceArguments(
                        name = "Test Source",
                        description = "",
                        iconUrl = "",
                        searchConfig = SelectorSearchConfig(
                            searchUrl = "https://example.com/search/{keyword}",
                        ),
                    ),
                ),
            ),
            repository = SelectorMediaSourceEpisodeCacheRepository(
                webSubjectInfoDao = NoopWebSearchSubjectInfoDao,
                webEpisodeInfoDao = NoopWebSearchEpisodeInfoDao,
            ),
            client = HttpClient(
                MockEngine {
                    respond(
                        content = "<html><body>Forbidden</body></html>",
                        status = HttpStatusCode.Forbidden,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
                    )
                },
            ).asScopedHttpClient(),
            webCaptchaCoordinator = coordinator,
        )

        val exception = assertFailsWith<CaptchaRequiredException> {
            source.fetch(
                MediaFetchRequest(
                    subjectId = "subject-1",
                    episodeId = "episode-1",
                    subjectNameCN = "Frieren",
                    subjectNames = listOf("Frieren"),
                    episodeSort = EpisodeSort(1),
                    episodeName = "Episode 1",
                    episodeEp = EpisodeSort(1),
                ),
            ).results.toList()
        }

        assertEquals(WebCaptchaKind.Unknown, exception.request.kind)
        assertEquals("https://example.com/", exception.request.pageUrl)
        assertEquals(1, coordinator.autoSolveCount)
    }

    private class FakeWebCaptchaCoordinator(
        private val solvedPage: WebCaptchaLoadedPage,
    ) : WebCaptchaCoordinator {
        var autoSolveCount = 0
            private set
        private var isSolved = false

        override suspend fun loadPageInSolvedSession(
            mediaSourceId: String,
            pageUrl: String,
        ): WebCaptchaLoadedPage? {
            return solvedPage.takeIf { isSolved }
        }

        override suspend fun tryAutoSolve(request: WebCaptchaRequest): WebCaptchaSolveResult {
            autoSolveCount++
            isSolved = true
            return WebCaptchaSolveResult.Solved(
                finalUrl = solvedPage.finalUrl,
                cookies = emptyList(),
            )
        }

        override suspend fun solveInteractively(request: WebCaptchaRequest): WebCaptchaSolveResult {
            return WebCaptchaSolveResult.Unsupported
        }
    }

    private data object NoopWebSearchSubjectInfoDao : WebSearchSubjectInfoDao {
        override suspend fun insert(item: WebSearchSubjectInfoEntity): Long = 0L

        override suspend fun upsert(item: List<WebSearchSubjectInfoEntity>) {
        }

        override suspend fun filterByMediaSourceIdAndSubjectName(
            mediaSourceId: String,
            subjectName: String,
        ): List<WebSearchSubjectInfoAndEpisodes> = emptyList()

        override suspend fun deleteAll() {
        }
    }

    private data object NoopWebSearchEpisodeInfoDao : WebSearchEpisodeInfoDao {
        override suspend fun upsert(item: WebSearchEpisodeInfoEntity) {
        }

        override suspend fun upsert(item: List<WebSearchEpisodeInfoEntity>) {
        }
    }
}
