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

    internal inner class SubjectSearchPagingSource(
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
                    filters = if (ignoreDoneAndDropped()) {
                        filters.copy(excludeCollectionTypes = listOf(UnifiedCollectionType.DONE, UnifiedCollectionType.DROPPED))
                    } else {
                        filters
                    },
                    sort = searchQuery.sort,
                    fields = subjectSearchFields,
                )

                return@withContext LoadResult.Page(
                    subjects,
                    prevKey = if (offset == 0) null else offset,
                    // 搜索响应只有 items, 没有总数, 以空页判断结束
                    nextKey = if (subjects.isEmpty()) null else offset + params.loadSize,
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
                // "最高排名" 按评分排序, 只看 Bangumi 有排名的条目: 排名要求足够的评分人数,
                // 否则排在最前面的全是一两个人打 10 分的冷门条目 #2380 #3505
                ranks = if (sort == SearchSort.RANK) listOf(">=1") else null,
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
