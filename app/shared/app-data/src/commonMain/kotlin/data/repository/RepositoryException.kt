/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository

import androidx.paging.PagingSource
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.io.IOException
import me.him188.ani.app.data.repository.RepositoryException.Companion.wrapOrThrowCancellation
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmName

/**
 * 数据层抛出的异常.
 */
sealed class RepositoryException : Exception {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)

    companion object {
        fun wrapOrThrowCancellation(cause: Throwable): RepositoryException = when (cause) {
            is CancellationException -> throw cause
            is RepositoryException -> cause
            is ClientRequestException -> {
                when (cause.response.status) {
                    HttpStatusCode.Unauthorized -> RepositoryAuthorizationException(cause.response.status.description)
                    HttpStatusCode.Forbidden -> RepositoryAuthorizationException(cause.response.status.description)
                    HttpStatusCode.TooManyRequests -> RepositoryRateLimitedException(cause.response.status.description)
                    else -> {
                        RepositoryUnknownException(cause)
                    }
                }
            }

            is IOException -> RepositoryNetworkException(null, cause)
            is ServerResponseException -> RepositoryServiceUnavailableException(cause.response.status.description)

            else -> {
                RepositoryUnknownException(cause)
            }
        }

    }
}

inline fun <K : Any, V : Any> runWrappingExceptionAsLoadResult(block: () -> PagingSource.LoadResult<K, V>): PagingSource.LoadResult<K, V> {
    return try {
        block()
    } catch (e: Throwable) {
        PagingSource.LoadResult.Error(wrapOrThrowCancellation(e))
    }
}

//fun <K : Any, T : Any> PagingSource.LoadResult.Companion.fromResult(result: Result<PagingSource.LoadResult<K, T>>): PagingSource.LoadResult<*, T> {
//    return result.fold(
//        onSuccess = { return it },
//        onFailure = { PagingSource.LoadResult.Error<K, T>(it) },
//    )
//}


/**
 * 一个请求需要用户登录, 而用户未登录.
 */
class RepositoryAuthorizationException(message: String? = null, cause: Throwable? = null) :
    RepositoryException(message, cause)

/**
 * 网络错误
 */
class RepositoryNetworkException(message: String? = null, cause: Throwable? = null) :
    RepositoryException(message, cause)

/**
 * 服务不可用
 */
class RepositoryServiceUnavailableException(message: String? = null, cause: Throwable? = null) :
    RepositoryException(message, cause)

/**
 * 服务不可用
 */
class RepositoryRateLimitedException(message: String? = null, cause: Throwable? = null) :
    RepositoryException(message, cause)

/**
 * 每个请求的专属错误, 例如邮箱格式不正确.
 */
class RepositoryRequestError(
    @get:JvmName("getLocalizedMessage0") val localizedMessage: String,
    message: String? = null,
    cause: Throwable? = null,
) : RepositoryException(message, cause)

class RepositoryUnknownException(throwable: Throwable) : RepositoryException(null, cause = throwable)

val PagingSource.LoadResult.Error<*, *>.repositoryException: RepositoryException?
    get() = throwable as? RepositoryException


fun RepositoryException.shouldRetry() = when (this) {
    is RepositoryAuthorizationException -> false
    is RepositoryNetworkException -> true
    is RepositoryRateLimitedException -> false
    is RepositoryServiceUnavailableException -> false
    is RepositoryUnknownException -> false
    is RepositoryRequestError -> false
}

fun RepositoryException.Companion.shouldRetry(throwable: Throwable): Boolean {
    return when (throwable) {
        is RepositoryException -> throwable.shouldRetry()
        is CancellationException -> false
        else -> {
            RepositoryException.wrapOrThrowCancellation(throwable).shouldRetry()
        }
    }
}
