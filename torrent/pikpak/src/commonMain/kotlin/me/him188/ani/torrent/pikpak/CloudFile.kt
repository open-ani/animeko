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
import io.github.nihildigit.pikpak.RangeAttempt
import io.github.nihildigit.pikpak.ResolvedFile
import io.github.nihildigit.pikpak.fileHandle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

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
        // Checked before the first lease: a file larger than the free storage cannot be leased at
        // all, and failing here is what lets the resolver still fall back to BT.
        val account = accountProvider()
        account.ensureLoggedIn()
        account.requireRoomFor(name, size)
        try {
            open().handle.prewarm()
        } catch (e: PikPakException) {
            // The lease folder was deleted on another device: the lease names a parent that is
            // gone. Resolving it again recreates it.
            if (!isFolderGone(e)) throw e
            logger.info { "[pikpak] temp folder no longer exists; creating it again" }
            account.driveIndex.invalidateTempFolder()
            release()
            open().handle.prewarm()
        }
        account.onLeased()
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
            account.ensureLoggedIn()
            val parentId = account.driveIndex.tempFolderId()
            val detail = account.leasedDetail(ResolvedFile(path = name, size = size, gcid = gcid), parentId)
            val handle = account.client.fileHandle(
                detail = detail,
                parentId = parentId,
                leased = true,
                onRangeAttempt = ::logAttempt,
            )
            val cache = handle.openCache(blockStore = storeProvider(), coroutineContext = cacheContext)
            Opened(account, handle, cache).also { opened = it }
        }
    }

    override suspend fun release() = mutex.withLock { retireLocked() }

    // Only the attempts worth reading: failed ones, and slow ones. Players cancel constantly and
    // fast attempts arrive by the dozen per second; the host is what tells a slow host from a slow
    // line from a throttled link.
    private fun logAttempt(attempt: RangeAttempt) {
        if (attempt.outcome == RangeAttempt.Outcome.Cancelled) return
        val slow = (attempt.timeToFirstByte ?: attempt.duration) > SLOW_FIRST_BYTE
        if (attempt.outcome == RangeAttempt.Outcome.Complete && !slow) return
        logger.info {
            "[pikpak] $name ${attempt.outcome} at ${attempt.start} on ${attempt.host}: " +
                "${attempt.delivered} bytes, first byte after ${attempt.timeToFirstByte}, took ${attempt.duration}"
        }
    }

    // Streams still open on the old cache find it closed on their next read and reopen through
    // cache(), on the new account; see StreamSeekableInput. close() makes the report the handle may
    // still owe, so an object a failed rebuild left behind is deleted as well.
    private fun retireLocked() {
        val previous = opened ?: return
        opened = null
        retiredDelivered += previous.cache.deliveredBytes
        previous.cache.close()
        previous.handle.close()
    }

    private companion object {
        val SLOW_FIRST_BYTE = 1.seconds
    }
}

class PikPakNotEnoughSpaceException(
    val fileName: String,
    val neededBytes: Long,
    val freeBytes: Long,
) : Exception("PikPak has $freeBytes bytes free and $fileName needs $neededBytes to be played through it")
