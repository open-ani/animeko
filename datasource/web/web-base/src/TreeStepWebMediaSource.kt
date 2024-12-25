/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.source

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.paging.SinglePagePagedSource
import me.him188.ani.datasources.api.paging.SizedSource
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.warn
import org.jsoup.nodes.Document

abstract class ThreeStepWebMediaSource : WebMediaSource() {
    data class Bangumi(
        val internalId: String, // 可以是数字, 例如 /bangumi/128.html 中的 128
        val name: String,
        val url: String, // absolute url, with baseUrl
    )

    data class Ep(
        val name: String,
        val url: String, // absolute url, with baseUrl
        val channel: String? = null, // 线路 
    )

    abstract val baseUrl: String // no trailing '/'

    // 搜索到的番剧结果
    abstract fun parseBangumiSearch(document: Document): List<Bangumi>

    abstract suspend fun search(name: String, query: MediaFetchRequest): List<Bangumi>


    // 点看番剧后的页面
    abstract fun parseEpisodeList(document: Document): List<Ep>

    protected abstract val client: HttpClient
    private val subtitleLanguages = listOf("CHS")

    fun createMediaMatch(
        bangumi: Bangumi,
        ep: Ep
    ): MediaMatch {
        val sort = EpisodeSort(ep.name.removePrefix("第").removeSuffix("集"))
        val suffixChannel = ep.channel?.let { "-$it" }
        return MediaMatch(
            DefaultMedia(
                mediaId = "$mediaSourceId.${bangumi.internalId}-${sort}$suffixChannel",
                mediaSourceId = mediaSourceId,
                originalUrl = bangumi.url,
                download = ResourceLocation.WebVideo(ep.url),
                originalTitle = """${bangumi.name} ${ep.name} ${ep.channel.orEmpty()}""".trim(),
                publishedTime = 0L,
                properties = MediaProperties(
                    subjectName = bangumi.name,
                    episodeName = ep.name,
                    subtitleLanguageIds = subtitleLanguages,
                    resolution = "1080P",
                    alliance = mediaSourceId,
                    size = FileSize.Unspecified,
                    subtitleKind = SubtitleKind.EMBEDDED,
                ),
                episodeRange = EpisodeRange.single(
                    if (isPossiblyMovie(ep.name) && sort is EpisodeSort.Special) {
                        EpisodeSort(1) // 电影总是 01
                    } else {
                        sort
                    },
                ),
                location = MediaSourceLocation.Online,
                kind = MediaSourceKind.WEB,
            ),
            MatchKind.FUZZY,
        )
    }

    private fun isPossiblyMovie(title: String): Boolean {
        return ("简" in title || "繁" in title) &&
                ("2160P" in title || "1440P" in title || "2K" in title || "4K" in title || "1080P" in title || "720P" in title)
    }

    override suspend fun checkConnection(): ConnectionStatus {
        return try {
            client.get(baseUrl).status.isSuccess().toConnectionStatus()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn { IllegalStateException("Failed to check connection for $mediaSourceId", e) }
            ConnectionStatus.FAILED
        }
    }

    override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> = SinglePagePagedSource {
        query.subjectNames.asFlow().flatMapMerge { name ->
            val bangumiList = flow {
                emit(search(name, query))
            }.retry(3) { e ->
                logger.warn(e) { "Failed to search using name '$name'" }
                true
            }.firstOrNull() ?: return@flatMapMerge emptyFlow()

            bangumiList.asFlow()
                .flatMapMerge { bangumi ->
                    val result = flow {
                        emit(client.get(bangumi.url).bodyAsDocument())
                    }.map {
                        parseEpisodeList(it)
                    }.retry(3) { e ->
                        logger.warn(e) { "Failed to get episodes using name '$name'" }
                        true
                    }.firstOrNull()
                        .orEmpty()
                        .asSequence()
                        .map { ep ->
                            createMediaMatch(bangumi, ep)
                        }
                        .filter {
                            it.definitelyMatches(query) ||
                                    isPossiblyMovie(it.media.originalTitle)
                        }
                        .toList()

                    logger.info { "$mediaSourceId fetched ${result.size} episodes for '$name': ${result.joinToString { it.media.episodeRange.toString() }}" }
                    result.asFlow()
                }
        }
    }
}
