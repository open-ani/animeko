/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.rss

import io.ktor.client.request.get
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import me.him188.ani.app.data.models.ApiFailure
import me.him188.ani.app.data.models.fold
import me.him188.ani.app.data.models.runApiRequest
import me.him188.ani.app.domain.mediasource.MediaListFilterContext
import me.him188.ani.app.domain.mediasource.codec.DefaultMediaSourceCodec
import me.him188.ani.app.domain.mediasource.codec.DontForgetToRegisterCodec
import me.him188.ani.app.domain.mediasource.codec.MediaSourceArguments
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.paging.PageBasedPagedSource
import me.him188.ani.datasources.api.paging.Paged
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
import me.him188.ani.utils.ktor.ScopedHttpClient

/**
 * [RssMediaSource] 的用户侧配置, 用于创建 [RssMediaSource] 实例.
 *
 * @since 3.9
 */
@OptIn(DontForgetToRegisterCodec::class)
@Serializable
data class RssMediaSourceArguments(
    override val name: String,
    val description: String,
    val iconUrl: String,
    val searchConfig: RssSearchConfig = RssSearchConfig.Empty,
) : MediaSourceArguments {
    companion object {
        const val DEFAULT_ICON_URL = "https://rss.com/blog/wp-content/uploads/2019/10/social_style_3_rss-512-1.png"

        val Default = RssMediaSourceArguments(
            name = "RSS",
            description = "",
            iconUrl = DEFAULT_ICON_URL,
            searchConfig = RssSearchConfig(
                searchUrl = "",
            ),
        )
    }
}

object RssMediaSourceCodec : DefaultMediaSourceCodec<RssMediaSourceArguments>(
    RssMediaSource.FactoryId, RssMediaSourceArguments::class,
    currentVersion = 1, RssMediaSourceArguments.serializer(),
)

/**
 * 用于对接 [MediaSource] 和 [RssMediaSourceEngine]
 */
class RssMediaSource(
    override val mediaSourceId: String,
    config: MediaSourceConfig,
    override val kind: MediaSourceKind = MediaSourceKind.BitTorrent,
    private val client: ScopedHttpClient,
) : HttpMediaSource() {
    companion object {
        val FactoryId = FactoryId("rss")
    }

    private val arguments =
        config.deserializeArgumentsOrNull(RssMediaSourceArguments.serializer()) ?: RssMediaSourceArguments.Default
    private val searchConfig = arguments.searchConfig

    private val usePaging = searchConfig.searchUrl.contains("{page}")

    private val engine by lazy { DefaultRssMediaSourceEngine(flowOf(client)) }

    override val location: MediaSourceLocation get() = MediaSourceLocation.Online

    class Factory : MediaSourceFactory {
        override val factoryId: FactoryId get() = FactoryId

        override val info: MediaSourceInfo = MediaSourceInfo(
            displayName = "RSS",
            description = "通用 RSS BT 数据源",
            // https://rss.com/blog/free-rss-icon/
            iconUrl = "https://rss.com/blog/wp-content/uploads/2019/10/social_style_3_rss-512-1.png",
        )

        override val allowMultipleInstances: Boolean get() = true
        override fun create(mediaSourceId: String, config: MediaSourceConfig, client: ScopedHttpClient): MediaSource =
            RssMediaSource(mediaSourceId, config, client = client)
    }

    override suspend fun checkConnection(): ConnectionStatus {
        return kotlin.runCatching {
            runApiRequest {
                client.use {
                    get(searchConfig.searchUrl) // 提交一个请求, 只要它不是因为网络错误就行
                }
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

    // https://garden.breadio.wiki/feed.xml?filter=[{"search":["樱trick"]}]
    // https://acg.rip/page/2.xml?term=%E9%AD%94%E6%B3%95%E5%B0%91%E5%A5%B3
    private fun startSearch(query: RssSearchQuery): SizedSource<Media> {
        return PageBasedPagedSource { page ->
            if (!usePaging && page != 0) return@PageBasedPagedSource null

            val result = engine.search(searchConfig, query, page, mediaSourceId)

            // 404 Not Found
            val channel = result.channel ?: return@PageBasedPagedSource null
            val topics = result.matchedMediaList ?: return@PageBasedPagedSource null

            Paged(
                null,
                hasMore = channel.items.isNotEmpty(),
                page = topics,
            )
        }
    }

    override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> {
        return query.subjectNames
            .map { name ->
                startSearch(
                    RssSearchQuery(
                        subjectName = name,
                        episodeSort = query.episodeSort,
                        allSubjectNames = query.subjectNames,
                        episodeEp = query.episodeEp,
                        episodeName = query.episodeName,
                    ),
                ).map {
                    MediaMatch(it, MatchKind.FUZZY)
                }
            }.merge()
    }
}

data class RssSearchQuery(
    val subjectName: String,
    val allSubjectNames: List<String>,
    val episodeSort: EpisodeSort,
    val episodeEp: EpisodeSort?,
    val episodeName: String?,
)

fun RssSearchQuery.toFilterContext() = MediaListFilterContext(
    subjectNames = allSubjectNames.toSet(),
    episodeSort = episodeSort,
    episodeEp = episodeSort,
    episodeName = episodeName,
)
