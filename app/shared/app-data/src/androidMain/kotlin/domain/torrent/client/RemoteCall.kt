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
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

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