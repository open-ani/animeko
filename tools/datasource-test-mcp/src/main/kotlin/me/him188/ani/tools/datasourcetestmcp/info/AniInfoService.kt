/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.info

import io.ktor.client.HttpClient
import me.him188.ani.client.apis.SubjectsAniApi
import me.him188.ani.client.apis.TrendsAniApi
import me.him188.ani.client.models.AniEpisodeCollection
import me.him188.ani.client.models.AniSubjectCollection

/**
 * 信息能力: 用名字搜索番剧, 获取番剧的剧集列表, 以及热门趋势. 数据来自 Ani API.
 */
class AniInfoService(
    private val httpClient: HttpClient,
) {
    suspend fun searchSubjects(input: SearchSubjectsInput): SearchSubjectsResult {
        val api = createApi(input.aniApiBaseUrl, input.aniBearerToken)
        return runCatching {
            val response = api.searchSubjects(
                q = input.query,
                offset = input.offset,
                limit = input.limit,
            ).body()
            val subjects = response.items.map { item ->
                val episodes = if (input.includeEpisodes) {
                    runCatching { api.getSubject(item.id).body().episodes.map { it.toEpisodeResult() } }
                        .getOrNull()
                } else {
                    null
                }
                SubjectResult(
                    subjectId = item.id,
                    name = item.name,
                    nameCn = item.nameCn,
                    airDate = item.airDate,
                    mainEpisodeCount = item.mainEpisodeCount,
                    episodes = episodes,
                )
            }
            SearchSubjectsResult(
                ok = true,
                summary = "Found ${subjects.size} subject(s) for \"${input.query}\"",
                subjects = subjects,
            )
        }.getOrElse { exception ->
            SearchSubjectsResult(
                ok = false,
                summary = "Subject search failed",
                errors = listOf("${exception::class.simpleName}: ${exception.message.orEmpty()}"),
            )
        }
    }

    suspend fun getSubjectEpisodes(input: GetSubjectEpisodesInput): GetSubjectEpisodesResult {
        val api = createApi(input.aniApiBaseUrl, input.aniBearerToken)
        return runCatching {
            val subject = api.getSubject(input.subjectId).body()
            GetSubjectEpisodesResult(
                ok = true,
                summary = "Subject ${subject.displayName()} has ${subject.episodes.size} episode(s)",
                subject = SubjectResult(
                    subjectId = subject.id,
                    name = subject.name,
                    nameCn = subject.nameCn,
                    airDate = subject.airDate,
                    mainEpisodeCount = subject.episodes.size,
                    episodes = subject.episodes.map { it.toEpisodeResult() },
                ),
            )
        }.getOrElse { exception ->
            GetSubjectEpisodesResult(
                ok = false,
                summary = "Failed to fetch subject ${input.subjectId}",
                errors = listOf("${exception::class.simpleName}: ${exception.message.orEmpty()}"),
            )
        }
    }

    suspend fun getTrends(input: GetTrendsInput): GetTrendsResult {
        val api = TrendsAniApi(input.aniApiBaseUrl, httpClient).apply {
            input.aniBearerToken?.takeIf { it.isNotBlank() }?.let(::setBearerToken)
        }
        return runCatching {
            val trending = api.getTrends().body().trendingSubjects
                .let { if (input.limit > 0) it.take(input.limit) else it }
                .mapIndexed { index, subject ->
                    TrendingSubjectResult(
                        rank = index + 1,
                        subjectId = subject.bangumiId.toLong(),
                        nameCn = subject.nameCn,
                        imageUrl = subject.imageLarge,
                    )
                }
            GetTrendsResult(
                ok = true,
                summary = "Top ${trending.size} trending subject(s)",
                subjects = trending,
            )
        }.getOrElse { exception ->
            GetTrendsResult(
                ok = false,
                summary = "Failed to fetch trends",
                errors = listOf("${exception::class.simpleName}: ${exception.message.orEmpty()}"),
            )
        }
    }

    /**
     * 获取用于 selector 引擎搜索的查询上下文 (条目名列表 + 剧集序号等).
     */
    suspend fun fetchEpisodeQueryContext(
        subjectId: Long,
        episodeId: Long,
        baseUrl: String,
        bearerToken: String?,
    ): EpisodeQueryContext {
        val api = createApi(baseUrl, bearerToken)
        val subject = api.getSubject(subjectId).body()
        val episode = api.getEpisode(subjectId, episodeId).body()
        return EpisodeQueryContext(
            subjectNames = buildList {
                add(subject.nameCn)
                add(subject.name)
                addAll(subject.aliases)
            }.map(String::trim).filter(String::isNotBlank).distinct(),
            subjectDisplayName = subject.nameCn.ifBlank { subject.name },
            episodeSort = episode.sort,
            episodeEp = episode.ep,
            episodeName = episode.nameCn.ifBlank { episode.name }.ifBlank { null },
        )
    }

    private fun createApi(baseUrl: String, bearerToken: String?): SubjectsAniApi {
        return SubjectsAniApi(baseUrl, httpClient).apply {
            bearerToken?.takeIf { it.isNotBlank() }?.let(::setBearerToken)
        }
    }
}

class EpisodeQueryContext(
    val subjectNames: List<String>,
    val subjectDisplayName: String,
    val episodeSort: String,
    val episodeEp: String?,
    val episodeName: String?,
)

private fun AniSubjectCollection.displayName(): String = nameCn.ifBlank { name }

private fun AniEpisodeCollection.toEpisodeResult(): EpisodeResult {
    return EpisodeResult(
        episodeId = episodeId,
        sort = sort,
        ep = ep,
        name = name,
        nameCn = nameCn,
        airDate = airdate,
        type = type.toString(),
    )
}
