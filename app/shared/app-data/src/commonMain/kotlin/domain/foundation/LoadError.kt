/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.foundation

import androidx.paging.CombinedLoadStates
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.data.repository.RepositoryRequestError
import me.him188.ani.app.data.repository.RepositoryServiceUnavailableException
import me.him188.ani.app.tools.paging.exceptions
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException

/**
 * 加载时遇到的问题.
 *
 * @see FlowLoadErrorObserver
 */ // UI: renderLoadErrorMessage
sealed class LoadError {
    data object NoResults : LoadError()
    data object RequiresLogin : LoadError()
    data object NetworkError : LoadError()
    data object ServiceUnavailable : LoadError()
    data object RateLimited : LoadError()
    data class RequestError(val localized: String, val throwable: Throwable?) : LoadError()
    data class UnknownError(val throwable: Throwable?) : LoadError()

    companion object {
        fun fromCombinedLoadStates(states: CombinedLoadStates): LoadError? {
            if (!states.hasError) {
                return null
            }
            val exceptions = states.exceptions()
            for (e in exceptions) {
                when (e) {
                    is RepositoryAuthorizationException -> return RequiresLogin
                    is RepositoryNetworkException -> return NetworkError
                    is RepositoryServiceUnavailableException -> return ServiceUnavailable
                    is RepositoryRateLimitedException -> return RateLimited
                    is RepositoryRequestError -> return RequestError(e.localizedMessage, e.cause)
                }
            }
            return UnknownError(exceptions.firstOrNull())
        }

        fun fromException(e: Throwable): LoadError {
            return when (e) {
                is RepositoryAuthorizationException -> RequiresLogin
                is RepositoryNetworkException -> NetworkError
                is RepositoryServiceUnavailableException -> ServiceUnavailable
                is RepositoryRateLimitedException -> RateLimited
                is RepositoryRequestError -> RequestError(e.localizedMessage, e.cause)
                else -> UnknownError(e)
            }
        }

        inline fun runAndWrapOrThrowCancellation(block: () -> Unit): LoadError? {
            @Suppress("WRONG_INVOCATION_KIND") // false positive
            contract {
                callsInPlace(block, InvocationKind.EXACTLY_ONCE)
            }

            return try {
                block()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                fromException(e)
            }
        }
    }
}
