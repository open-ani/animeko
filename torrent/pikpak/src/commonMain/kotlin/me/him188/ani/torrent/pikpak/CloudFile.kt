/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import io.github.nihildigit.pikpak.PikPakFileHandle
import io.github.nihildigit.pikpak.RangeSource
import io.github.nihildigit.pikpak.ResolvedFile
import io.github.nihildigit.pikpak.instantCreate
import io.ktor.utils.io.ByteReadChannel
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.concurrent.Volatile

// GCID identifies content across accounts. Cloud objects exist only to mint signed links,
// which remain readable after those objects are deleted.
internal class CloudFile(
    private val gcid: String,
    private val size: Long,
    private val name: String,
    private val accountProvider: suspend () -> PikPakAccount,
    private val connectionBudget: Int,
    private val scope: CoroutineScope,
) : RangeSource {
    private val logger = logger<CloudFile>()

    private val mutex = Mutex()

    @Volatile
    private var handle: Pair<PikPakAccount, PikPakFileHandle>? = null

    /**
     * Mints the link now, rather than on the first read.
     *
     * Reads open the handle lazily, which would put a sign-in failure, a dead refresh token or an
     * exhausted quota after the resolver has committed to this engine, where BT fallback is unavailable.
     * Callers use this to surface those failures while fallback is still possible.
     *
     * This is also where the file's length is checked, though nothing here compares it: minting
     * goes through instantCreate, which submits size and gcid together and fails unless PikPak
     * holds content of exactly that description. A length assertion in the app would be comparing
     * the declared length against itself -- the SDK answers streamSize from the size handed to it
     * once there is no transcode to probe -- so there is deliberately none.
     */
    suspend fun prepare() {
        openHandle()
    }

    override suspend fun <T> read(
        start: Long,
        length: Long,
        priority: Int,
        block: suspend (ByteReadChannel) -> T,
    ): T = openHandle().read(start, length, priority, block)

    override suspend fun readBytes(start: Long, length: Long, priority: Int): ByteArray =
        openHandle().readBytes(start, length, priority)

    private suspend fun openHandle(): PikPakFileHandle {
        // handle is published whole, so a reader that finds one matching its account can use it
        // without queueing. Everything below suspends on the network, and a rebuild would otherwise
        // hold every read of this file behind it: eight fetcher workers, the playback stream and
        // the tail prefetch.
        val fastPath = accountProvider()
        handle?.takeIf { it.first === fastPath }?.let { return it.second }
        return openHandleLocked()
    }

    // One handle per file at a time: two callers rebuilding at once would each mint a cloud object,
    // and only one of them would ever be reported to discard.
    private suspend fun openHandleLocked(): PikPakFileHandle = mutex.withLock {
        val account = accountProvider()
        handle?.takeIf { it.first === account }?.let { return@withLock it.second }
        handle?.let { (_, previousHandle) -> retire(previousHandle) }
        handle = null

        if (gcid.isEmpty()) {
            throw PikPakNotIndexedException(name, "$name has no gcid; it exists only on disk")
        }
        account.login.await()
        val client = account.client
        val (parentId, fileId) = account.driveIndex.withTempFolder { id ->
            id to client.instantCreate(
                ResolvedFile(path = name, size = size, gcid = gcid),
                parentId = id,
                name = name,
            )
        }

        // Until a link has been minted from it, this object's id lives only in this frame: the
        // handle is not published yet and onObjectMinted has not fired. Throwing or being cancelled
        // before that point would leave an object nothing can name again. The startup sweep only
        // removes objects older than its safety cutoff, so clean this one up immediately.
        val minted = atomic(false)
        try {
            PikPakFileHandle(
                client = client,
                gcid = gcid,
                size = size,
                name = name,
                initialFileId = fileId,
                parentId = parentId,
                connectionBudget = connectionBudget,
                onObjectMinted = {
                    minted.value = true
                    discard(account, it)
                },
            ).also {
                it.prewarm()
                it.streamSize()
                handle = account to it
            }
        } catch (e: Throwable) {
            if (!minted.value) discard(account, fileId)
            throw e
        }
    }

    // In-flight reads may still hold this handle; let them finish on their original client. Closing
    // only refuses reads that have not started, and every read reaches a handle through openHandle,
    // so the ones still running keep the reader they hold and nothing new arrives.
    //
    // It has to go through closeAndReport: a handle whose rebuild succeeded and whose detail lookup
    // then failed still owes a report, and letting the reference go is the last moment anything
    // could collect the object behind it.
    private fun retire(previous: PikPakFileHandle) {
        scope.launch {
            withContext(NonCancellable) {
                runCatching { previous.closeAndReport() }
                    .onFailure { logger.warn(it) { "[pikpak] could not close the handle $name left behind" } }
            }
        }
    }

    private fun discard(account: PikPakAccount, fileId: String) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    account.driveIndex.delete(listOf(fileId))
                    logger.info { "[pikpak] dropped the file object for $name; its link stands on its own" }
                } catch (e: Throwable) {
                    logger.warn(e) { "[pikpak] could not drop $fileId; the next startup sweep takes it" }
                }
            }
        }
    }

}
