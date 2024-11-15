/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.client

import android.os.DeadObjectException
import android.os.IInterface
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.coroutines.suspendCancellableCoroutine
import me.him188.ani.app.domain.torrent.IDisposableHandle
import me.him188.ani.app.domain.torrent.parcel.RemoteContinuationException
import me.him188.ani.utils.coroutines.CancellationException
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wrapper for remote call
 */
interface RemoteCall<I : IInterface> {
    fun <R : Any?> call(block: I.() -> R): R

    fun <T : IInterface, R> T.callOnceOrNull(block: T.() -> R): R?
}

/**
 * Impl for remote call safely with retry mechanism.
 */
class RetryRemoteCall<I : IInterface>(
    private val getRemote: () -> I
) : RemoteCall<I> {
    private val logger = logger(this::class)

    private var remote: I? = null
    private val lock = SynchronizedObject()

    private fun setRemote(): I = synchronized(lock) {
        val currentRemote = remote
        if (currentRemote != null) return@synchronized currentRemote

        val newRemote = getRemote()
        remote = newRemote

        newRemote
    }

    override fun <R : Any?> call(block: I.() -> R): R {
        var retryCount = 0

        while (true) {
            val currentRemote = remote.let { it ?: setRemote() }

            try {
                return block(currentRemote)
            } catch (doe: DeadObjectException) {
                if (retryCount > 2) throw doe

                retryCount += 1
                logger.warn(Exception("Show stacktrace")) {
                    "Remote interface $currentRemote is dead, attempt to fetch new remote. retryCount = $retryCount"
                }
                remote = null
            }
        }
    }

    override fun <T : IInterface, R> T.callOnceOrNull(block: T.() -> R): R? {
        return try {
            block(this)
        } catch (doe: DeadObjectException) {
            null
        }
    }
}

/**
 * Wrapper for call which takes a continuation-like argument and returns [IDisposableHandle],
 * which means this is a asynchronous RPC call.
 *
 * [IDisposableHandle] takes responsibility to pass cancellation to server.
 */
suspend inline fun <I : IInterface, T, P> RemoteCall<I>.callSuspendCancellable(
    crossinline transact: I.(
        resolve: (P?) -> Unit,
        reject: (RemoteContinuationException?) -> Unit
    ) -> IDisposableHandle?,
    crossinline convert: (P) -> T,
): T = suspendCancellableCoroutine { cont ->
    val disposable = call {
        transact(
            { value ->
                if (value == null) {
                    cont.resumeWithException(CancellationException("Remote resume a null value."))
                } else {
                    cont.resume(convert(value))
                }
            },
            { exception ->
                cont.resumeWithException(
                    exception?.smartCast() ?: Exception("Remote resume a null exception."),
                )
            },
        )
    }

    if (disposable != null) {
        cont.invokeOnCancellation { disposable.callOnceOrNull { dispose() } }
    } else {
        cont.resumeWithException(CancellationException("Remote disposable is null."))
    }
}

/**
 * Wrapper for call which takes a continuation-like argument and returns [IDisposableHandle],
 * which means this is a asynchronous RPC call.
 *
 * Returns [CompletableFuture] to get the result.
 *
 * Cancellation of [scope] will also cancel the future.
 */
inline fun <I : IInterface, T, P> RemoteCall<I>.callSuspendCancellableAsFuture(
    crossinline transact: I.(
        resolve: (P?) -> Unit,
        reject: (RemoteContinuationException?) -> Unit
    ) -> IDisposableHandle?,
    crossinline convert: (P) -> T,
): CompletableFuture<T> {
    val completableFuture = CompletableFuture<T>()

    val disposable = call {
        transact(
            { value ->
                if (completableFuture.isDone) return@transact
                if (value == null) {
                    completableFuture.completeExceptionally(CancellationException("Remote resume a null value."))
                } else {
                    completableFuture.complete(convert(value))
                }
            },
            { exception ->
                if (completableFuture.isDone) return@transact
                completableFuture.completeExceptionally(
                    exception?.smartCast() ?: Exception("Remote resume a null exception."),
                )
            },
        )
    }

    if (disposable == null) {
        completableFuture.completeExceptionally(CancellationException("Remote disposable is null."))
    } else {
        completableFuture.handle { _, _ ->
            // We don't care about the result. Just dispose service.
            disposable.callOnceOrNull { dispose() }
        }
    }

    return completableFuture
}