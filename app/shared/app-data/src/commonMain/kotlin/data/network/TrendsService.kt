/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.app.data.models.trending.TrendsInfo
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.runWrappingExceptionAsLoadResult
import me.him188.ani.app.tools.paging.SinglePagePagingSource
import me.him188.ani.client.apis.TrendsAniApi
import me.him188.ani.client.models.AniTrends
import me.him188.ani.datasources.bangumi.next.apis.TrendingBangumiNextApi
import me.him188.ani.datasources.bangumi.next.models.BangumiNextSlimSubject
import me.him188.ani.datasources.bangumi.next.models.BangumiNextSubjectType
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.logging.error
import kotlin.coroutines.CoroutineContext

class TrendsRepository(
    private val trendsApi: ApiInvoker<TrendsAniApi>,
    private val bangumiTrendingApi: ApiInvoker<TrendingBangumiNextApi>,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_
) : Repository() {
    suspend fun getTrendsInfo(): TrendsInfo {
        return withContext(ioDispatcher) {
            trendsApi {
                getTrends().body().toTrendsInfo()
            }
        }
    }

    // From animeko server
    fun trendsInfoPager(): Flow<PagingData<TrendsInfo>> {
        return Pager(defaultPagingConfig) {
            SinglePagePagingSource<Unit, TrendsInfo> {
                runWrappingExceptionAsLoadResult<Unit, TrendsInfo> {
                    val trendsInfo = withContext(ioDispatcher) {
                        trendsApi {
                            getTrends().body().toTrendsInfo()
                        }
                    }
                    PagingSource.LoadResult.Page(
                        listOf(trendsInfo),
                        null,
                        null,
                    )
                }.also {
                    if (it is PagingSource.LoadResult.Error) {
                        logger.error(it.throwable) { "Failed to load ani trends info." }
                    }
                }
            }
        }.flow
    }

    fun bangumiTrendingSubjectsPager(): Flow<PagingData<TrendingSubjectInfo>> {
        return Pager(
            defaultPagingConfig,
            initialKey = 0,
        ) {
            BangumiTrendingSubjectPagingSource()
        }.flow
    }

    private inner class BangumiTrendingSubjectPagingSource : PagingSource<Int, TrendingSubjectInfo>() {
        override fun getRefreshKey(state: PagingState<Int, TrendingSubjectInfo>): Int? = state.anchorPosition

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TrendingSubjectInfo> {
            val offset = params.key ?: 0
            val loadSize = params.loadSize
            return runWrappingExceptionAsLoadResult {
                val list = withContext(ioDispatcher) {
                    bangumiTrendingApi.invoke {
                        getTrendingSubjects(
                            type = BangumiNextSubjectType.Anime,
                            offset = offset,
                            limit = loadSize,
                        ).body()
                    }
                }.data.map {
                    it.subject.toTrendingSubjectInfo()
                }

                LoadResult.Page(
                    list,
                    prevKey = if (offset == 0) null else offset - loadSize,
                    // If server returns fewer items than `loadSize`, we assume there's nothing more.
                    nextKey = if (list.size < loadSize) null else offset + loadSize,
                )
            }.also {
                if (it is LoadResult.Error) {
                    logger.error(it.throwable) { "Failed to load bangumi trends info." }
                }
            }
        }
    }

}

private fun BangumiNextSlimSubject.toTrendingSubjectInfo(): TrendingSubjectInfo {
    return TrendingSubjectInfo(
        bangumiId = id,
        nameCn = nameCN.ifEmpty { name },
        imageLarge = images?.large ?: images?.medium ?: images?.common ?: "",
    )
}

fun AniTrends.toTrendsInfo(): TrendsInfo {
    return TrendsInfo(
        subjects = trendingSubjects.map {
            TrendingSubjectInfo(it.bangumiId, it.nameCn, it.imageLarge)
        },
    )
}