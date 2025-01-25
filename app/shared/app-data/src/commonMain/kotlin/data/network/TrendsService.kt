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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.app.data.models.trending.TrendsInfo
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.runWrappingExceptionAsLoadResult
import me.him188.ani.app.domain.session.AniApiInvoker
import me.him188.ani.app.tools.paging.SinglePagePagingSource
import me.him188.ani.client.apis.TrendsAniApi
import me.him188.ani.client.models.AniTrends
import me.him188.ani.utils.coroutines.IO_
import kotlin.coroutines.CoroutineContext

class TrendsRepository(
    private val trendsApi: AniApiInvoker<TrendsAniApi>,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_
) : Repository() {
    suspend fun getTrendsInfo(): TrendsInfo {
        return withContext(ioDispatcher) {
            trendsApi {
                getTrends().body().toTrendsInfo()
            }
        }
    }

    fun trendsInfoPager(): Flow<PagingData<TrendsInfo>> {
        return Pager(defaultPagingConfig) {
            SinglePagePagingSource<Unit, TrendsInfo> {
                runWrappingExceptionAsLoadResult {
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
                }
            }
        }.flow
    }
}

fun AniTrends.toTrendsInfo(): TrendsInfo {
    return TrendsInfo(
        subjects = trendingSubjects.map {
            TrendingSubjectInfo(it.bangumiId, it.nameCn, it.imageLarge)
        },
    )
}