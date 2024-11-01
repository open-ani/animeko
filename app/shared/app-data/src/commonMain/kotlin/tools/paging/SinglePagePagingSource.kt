/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlin.coroutines.cancellation.CancellationException

@Suppress("FunctionName")
fun <Key : Any, V : Any> SinglePagePagingSource(
    load: suspend (params: PagingSource.LoadParams<Key>) -> PagingSource.LoadResult<Key, V>
): PagingSource<Key, V> {
    return object : PagingSource<Key, V>() {
        override suspend fun load(params: LoadParams<Key>): LoadResult<Key, V> {
            return try {
                load(params)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }

        override fun getRefreshKey(state: PagingState<Key, V>): Key? = null
    }
}
