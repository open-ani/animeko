/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import me.him188.ani.app.data.models.trending.TrendingRankingItemInfo
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.app.data.models.trending.TrendsInfo
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.runWrappingExceptionAsLoadResult
import me.him188.ani.app.tools.paging.SinglePagePagingSource
import me.him188.ani.client.apis.TrendsAniApi
import me.him188.ani.client.models.AniTrendingRankingItem
import me.him188.ani.client.models.AniTrends
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext

class TrendsRepository(
    private val trendsApi: ApiInvoker<TrendsAniApi>,
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

    /**
     * 完整的 Bangumi 热度排行, 最多 1000 个条目.
     *
     * 注意, 此方法返回的数据可能包含 NSFW 条目. 调用方需要自行根据用户设置考虑过滤.
     */
    fun trendingRankingPager(): Flow<PagingData<TrendingRankingItemInfo>> {
        return Pager(defaultPagingConfig, initialKey = 0) {
            TrendingRankingPagingSource(trendsApi, ioDispatcher)
        }.flow
    }
}

internal class TrendingRankingPagingSource(
    private val trendsApi: ApiInvoker<TrendsAniApi>,
    private val ioDispatcher: CoroutineContext,
) : PagingSource<Int, TrendingRankingItemInfo>() {
    // 排行随时可能更新, 名次会变, 刷新时从第一名重新加载
    override fun getRefreshKey(state: PagingState<Int, TrendingRankingItemInfo>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TrendingRankingItemInfo> {
        val offset = params.key ?: 0
        val limit = params.loadSize.coerceAtMost(MAX_LIMIT)
        return runWrappingExceptionAsLoadResult {
            val response = withContext(ioDispatcher) {
                trendsApi {
                    getTrendingSubjects(offset = offset, limit = limit).body()
                }
            }
            LoadResult.Page(
                response.items.map { it.toTrendingRankingItemInfo() },
                prevKey = null,
                nextKey = if (offset + limit >= response.total) null else offset + limit,
            )
        }.also {
            if (it is LoadResult.Error) {
                logger.error(it.throwable) { "Failed to load trending ranking." }
            }
        }
    }

    private companion object {
        private val logger = logger<TrendingRankingPagingSource>()

        /** 服务端单次最多返回的条目数. */
        const val MAX_LIMIT = 100
    }
}

fun AniTrends.toTrendsInfo(): TrendsInfo {
    return TrendsInfo(
        subjects = trendingSubjects.map {
            TrendingSubjectInfo(it.bangumiId, it.nameCn, it.imageLarge)
        },
    )
}

private fun AniTrendingRankingItem.toTrendingRankingItemInfo(): TrendingRankingItemInfo {
    return TrendingRankingItemInfo(
        rank = trendingRank,
        heat = heat,
        subject = subject.toBatchSubjectDetails(),
    )
}
