/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import io.github.nihildigit.pikpak.FileDetail
import io.github.nihildigit.pikpak.LeaseBudget
import io.github.nihildigit.pikpak.MagnetResource
import io.github.nihildigit.pikpak.PikPakClient
import io.github.nihildigit.pikpak.ResolvedFile
import io.github.nihildigit.pikpak.SessionStore
import io.github.nihildigit.pikpak.leaseDetail
import io.github.nihildigit.pikpak.getQuota
import io.github.nihildigit.pikpak.resolveMagnet
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.app.torrent.api.TorrentDownloader
import me.him188.ani.app.torrent.api.TorrentLibInfo
import me.him188.ani.app.torrent.api.TorrentSession
import me.him188.ani.app.torrent.api.files.EncodedTorrentInfo
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.isDirectory
import me.him188.ani.utils.io.length
import me.him188.ani.utils.io.name
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.useDirectoryEntries
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource

// Resolve magnets to content hashes, then serve them through the torrent playback/cache APIs.
// The injected API client must omit ContentNegotiation and HttpRequestRetry; the SDK owns both.
class PikPakTorrentDownloader(
    private val httpClient: HttpClient,
    private val credentials: StateFlow<PikPakCredentials?>,
    private val sessionStore: SessionStore,
    private val rootDataDirectory: SystemPath,
    private val config: PikPakEngineConfig = PikPakEngineConfig(),
    parentCoroutineContext: CoroutineContext,
    /** A file could not be leased for want of drive space; see [PikPakNotEnoughSpaceException]. */
    private val onNotEnoughSpace: (PikPakNotEnoughSpaceException) -> Unit = {},
) : TorrentDownloader {
    private val logger = logger<PikPakTorrentDownloader>()
    private val scope = CoroutineScope(parentCoroutineContext + SupervisorJob(parentCoroutineContext[Job]))

    // Closing sessions has to outlive this downloader: close() cancels scope, and the stores' last
    // bitmap write would go down with it. Deliberately parentless, so cancelling scope cannot reach it.
    private val cleanupScope = CoroutineScope(parentCoroutineContext.minusKey(Job) + SupervisorJob())

    // RangeReader detects a silent response body. This socket timeout is a wider backstop that also
    // covers connection setup and response headers without aborting a request that is still making
    // progress. config() keeps the SDK's tuned connection pool.
    private val cdnClientHolder = lazy {
        PikPakClient.tunedCdnClient().config {
            install(HttpTimeout) {
                socketTimeoutMillis = CDN_SOCKET_TIMEOUT.inWholeMilliseconds
                // A range request is free to take as long as it needs while bytes keep arriving.
                requestTimeoutMillis = Long.MAX_VALUE
            }
        }
    }

    private val cdnClient: HttpClient by cdnClientHolder

    private val clientLock = Mutex()
    private var clientEntry: Pair<PikPakCredentials, PikPakAccount>? = null

    private val sessionLock = Mutex()
    private val sessions = mutableMapOf<String, Deferred<PikPakSession>>()

    // The keys of `sessions`, readable without suspending: listSaves is not a suspend function, and
    // the cache engine's startup pruning deletes whatever save it lists.
    private val liveKeysLock = SynchronizedObject()
    private val liveKeys = HashSet<String>()

    private fun trackLive(sourceKey: String, live: Boolean) = synchronized(liveKeysLock) {
        if (live) liveKeys += sourceKey else liveKeys -= sourceKey
    }


    private val resolvedMagnetsLock = Mutex()
    private val resolvedMagnets = mutableMapOf<String, ResolvedMagnet>()

    private class ResolvedMagnet(val resource: MagnetResource, val at: TimeSource.Monotonic.ValueTimeMark)

    @Volatile
    private var closed = false

    override val vendor: TorrentLibInfo = TorrentLibInfo(
        vendor = "PikPak",
        version = SDK_VERSION,
        supportsStreaming = true,
    )

    internal val startupSweep: Job = scope.launch {
        try {
            credentials.filterNotNull().first { it.isValid }
            account().startupSweep.join()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn(e) { "[pikpak] could not initialize startup sweep" }
        }
    }

    override val totalStats: Flow<TorrentDownloader.Stats> = flow {
        var lastDelivered = 0L
        var lastMark = TimeSource.Monotonic.markNow()
        while (true) {
            val live = sessionLock.withLock { sessions.values.mapNotNull { it.takeIf { d -> d.isCompleted } } }
                .mapNotNull { runCatching { it.getCompleted() }.getOrNull() }
            val delivered = live.sumOf { it.deliveredBytes }
            val elapsed = lastMark.elapsedNow()
            val speed = if (elapsed.inWholeMilliseconds <= 0) 0L else {
                ((delivered - lastDelivered) * 1000 / elapsed.inWholeMilliseconds).coerceAtLeast(0)
            }
            lastDelivered = delivered
            lastMark = TimeSource.Monotonic.markNow()

            val totalSize = live.sumOf { it.totalSize }
            val downloaded = live.sumOf { it.downloadedBytes }
            emit(
                TorrentDownloader.Stats(
                    totalSize = totalSize,
                    downloadedBytes = downloaded,
                    downloadSpeed = speed,
                    uploadedBytes = 0,
                    uploadSpeed = 0,
                    downloadProgress = if (totalSize == 0L) 0f else {
                        (downloaded.toFloat() / totalSize).coerceIn(0f, 1f)
                    },
                ),
            )
            delay(1.seconds)
        }
    }

    override suspend fun fetchTorrent(uri: String, timeoutSeconds: Int): EncodedTorrentInfo =
        PikPakSavedFiles.encodedTorrentInfoFor(uri)

    override suspend fun startDownload(
        data: EncodedTorrentInfo,
        parentCoroutineContext: CoroutineContext,
    ): TorrentSession {
        check(!closed) { "PikPakTorrentDownloader is closed" }
        val uri = decodeUri(data)
        val sourceKey = sourceKeyFor(uri)

        // Share creation as well as the resulting session so concurrent opens cannot write the same files.
        val deferred = awaitReusableSession(uri, sourceKey, parentCoroutineContext)
        return try {
            deferred.await()
        } catch (e: Throwable) {
            // Cancelling one waiter must not discard another caller's session: not one still being
            // created, and not one that completed while this caller was being cancelled either.
            if (deferred.isCancelled || (deferred.isCompleted && completedSessionOf(deferred) == null)) {
                sessionLock.withLock {
                    if (sessions[sourceKey] === deferred) {
                        sessions.remove(sourceKey)
                        trackLive(sourceKey, false)
                    }
                }
            } else {
                // Creation runs on this downloader's scope, so it outlives the caller that gave up —
                // a resolver falling back to another engine on a timeout, most of the time. The
                // session it leaves behind holds entries and file handles that nothing will ask for.
                // closeIfNotInUse keeps it when another caller has taken a handle meanwhile.
                cleanupScope.launch {
                    val abandoned = runCatching { deferred.await() }.getOrNull()
                    runCatching { abandoned?.closeIfNotInUse() }
                        .onFailure { logger.warn(it) { "[pikpak] could not release the abandoned session $sourceKey" } }
                }
            }
            throw e
        }
    }

    override fun getSaveDirForTorrent(data: EncodedTorrentInfo): SystemPath =
        PikPakSavedFiles.saveDirectoryFor(rootDataDirectory, decodeUri(data))

    // An open session is never listed. The cache engine's startup pruning deletes every listed save
    // it has no record for, and playback started while the records are still being restored has
    // none: its sparse file would go from under an open cache.
    override fun listSaves(): List<SystemPath> {
        if (!rootDataDirectory.exists()) return emptyList()
        val live = synchronized(liveKeysLock) { liveKeys.toSet() }
        return rootDataDirectory.useDirectoryEntries { entries ->
            entries.filter { it.isDirectory() && it.name !in live }.toList()
        }
    }

    suspend fun testConnection(): Boolean {
        val client = client()
        client.login()
        client.getQuota()
        return true
    }

    // Engine selection precedes episode selection, so every video currently needs a GCID.
    suspend fun canServe(uri: String): Boolean {
        val sourceKey = sourceKeyFor(uri)
        indexedFilesOnDisk(sourceKey)?.let { files ->
            val videos = files.filter { it.length > 0 && isVideoPath(it.pathInTorrent) }
            return videos.isNotEmpty() && videos.none { it.gcid.isEmpty() }
        }

        val resolved = sharedMagnetResource(uri, sourceKey) ?: return false
        val videos = resolved.files.filter { it.size > 0 && isVideoPath(it.path) }
        if (videos.isEmpty()) return false
        return videos.none { it.gcid.isNullOrEmpty() }
    }

    // The download dialog asks canServe and then starts the download, and the answer to the first is
    // the listing the second needs. Resolving twice costs a login and a magnet lookup for nothing.
    //
    // The listing describes PikPak's content index rather than the account's drive, so it does not
    // change with the signed-in user; it can go stale only if PikPak drops the content, which
    // MAGNET_CACHE_TTL bounds.
    private suspend fun sharedMagnetResource(uri: String, sourceKey: String): MagnetResource? {
        resolvedMagnetsLock.withLock {
            resolvedMagnets[sourceKey]?.takeIf { it.at.elapsedNow() < MAGNET_CACHE_TTL }?.resource
        }?.let { return it }

        val resolved = magnetResolver(uri) ?: return null
        resolvedMagnetsLock.withLock {
            resolvedMagnets[sourceKey] = ResolvedMagnet(resolved, TimeSource.Monotonic.markNow())
        }
        return resolved
    }

    private suspend fun indexedFilesOnDisk(sourceKey: String): List<PikPakFileMeta>? =
        withContext(Dispatchers.IO_) {
            PikPakResumeData(rootDataDirectory.resolve(sourceKey)).read()
                ?.takeIf { it.indexed && it.files.isNotEmpty() }
                ?.files
        }

    suspend fun legacyFolderItems(): List<PikPakDriveItem> =
        account().driveIndex.listLegacy().filter { it.id.isNotEmpty() }.map { PikPakDriveItem(id = it.id, name = it.name) }

    suspend fun clearLegacyFolder(ids: List<String>) {
        if (ids.isEmpty()) return
        account().driveIndex.delete(ids)
        logger.info { "[pikpak] cleared ${ids.size} item(s) from the legacy folder at the user's request" }
    }

    suspend fun driveUsage(): PikPakDriveUsage {
        val quota = client().getQuota().quota
        return PikPakDriveUsage(
            accountUsedBytes = quota.usageBytes,
            accountLimitBytes = quota.limitBytes,
        )
    }

    internal suspend fun tempFolderObjectIds(): Set<String> =
        account().driveIndex.listTemp().mapTo(mutableSetOf()) { it.id }

    override fun close() {
        if (closed) return
        closed = true
        // Sessions run on the context their caller passed in, so cancelling scope does not reach
        // them, while the fetcher's file handle and the bitmap's last write need an explicit close.
        // The snapshot is taken under sessionLock, which this non-suspend function cannot hold
        // itself. Creation still in flight dies with scope, and a Deferred cancelled that way is not
        // a completed session, so taking the snapshot after the cancel changes nothing.
        cleanupScope.launch {
            withContext(NonCancellable) {
                val live = sessionLock.withLock { sessions.values.mapNotNull { completedSessionOf(it) } }
                live.forEach { session ->
                    try {
                        session.close()
                    } catch (e: Throwable) {
                        logger.warn(e) { "[pikpak] ${session.sourceKey} did not close cleanly on shutdown" }
                    }
                }
            }
        }
        scope.cancel()
        if (cdnClientHolder.isInitialized()) cdnClient.close()
    }

    private suspend fun createSession(
        uri: String,
        sourceKey: String,
        parentCoroutineContext: CoroutineContext,
    ): PikPakSession {
        val saveDirectory = rootDataDirectory.resolve(sourceKey)
        saveDirectory.createDirectories()
        val resumeData = PikPakResumeData(saveDirectory)
        // The listing has served its second reader by the time the session exists; keeping it would
        // hold a stale copy of what the drive holds.
        try {
            return buildSession(uri, sourceKey, saveDirectory, resumeData, parentCoroutineContext)
        } finally {
            resolvedMagnetsLock.withLock { resolvedMagnets.remove(sourceKey) }
        }
    }

    private suspend fun buildSession(
        uri: String,
        sourceKey: String,
        saveDirectory: SystemPath,
        resumeData: PikPakResumeData,
        parentCoroutineContext: CoroutineContext,
    ): PikPakSession {
        val restored = resumeData.read()?.takeIf { it.files.isNotEmpty() && filesConsistent(saveDirectory, it) }
        val meta = when {
            restored == null -> indexFromMagnet(uri, sourceKey).also {
                resumeData.write(it)
            }

            restored.indexed -> {
                logger.info { "[pikpak] restored $sourceKey from disk, ${restored.files.size} file(s), no API call" }
                restored
            }

            else -> completeIndex(restored, uri, sourceKey, saveDirectory, resumeData)
        }

        var session: PikPakSession? = null
        val entries = meta.files.map { fileMeta ->
            buildEntry(
                fileMeta, sourceKey, saveDirectory, parentCoroutineContext,
                onHandleCountChanged = { session?.closeIfNotInUse() },
                // An import has no GCID and nothing but the disk to play from
                kept = fileMeta.pathInTorrent in meta.kept || fileMeta.gcid.isEmpty(),
                persistKept = { kept -> persistKept(resumeData, fileMeta.pathInTorrent, kept) },
            )
        }
        return PikPakSession(
            sourceKey = sourceKey,
            torrentName = meta.name,
            saveDirectory = saveDirectory,
            entries = entries,
            listingComplete = meta.indexed,
            // Removing by key alone would let a stale session drop the entry a newer one for the
            // same save directory installed, and two live sessions would then write the same files.
            onClosed = { closing ->
                sessionLock.withLock {
                    val current = sessions[closing.sourceKey]
                    if (current != null && completedSessionOf(current) === closing) {
                        sessions.remove(closing.sourceKey)
                        trackLive(closing.sourceKey, false)
                    }
                }
            },
            parentCoroutineContext = parentCoroutineContext,
        ).also { session = it }
    }

    // getCompleted throws unless the Deferred completed with a value, so null means there is no live
    // session behind this entry: it is still being created, or it failed, or it was cancelled.
    private fun completedSessionOf(deferred: Deferred<PikPakSession>): PikPakSession? =
        if (!deferred.isCompleted) null else runCatching { deferred.getCompleted() }.getOrNull()

    /**
     * The usable session for this key, creating one when there is none.
     *
     * A Deferred that completed successfully does not turn cancelled when its session starts
     * closing, so [Deferred.isCancelled] alone would hand out an instance whose entries are gone.
     * Replacing the map entry instead is no better: the teardown is still running, and the two
     * instances would write the same save directory. Waiting it out is the only option, and by then
     * onClosed has removed it.
     */
    private suspend fun awaitReusableSession(
        uri: String,
        sourceKey: String,
        parentCoroutineContext: CoroutineContext,
    ): Deferred<PikPakSession> {
        while (true) {
            val existing = sessionLock.withLock {
                val candidate = sessions[sourceKey]?.takeIf { !it.isCancelled }
                if (candidate == null) {
                    return@withLock scope.async { createSession(uri, sourceKey, parentCoroutineContext) }
                        .also {
                            sessions[sourceKey] = it
                            trackLive(sourceKey, true)
                        }
                }
                // Not while closing, and not awaited here: onClosed takes this same lock.
                if (completedSessionOf(candidate)?.isClosing == true) null else candidate
            }
            if (existing != null) return existing

            val closing = sessionLock.withLock { sessions[sourceKey]?.let { completedSessionOf(it) } }
            closing?.takeIf { it.isClosing }?.awaitClosed()
        }
    }

    private suspend fun completeIndex(
        partial: PikPakTorrentMeta,
        uri: String,
        sourceKey: String,
        saveDirectory: SystemPath,
        resumeData: PikPakResumeData,
    ): PikPakTorrentMeta {
        val cloud = try {
            indexFromMagnet(uri, sourceKey)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logger.warn(e) { "[pikpak] $sourceKey holds imported files only and could not be indexed" }
            return partial
        }
        // Merged into the record as it is now: an import may have landed during the listing
        val result = resumeData.update { current ->
            val merged = mergeImportedInto(cloud, current ?: partial)
            if (filesConsistent(saveDirectory, merged)) merged else cloud
        }!!
        logger.info { "[pikpak] $sourceKey filled in from the magnet, ${result.files.size} file(s)" }
        return result
    }

    private suspend fun buildEntry(
        fileMeta: PikPakFileMeta,
        sourceKey: String,
        saveDirectory: SystemPath,
        parentCoroutineContext: CoroutineContext,
        onHandleCountChanged: suspend () -> Unit,
        kept: Boolean,
        persistKept: suspend (kept: Boolean) -> Unit,
    ): PikPakFileEntry {
        return PikPakFileEntry(
            index = fileMeta.index,
            length = fileMeta.length,
            saveDirectory = saveDirectory,
            relativePath = fileMeta.pathInTorrent,
            torrentId = sourceKey,
            parentCoroutineContext = parentCoroutineContext,
            meta = fileMeta,
            cloudFactory = { storeProvider ->
                CloudFile(
                    gcid = fileMeta.gcid,
                    size = fileMeta.length,
                    name = fileMeta.pathInTorrent.substringAfterLast('/'),
                    accountProvider = ::account,
                    connectionBudget = config.effectiveConcurrency,
                    storeProvider = storeProvider,
                    cacheContext = parentCoroutineContext + Dispatchers.IO_,
                )
            },
            onHandleCountChanged = onHandleCountChanged,
            kept = kept,
            persistKept = persistKept,
        )
    }

    private suspend fun persistKept(resumeData: PikPakResumeData, pathInTorrent: String, kept: Boolean) =
        withContext(Dispatchers.IO_) {
            resumeData.update { meta ->
                when {
                    meta == null || (pathInTorrent in meta.kept) == kept -> null
                    kept -> meta.copy(kept = meta.kept + pathInTorrent)
                    else -> meta.copy(kept = meta.kept - pathInTorrent)
                }
            }
        }

    internal var magnetResolver: suspend (uri: String) -> MagnetResource? = { uri ->
        val client = client()
        client.login()
        client.resolveMagnet(uri)
    }

    private suspend fun indexFromMagnet(uri: String, sourceKey: String): PikPakTorrentMeta {
        val resource = sharedMagnetResource(uri, sourceKey)
            ?: throw PikPakNotIndexedException(uri, "PikPak has no record of $sourceKey; use another source")

        val present = resource.files.filter { it.size > 0 }
        if (present.none { it.gcid != null }) {
            throw PikPakNotIndexedException(uri, "PikPak indexed $sourceKey but holds none of its files")
        }

        val files = present.sortedBy { it.path }.mapIndexed { index, file ->
            PikPakFileMeta(
                index = index,
                pathInTorrent = file.path,
                gcid = file.gcid.orEmpty(),
                length = file.size,
            )
        }
        if (resource.truncated) {
            logger.warn { "[pikpak] $sourceKey lists more than one page of root entries; treating the listing as partial" }
        }
        return PikPakTorrentMeta(
            uri = uri,
            sourceKey = sourceKey,
            name = resource.name.ifEmpty { sourceKey },
            // PikPak pages a torrent's root entries and only the first page arrives. Taken as the
            // whole torrent, the episode fallback would pick from a list missing files, and the
            // listing would never be asked for again.
            indexed = !resource.truncated,
            files = files,
        )
    }

    private suspend fun client(): PikPakClient = account().client

    private suspend fun account(): PikPakAccount = clientLock.withLock {
        val creds = credentials.value
            ?: throw PikPakNotConfiguredException("PikPak credentials are not set")
        if (!creds.isValid) throw PikPakNotConfiguredException("PikPak credentials are incomplete")
        clientEntry?.takeIf { it.first == creds }?.second?.let { return it }
        PikPakClient(
            account = creds.username,
            password = creds.password,
            sessionStore = sessionStore,
            httpClient = httpClient,
            cdnHttpClient = cdnClient,
            connectionBudget = config.effectiveConcurrency,
            // Two files' worth, so a cache download and playback each keep a full link's budget.
            // The CDN client admits as many per host (tunedCdnClient's default); an engine cap below
            // the gates would queue requests where RangeReader reads the wait as a dead host.
            accountConnectionBudget = config.accountConcurrency,
        ).let { PikPakAccount(it, scope, onNotEnoughSpace) }.also { clientEntry = creds to it }
    }

    private companion object {
        const val SDK_VERSION = "2.0.0"

        // A backstop, not the body watchdog -- RangeReader owns that. It also bounds the wait for
        // response headers and connection setup.
        val CDN_SOCKET_TIMEOUT = 20.seconds

        // Long enough to cover a canServe followed by the download it decides, short enough that a
        // torrent PikPak has since dropped is not answered for from memory.
        val MAGNET_CACHE_TTL = 1.minutes
    }
}

data class PikPakEngineConfig(
    val concurrency: Int = 8,
) {
    companion object {
        // PikPak rejects a ninth simultaneous connection to the same signed URL.
        const val MAX_CONCURRENCY: Int = 8
    }

    val effectiveConcurrency: Int get() = concurrency.coerceIn(1, MAX_CONCURRENCY)

    // Lowering the per-file count is how a slow line is accommodated, and the account-wide count
    // has to come down with it: past what the line carries, more connections only make each slower.
    val accountConcurrency: Int get() = effectiveConcurrency * 2
}

data class PikPakCredentials(
    val username: String,
    val password: String,
) {
    val isValid: Boolean get() = username.isNotEmpty()
}

data class PikPakDriveItem(
    val id: String,
    val name: String,
)

data class PikPakDriveUsage(
    val accountUsedBytes: Long,
    val accountLimitBytes: Long,
)

class PikPakNotConfiguredException(message: String) : Exception(message)

class PikPakNotIndexedException(
    val uri: String,
    message: String = "PikPak has no record of $uri",
) : Exception(message)

@Serializable
internal data class PikPakEncodedTorrent(val uri: String)

private val encodedTorrentJson = Json { ignoreUnknownKeys = true }

internal fun decodeUri(data: EncodedTorrentInfo): String =
    encodedTorrentJson.decodeFromString(PikPakEncodedTorrent.serializer(), data.data.decodeToString()).uri

internal fun encodeUri(uri: String): ByteArray =
    encodedTorrentJson.encodeToString(PikPakEncodedTorrent.serializer(), PikPakEncodedTorrent(uri))
        .encodeToByteArray()

private val VIDEO_EXTENSIONS =
    setOf("mkv", "mp4", "ts", "m2ts", "avi", "mov", "flv", "webm", "rm", "rmvb", "wmv", "mpeg", "m4v")

internal fun isVideoPath(path: String): Boolean =
    path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

// Cache records retain imported paths. Cloud access uses GCIDs independently of the local layout.
internal fun mergeImportedInto(cloud: PikPakTorrentMeta, local: PikPakTorrentMeta): PikPakTorrentMeta {
    val byPath = local.files.associateBy { it.pathInTorrent }
    val cloudPaths = cloud.files.mapTo(HashSet()) { it.pathInTorrent }
    val unmatched = local.files.filter { it.pathInTorrent !in cloudPaths }
    val cloudByName = cloud.files.groupBy { it.pathInTorrent.substringAfterLast('/') }
    val unmatchedByName = unmatched.groupBy { it.pathInTorrent.substringAfterLast('/') }
    val byName = mutableMapOf<String, PikPakFileMeta>()
    val extras = mutableListOf<PikPakFileMeta>()
    for (entry in unmatched) {
        val target = cloudByName[entry.pathInTorrent.substringAfterLast('/')]?.singleOrNull()
        if (target == null || target.pathInTorrent in byPath ||
            unmatchedByName[entry.pathInTorrent.substringAfterLast('/')]?.size != 1
        ) {
            extras += entry
        } else {
            byName[target.pathInTorrent] = entry
        }
    }

    var nextIndex = cloud.files.size
    val files = cloud.files.map { remote ->
        val onDisk = byPath[remote.pathInTorrent] ?: byName[remote.pathInTorrent] ?: return@map remote
        remote.copy(
            pathInTorrent = onDisk.pathInTorrent,
            length = onDisk.length,
        )
    } + extras.map { it.copy(index = nextIndex++) }
    return cloud.copy(files = files, kept = (cloud.kept + local.kept).distinct())
}

// Missing files are normal for streaming sessions, and data files grow as blocks land.
internal fun filesConsistent(saveDirectory: SystemPath, meta: PikPakTorrentMeta): Boolean = meta.files.all {
    val path = saveDirectory.resolve(it.pathInTorrent)
    !path.exists() || path.length() <= it.length
}

// Directory IDs and cleanup requests must use the client that created them, even after an account switch.
internal class PikPakAccount(
    val client: PikPakClient,
    scope: CoroutineScope,
    private val onNotEnoughSpace: (PikPakNotEnoughSpaceException) -> Unit = {},
) {
    val driveIndex = PikPakDriveIndex(clientProvider = { client })

    private val loginLock = Mutex()

    @Volatile
    private var loggedIn = false

    /**
     * Free storage as read right after sign-in, or null until then or when it could not be read.
     * A leased object takes its file's full size while it exists, so a file larger than this cannot
     * be leased at all; see [CloudFile.prepare].
     */
    @Volatile
    var freeBytes: Long? = null
        private set

    /**
     * Signs in once, and again after a failure. Every other entry point logs in before its first
     * request. Skipping it here sent the first request of a cold start out unauthenticated: the 401
     * recovery path reads only the in-memory session, never the store, so a stored refresh token
     * could not be used and the client fell back to a full captcha sign-in.
     *
     * Only a success is remembered: a cold start without network would otherwise leave the
     * account failed until the app restarts.
     */
    suspend fun ensureLoggedIn() {
        if (loggedIn) return
        loginLock.withLock {
            if (loggedIn) return
            client.login()
            loggedIn = true
        }
        budgetLeases()
    }

    // Reported once until a file is leased again: playback, its fallback and every retry of a cache
    // download are refused again, and one notice is enough to send the user to free up space.
    private val shortOfSpaceLock = SynchronizedObject()
    private var shortOfSpaceReported = false

    /** A lease went through, so the next shortage is news again; see [requireRoomFor]. */
    fun onLeased() = synchronized(shortOfSpaceLock) { shortOfSpaceReported = false }

    /**
     * Throws [PikPakNotEnoughSpaceException] when a lease of [size] bytes cannot fit. The figure read
     * at sign-in is compared first; a file that does not fit it is compared once more against a
     * fresh one, since the user may have made room since.
     */
    suspend fun requireRoomFor(fileName: String, size: Long) {
        val known = freeBytes ?: return
        if (size <= known) return
        val free = try {
            client.getQuota().quota.remainingBytes.coerceAtLeast(0).also { freeBytes = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger<PikPakAccount>().warn(e) { "[pikpak] could not read the free space again" }
            known
        }
        if (size <= free) return
        val failure = PikPakNotEnoughSpaceException(fileName, size, free)
        val report = synchronized(shortOfSpaceLock) {
            !shortOfSpaceReported.also { shortOfSpaceReported = true }
        }
        if (report) onNotEnoughSpace(failure)
        throw failure
    }

    // Leases whose links are still good, by gcid. A lease costs an instant create -- 15 % of the
    // file's size from the monthly upload allowance -- and its link outlives the object for a day,
    // so a second session of the same file, or a cache reopened after a delete, reuses the detail.
    private val leasesLock = Mutex()
    private val leases = HashMap<String, FileDetail>()

    suspend fun leasedDetail(file: ResolvedFile, parentId: String): FileDetail {
        val gcid = file.gcid.orEmpty().uppercase()
        leasesLock.withLock { leases[gcid] }?.takeIf { it.linkUsable() }?.let { return it }
        return client.leaseDetail(file, parentId = parentId).also { detail ->
            leasesLock.withLock { leases[gcid] = detail }
        }
    }

    private fun FileDetail.linkUsable(): Boolean {
        val expiresAt = octetStream.expiresAt ?: return false
        return octetStream.url.isNotBlank() && expiresAt - Clock.System.now() > LINK_MARGIN
    }

    // Separate from sign-in so that a file's first mint waits for sign-in alone. The sweep's
    // listing and delete are two more round trips on the first-playback path, against the resolver's
    // fallback budget, and they cannot touch a fresh object: the sweep collects only objects older
    // than its cutoff.
    val startupSweep = scope.launch {
        try {
            ensureLoggedIn()
            // The SDK deletes each leased object once its link is in hand; what is left here is a
            // delete that never ran, the process having died first. Five minutes allows active work
            // on another device to finish.
            val cutoff = Clock.System.now() - 5.minutes
            val leftovers = driveIndex.listTemp().filter {
                // Unknown timestamps are not evidence of abandonment.
                it.isFile && it.id.isNotEmpty() &&
                        runCatching { Instant.parse(it.createdTime) < cutoff }.getOrDefault(false)
            }
            driveIndex.delete(leftovers.map { it.id })
            if (leftovers.isNotEmpty()) {
                logger<PikPakAccount>().info { "[pikpak] startup sweep collected ${leftovers.size} leftover object(s)" }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger<PikPakAccount>().warn(e) { "[pikpak] startup sweep failed" }
        }
    }

    // A leased object takes its full size of storage until its delete lands, and a free account has
    // 6 GB: several episodes minted at once would fail past the free space. Read once, right after
    // sign-in, before the first lease: `about` trails writes by more than a lease lasts, so it is
    // not kept up to date. Anything the startup sweep has yet to remove counts as used, which errs
    // on the safe side.
    private suspend fun budgetLeases() {
        if (freeBytes != null) return
        try {
            val free = client.getQuota().quota.remainingBytes.coerceAtLeast(0)
            freeBytes = free
            // At least one byte: a zero capacity would admit everything. A file larger than the
            // whole budget still goes through, alone.
            client.leaseBudget = LeaseBudget(free.coerceIn(1, MAX_LEASE_BUDGET))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger<PikPakAccount>().warn(e) { "[pikpak] could not read the free space; leases stay unbounded" }
        }
    }

    private companion object {
        // A premium account reports terabytes free; nothing near that is ever leased at once.
        const val MAX_LEASE_BUDGET = 64L * 1024 * 1024 * 1024

        // The SDK's own refresh margin: a link this close to expiry is not handed out again.
        val LINK_MARGIN = 5.minutes
    }
}
