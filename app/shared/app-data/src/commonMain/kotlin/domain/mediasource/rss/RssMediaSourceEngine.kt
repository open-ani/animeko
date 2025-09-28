/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.rss

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.data.repository.RepositoryServiceUnavailableException
import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.app.domain.mediasource.MediaSourceEngineHelpers
import me.him188.ani.app.domain.mediasource.asCandidate
import me.him188.ani.app.domain.rss.RssChannel
import me.him188.ani.app.domain.rss.RssItem
import me.him188.ani.app.domain.rss.RssParser
import me.him188.ani.app.domain.rss.getMediaSize
import me.him188.ani.app.domain.rss.guessResourceLocation
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.Resolution
import me.him188.ani.datasources.api.topic.titles.RawTitleParser
import me.him188.ani.datasources.api.topic.titles.parse
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.ktor.toSource
import me.him188.ani.utils.xml.Document
import me.him188.ani.utils.xml.Xml
import kotlin.coroutines.cancellation.CancellationException

/**
 * 一个共享接口同时用于 [RssMediaSource] 编辑时的测试.
 *
 * 默认实现为 [DefaultRssMediaSourceEngine].
 * 在用户编辑数据源时的测试功能中, 也是使用 [DefaultRssMediaSourceEngine] (因为也要执行网络请求).
 *
 * @see DefaultRssMediaSourceEngine
 */
abstract class RssMediaSourceEngine {
    data class Result(
        val encodedUrl: Url,
        val query: RssSearchQuery,
        val document: Document?,
        val channel: RssChannel?,
        val matchedMediaList: List<Media>?, // null means not found
        val error: Throwable? = null, // ClientRequestException
    )

    /**
     * 搜索并使用 [searchConfig] 过滤.
     */
    @Throws(RepositoryException::class, CancellationException::class)
    suspend fun search(
        searchConfig: RssSearchConfig,
        query: RssSearchQuery,
        page: Int?,
        mediaSourceId: String,
    ): Result {
        val encodedUrl = MediaSourceEngineHelpers.encodeUrlSegment(query.subjectName)

        val finalUrl = Url(
            searchConfig.searchUrl
                .replace("{keyword}", encodedUrl)
                .replace("{page}", page.toString()),
        )

        return searchImpl(finalUrl, searchConfig, query, page, mediaSourceId)
    }

    @Throws(RepositoryException::class, CancellationException::class)
    protected abstract suspend fun searchImpl(
        finalUrl: Url,
        config: RssSearchConfig,
        query: RssSearchQuery,
        page: Int?,
        mediaSourceId: String,
    ): Result

    protected companion object {
        fun convertItemToMedia(
            item: RssItem,
            mediaSourceId: String,
        ): Media? {
            val details = RawTitleParser.getDefault().parse(item.title, null)

            return DefaultMedia(
                mediaId = "$mediaSourceId.${item.guid}",
                mediaSourceId = mediaSourceId,
                originalUrl = item.link.takeIf { it.isNotBlank() } ?: item.guid,
                download = item.guessResourceLocation() ?: return null,
                originalTitle = item.title,
                publishedTime = item.pubDate?.toInstant(TimeZone.currentSystemDefault())
                    ?.toEpochMilliseconds() ?: 0,
                properties = MediaProperties(
                    subjectName = null,
                    episodeName = null,
                    subtitleLanguageIds = details.subtitleLanguages.map { it.id },
                    resolution = details.resolution?.toString() ?: Resolution.R1080P.toString(),
                    alliance = item.title.trim().split("]", "】").getOrNull(0).orEmpty().removePrefix("[")
                        .removePrefix("【").trim(),
                    size = item.getMediaSize(),
                    subtitleKind = details.subtitleKind,
                ),
                episodeRange = details.episodeRange,
                kind = MediaSourceKind.BitTorrent,
                location = MediaSourceLocation.Online,
            )
        }
    }
}

/**
 * 决定 [RssMediaSourceEngine.search] 的行为的配置.
 *
 * 添加新配置需要考虑兼容旧版本, 需要修改:
 * - [createFilters]
 * - 编辑数据源时的测试功能 (`RssTestPane`).
 *
 * 如果新加功能会影响产出结果 (例如新加一个过滤功能), 还需要修改 UI 的 tags 来在测试页面能 debug 到这个新过滤功能: `RssItemPresentation.computeTags`
 *
 * @since 3.9
 * @see createFilters
 */
@Serializable
data class RssSearchConfig(
    val searchUrl: String = "", // required
    val filterByEpisodeSort: Boolean = true,
    val filterBySubjectName: Boolean = true,
) {
    companion object {
        val Empty = RssSearchConfig()
    }
}

/**
 * 根据配置信息创建应当用于过滤搜索到的 [Media] 列表的过滤器.
 *
 * 注意, 这不会影响 UI 里每个 RSS 卡片显示的信息. 如需修改, 查看 [RssItemPresentation.computeTags]
 *
 * @see me.him188.ani.app.domain.mediasource.MediaListFilter
 */
fun RssSearchConfig.createFilters() = buildList {
    if (filterByEpisodeSort) add(MediaListFilters.ContainsAnyEpisodeInfo)
}

/**
 * @see RssMediaSourceEngine
 */
class DefaultRssMediaSourceEngine(
    /**
     * Engine 自己不会 cache 实例, 每次都调用 `.first()`.
     */
    private val client: Flow<ScopedHttpClient>,
    private val parser: RssParser = RssParser(includeOrigin = false),
) : RssMediaSourceEngine() {
    @Throws(RepositoryException::class, CancellationException::class)
    override suspend fun searchImpl(
        finalUrl: Url,
        config: RssSearchConfig,
        query: RssSearchQuery,
        page: Int?,
        mediaSourceId: String
    ): Result {
        return try {
            val document = try {
                client.first().use {
                    get(finalUrl).let { resp ->
                        when (resp.status.value) {
                            in 500..599 -> throw RepositoryServiceUnavailableException()
                            in 200..299 -> resp.bodyAsChannel().toSource().use {
                                Xml.parse(it)
                            }

                            else -> {
                                throw RepositoryNetworkException(
                                    "HTTP ${resp.status.value} ${resp.status.description}",
                                    null,
                                )
                            }
                        }
                    }
                }
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.NotFound) {
                    // 404 Not Found
                    return Result(
                        finalUrl,
                        query,
                        document = null,
                        channel = null,
                        matchedMediaList = null,
                    )
                }
                if (e.response.status == HttpStatusCode.BadRequest) {
                    // io.ktor.client.plugins.ClientRequestException: Client request(GET https://garden.breadio.wiki/feed.xml?filter=[{"type":"动画","search":["Bocchi%20the%20%22Guitar%20Hero%22%20Rock%20Story"]}]) invalid: 400 . Text: "{"status":400,"detail":{"url":"https://garden.breadio.wiki/feed.xml?filter=[{%22type%22:%22%E5%8A%A8%E7%94%BB%22,%22search%22:[%22Bocchi%20the%20%22Guitar%20Hero%22%20Rock%20Story%22]}]","filter":"[{\"type\":\"动画\",\"search\":[\"Bocchi the \"Guitar Hero\" Rock Story\"]}]","message":"Expected ',' or ']' after array element in JSON at position 37"}}"
                    return Result(
                        finalUrl,
                        query,
                        document = null,
                        channel = null,
                        matchedMediaList = null,
                        error = e,
                    )
                }
                throw e
            }

            val channel = parser.parse(document)
                ?: return Result(
                    finalUrl,
                    query,
                    document,
                    null,
                    matchedMediaList = null,
                )

            val filters = config.createFilters()

            val items = with(query.toFilterContext()) {
                channel.items.mapNotNull { rssItem ->
                    convertItemToMedia(rssItem, mediaSourceId)
                        ?.takeIf { media ->
                            filters.applyOn(media.asCandidate())
                        }
                }
            }

            Result(
                finalUrl,
                query,
                document,
                channel,
                items,
            )
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }
}
