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
import android.os.RemoteException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.app.domain.torrent.IDisposableHandle
import me.him188.ani.app.domain.torrent.parcel.RemoteContinuationException
import me.him188.ani.utils.coroutines.update
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wrapper for remote call
 */
interface RemoteObject<I : IInterface> {
    fun <R : Any?> call(block: I.() -> R): R
}

/**
 * Impl for remote call safely with retry mechanism.
 */
class RetryRemoteObject<I : IInterface>(
    private val scope: CoroutineScope,
    private val getRemote: suspend () -> I
) : RemoteObject<I> {
    private val logger = logger<RetryRemoteObject<*>>()

    private val remote: MutableStateFlow<I?> = MutableStateFlow(null)
    private val lock = Mutex()

    private fun setRemote(): Deferred<I> = scope.async {
        remote.value?.let { return@async it }

        lock.withLock {
            remote.value?.let { return@async it }

            val newRemote = getRemote()
            remote.update { newRemote }
            newRemote
        }
    }

    override fun <R : Any?> call(block: I.() -> R): R {
        var retryCount = 0
        
        while (true) {
            // remote 为 null 时所有的 call 都要阻塞, 直到第一个 setRemote
            // 其他在等待的 call 返回第一个 setRemote 的值
            val currentRemote = remote.value ?: runBlocking {
                setRemote().await()
            }

            try {
                return block(currentRemote)
            } catch (doe: DeadObjectException) {
                if (retryCount > 2) throw doe

                retryCount += 1
                logger.warn {
                    "Remote interface $currentRemote is dead, attempt to fetch new remote. retryCount = $retryCount"
                }

                if (!remote.compareAndSet(currentRemote, null)) {
                    logger.warn(IllegalStateException("Failed to invalidate current remote interface because it is changed. Before: $currentRemote, After: ${remote.value}."))
                    remote.value = null
                }
            }
        }
    }
}

/**
 * Wrapper for call which takes a continuation-like argument and returns [IDisposableHandle],
 * which means this is a asynchronous RPC call.
 *
 * [IDisposableHandle] takes responsibility to pass cancellation to server.
 */
suspend inline fun <I : IInterface, T : Any> RemoteObject<I>.callSuspendCancellable(
    crossinline transact: I.(RemoteContinuation<T>) -> IDisposableHandle?,
): T = suspendCancellableCoroutine { cont ->
    val disposable = call {
        transact(
            object : RemoteContinuation<T> {
                override fun resume(value: T?) {
                    if (value == null) {
                        cont.resumeWithException(RemoteException("Remote resume a null value."))
                    } else {
                        cont.resume(value)
                    }
                }

                override fun resumeWithException(e: RemoteContinuationException?) {
                    cont.resumeWithException(
                        e?.smartCast() ?: RemoteException("Remote resume a null exception."),
                    )
                }
            },
        )
    }

    if (disposable != null) {
        cont.invokeOnCancellation {
            try {
                disposable.dispose()
            } catch (_: DeadObjectException) {
            }
        }
    } else {
        cont.resumeWithException(RemoteException("Remote disposable is null."))
    }
}

interface RemoteContinuation<T> {
    fun resume(value: T?)
    fun resumeWithException(e: RemoteContinuationException?)
}