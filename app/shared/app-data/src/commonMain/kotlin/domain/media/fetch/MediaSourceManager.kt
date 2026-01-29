/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.fetch

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonElement
import me.him188.ani.app.data.models.preference.ProxyAuthorization
import me.him188.ani.app.data.models.preference.ProxyConfig
import me.him188.ani.app.data.repository.media.MediaSourceInstanceRepository
import me.him188.ani.app.data.repository.media.MikanIndexCacheRepository
import me.him188.ani.app.data.repository.media.SelectorMediaSourceEpisodeCacheRepository
import me.him188.ani.app.data.repository.media.updateConfig
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.media.cache.MediaCacheManager.Companion.LOCAL_FS_MEDIA_SOURCE_ID
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.codec.getArgumentOrNull
import me.him188.ani.app.domain.mediasource.instance.MediaSourceInstance
import me.him188.ani.app.domain.mediasource.instance.MediaSourceSave
import me.him188.ani.app.domain.mediasource.rss.RssMediaSource
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSource
import me.him188.ani.app.domain.settings.ProxyProvider
import me.him188.ani.app.platform.getAniUserAgent
import me.him188.ani.app.tools.ServiceLoader
import me.him188.ani.datasources.api.matcher.MediaSourceWebVideoMatcherLoader
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceFactory
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.serializeArguments
import me.him188.ani.datasources.ikaros.IkarosMediaSource
import me.him188.ani.datasources.jellyfin.EmbyMediaSource
import me.him188.ani.datasources.jellyfin.JellyfinMediaSource
import me.him188.ani.datasources.mikan.MikanCNMediaSource
import me.him188.ani.datasources.mikan.MikanMediaSource
import me.him188.ani.utils.coroutines.onReplacement
import me.him188.ani.utils.ktor.ClientProxyConfig
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatform
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext

interface MediaSourceManager { // available by inject
    /**
     * 全部 [MediaSourceInstance] 列表.
     */
    val allInstances: Flow<List<MediaSourceInstance>>

    /**
     * 全部的 [MediaSource], 包括那些设置里关闭的, 包括本地的.
     */
    val allFactories: List<MediaSourceFactory>

    /**
     * 全部的 [MediaSource], 包括那些设置里关闭的, 包括本地的.
     */
    val allFactoryIds: List<FactoryId>

    /**
     * 全部的 [MediaSource], 包括那些设置里关闭的, 但不包括本地的.
     */
    val allFactoryIdsExceptLocal: List<FactoryId>
        get() = allFactoryIds.filter { !isLocal(it) }

    /**
     * 根据启用的 [MediaSourceInstance] 创建的 [MediaSourceMediaFetcher].
     */
    val mediaFetcher: Flow<MediaFetcher>

    val webVideoMatcherLoader: MediaSourceWebVideoMatcherLoader

    fun isLocal(factoryId: FactoryId): Boolean = isLocal(factoryId.value)
    fun isLocal(mediaSourceId: String): Boolean {
        return mediaSourceId == LOCAL_FS_MEDIA_SOURCE_ID
    }

    // null means not found
    fun instanceConfigFlow(instanceId: String): Flow<MediaSourceConfig?>

    fun findInfoByFactoryId(factoryId: FactoryId): MediaSourceInfo? {
        return allFactories.find { it.factoryId == factoryId }?.info
    }

    fun infoFlowByMediaSourceId(mediaSourceId: String): Flow<MediaSourceInfo?> {
        if (mediaSourceId == "Bangumi") { // workaround for bangumi connectivity testing
            return flowOf(
                MediaSourceInfo(
                    "Bangumi",
                    "提供观看记录数据",
                    "https://bangumi.tv",
                    "https://bangumi.tv/img/favicon.ico",
                    iconResourceId = "bangumi.png",
                ),
            )
        }
        return allInstances.map { list ->
            list.find { it.mediaSourceId == mediaSourceId }?.source?.info
        }
    }

    suspend fun addInstance(
        instanceId: String,
        mediaSourceId: String,
        factoryId: FactoryId,
        config: MediaSourceConfig
    )

    suspend fun getListBySubscriptionId(subscriptionId: String): List<MediaSourceSave>

    /**
     * @see MediaSourceInstanceRepository.partiallyReorder
     */
    suspend fun partiallyReorderInstances(instanceIds: List<String>)

    /**
     * deprecated.
     */
    suspend fun updateConfig(instanceId: String, config: MediaSourceConfig): Boolean
    suspend fun setEnabled(instanceId: String, enabled: Boolean)
    suspend fun removeInstance(instanceId: String)

    fun mediaSourceTiersFlow(): Flow<MediaSelectorSourceTiers>
}

suspend fun <T> MediaSourceManager.addInstance(
    instanceId: String,
    mediaSourceId: String,
    factoryId: FactoryId,
    serializer: SerializationStrategy<T>,
    arguments: T
) = addInstance(
    instanceId,
    mediaSourceId,
    factoryId,
    MediaSourceConfig(serializedArguments = MediaSourceConfig.serializeArguments(serializer, arguments)),
)

suspend fun MediaSourceManager.updateMediaSourceArguments(
    instanceId: String,
    arguments: JsonElement
): Boolean {
    val config = instanceConfigFlow(instanceId).first() ?: return false
    return updateConfig(
        instanceId,
        config.copy(serializedArguments = arguments),
    )
}

suspend fun <T> MediaSourceManager.updateMediaSourceArguments(
    instanceId: String,
    serializer: SerializationStrategy<T>,
    arguments: T
) = updateMediaSourceArguments(instanceId, MediaSourceConfig.serializeArguments(serializer, arguments))

/**
 * 根据请求创建 [MediaFetchSession].
 * 也会跟随用户设置的启用/禁用数据源 emit 新的 [MediaFetchSession].
 *
 * @param requestLazy 相当于 [lazy]. 只有第一个元素会被使用. 必须至少 emit 一个元素.
 *
 * @see MediaFetchRequest.Companion.create
 */
fun MediaSourceManager.createFetchFetchSessionFlow(requestLazy: Flow<MediaFetchRequest>): Flow<MediaFetchSession> =
    this.mediaFetcher.map { it.newSession(requestLazy) }

class MediaSourceManagerImpl(
    /**
     * 必须是 Factory:MediaSource:Instance = 1:1:1 的关系.
     *
     * @see LOCAL_FS_MEDIA_SOURCE_ID
     */
    additionalSources: () -> List<MediaSource>, // local sources, calculated only once
    private val flowCoroutineContext: CoroutineContext = Dispatchers.Default,
) : MediaSourceManager, KoinComponent {
    private val proxyProvider: ProxyProvider by inject()
    private val mikanIndexCacheRepository: MikanIndexCacheRepository by inject()
    private val instances: MediaSourceInstanceRepository by inject()
    private val selectorMediaSourceEpisodeCacheRepository: SelectorMediaSourceEpisodeCacheRepository by inject()
    private val clientProvider: HttpClientProvider by inject()
    private val codecManager: MediaSourceCodecManager by inject()

    private val scope = CoroutineScope(
        CoroutineExceptionHandler { _, throwable ->
            // log error
            logger.error(throwable) { "DownloadProviderManager scope error" }
        },
    )
    private val factories: List<MediaSourceFactory> = buildSet {
        addAll(ServiceLoader.loadServices(MediaSourceFactory::class))
        add(MikanMediaSource.Factory()) // Kotlin bug, MPP 加载不了 resources
        add(MikanCNMediaSource.Factory())
        add(RssMediaSource.Factory())
        add(JellyfinMediaSource.Factory())
        add(EmbyMediaSource.Factory())
        add(IkarosMediaSource.Factory())
        add(SelectorMediaSource.Factory(selectorMediaSourceEpisodeCacheRepository))
    }.toList()

    private val additionalSources by lazy {
        additionalSources().map { source ->
            MediaSourceInstance(
                instanceId = source.mediaSourceId,
                factoryId = FactoryId(source.mediaSourceId),
                isEnabled = true,
                config = MediaSourceConfig.Default,
                source = source,
            )
        }
    }
    override val allInstances =
        combine(instances.flow, proxyProvider.proxy.distinctUntilChanged()) { saves, config ->
            // 一定要 additionalSources 在前面, local sources 需要优先使用
            this.additionalSources + saves.mapNotNull { createInstance(it, config) }
        }.onReplacement { list ->
            list.forEach { it.close() }
        }.flowOn(flowCoroutineContext).shareIn(scope, replay = 1, started = SharingStarted.Lazily)
    override val allFactories: List<MediaSourceFactory> get() = factories

    private fun createInstance(save: MediaSourceSave, config: ProxyConfig?): MediaSourceInstance? {
        val factory = factories.find { it.factoryId == save.factoryId }
        return if (factory == null) {
            logger.error { "MediaSourceFactory '${save.factoryId}' not found for ${save.mediaSourceId}" }
            null
        } else {
            MediaSourceInstance(
                instanceId = save.instanceId,
                factoryId = save.factoryId,
                isEnabled = save.isEnabled,
                config = save.config,
                source = factory.create(
                    config,
                    save.mediaSourceId,
                    save.config,
                    clientProvider.get(ScopedHttpClientUserAgent.BROWSER),
                ),
            )
        }
    }

    override val allFactoryIds: List<FactoryId> by lazy {
        factories
            .map { it.factoryId }
            .plus(this.additionalSources.map { it.factoryId })
    }

    override val mediaFetcher: Flow<MediaFetcher> = allInstances.map { instances ->
        MediaSourceMediaFetcher(
            configProvider = { MediaFetcherConfig(currentPlatform() != Platform.Ios) },
            mediaSources = instances,
        )
    }
    override val webVideoMatcherLoader: MediaSourceWebVideoMatcherLoader = MediaSourceWebVideoMatcherLoader(
        allInstances.map { list -> list.map { it.source } },
    )

    override fun instanceConfigFlow(instanceId: String): Flow<MediaSourceConfig?> {
        return instances.flow.map { list ->
            list.find { it.instanceId == instanceId }?.config
        }
    }

    override suspend fun addInstance(
        instanceId: String,
        mediaSourceId: String,
        factoryId: FactoryId,
        config: MediaSourceConfig
    ) {
        val save = MediaSourceSave(
            instanceId = instanceId,
            mediaSourceId = mediaSourceId,
            factoryId = factoryId,
            isEnabled = true,
            config = config,
        )
        instances.add(save)
    }

    override suspend fun getListBySubscriptionId(subscriptionId: String): List<MediaSourceSave> {
        return instances.flow.first().filter { save ->
            save.config.subscriptionId == subscriptionId
        }
    }

    override suspend fun partiallyReorderInstances(instanceIds: List<String>) {
        return instances.partiallyReorder(instanceIds)
    }

    override suspend fun updateConfig(instanceId: String, config: MediaSourceConfig): Boolean {
        return instances.updateConfig(instanceId, config)
    }

    override suspend fun setEnabled(instanceId: String, enabled: Boolean) {
        instances.updateSave(instanceId) {
            copy(isEnabled = enabled)
        }
    }

    override suspend fun removeInstance(instanceId: String) {
        instances.remove(instanceId)
    }

    override fun mediaSourceTiersFlow(): Flow<MediaSelectorSourceTiers> = instances.flow.map { list ->
        MediaSelectorSourceTiers(
            buildMap {
                for (save in list) {
                    val argument = save.getArgumentOrNull(codecManager)
                    if (argument != null) {
                        put(save.mediaSourceId, argument.tier)
                    }
                }
            },
        )
    }.flowOn(flowCoroutineContext)

    private fun MediaSourceFactory.create(
        proxyConfig: ProxyConfig?,
        mediaSourceId: String,
        config: MediaSourceConfig,
        client: ScopedHttpClient,
    ): MediaSource {
        @Suppress("DEPRECATION")
        val mediaSourceConfig = config.copy(
            proxy = config.proxy ?: proxyConfig?.toClientProxyConfig(),
            userAgent = getAniUserAgent(),
        )
        return when (this) {
            is MikanMediaSource.Factory -> create(mediaSourceConfig, mikanIndexCacheRepository, client)
            is MikanCNMediaSource.Factory -> create(mediaSourceConfig, mikanIndexCacheRepository, client)
            else -> create(mediaSourceId, mediaSourceConfig, client)
        }
    }

    private companion object {
        private val logger = logger<MediaSourceManager>()
    }
}

fun ProxyConfig.toClientProxyConfig() = ClientProxyConfig(
    url = url,
    authorization = authorization?.toHeader(),
)

fun ProxyAuthorization.toHeader(): String = "Basic ${"$username:$password"}"
