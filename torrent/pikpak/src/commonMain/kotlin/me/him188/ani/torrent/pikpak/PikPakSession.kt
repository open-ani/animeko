/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import me.him188.ani.app.torrent.api.TorrentHandleState
import me.him188.ani.app.torrent.api.TorrentSession
import me.him188.ani.app.torrent.api.files.TorrentFileEntry
import me.him188.ani.app.torrent.api.peer.PeerInfo
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal class PikPakSession(
    val sourceKey: String,
    private val torrentName: String,
    val saveDirectory: SystemPath,
    private val entries: List<PikPakFileEntry>,
    override val listingComplete: Boolean,
    private val onClosed: suspend (PikPakSession) -> Unit,
    parentCoroutineContext: CoroutineContext,
) : TorrentSession, PartialListing {
    private val logger = logger<PikPakSession>()
    private val scope = CoroutineScope(parentCoroutineContext + SupervisorJob(parentCoroutineContext[Job]))

    @Volatile
    private var closed = false

    private val closedSignal = CompletableDeferred<Unit>()

    // Closing outlives the flag: the instance stays in the downloader's map until onClosed runs,
    // and its entries are already going away, so handing it to a new caller yields a dead session.
    val isClosing: Boolean get() = closed

    suspend fun awaitClosed() {
        closedSignal.await()
    }

    val totalSize: Long get() = entries.sumOf { it.length }

    override val sessionStats: Flow<TorrentSession.Stats?> = flow {
        emit(null)
        var lastDelivered = entries.sumOf { it.deliveredBytes }
        var lastMark = TimeSource.Monotonic.markNow()
        while (true) {
            delay(1.seconds)
            val delivered = entries.sumOf { it.deliveredBytes }
            val elapsed = lastMark.elapsedNow()
            val speed = if (elapsed.inWholeMilliseconds <= 0) {
                0L
            } else {
                (delivered - lastDelivered) * 1000 / elapsed.inWholeMilliseconds
            }
            lastDelivered = delivered
            lastMark = TimeSource.Monotonic.markNow()

            val requested = entries.filter { it.hasOpenHandles }.ifEmpty { entries }

            val requestedSize = requested.sumOf { it.length }
            val downloaded = requested.sumOf { it.downloadedBytes }
            emit(
                TorrentSession.Stats(
                    totalSizeRequested = requestedSize,
                    downloadedBytes = downloaded,
                    downloadSpeed = speed.coerceAtLeast(0),
                    uploadedBytes = 0,
                    uploadSpeed = 0,
                    downloadProgress = if (requestedSize == 0L) 0f else {
                        (downloaded.toFloat() / requestedSize).coerceIn(0f, 1f)
                    },
                ),
            )
        }
    }

    val deliveredBytes: Long get() = entries.sumOf { it.deliveredBytes }

    val downloadedBytes: Long get() = entries.sumOf { it.downloadedBytes }

    override suspend fun getName(): String = torrentName

    override suspend fun getFiles(): List<TorrentFileEntry> = entries

    override fun getPeers(): List<PeerInfo> = emptyList()

    // The states this reports are a libtorrent handle's: checking files, allocating, seeding. A
    // cloud file passes through none of them, and the interface reads null as "engine does not
    // report one".
    override fun getState(): TorrentHandleState? = null

    override suspend fun close() {
        // A second caller waits for the first to finish, or it would return believing the save
        // directory is free.
        if (closed) {
            closedSignal.await()
            return
        }
        closed = true
        try {
            // The caller's coroutine may already be cancelled — playback cancelled while the cloud
            // was still getting ready is the ordinary case — and entry.close() suspends on
            // cancelAndJoin, which throws there. Letting that through would skip onClosed and leave
            // this instance in the downloader's map for good, where every later request for the
            // same torrent finds it closing, waits on an already-completed signal and retries.
            withContext(NonCancellable) {
                logger.info { "[pikpak] closing session $sourceKey" }
                entries.forEach { entry ->
                    try {
                        entry.close()
                    } catch (e: Throwable) {
                        logger.warn(e) { "[pikpak] $sourceKey: ${entry.pathInTorrent} did not close cleanly" }
                    }
                }
                scope.cancel()
                onClosed(this@PikPakSession)
            }
        } finally {
            // A failed teardown must still release the waiters.
            closedSignal.complete(Unit)
        }
    }

    override suspend fun closeIfNotInUse() {
        if (entries.none { it.hasOpenHandles }) {
            close()
        }
    }
}

// A partial import cannot use the "only video in this torrent" fallback for episode selection.
interface PartialListing {
    val listingComplete: Boolean
}
