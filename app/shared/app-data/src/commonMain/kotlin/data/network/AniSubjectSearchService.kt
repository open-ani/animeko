/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.subject.PersonPosition
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.client.apis.SubjectsAniApi
import me.him188.ani.client.models.AniNsfwFilter
import me.him188.ani.client.models.AniSubjectSearch
import me.him188.ani.client.models.AniSubjectSearchSortBy
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.bangumi.models.search.BangumiSort
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.coroutines.CoroutineContext


class AniSubjectSearchService(
    private val subjectApi: ApiInvoker<SubjectsAniApi>,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) {
    suspend fun searchSubjects(
        keyword: String,
        useNewApi: Boolean,
        offset: Int? = null,
        limit: Int? = null,

        sort: BangumiSort? = null,
        filters: BangumiSearchFilters? = null,
    ): List<BatchSubjectDetails> = withContext(ioDispatcher) {
        val result = subjectApi.invoke {
            searchSubjects(
                q = keyword,
                offset = offset,
                limit = limit,
                tags = filters?.tags,
                includeNsfw = when (filters?.nsfw) {
                    true -> AniNsfwFilter.INCLUDE
                    false -> AniNsfwFilter.EXCLUDE
                    null -> null
                },
                sortBy = when (sort) {
                    BangumiSort.MATCH -> AniSubjectSearchSortBy.RELEVANCE
                    BangumiSort.HEAT -> AniSubjectSearchSortBy.AIR_DATE_DESC
                    BangumiSort.RANK -> AniSubjectSearchSortBy.RANK_ASC
                    BangumiSort.SCORE -> AniSubjectSearchSortBy.RATING_DESC
                    null -> null
                },
            )
        }.body()

        result.items.map { search -> search.toBatchSubjectDetails() }
    }

    companion object {
        fun sanitizeKeyword(keyword: String): String {
            return buildString(keyword.length) {
                for (c in keyword) {
                    if (MediaListFilters.charsToDeleteForSearch.contains(c.code)) {
                        append(' ')
                    } else {
                        append(c)
                    }
                }
            }
        }
    }

    private fun AniSubjectSearch.toBatchSubjectDetails(): BatchSubjectDetails {
        return BatchSubjectDetails(
            subjectInfo = SubjectInfo(
                subjectId = this.id.toInt(),
                subjectType = SubjectType.ANIME,
                name = this.name,
                nameCn = this.nameCn,
                summary = this.summary,
                nsfw = this.nsfw,
                imageLarge = this.imageLarge,
                totalEpisodes = this.mainEpisodeCount,
                airDate = PackedDate.parseFromDate(this.airDate),
                tags = this.tags.map { Tag(it.name, it.count) },
                aliases = emptyList(),
                ratingInfo = RatingInfo(this.rank ?: 0, this.ratingTotal, RatingCounts.Zero, this.score ?: ""),
                collectionStats = SubjectCollectionStats.Zero,
                completeDate = PackedDate.Invalid,

                ),
            mainEpisodeCount = this.mainEpisodeCount,
            lightSubjectRelations = LightSubjectRelations(
                lightRelatedPersonInfoList = this.lightRelatedPersonInfoList.map { pi ->
                    LightRelatedPersonInfo(pi.name, PersonPosition(pi.position))
                },
                lightRelatedCharacterInfoList = emptyList(),
            ),
        )
    }
}