/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import io.github.nihildigit.pikpak.MagnetResource
import io.github.nihildigit.pikpak.PikPakClient
import io.github.nihildigit.pikpak.SessionStore
import io.github.nihildigit.pikpak.getQuota
import io.github.nihildigit.pikpak.resolveMagnet
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
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
    private val config: StateFlow<PikPakEngineConfig>,
    parentCoroutineContext: CoroutineContext,
) : TorrentDownloader {
    private val logger = logger<PikPakTorrentDownloader>()
    private val scope = CoroutineScope(parentCoroutineContext + SupervisorJob(parentCoroutineContext[Job]))

    // Deleting a leased cloud object has to outlive this downloader. close() is what ends playback,
    // and the object whose link was minted moments before it would go down with scope: the delete
    // is submitted but never scheduled, and the NonCancellable inside cannot save a coroutine that
    // never starts. The next startup sweep does not collect it either, its timestamp being far
    // newer than the sweep's cutoff. Deliberately parentless, so cancelling scope cannot reach it.
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


    private val resolvedMagnetsLock = Mutex()
    private val resolvedMagnets = mutableMapOf<String, ResolvedMagnet>()

    private class ResolvedMagnet(val resource: MagnetResource, val at: TimeSource.Monotonic.ValueTimeMark)

    private val downloadScheduler = DownloadScheduler()

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
                sessionLock.withLock { if (sessions[sourceKey] === deferred) sessions.remove(sourceKey) }
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

    override fun listSaves(): List<SystemPath> {
        if (!rootDataDirectory.exists()) return emptyList()
        return rootDataDirectory.useDirectoryEntries { entries -> entries.filter { it.isDirectory() }.toList() }
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
                        .also { sessions[sourceKey] = it }
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
        val merged = mergeImportedInto(cloud, partial)
        val result = if (filesConsistent(saveDirectory, merged)) merged else cloud
        resumeData.write(result)
        logger.info { "[pikpak] $sourceKey filled in from the magnet, ${result.files.size} file(s)" }
        return result
    }

    private suspend fun buildEntry(
        fileMeta: PikPakFileMeta,
        sourceKey: String,
        saveDirectory: SystemPath,
        parentCoroutineContext: CoroutineContext,
        onHandleCountChanged: suspend () -> Unit,
    ): PikPakFileEntry {
        val cloudFile = CloudFile(
            gcid = fileMeta.gcid,
            size = fileMeta.length,
            name = fileMeta.pathInTorrent.substringAfterLast('/'),
            accountProvider = ::account,
            connectionBudget = config.value.effectiveConcurrency,
            scope = cleanupScope,
        )

        return PikPakFileEntry(
            index = fileMeta.index,
            length = fileMeta.length,
            saveDirectory = saveDirectory,
            relativePath = fileMeta.pathInTorrent,
            torrentId = sourceKey,
            parentCoroutineContext = parentCoroutineContext,
            meta = fileMeta,
            source = cloudFile,
            prepareSource = cloudFile::prepare,
            concurrency = config.value.effectiveConcurrency,
            scheduler = downloadScheduler,
            onHandleCountChanged = onHandleCountChanged,
        )
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
        return PikPakTorrentMeta(
            uri = uri,
            sourceKey = sourceKey,
            name = resource.name.ifEmpty { sourceKey },
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
            // One gate, not two. The CDN client caps this host at the per-file budget, so an
            // account budget above it does not admit more work, it only moves the queue into
            // OkHttp's dispatcher -- which is FIFO, and where the playback read's priority stops
            // meaning anything. Keep both limits aligned so priority remains effective at the gate.
            accountConnectionBudget = config.value.effectiveConcurrency,
        ).let { PikPakAccount(it, scope) }.also { clientEntry = creds to it }
    }

    private companion object {
        const val SDK_VERSION = "0.6.6"

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
    return cloud.copy(indexed = true, files = files)
}

// Missing files are normal for streaming sessions; preallocation makes shorter files resumable.
internal fun filesConsistent(saveDirectory: SystemPath, meta: PikPakTorrentMeta): Boolean = meta.files.all {
    val path = saveDirectory.resolve(it.pathInTorrent)
    !path.exists() || path.length() <= it.length
}

// Directory IDs and cleanup requests must use the client that created them, even after an account switch.
internal class PikPakAccount(val client: PikPakClient, scope: CoroutineScope) {
    val driveIndex = PikPakDriveIndex(clientProvider = { client })

    // Every other entry point logs in before its first request. Skipping it here sent the first
    // request of a cold start out unauthenticated: the 401 recovery path reads only the in-memory
    // session, never the store, so a stored refresh token could not be used and the client fell
    // back to a full captcha sign-in.
    //
    // Separate from the sweep so that a file's first mint waits for this alone. The sweep's listing
    // and delete are two more round trips on the first-playback path, against the resolver's
    // fallback budget, and they cannot touch a fresh object: the sweep collects only objects older
    // than its cutoff.
    val login: Deferred<Unit> = scope.async { client.login() }

    val startupSweep = scope.launch {
        try {
            login.await()
            // Cloud objects only bridge instant creation and signed-link minting. Five minutes
            // allows active work on another device to finish while bounding leaked temporary data.
            // A reader can recreate an object if a stale link later returns 404.
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
}
