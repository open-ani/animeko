/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.info

import kotlinx.serialization.Serializable
import me.him188.ani.tools.datasourcetestmcp.DEFAULT_ANI_API_BASE_URL

@Serializable
data class SearchSubjectsInput(
    val query: String,
    val limit: Int = 10,
    val offset: Int = 0,
    val includeEpisodes: Boolean = false,
    val aniApiBaseUrl: String = DEFAULT_ANI_API_BASE_URL,
    val aniBearerToken: String? = null,
)

@Serializable
data class SearchSubjectsResult(
    val ok: Boolean,
    val summary: String,
    val subjects: List<SubjectResult> = emptyList(),
    val errors: List<String> = emptyList(),
)

@Serializable
data class SubjectResult(
    val subjectId: Long,
    val name: String,
    val nameCn: String,
    val airDate: String,
    val mainEpisodeCount: Int,
    val episodes: List<EpisodeResult>? = null,
)

@Serializable
data class GetSubjectEpisodesInput(
    val subjectId: Long,
    val aniApiBaseUrl: String = DEFAULT_ANI_API_BASE_URL,
    val aniBearerToken: String? = null,
)

@Serializable
data class GetSubjectEpisodesResult(
    val ok: Boolean,
    val summary: String,
    val subject: SubjectResult? = null,
    val errors: List<String> = emptyList(),
)

@Serializable
data class EpisodeResult(
    val episodeId: Long,
    /**
     * 在条目内的序号, 例如 "26" (第二季第一集可能为 26)
     */
    val sort: String,
    /**
     * 在季度内的序号, 例如 "1"
     */
    val ep: String? = null,
    val name: String,
    val nameCn: String,
    val airDate: String? = null,
    val type: String,
)

@Serializable
data class GetTrendsInput(
    /**
     * 返回条数上限, <= 0 表示全部
     */
    val limit: Int = 20,
    val aniApiBaseUrl: String = DEFAULT_ANI_API_BASE_URL,
    val aniBearerToken: String? = null,
)

@Serializable
data class GetTrendsResult(
    val ok: Boolean,
    val summary: String,
    val subjects: List<TrendingSubjectResult> = emptyList(),
    val errors: List<String> = emptyList(),
)

@Serializable
data class TrendingSubjectResult(
    /**
     * 排名, 从 1 开始
     */
    val rank: Int,
    /**
     * 与 search_subjects / get_subject_episodes 的 subjectId 同一 ID 空间
     */
    val subjectId: Long,
    val nameCn: String,
    val imageUrl: String,
)
