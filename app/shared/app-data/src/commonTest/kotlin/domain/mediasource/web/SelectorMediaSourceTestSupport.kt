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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import me.him188.ani.app.data.persistent.database.dao.WebSearchSessionCacheDao
import me.him188.ani.app.data.persistent.database.dao.WebSearchSessionCacheEntity
import me.him188.ani.app.data.repository.media.SelectorMediaSourceEpisodeCacheRepository
import me.him188.ani.app.domain.mediasource.web.captcha.UnsupportedCaptchaBrowserFactory
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceCookieJar
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceIdentityRegistry
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.serializeArguments
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.time.Duration

/**
 * 用 [MockEngine] 模拟站点, 构造一个走真实 [WebSessionManager] 链路、不命中搜索缓存的 [SelectorMediaSource].
 */
internal fun TestScope.createTestSelectorMediaSource(
    searchConfig: SelectorSearchConfig,
    engine: MockEngine,
    mediaSourceId: String = "test-source",
    cacheDao: WebSearchSessionCacheDao = NoopWebSearchSessionCacheDao,
    /** 用户设置的搜索缓存有效期. 默认 0, 即不写缓存. */
    cacheTtl: Duration = Duration.ZERO,
): SelectorMediaSource {
    val client = HttpClient(engine).asScopedHttpClient()
    val sessionManager = WebSessionManager(
        browserFactory = UnsupportedCaptchaBrowserFactory,
        evaluator = PageEvaluator(),
        cookieJar = WebSourceCookieJar(),
        identityRegistry = WebSourceIdentityRegistry(),
        client = client,
        backgroundScope = backgroundScope,
    )
    val arguments = SelectorMediaSourceArguments.Default.copy(searchConfig = searchConfig)
    return SelectorMediaSource(
        mediaSourceId = mediaSourceId,
        config = MediaSourceConfig(
            serializedArguments = MediaSourceConfig.serializeArguments(
                SelectorMediaSourceArguments.serializer(),
                arguments,
            ),
        ),
        repository = SelectorMediaSourceEpisodeCacheRepository(cacheDao, flowOf(cacheTtl)),
        client = client,
        sessionManager = sessionManager,
    )
}

/**
 * 不存储任何行的 [WebSearchSessionCacheDao]: 搜索缓存永远不命中, 每次都请求站点.
 */
/**
 * 真的存行的 [WebSearchSessionCacheDao], 用于验证搜索缓存与关键词记忆.
 */
internal class InMemoryWebSearchSessionCacheDao : WebSearchSessionCacheDao {
    val rows = mutableListOf<WebSearchSessionCacheEntity>()
    private var nextId = 1L

    override suspend fun insertAll(items: List<WebSearchSessionCacheEntity>) {
        items.forEach { rows += it.copy(id = nextId++) }
    }

    override suspend fun deletePage(
        requesterSubjectId: Int?,
        mediaSourceId: String,
        subjectName: String,
        subjectUrl: String,
    ) {
        rows.removeAll {
            it.requesterSubjectId == requesterSubjectId && it.mediaSourceId == mediaSourceId &&
                    it.subjectName == subjectName && it.subjectUrl == subjectUrl
        }
    }

    override suspend fun filterBySubjectName(
        requesterSubjectId: Int?,
        mediaSourceId: String,
        subjectName: String,
        now: Long,
    ): List<WebSearchSessionCacheEntity> = rows.filter {
        it.requesterSubjectId == requesterSubjectId && it.mediaSourceId == mediaSourceId &&
                it.subjectName == subjectName && it.expiresAt > now
    }.sortedBy { it.id }

    override suspend fun subjectNamesWithCache(
        requesterSubjectId: Int?,
        mediaSourceId: String,
        now: Long,
    ): List<String> = rows.filter {
        it.requesterSubjectId == requesterSubjectId && it.mediaSourceId == mediaSourceId && it.expiresAt > now
    }.map { it.subjectName }.distinct()

    override suspend fun deleteExpired(now: Long) {
        rows.removeAll { it.expiresAt <= now }
    }

    override suspend fun deleteByRequestedSubject(requesterSubjectId: Int?) {
        rows.removeAll { it.requesterSubjectId == requesterSubjectId }
    }

    override suspend fun deleteByRequestedSubjectAndSource(requesterSubjectId: Int?, mediaSourceId: String) {
        rows.removeAll { it.requesterSubjectId == requesterSubjectId && it.mediaSourceId == mediaSourceId }
    }
}

internal object NoopWebSearchSessionCacheDao : WebSearchSessionCacheDao {
    override suspend fun insertAll(items: List<WebSearchSessionCacheEntity>) {}
    override suspend fun deletePage(
        requesterSubjectId: Int?,
        mediaSourceId: String,
        subjectName: String,
        subjectUrl: String,
    ) {
    }

    override suspend fun filterBySubjectName(
        requesterSubjectId: Int?,
        mediaSourceId: String,
        subjectName: String,
        now: Long,
    ): List<WebSearchSessionCacheEntity> = emptyList()

    override suspend fun subjectNamesWithCache(
        requesterSubjectId: Int?,
        mediaSourceId: String,
        now: Long,
    ): List<String> = emptyList()

    override suspend fun deleteExpired(now: Long) {}
    override suspend fun deleteByRequestedSubject(requesterSubjectId: Int?) {}
    override suspend fun deleteByRequestedSubjectAndSource(requesterSubjectId: Int?, mediaSourceId: String) {}
}
