/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import androidx.collection.MutableIntList
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
import me.him188.ani.app.data.network.BangumiSearchFilters
import me.him188.ani.app.data.network.BangumiSubjectSearchService
import me.him188.ani.app.data.network.BatchSubjectDetails
import me.him188.ani.app.data.network.SubjectService
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.search.RatingRange
import me.him188.ani.app.domain.search.SearchSort
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.models.BangumiSubjectType
import me.him188.ani.datasources.bangumi.models.search.BangumiSort
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

class SubjectSearchRepository(
    private val bangumiSubjectSearchService: BangumiSubjectSearchService,
    private val subjectCollectionRepository: SubjectCollectionRepository,
    private val subjectService: SubjectService,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : Repository(defaultDispatcher) {

    /**
     * 使用 [searchQuery] 搜索条目.
     *
     * 注意, 此方法返回的数据总是会包含 NSFW 条目. 调用方需要自行根据用户设置考虑过滤.
     */
    fun searchSubjects(
        searchQuery: SubjectSearchQuery,
        useNewApi: suspend () -> Boolean = { false },
        ignoreDoneAndDropped: suspend () -> Boolean = { false },
        pagingConfig: PagingConfig = Repository.defaultPagingConfig
    ): Flow<PagingData<BatchSubjectDetails>> = Pager(
        config = pagingConfig,
        initialKey = 0,
//        remoteMediator = SubjectSearchRemoteMediator(useNewApi, searchQuery, pagingConfig),
        pagingSourceFactory = {
            SubjectSearchPagingSource(useNewApi, ignoreDoneAndDropped, searchQuery)
        },
    ).flow.flowOn(defaultDispatcher)

    private inner class SubjectSearchPagingSource(
        private val useNewApi: suspend () -> Boolean,
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
                val res = bangumiSubjectSearchService.searchSubjectIds(
                    searchQuery.keywords,
                    useNewApi = useNewApi(),
                    offset = offset,
                    limit = params.loadSize,
                    filters = filters,
                    sort = searchQuery.sort.toBangumiSort(),
                )

                val filteredIds = if (ignoreDoneAndDropped()) {
                    val excludedIds = subjectCollectionRepository.getSubjectIdsByCollectionType(
                        types = listOf(UnifiedCollectionType.DONE, UnifiedCollectionType.DROPPED),
                    ).first()

                    MutableIntList().apply {
                        res.forEach { if (it !in excludedIds) add(it) }
                    }
                } else {
                    res
                }

                // 在分页源中直接过滤掉不符合条件的数据 #2380
                val subjectInfos = filterSubjectsBySort(
                    subjectService.batchGetSubjectDetails(filteredIds), 
                    searchQuery.sort
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

        private fun SearchSort.toBangumiSort(): BangumiSort? {
            return when (this) {
                SearchSort.MATCH -> BangumiSort.MATCH
                SearchSort.RANK -> BangumiSort.SCORE // 不能用 RANK, bangumi 会把 #0 也包含, 排在最前
                SearchSort.COLLECTION -> BangumiSort.HEAT
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
                SearchSort.MATCH,
                SearchSort.COLLECTION -> subjects
            }
        }
    }

//    private inner class SubjectSearchRemoteMediator(
//        val useNewApi: Boolean,
//        val searchQuery: SubjectSearchQuery,
//        val pagingConfig: PagingConfig,
//    ) : RemoteMediator<Int, SubjectInfoNew>() {
//        override suspend fun load(
//            loadType: LoadType,
//            state: PagingState<Int, SubjectInfoNew>
//        ): MediatorResult {
//            val currentPage = when (loadType) {
//                LoadType.REFRESH -> 0
//                LoadType.PREPEND -> state.anchorPosition?.minus(1) ?: 0
//                LoadType.APPEND -> state.anchorPosition?.plus(1) ?: 0
//            }
//            val api = searchApi.first()
//            val res = if (useNewApi) {
//                api.searchSubjectByKeywords(
//                    searchQuery.keyword,
//                    offset = pagingConfig.pageSize * (currentPage - 0),
//                    limit = pagingConfig.pageSize,
//                ).map {
//                    it.toSubjectInfo()
//                }
//            } else {
//                api.searchSubjectsByKeywordsWithOldApi(
//                    searchQuery.keyword,
//                    type = searchQuery.type.toBangumiSubjectType(),
//                    responseGroup = BangumiSubjectImageSize.SMALL,
//                    start = pagingConfig.pageSize * (currentPage - 0),
//                    maxResults = pagingConfig.pageSize,
//                ).map {
//                    it.toSubjectInfo()
//                }
//            }
//
//            /*
//              (findInfoboxValue("播放结束") ?: findInfoboxValue("放送结束"))
//                ?.let {
//                    PackedDate.parseFromDate(
//                        it.replace('年', '-')
//                            .replace('月', '-')
//                            .removeSuffix("日"),
//                    )
//                }
//                ?: PackedDate.Invalid
//             */
//
//            subjectInfoDao.upsert(
//                res.page.map {
//                    it.toEntity()
//                },
//            )
//            return MediatorResult.Success(
//                endOfPaginationReached = res.isEmpty(),
//            )
//        }
//    }

    private companion object {
        private val logger = logger<SubjectSearchRepository>()
    }
}

private fun SubjectType.toBangumiSubjectType(): BangumiSubjectType = when (this) {
    SubjectType.ANIME -> BangumiSubjectType.Anime
}

