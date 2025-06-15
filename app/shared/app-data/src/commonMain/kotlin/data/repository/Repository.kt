/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import me.him188.ani.utils.coroutines.flows.shareTransparentlyIn
import me.him188.ani.utils.logging.thisLogger
import kotlin.coroutines.CoroutineContext

/**
 * 每个 Repository 封装对相应数据的访问逻辑, 提供简单的接口, 让调用方无需关心数据的来源是网络还是本地存储.
 *
 * 实现约束:
 * - 所有访问数据的接口都只会抛出 [RepositoryException], 用于向调用方传递已知的情况, 例如网络连接失败.
 *   其他异常属于 bug.
 */
abstract class Repository(
    protected val defaultDispatcher: CoroutineContext = Dispatchers.Default,
) {
    protected val logger = thisLogger()

    private val sharingScope = CoroutineScope(defaultDispatcher)

    protected fun <T> Flow<T>.cachedWithTransparentException(): Flow<T> {
        // WhileSubscribed 使用调用方的生命周期. 停止 collect 时也会停止查询. SharedFlow 同时会相当于一个 mutex.
        return shareTransparentlyIn(sharingScope, started = SharingStarted.WhileSubscribed(), replay = 1)
    }

    companion object {
        val defaultPagingConfig = PagingConfig(
            pageSize = 30,
        )
    }
}

internal fun <T : Any> List<T>?.toPage(pageNumber: Int): PagingSource.LoadResult.Page<Int, T> {
    val items = this
    return PagingSource.LoadResult.Page(
        data = items ?: emptyList(),
        prevKey = if (pageNumber > 0) pageNumber - 1 else null,
        nextKey = if (!items.isNullOrEmpty()) pageNumber + 1 else null,
    )
}
