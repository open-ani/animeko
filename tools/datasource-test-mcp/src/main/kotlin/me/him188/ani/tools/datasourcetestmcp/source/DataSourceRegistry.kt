/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.source

import me.him188.ani.app.data.persistent.database.dao.WebSearchEpisodeInfoDao
import me.him188.ani.app.data.persistent.database.dao.WebSearchEpisodeInfoEntity
import me.him188.ani.app.data.persistent.database.dao.WebSearchSubjectInfoAndEpisodes
import me.him188.ani.app.data.persistent.database.dao.WebSearchSubjectInfoDao
import me.him188.ani.app.data.persistent.database.dao.WebSearchSubjectInfoEntity
import me.him188.ani.app.data.repository.media.SelectorMediaSourceEpisodeCacheRepository
import me.him188.ani.app.domain.mediasource.rss.RssMediaSource
import me.him188.ani.app.domain.mediasource.web.PageEvaluator
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSource
import me.him188.ani.app.domain.mediasource.web.captcha.UnsupportedCaptchaBrowserFactory
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceCookieJar
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceIdentityRegistry
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceFactory
import me.him188.ani.datasources.dmhy.DmhyMediaSource
import me.him188.ani.datasources.ikaros.IkarosMediaSource
import me.him188.ani.datasources.jellyfin.EmbyMediaSource
import me.him188.ani.datasources.jellyfin.JellyfinMediaSource
import me.him188.ani.datasources.mikan.MikanCNMediaSource
import me.him188.ani.datasources.mikan.MikanMediaSource
import me.him188.ani.utils.ktor.ScopedHttpClient
import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicLong

class DataSourceRegistry(
    private val client: ScopedHttpClient,
    scope: kotlinx.coroutines.CoroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default),
) {
    private val selectorRepository = SelectorMediaSourceEpisodeCacheRepository(
        InMemoryWebSearchSubjectInfoDao(),
        InMemoryWebSearchEpisodeInfoDao(),
    )

    // 无头环境: 无浏览器, 直连 HTTP; 验证码只会如实上报为 CaptchaRequired
    private val webSessionManager = WebSessionManager(
        browserFactory = UnsupportedCaptchaBrowserFactory,
        evaluator = PageEvaluator(),
        cookieJar = WebSourceCookieJar(),
        identityRegistry = WebSourceIdentityRegistry(),
        client = client,
        backgroundScope = scope,
    )

    private val factories: Map<String, MediaSourceFactory> = buildMap {
        ServiceLoader.load(MediaSourceFactory::class.java).forEach { put(it.factoryId.value, it) }
        put(DmhyMediaSource.ID, DmhyMediaSource.Factory())
        put(MikanMediaSource.ID, MikanMediaSource.Factory())
        put(MikanCNMediaSource.ID, MikanCNMediaSource.Factory())
        put(JellyfinMediaSource.ID, JellyfinMediaSource.Factory())
        put(EmbyMediaSource.ID, EmbyMediaSource.Factory())
        put(IkarosMediaSource.ID, IkarosMediaSource.Factory())
        put(RssMediaSource.FactoryId.value, RssMediaSource.Factory())
        put(SelectorMediaSource.FactoryId.value, SelectorMediaSource.Factory(selectorRepository, webSessionManager))
    }

    fun listToolsDefaultFactories(): List<String> {
        return listOf(
            DmhyMediaSource.ID,
            MikanMediaSource.ID,
            MikanCNMediaSource.ID,
        )
    }

    fun createSources(spec: MediaSourceSpec?): List<MediaSource> {
        if (spec != null) {
            return listOf(createSource(spec))
        }

        return listToolsDefaultFactories().map { factoryId ->
            val factory = factories.getValue(factoryId)
            factory.create(factoryId, MediaSourceConfig.Default, client)
        }
    }

    fun createSource(spec: MediaSourceSpec): MediaSource {
        val factory = factories[spec.factoryId] ?: error("Unknown media source factory: ${spec.factoryId}")
        val mediaSourceId = spec.mediaSourceId ?: spec.factoryId
        val config = MediaSourceConfig(
            arguments = spec.arguments,
            serializedArguments = spec.serializedArguments,
        )
        return factory.create(mediaSourceId, config, client)
    }

    fun factoryIds(): List<String> = factories.keys.sorted()
}

private class InMemoryWebSearchSubjectInfoDao : WebSearchSubjectInfoDao {
    private val nextId = AtomicLong(1L)
    private val items = linkedMapOf<Long, WebSearchSubjectInfoEntity>()

    override suspend fun insert(item: WebSearchSubjectInfoEntity): Long {
        val existing = items.values.firstOrNull {
            it.mediaSourceId == item.mediaSourceId && it.subjectName == item.subjectName
        }
        if (existing != null) {
            items[existing.id] = item.copy(id = existing.id)
            return existing.id
        }

        val id = nextId.getAndIncrement()
        items[id] = item.copy(id = id)
        return id
    }

    override suspend fun upsert(item: List<WebSearchSubjectInfoEntity>) {
        item.forEach { insert(it) }
    }

    override suspend fun filterByMediaSourceIdAndSubjectName(
        mediaSourceId: String,
        subjectName: String,
    ): List<WebSearchSubjectInfoAndEpisodes> {
        return items.values
            .filter { it.mediaSourceId == mediaSourceId && it.subjectName == subjectName }
            .map { WebSearchSubjectInfoAndEpisodes(it, emptyList()) }
    }

    override suspend fun deleteAll() {
        items.clear()
    }
}

private class InMemoryWebSearchEpisodeInfoDao : WebSearchEpisodeInfoDao {
    override suspend fun upsert(item: WebSearchEpisodeInfoEntity) {
    }

    override suspend fun upsert(item: List<WebSearchEpisodeInfoEntity>) {
    }
}
