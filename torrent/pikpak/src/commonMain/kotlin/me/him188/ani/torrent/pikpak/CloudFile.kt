/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import io.github.nihildigit.pikpak.BlockStore
import io.github.nihildigit.pikpak.PikPakException
import io.github.nihildigit.pikpak.PikPakFileCache
import io.github.nihildigit.pikpak.PikPakFileHandle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext

/**
 * The cloud side of one file: where its bytes come from, and the SDK's cache that every stream
 * and download of the file shares.
 */
internal interface CloudSource {
    /**
     * Mints the link now, rather than on the first read.
     *
     * Reads open the handle lazily, which would put a sign-in failure, a dead refresh token or an
     * exhausted quota after the resolver has committed to this engine, where BT fallback is
     * unavailable. Callers use this to surface those failures while fallback is still possible.
     */
    suspend fun prepare()

    /** The file's cache on the current account. The same instance until the account changes. */
    suspend fun cache(): PikPakFileCache

    /** Bytes the CDN delivered for this file, monotonic across account changes. */
    val deliveredBytes: Long

    /**
     * Closes the current cache and handle. The next [cache] opens new ones, over the store the
     * provider hands out then: how a deleted file's replacement store gets a cache of its own.
     */
    suspend fun release()
}

// GCID identifies content across accounts. Cloud objects exist only to mint signed links, which
// remain readable after those objects are deleted, so the handle is leased: the SDK deletes every
// object it creates as soon as its link is in hand, the rebuilds after an expiry included.
internal class CloudFile(
    private val gcid: String,
    private val size: Long,
    private val name: String,
    private val accountProvider: suspend () -> PikPakAccount,
    private val connectionBudget: Int,
    private val storeProvider: () -> BlockStore,
    private val cacheContext: CoroutineContext,
) : CloudSource {
    private val logger = logger<CloudFile>()

    private class Opened(val account: PikPakAccount, val handle: PikPakFileHandle, val cache: PikPakFileCache)

    private val mutex = Mutex()

    @Volatile
    private var opened: Opened? = null

    // What caches replaced by an account change had delivered, so the total never runs backwards.
    @Volatile
    private var retiredDelivered = 0L

    override val deliveredBytes: Long get() = retiredDelivered + (opened?.cache?.deliveredBytes ?: 0L)

    override suspend fun prepare() {
        val current = open()
        try {
            current.handle.prewarm()
        } catch (e: PikPakException) {
            // The lease folder was deleted on another device: the rebuild names a parent that is
            // gone. Resolving it again recreates it.
            if (!isFolderGone(e)) throw e
            logger.info { "[pikpak] temp folder no longer exists; creating it again" }
            current.account.driveIndex.invalidateTempFolder()
            release()
            open().handle.prewarm()
        }
    }

    override suspend fun cache(): PikPakFileCache = open().cache

    private suspend fun open(): Opened {
        // Published whole, so a reader that finds one for its account uses it without queueing
        // behind a rebuild that is on the network.
        val account = accountProvider()
        opened?.takeIf { it.account === account && !it.cache.isClosed }?.let { return it }
        return mutex.withLock {
            opened?.takeIf { it.account === account && !it.cache.isClosed }?.let { return@withLock it }
            retireLocked()
            if (gcid.isEmpty()) {
                throw PikPakNotIndexedException(name, "$name has no gcid; it exists only on disk")
            }
            account.login.await()
            val handle = PikPakFileHandle(
                client = account.client,
                gcid = gcid,
                size = size,
                name = name,
                parentId = account.driveIndex.tempFolderId(),
                connectionBudget = connectionBudget,
                leased = true,
            )
            val cache = handle.openCache(blockStore = storeProvider(), coroutineContext = cacheContext)
            Opened(account, handle, cache).also { opened = it }
        }
    }

    override suspend fun release() = mutex.withLock { retireLocked() }

    // Streams still open on the old cache fail their next read, and the player reopens through
    // cache(), on the new account. close() makes the report the handle may still owe, so an object
    // a failed rebuild left behind is deleted as well.
    private fun retireLocked() {
        val previous = opened ?: return
        opened = null
        retiredDelivered += previous.cache.deliveredBytes
        previous.cache.close()
        previous.handle.close()
    }
}
