/*
 * Copyright (C) 2024 OpenAni and contributors.
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
import me.him188.ani.app.data.network.BangumiSubjectService
import me.him188.ani.app.data.network.BatchSubjectDetails
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.datasources.bangumi.BangumiRateLimitedException
import me.him188.ani.datasources.bangumi.client.BangumiSearchApi
import me.him188.ani.datasources.bangumi.models.BangumiSubjectType
import me.him188.ani.datasources.bangumi.models.subjects.BangumiSubjectImageSize
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.collections.mapToIntArray
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

class SubjectSearchRepository(
    private val searchApi: Flow<BangumiSearchApi>,
    private val subjectService: BangumiSubjectService,
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
        pagingConfig: PagingConfig = Repository.defaultPagingConfig
    ): Flow<PagingData<BatchSubjectDetails>> = Pager(
        config = pagingConfig,
        initialKey = 0,
//        remoteMediator = SubjectSearchRemoteMediator(useNewApi, searchQuery, pagingConfig),
        pagingSourceFactory = {
            SubjectSearchPagingSource(useNewApi, searchQuery)
        },
    ).flow.flowOn(defaultDispatcher)

    private inner class SubjectSearchPagingSource(
        private val useNewApi: suspend () -> Boolean,
        private val searchQuery: SubjectSearchQuery
    ) : PagingSource<Int, BatchSubjectDetails>() {
        override fun getRefreshKey(state: PagingState<Int, BatchSubjectDetails>): Int? = null
        override suspend fun load(
            params: LoadParams<Int>
        ): LoadResult<Int, BatchSubjectDetails> = withContext(defaultDispatcher) {
            val offset = params.key
                ?: return@withContext LoadResult.Error(IllegalArgumentException("Key is null"))
            return@withContext try {
                val api = searchApi.first()
                val res = if (useNewApi()) {
                    api.searchSubjectByKeywords(
                        searchQuery.keyword,
                        offset = offset,
                        limit = params.loadSize,
                        types = listOf(BangumiSubjectType.Anime),
                    )
                } else {
                    try {
                        api.searchSubjectsByKeywordsWithOldApi(
                            searchQuery.keyword,
                            type = searchQuery.type.toBangumiSubjectType(),
                            responseGroup = BangumiSubjectImageSize.SMALL,
                            start = offset,
                            maxResults = params.loadSize,
                        ).page.mapToIntArray {
                            it.id
                        }
                    } catch (e: BangumiRateLimitedException) {
                        throw RepositoryRateLimitedException(cause = e)
                    }
                }

                val subjectInfos = subjectService.batchGetSubjectDetails(res)

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

