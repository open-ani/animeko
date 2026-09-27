/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmInline

@Serializable
sealed interface ApiFailure {
    @Serializable data object Unauthorized : ApiFailure
    @Serializable data object NetworkError : ApiFailure // IOException
    @Serializable data object ServiceUnavailable : ApiFailure // 500..599
}

/**
 * 用于需要授权的 HTTP 请求, 将已知错误解析为 [ApiFailure].
 */
@JvmInline
value class ApiResponse<out T> private constructor(
    private val value: Any?,
) {
    val isSuccess: Boolean get() = !isFailure

    val isFailure: Boolean get() = value is ApiFailure

    fun getOrNull(): T? =
        @Suppress("UNCHECKED_CAST")
        if (isSuccess) value as T else null

    fun failureOrNull(): ApiFailure? = value as? ApiFailure

    fun getOrThrow(): T {
        val value = value
        @Suppress("UNCHECKED_CAST")
        return if (isSuccess) value as T else {
            check(value is ApiFailure)
            throw IllegalStateException("Request failed: $value")
        }
    }

    companion object {
        fun <T> success(value: T): ApiResponse<T> {
            require(value !is ApiFailure) { "value must not be a RequestFailure" }
            return ApiResponse(value)
        }

        fun <T> failure(failure: ApiFailure): ApiResponse<T> {
            return ApiResponse(failure)
        }
    }
}

fun <T> ApiResponse.Companion.unauthorized(): ApiResponse<T> = failure(ApiFailure.Unauthorized)
fun <T> ApiResponse.Companion.networkError(): ApiResponse<T> = failure(ApiFailure.NetworkError)
fun <T> ApiResponse.Companion.serviceUnavailable(): ApiResponse<T> = failure(ApiFailure.ServiceUnavailable)

/**
 * 执行一个请求 [block], 并把它的结果封装为 [ApiResponse].
 *
 * 执行请求时抛出的已知类型异常将会被转换为 [ApiResponse.failure]. [block] 只能抛出已知异常类型或 [CancellationException].
 *
 * 已知类型包含:
 * - [HttpStatusCode.Unauthorized] or [HttpStatusCode.Forbidden] -> [ApiFailure.Unauthorized]
 * - [ServerResponseException] -> [ApiFailure.ServiceUnavailable]
 * - [IOException] -> [ApiFailure.NetworkError]
 *
 * 为了支持 cancellation, [CancellationException] 会原封不动地抛出.
 * 其他异常将会被是作为 bug, 会被封装为 [IllegalStateException] 后抛出. [Error] 会被原封不动地抛出.
 */
inline fun <T> runApiRequest(block: () -> T): ApiResponse<T> {
    try {
        return ApiResponse.success(block())
    } catch (e: ClientRequestException) {
        if (e.response.status == HttpStatusCode.Unauthorized || e.response.status == HttpStatusCode.Forbidden) {
            return ApiResponse.failure(ApiFailure.Unauthorized)
        }
        throw IllegalStateException("runApiRequest failed, see cause", e)
    } catch (e: ServerResponseException) {
        return ApiResponse.failure(ApiFailure.ServiceUnavailable)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        return ApiResponse.failure(ApiFailure.NetworkError)
    } catch (e: Exception) {
        throw IllegalStateException("runApiRequest failed, see cause", e)
    }
}

/**
 * @see me.him188.ani.app.data.models.runApiRequest
 */
inline fun <R, T> R.runApiRequest(block: R.() -> T): ApiResponse<T> =
    me.him188.ani.app.data.models.runApiRequest { block() }

inline fun <T, R> ApiResponse<T>.map(transform: (T) -> R): ApiResponse<R> {
    contract { callsInPlace(transform, InvocationKind.AT_MOST_ONCE) }
    return if (isSuccess) {
        @Suppress("UNCHECKED_CAST")
        ApiResponse.success(transform(getOrNull() as T))
    } else {
        @Suppress("UNCHECKED_CAST")
        this as ApiResponse<R>
    }
}

inline fun <T : R, R> ApiResponse<T>.valueOrElse(
    block: (ApiFailure) -> R,
): R {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    return if (isSuccess) {
        @Suppress("UNCHECKED_CAST")
        getOrNull() as T
    } else {
        block(failureOrNull()!!)
    }
}

inline fun <T, R> ApiResponse<T>.fold(
    onSuccess: (value: T) -> R,
    onKnownFailure: (ApiFailure) -> R,
): R {
    contract {
        callsInPlace(onSuccess, InvocationKind.AT_MOST_ONCE)
        callsInPlace(onKnownFailure, InvocationKind.AT_MOST_ONCE)
    }

    @Suppress("UNCHECKED_CAST")
    return when {
        isSuccess -> onSuccess(getOrNull() as T) // T can be null
        else -> onKnownFailure(failureOrNull()!!)
    }
}

inline fun <T, R> ApiResponse<T>.flatMap(
    onSuccess: (value: T) -> ApiResponse<R>,
): ApiResponse<R> {
    contract {
        callsInPlace(onSuccess, InvocationKind.AT_MOST_ONCE)
    }

    @Suppress("UNCHECKED_CAST")
    return when {
        isSuccess -> onSuccess(getOrNull() as T) // T can be null
        else -> this as ApiResponse<R> // does not contain a T so it's safe to cast
    }
}
