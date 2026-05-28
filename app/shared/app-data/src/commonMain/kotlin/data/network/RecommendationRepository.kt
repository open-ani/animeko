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
import io.ktor.client.call.body
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.runWrappingExceptionAsLoadResult
import me.him188.ani.client.apis.HomeAniApi
import me.him188.ani.client.models.AniSubjectRecommendation
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.logging.error
import kotlin.coroutines.CoroutineContext

class RecommendationRepository(
    private val homeApi: ApiInvoker<HomeAniApi>,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_
) : Repository() {
    fun recommendedSubjectsPager(): Flow<PagingData<RecommendedItemInfo>> {
        return Pager(defaultPagingConfig, initialKey = 0) {
            HomeRecommendationPagingSource()
        }.flow
    }

    private inner class HomeRecommendationPagingSource : PagingSource<Int, RecommendedItemInfo>() {
        override fun getRefreshKey(state: PagingState<Int, RecommendedItemInfo>): Int? = state.anchorPosition

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, RecommendedItemInfo> {
            val offset = params.key ?: 0
            val loadSize = params.loadSize
            return runWrappingExceptionAsLoadResult {
                val response = withContext(ioDispatcher) {
                    homeApi {
                        getHomeRecommendations(
                            offset = offset,
                            limit = loadSize,
                        ).body()
                    }
                }
                val list: List<RecommendedItemInfo> = response.items.mapNotNull { it.toRecommendedSubjectInfo() }

                LoadResult.Page(
                    list,
                    prevKey = if (offset == 0) null else (offset - loadSize).coerceAtLeast(0),
                    nextKey = if (offset + list.size >= response.total) null else offset + loadSize,
                )
            }.also {
                if (it is LoadResult.Error) {
                    logger.error(it.throwable) { "Failed to load home recommendations." }
                }
            }
        }
    }

    private fun AniSubjectRecommendation.toRecommendedSubjectInfo(): RecommendedSubjectInfo? {
        val id = subjectId?.takeIf { it > 0 }?.toInt() ?: return null
        return RecommendedSubjectInfo(
            bangumiId = id,
            nameCn = subjectNameCn.ifEmpty { subjectName },
            imageLarge = imageUrl,
        )
    }
}
