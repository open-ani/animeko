/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.schedule.AnimeSeasonId
import me.him188.ani.app.data.models.schedule.yearMonths
import me.him188.ani.app.data.network.AniSubjectSearchService
import me.him188.ani.app.data.network.BatchSubjectDetails
import me.him188.ani.app.data.network.SubjectSearchField
import me.him188.ani.app.data.network.SubjectSearchFilters
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.search.RatingRange
import me.him188.ani.app.domain.search.SearchSort
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

class SubjectSearchRepository(
    private val aniSubjectSearchService: AniSubjectSearchService,
    private val subjectCollectionRepository: SubjectCollectionRepository,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : Repository(defaultDispatcher) {

    /**
     * 使用 [searchQuery] 搜索条目.
     *
     * 注意, 此方法返回的数据总是会包含 NSFW 条目. 调用方需要自行根据用户设置考虑过滤.
     */
    fun searchSubjects(
        searchQuery: SubjectSearchQuery,
        ignoreDoneAndDropped: suspend () -> Boolean = { false },
        pagingConfig: PagingConfig = bangumiSearchPagingConfig
    ): Flow<PagingData<BatchSubjectDetails>> = Pager(
        config = pagingConfig,
        initialKey = 0,
        pagingSourceFactory = {
            SubjectSearchPagingSource(ignoreDoneAndDropped, searchQuery)
        },
    ).flow.flowOn(defaultDispatcher)

    private inner class SubjectSearchPagingSource(
        private val ignoreDoneAndDropped: suspend () -> Boolean,
        private val searchQuery: SubjectSearchQuery
    ) : PagingSource<Int, BatchSubjectDetails>() {
        private val filters = searchQuery.toSubjectSearchFilters()
        override fun getRefreshKey(state: PagingState<Int, BatchSubjectDetails>): Int? = null
        override suspend fun load(
            params: LoadParams<Int>
        ): LoadResult<Int, BatchSubjectDetails> = withContext(defaultDispatcher) {
            val offset = params.key
                ?: return@withContext LoadResult.Error(IllegalArgumentException("Key is null"))
            return@withContext try {
                val subjects = aniSubjectSearchService.searchSubjects(
                    searchQuery.keywords,
                    offset = offset,
                    limit = params.loadSize,
                    filters = filters,
                    sort = searchQuery.sort,
                    fields = subjectSearchFields,
                )

                val filteredSubjects = if (ignoreDoneAndDropped()) {
                    val excludedIds = subjectCollectionRepository.getSubjectIdsByCollectionType(
                        types = listOf(UnifiedCollectionType.DONE, UnifiedCollectionType.DROPPED),
                    ).first()

                    subjects.filter { it.subjectInfo.subjectId !in excludedIds }
                } else {
                    subjects
                }

                // 在分页源中直接过滤掉不符合条件的数据 #2380
                val subjectInfos = filterSubjectsBySort(
                    filteredSubjects,
                    searchQuery.sort,
                )

                return@withContext LoadResult.Page(
                    subjectInfos,
                    prevKey = if (offset == 0) null else offset,
                    nextKey = if (subjectInfos.isEmpty()) null else offset + params.loadSize,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoadResult.Error(RepositoryException.wrapOrThrowCancellation(e))
            }
        }

        private fun SubjectSearchQuery.toSubjectSearchFilters(): SubjectSearchFilters {
            return SubjectSearchFilters(
                tags,
                airDates = toBangumiAirDates(),
                ratings = rating?.toBangumiRatings(),
                nsfw = nsfw,
            )
        }

        private fun RatingRange.toBangumiRatings(): List<String> {
            val range = this
            return listOfNotNull(
                range.min?.let { ">=${it}" },
                range.max?.let { "<${it}" },
            )
        }

        /**
         * 将数据过滤从View提升到分页层，不然会导致 #2380
         */
        private fun filterSubjectsBySort(
            subjects: List<BatchSubjectDetails>,
            sort: SearchSort
        ): List<BatchSubjectDetails> {
            return when (sort) {
                SearchSort.RANK -> subjects.filter { it.subjectInfo.ratingInfo.total >= 50 }
                SearchSort.DATE -> subjects.sortedByDescending { it.subjectInfo.airDate }
                SearchSort.MATCH,
                SearchSort.COLLECTION -> subjects
            }
        }
    }

    private companion object {
        private val bangumiSearchPagingConfig = PagingConfig(
            pageSize = 20, // Bangumi API 实际最多返回 20 个结果 #2417
            initialLoadSize = 20,
        )

        private val subjectSearchFields = listOf(
            SubjectSearchField.NAME,
            SubjectSearchField.SUMMARY,
            SubjectSearchField.IMAGE_LARGE,
            SubjectSearchField.NSFW,
            SubjectSearchField.AIR_DATE,
            SubjectSearchField.SCORE,
            SubjectSearchField.RANK,
            SubjectSearchField.RATING_TOTAL,
            SubjectSearchField.TAGS,
            SubjectSearchField.MAIN_EPISODE_COUNT,
            SubjectSearchField.LIGHT_RELATED_PERSON_INFO,
        )
    }
}

/**
 * 年份/季度筛选对应的 Bangumi airDates 区间.
 *
 * 仅年份: 该自然年全年. 年份+季度: 该季度覆盖的月份 (如冬季从上年 12 月到本年 2 月).
 * 无年份: null (不限).
 *
 * 上界统一取区间后的下一天 (开区间), 避免 "MM-31" 这类不存在的日期;
 * 月份统一补零为两位数.
 */
internal fun SubjectSearchQuery.toBangumiAirDates(): List<String>? {
    val y = year ?: return null
    val q = season
    if (q == null) {
        return listOf(">=$y-01-01", "<${y + 1}-01-01")
    }
    val (begin, _, end) = AnimeSeasonId(y, q).yearMonths
    // 季末次月 1 日为开区间上界. 现有 yearMonths 的季末月 ∈ {2, 5, 8, 11}, 次月不跨年;
    // 若未来某季的末月是 12 月, 上界需改为次年 1 月 (此处假设由测试兜底).
    fun Int.twoDigits(): String = toString().padStart(2, '0')
    return listOf(
        ">=${begin.first}-${begin.second.twoDigits()}-01",
        "<${end.first}-${(end.second + 1).twoDigits()}-01",
    )
}
