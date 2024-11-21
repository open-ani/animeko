/*
 * Copyright (C) 2024 OpenAni and contributors.
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

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
    protected val logger = logger(this::class)

    private val sharingScope = CoroutineScope(defaultDispatcher)

    protected fun <T> Flow<T>.cachedWithTransparentException() = this
        .map { Result.success(it) }
        .catch { Result.failure<T>(it) } // 封装异常以传递给下游
        .shareIn(
            sharingScope, started = SharingStarted.WhileSubscribed(), replay = 1,
        ) // WhileSubscribed 使用调用方的生命周期. 停止 collect 时也会停止查询. SharedFlow 同时会相当于一个 mutex.
        .map {
            it.getOrThrow() // 透明异常. 上游的异常传递给下游
        }

    companion object {
        val defaultPagingConfig = PagingConfig(
            pageSize = 30,
            enablePlaceholders = false,
        )
    }
}


fun interface RepositoryUsernameProvider {
    @Throws(RepositoryException::class, CancellationException::class)
    suspend operator fun invoke(): String
}

@Throws(RepositoryAuthorizationException::class, CancellationException::class)
suspend fun RepositoryUsernameProvider.getOrThrow(): String {
    val username = try {
        invoke()
    } catch (e: Exception) {
        throw RepositoryException.wrapOrThrowCancellation(e)
    }
    return username
}


internal fun <T : Any> List<T>?.toPage(pageNumber: Int): PagingSource.LoadResult.Page<Int, T> {
    val items = this
    return PagingSource.LoadResult.Page(
        data = items ?: emptyList(),
        prevKey = if (pageNumber > 0) pageNumber - 1 else null,
        nextKey = if (!items.isNullOrEmpty()) pageNumber + 1 else null,
    )
}
