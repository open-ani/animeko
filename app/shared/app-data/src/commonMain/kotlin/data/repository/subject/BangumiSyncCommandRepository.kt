/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.ktor.util.reflect.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.PaginatedResponse
import me.him188.ani.app.data.models.bangumi.BangumiSyncCommand
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.client.apis.BangumiAniApi
import me.him188.ani.client.models.AniListSyncCommandsSortBy
import me.him188.ani.utils.ktor.ApiInvoker

class BangumiSyncCommandRepository(
    private val bangumiApi: ApiInvoker<BangumiAniApi>,
) : Repository() {
    suspend fun executeSyncCommands() {
        withContext(Dispatchers.Default) {
            try {
                bangumiApi.invoke {
                    this.executeSyncCommands().body()
                }
            } catch (e: Exception) {
                throw RepositoryException.wrapOrThrowCancellation(e)
            }
        }
    }

    suspend fun listSyncCommands(
        offset: Int = 0, limit: Int = 100,
    ): PaginatedResponse<BangumiSyncCommand> = withContext(Dispatchers.Default) {
        try {
            bangumiApi.invoke {
                this.listSyncCommands(
                    offset,
                    limit,
                    sortBy = AniListSyncCommandsSortBy.CREATED_AT_DESC,
                ).typedBody<PaginatedResponse<BangumiSyncCommand>>(typeInfo<PaginatedResponse<BangumiSyncCommand>>())
                    .let {
                        PaginatedResponse(it.total, it.items)
                    }
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    fun syncCommandsPager(
        pageSize: Int = 100,
    ): Flow<PagingData<BangumiSyncCommand>> = Pager(
        defaultPagingConfig,
    ) {
        object : PagingSource<Int, BangumiSyncCommand>() {
            override fun getRefreshKey(state: PagingState<Int, BangumiSyncCommand>): Int? = null
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, BangumiSyncCommand> {
                val page = params.key ?: 0
                val response = listSyncCommands(
                    offset = page,
                    limit = pageSize,
                )
                return LoadResult.Page(
                    data = response.items,
                    prevKey = if (page == 0) null else page - 1,
                    nextKey = if (response.items.size < pageSize) null else page + 1,
                )
            }
        }
    }.flow
}
