/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */


package me.him188.ani.app.domain.mediasource.web

import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.him188.ani.app.data.models.ApiFailure
import me.him188.ani.app.data.models.ApiResponse
import me.him188.ani.app.data.models.fold
import me.him188.ani.app.data.models.map
import me.him188.ani.app.data.models.runApiRequest
import me.him188.ani.app.data.repository.media.SelectorMediaSourceEpisodeCacheRepository
import me.him188.ani.app.domain.mediasource.codec.DefaultMediaSourceCodec
import me.him188.ani.app.domain.mediasource.codec.DontForgetToRegisterCodec
import me.him188.ani.app.domain.mediasource.codec.MediaSourceArguments
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.matcher.WebVideoMatcher
import me.him188.ani.datasources.api.matcher.WebVideoMatcherContext
import me.him188.ani.datasources.api.matcher.WebVideoMatcherProvider
import me.him188.ani.datasources.api.matcher.WebViewConfig
import me.him188.ani.datasources.api.paging.SinglePagePagedSource
import me.him188.ani.datasources.api.paging.SizedSource
import me.him188.ani.datasources.api.paging.map
import me.him188.ani.datasources.api.paging.merge
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.HttpMediaSource
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceFactory
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.source.deserializeArgumentsOrNull
import me.him188.ani.datasources.api.source.useHttpClient

@Suppress("unused") // bug
private typealias ArgumentType = SelectorMediaSourceArguments
private typealias EngineType = DefaultSelectorMediaSourceEngine

/**
 * [SelectorMediaSource] 的用户侧配置, 用于创建 [SelectorMediaSource] 实例.
 *
 * @since 3.10
 */
@OptIn(DontForgetToRegisterCodec::class)
@Serializable
data class SelectorMediaSourceArguments(
    override val name: String,
    val description: String,
    val iconUrl: String,
    val searchConfig: SelectorSearchConfig = SelectorSearchConfig.Empty,
) : MediaSourceArguments {
    companion object {
        val Default = SelectorMediaSourceArguments(
            name = "Selector",
            description = "",
            iconUrl = "",
            searchConfig = SelectorSearchConfig.Empty,
        )
    }
}

object SelectorMediaSourceCodec : DefaultMediaSourceCodec<SelectorMediaSourceArguments>(
    SelectorMediaSource.FactoryId,
    SelectorMediaSourceArguments::class,
    currentVersion = 2,
    SelectorMediaSourceArguments.serializer(),
)

/**
 * @since 3.10
 */
class SelectorMediaSource(
    override val mediaSourceId: String,
    config: MediaSourceConfig,
    val repository: SelectorMediaSourceEpisodeCacheRepository,
    override val kind: MediaSourceKind = MediaSourceKind.WEB,
) : HttpMediaSource(), WebVideoMatcherProvider {
    companion object {
        val FactoryId = FactoryId("web-selector")
    }

    private val arguments =
        config.deserializeArgumentsOrNull(ArgumentType.serializer())
            ?: SelectorMediaSourceArguments.Default
    private val searchConfig = arguments.searchConfig

    private val client by lazy { useHttpClient(config) }
    private val engine by lazy { EngineType(flowOf(client)) }

    override val location: MediaSourceLocation get() = MediaSourceLocation.Online

    class Factory(
        val repository: SelectorMediaSourceEpisodeCacheRepository
    ) : MediaSourceFactory {
        override val factoryId: FactoryId get() = FactoryId

        override val info: MediaSourceInfo = MediaSourceInfo(
            displayName = "Selector",
            description = "通用 CSS Selector 数据源",
            iconUrl = "",
        )

        override val allowMultipleInstances: Boolean get() = true
        override fun create(mediaSourceId: String, config: MediaSourceConfig): MediaSource =
            SelectorMediaSource(mediaSourceId, config, repository)
    }

    override suspend fun checkConnection(): ConnectionStatus {
        return kotlin.runCatching {
            runApiRequest {
                client.get(searchConfig.searchUrl) // 提交一个请求, 只要它不是因为网络错误就行
            }.fold(
                onSuccess = { ConnectionStatus.SUCCESS },
                onKnownFailure = {
                    when (it) {
                        ApiFailure.NetworkError -> ConnectionStatus.FAILED
                        ApiFailure.ServiceUnavailable -> ConnectionStatus.FAILED
                        ApiFailure.Unauthorized -> ConnectionStatus.SUCCESS
                    }
                },
            )
        }.recover {
            // 只要不是网络错误就行
            ConnectionStatus.SUCCESS
        }.getOrThrow()
    }

    override val info: MediaSourceInfo = MediaSourceInfo(
        displayName = arguments.name,
        description = arguments.description,
        websiteUrl = searchConfig.searchUrl,
        iconUrl = arguments.iconUrl,
    )

    // all-in-one search
    private suspend fun EngineType.search(
        searchConfig: SelectorSearchConfig,
        query: SelectorSearchQuery,
        mediaSourceId: String,
    ): ApiResponse<List<DefaultMedia>> = withContext(Dispatchers.Default) {
        val cache = repository.getCache(mediaSourceId, query.subjectName)
        if (cache.isNotEmpty()) {
            return@withContext ApiResponse.success(
                cache.flatMap { webSearchInfo ->
                    selectMedia(
                        webSearchInfo.webEpisodeInfos.asSequence(),
                        searchConfig,
                        query,
                        mediaSourceId,
                        subjectName = webSearchInfo.webSubjectInfo.name,
                    ).filteredList
                },
            )
        }
        searchSubjects(
            searchConfig.searchUrl,
            subjectName = query.subjectName,
            useOnlyFirstWord = searchConfig.searchUseOnlyFirstWord,
            removeSpecial = searchConfig.searchRemoveSpecial,
        ).map { (_, document) ->
            document ?: return@map emptyList()

            buildList {
                val subjects = selectSubjects(document, searchConfig)
                    .orEmpty()
                    .let { originalList ->
                        val filters = searchConfig.createFiltersForSubject()
                        with(query.toFilterContext()) {
                            originalList.filter {
                                filters.applyOn(it.asCandidate())
                            }
                        }
                    }

                for (subjectInfo in subjects) {
                    val episodeDocument = doHttpGet(subjectInfo.fullUrl).getOrNull() ?: continue
                    val episodes =
                        selectEpisodes(episodeDocument, subjectInfo.fullUrl, searchConfig)?.episodes ?: continue
                    repository.addCache(mediaSourceId, query.subjectName, subjectInfo, episodes)
                    addAll(
                        selectMedia(
                            episodes.asSequence(),
                            searchConfig,
                            query,
                            mediaSourceId,
                            subjectName = subjectInfo.name,
                        ).filteredList,
                    )
                }
            }
        }
    }

    override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> {
        return query.subjectNames
            .map { name ->
                SinglePagePagedSource {
                    engine.search(
                        searchConfig,
                        SelectorSearchQuery(
                            subjectName = name,
                            episodeSort = query.episodeSort,
                            allSubjectNames = query.subjectNames,
                            episodeEp = query.episodeEp,
                            episodeName = query.episodeName,
                        ),
                        mediaSourceId,
                    ).getOrThrow().asFlow()
                }.map {
                    MediaMatch(it, MatchKind.FUZZY)
                }
            }.merge()
    }

    override val matcher: WebVideoMatcher by lazy(LazyThreadSafetyMode.PUBLICATION) {
        object : WebVideoMatcher {
            override fun match(
                url: String,
                context: WebVideoMatcherContext
            ): WebVideoMatcher.MatchResult = engine.matchWebVideo(url, arguments.searchConfig.matchVideo)

            override fun patchConfig(config: WebViewConfig): WebViewConfig {
                val myCookies = arguments.searchConfig.matchVideo.cookies
                return config.copy(
                    cookies = myCookies.lines().filter { it.isNotBlank() },
                )
            }
        }
    }
}