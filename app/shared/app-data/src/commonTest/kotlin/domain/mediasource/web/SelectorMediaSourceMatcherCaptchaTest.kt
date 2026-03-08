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
import me.him188.ani.datasources.api.matcher.WebViewConfig
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.serializeArguments
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectorMediaSourceMatcherCaptchaTest {
    @Test
    fun `matcher patchConfig includes captcha cookies`() {
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
                            searchUrl = "https://www.example.com/search/{keyword}",
                            matchVideo = SelectorSearchConfig.MatchVideoConfig(
                                cookies = "quality=1080\nsession=static",
                            ),
                        ),
                    ),
                ),
            ),
            repository = SelectorMediaSourceEpisodeCacheRepository(
                webSubjectInfoDao = NoopWebSearchSubjectInfoDao,
                webEpisodeInfoDao = NoopWebSearchEpisodeInfoDao,
            ),
            client = HttpClient().asScopedHttpClient(),
            webCaptchaCoordinator = FakeWebCaptchaCoordinator(
                mapOf(
                    WebCaptchaRequest(
                        mediaSourceId = "source-1",
                        pageUrl = "https://example.com/challenge",
                        kind = WebCaptchaKind.Cloudflare,
                    ).storageKey() to listOf(
                        "cf_clearance=fresh",
                        "session=dynamic",
                    ),
                ),
            ),
        )

        val patched = source.matcher.patchConfig(
            WebViewConfig(
                cookies = listOf(
                    "baseline=1",
                    "cf_clearance=stale",
                ),
            ),
        )

        assertEquals(
            mapOf(
                "baseline" to "baseline=1",
                "cf_clearance" to "cf_clearance=fresh",
                "quality" to "quality=1080",
                "session" to "session=dynamic",
            ),
            patched.cookies.associateBy { it.substringBefore("=") },
        )
    }

    private class FakeWebCaptchaCoordinator(
        private val solvedCookies: Map<String, List<String>>,
    ) : WebCaptchaCoordinator {
        override fun getSolvedCookies(
            mediaSourceId: String,
            pageUrl: String,
        ): List<String> {
            val key = WebCaptchaRequest(
                mediaSourceId = mediaSourceId,
                pageUrl = pageUrl,
                kind = WebCaptchaKind.Unknown,
            ).storageKey()
            return solvedCookies[key].orEmpty()
        }

        override suspend fun tryAutoSolve(request: WebCaptchaRequest): WebCaptchaSolveResult {
            return WebCaptchaSolveResult.Unsupported
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
