/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.source

import me.him188.ani.app.data.persistent.database.dao.WebSearchSessionCacheDao
import me.him188.ani.app.data.persistent.database.dao.WebSearchSessionCacheEntity
import me.him188.ani.app.data.repository.media.SelectorMediaSourceEpisodeCacheRepository
import me.him188.ani.app.domain.mediasource.rss.RssMediaSource
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSource
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
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
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.utils.ktor.ScopedHttpClient
import java.util.ServiceLoader
import kotlin.time.Duration

class DataSourceRegistry(
    private val client: ScopedHttpClient,
    /**
     * 与 `selector_*` 工具共用同一个会话管理器: selector 源在这里也会自动解验证码,
     * 解不掉时 `SelectorMediaSource` 抛 `BlockedException`, 由调用方作为该源的失败上报.
     */
    private val webSessionManager: WebSessionManager,
) {
    private val selectorRepository = SelectorMediaSourceEpisodeCacheRepository(
        NoopWebSearchSessionCacheDao(),
        userTtlFlow = flowOf(Duration.INFINITE),
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

/**
 * 测试工具总是执行真实搜索: 不存储任何缓存行, `SelectorMediaSource` 的
 * 播放 session 缓存路径因此永远不会命中.
 */
private class NoopWebSearchSessionCacheDao : WebSearchSessionCacheDao {
    override suspend fun insertAll(items: List<WebSearchSessionCacheEntity>) {
    }

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

    override suspend fun deleteExpired(now: Long) {
    }

    override suspend fun deleteByRequestedSubject(requesterSubjectId: Int?) {
    }

    override suspend fun deleteByRequestedSubjectAndSource(requesterSubjectId: Int?, mediaSourceId: String) {
    }
}
