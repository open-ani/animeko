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
import me.him188.ani.app.data.network.BangumiSearchFilters
import me.him188.ani.app.data.network.BatchSubjectDetails
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
        private val filters = searchQuery.toBangumiSearchFilters()
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

        private fun SubjectSearchQuery.toBangumiSearchFilters(): BangumiSearchFilters {
            return BangumiSearchFilters(
                tags,
                airDates = season?.toBangumiAirDates(),
                ratings = rating?.toBangumiRatings(),
                nsfw = nsfw,
            )
        }

        private fun AnimeSeasonId.toBangumiAirDates(): List<String> {
            val (begin, _, end) = this.yearMonths
            return listOf(
                ">=${begin.first}-${begin.second}-01",
                "<${end.first}-${end.second}-31",
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
    }
}
