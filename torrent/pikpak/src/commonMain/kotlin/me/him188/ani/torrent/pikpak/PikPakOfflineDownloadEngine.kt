/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import io.github.nihildigit.pikpak.CreateUrlResult
import io.github.nihildigit.pikpak.FileDetail
import io.github.nihildigit.pikpak.FileKind
import io.github.nihildigit.pikpak.FileStat
import io.github.nihildigit.pikpak.PikPakClient
import io.github.nihildigit.pikpak.SessionStore
import io.github.nihildigit.pikpak.batchDelete
import io.github.nihildigit.pikpak.createUrlFile
import io.github.nihildigit.pikpak.getFile
import io.github.nihildigit.pikpak.getOrCreateDeepFolderId
import io.github.nihildigit.pikpak.listFiles
import io.github.nihildigit.pikpak.listOfflineTasks
import io.ktor.client.HttpClient
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.io.bytestring.encodeToByteString
import me.him188.ani.torrent.offline.OfflineDownloadAuthException
import me.him188.ani.torrent.offline.OfflineDownloadEngine
import me.him188.ani.torrent.offline.OfflineDownloadRejectedException
import me.him188.ani.torrent.offline.ResolvedMedia
import me.him188.ani.utils.io.DigestAlgorithm
import me.him188.ani.utils.io.digest
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.ktor.UnsafeScopedHttpClientApi
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Credentials + enabled flag for the PikPak engine. Emitted by the app-level
 * SettingsRepository wrapper so the engine doesn't know about DataStore.
 *
 * [password] may be empty when the session store already holds a usable
 * refresh token. The SDK will try the refresh path first and only fall back
 * to signin-with-password if refresh fails; this lets us stop persisting the
 * plaintext password once we've bootstrapped a session.
 */
data class PikPakCredentials(
    val username: String,
    val password: String,
) {
    val isValid: Boolean get() = username.isNotEmpty()
}

/**
 * The PikPak implementation of [OfflineDownloadEngine], built on top of the
 * `io.github.nihildigit:pikpak-kotlin` SDK. The SDK owns auth, token refresh,
 * captcha, rate limiting and retry; this class owns the offline-task
 * orchestration policy (when to poll, season-pack child pick, failure cleanup).
 *
 * Pattern: per-credentials [PikPakClient] cached in [clientEntry]. Recreated
 * when credentials change, so a re-login after the user edits settings happens
 * naturally through the StateFlow pre-warm side-effect.
 */
class PikPakOfflineDownloadEngine(
    scopedHttpClient: ScopedHttpClient,
    private val credentials: StateFlow<PikPakCredentials?>,
    private val scope: CoroutineScope,
    private val sessionStore: SessionStore,
    private val pollInterval: Duration = 2.seconds,
    private val resolveTimeout: Duration = 5.minutes,
    private val slotFolderName: String = "Animeko-Playing",
    /**
     * Supplies the user's desired slot-queue length at resolve time. Reading
     * the lambda per-resolve means the engine picks up setting changes without
     * needing a restart. Values `>= SLOT_QUEUE_UNLIMITED_SENTINEL` disable
     * eviction entirely.
     */
    private val slotQueueLength: () -> Int = { 1 },
) : OfflineDownloadEngine {

    companion object {
        /**
         * When [slotQueueLength] returns this (or more), the engine stops
         * evicting stale buckets — the user's PikPak drive becomes the limit.
         * Matches `PikPakConfig.SLOT_QUEUE_UNLIMITED` on the app side.
         */
        const val SLOT_QUEUE_UNLIMITED_SENTINEL: Int = 14
    }

    private val logger = logger<PikPakOfflineDownloadEngine>()

    override val id: String = "pikpak"
    override val displayName: String = "PikPak"

    override val isSupported: StateFlow<Boolean> = credentials
        .map { it != null && it.isValid }
        .stateIn(scope, SharingStarted.Eagerly, initialValue = credentials.value?.isValid == true)

    // Borrow the underlying HttpClient for the lifetime of this engine. The
    // SDK needs a stable HttpClient to hand to its OkHttp/Darwin engine, and
    // ScopedHttpClient's borrowForever() is the documented escape hatch for
    // that exact scenario. The engine is a process-singleton (Koin `single`),
    // so no leak concern.
    @OptIn(UnsafeScopedHttpClientApi::class)
    private val sharedHttp: HttpClient = scopedHttpClient.borrowForever().client

    @Volatile
    private var clientEntry: Pair<PikPakCredentials, PikPakClient>? = null

    // Serialises [resolve] across concurrent callers. The engine is a single
    // per-process Koin instance and its slot lives on a shared PikPak folder
    // ("Animeko-Playing"), so two concurrent resolves would race on
    // listFiles → eviction → createBucket and could delete each other's
    // in-flight buckets. withTimeout stays on the outside so the resolveTimeout
    // budget covers queueing + work; a caller doesn't starve indefinitely
    // behind an earlier resolve.
    private val resolveMutex = Mutex()

    init {
        // Pre-warm the bearer token whenever valid credentials are available.
        // The user likely toggled PikPak on well before they actually hit play,
        // so doing the signin round-trip eagerly turns the first `resolve()`
        // call of a session into a task-submit + poll path (the bearer is
        // already cached).
        credentials
            .filter { it != null && it.isValid }
            .distinctUntilChanged()
            .onEach { creds ->
                scope.launch {
                    runCatching { clientFor(creds!!).login() }
                        .onFailure { logger.warn(it) { "[pikpak] pre-warm signin failed (non-fatal)" } }
                }
            }
            .launchIn(scope)
    }

    override suspend fun resolve(
        uri: String,
        pickVideoFile: (candidateFilenames: List<String>) -> String?,
    ): ResolvedMedia =
        withTimeout(resolveTimeout) {
            resolveMutex.withLock {
            val creds = credentials.value
                ?: throw OfflineDownloadAuthException("PikPak not configured")
            if (!creds.isValid) {
                throw OfflineDownloadAuthException("PikPak credentials incomplete")
            }
            val client = clientFor(creds)
            // The SDK's endpoint functions don't auto-ensure a session; they
            // rely on the caller having populated one. The init-block pre-warm
            // is async and may not have finished by the time a user hits play,
            // so explicitly await login here. Cheap when already authed.
            client.login()

            // Single-slot design:
            //
            // The engine maintains one well-known folder in the user's PikPak
            // drive (default name "Animeko-Playing") that always holds at most
            // one video — whatever's being played right now. Cleanup runs at
            // the *start* of the next resolve(), not the end of the current
            // one, so the URL we just handed to libvlc stays valid for this
            // whole playback session.
            //
            // Benefits over fire-and-forget batchDelete:
            //   * State lives server-side. App crashes / restarts don't leak —
            //     the next resolve() drains the slot anyway.
            //   * No racing against libvlc's first GET (that was the root cause
            //     of every "VLC is unable to open MRL" we hit).
            //   * Season-pack siblings don't linger; the old pack folder is
            //     cascade-deleted before the new task is submitted.
            val slotId = client.getOrCreateDeepFolderId(parentId = "", path = slotFolderName)

            // Per-source sub-folder: every magnet / .torrent URL gets its own
            // namespace inside the slot, keyed by infohash (magnet) or a hash
            // of the URL (.torrent). This is what lets us do a "cache hit"
            // safely — previously pickVideoFile alone was the hit predicate,
            // which mis-fired when an old cached episode and a new resolve's
            // target shared an episode number (e.g. slot held "Android ... 02"
            // and the user opened SPY×FAMILY E02; the filename's "02" pattern
            // matched and we handed back the wrong show).
            val sourceKey = sourceKeyFor(uri)
            val topEntries = client.listFiles(parentId = slotId)
            val myBucket = topEntries.firstOrNull { it.name == sourceKey }

            // Slot-hit fast path: we only reuse when the bucket belongs to
            // *this exact source*. pickVideoFile still runs inside the bucket
            // to pick the right episode out of a cached season pack.
            if (myBucket != null) {
                val cached = collectSlotCandidates(client, myBucket.id)
                if (cached.isNotEmpty()) {
                    val hitName = pickVideoFile(cached.map { it.name })
                    if (hitName != null) {
                        val hit = cached.first { it.name == hitName }
                        logger.info { "[pikpak] slot hit: bucket=$sourceKey reusing '${hit.name}' (${hit.id})" }
                        return@withTimeout buildResolvedMedia(client.getFile(hit.id))
                    }
                }
            }

            // Slot miss → evict to honor the user-configured queue length.
            val queueLen = slotQueueLength().coerceAtLeast(1)
            val toEvict = pickEvictions(topEntries, sourceKey, queueLen)
            if (toEvict.isNotEmpty()) {
                logger.info { "[pikpak] draining slot: keeping ${(queueLen - 1).coerceAtLeast(0)} newest, deleting ${toEvict.size}" }
                runCatching { client.batchDelete(toEvict) }
                    .onFailure { logger.warn(it) { "[pikpak] slot drain failed (non-fatal)" } }
            } else if (queueLen >= SLOT_QUEUE_UNLIMITED_SENTINEL) {
                logger.debug { "[pikpak] slot queue length = unlimited; skipping eviction" }
            }

            // Ensure this source's bucket exists, and submit the task *into
            // it*. PikPak lands everything from the task under parent_id, so
            // a season pack's pack-folder + children all sit inside the bucket.
            val bucketId = myBucket?.id
                ?: client.getOrCreateDeepFolderId(parentId = "", path = "$slotFolderName/$sourceKey")

            // Track the latest root id we've seen — used for failure cleanup
            // only. On success, the task's result sits in the bucket for the
            // next resolve() to either hit or drain.
            var failureCleanupId: String? = null

            try {
                logger.info { "[pikpak] submit offline task for ${uri.take(60)}... bucket=$sourceKey" }
                val fileId = when (val result = client.createUrlFile(parentId = bucketId, url = uri)) {
                    is CreateUrlResult.Queued -> {
                        val task = result.task
                        logger.debug {
                            "[pikpak] submitted task id=${task.id} file_id=${task.fileId} file_name=${task.fileName}"
                        }
                        if (task.fileId.isNotEmpty()) failureCleanupId = task.fileId
                        awaitCompletion(client, task.id, task.fileId) { observed ->
                            failureCleanupId = observed
                        }
                    }
                    // PikPak recognised the URL's content as already cached on
                    // their side and dropped the file straight into our bucket
                    // without queuing a task. The bucket is per-source, so the
                    // newest entry by createdTime is ours.
                    CreateUrlResult.InstantComplete -> {
                        logger.info {
                            "[pikpak] instant-complete for bucket=$sourceKey; recovering landed file from bucket listing"
                        }
                        val landed = client.listFiles(parentId = bucketId)
                            .maxByOrNull { it.createdTime }
                            ?: throw OfflineDownloadRejectedException(
                                "PikPak reported instant-complete but bucket $sourceKey is empty after submission",
                            )
                        landed.id
                    }
                }
                failureCleanupId = fileId

                val rootInfo = client.getFile(fileId)
                if (rootInfo.id.isNotEmpty()) failureCleanupId = rootInfo.id

                val videoFile = if (rootInfo.kind == FileKind.FOLDER) {
                    // Season pack: filter the pack folder's children with the
                    // caller's pickVideoFile; fetch the chosen child for its
                    // signed URL. The whole pack folder stays inside the slot
                    // until the next resolve drains it (cascade delete).
                    val children = client.listFiles(parentId = rootInfo.id)
                    val chosenName = pickVideoFile(children.map { it.name })
                        ?: throw OfflineDownloadRejectedException(
                            "PikPak folder ${rootInfo.id} contains no matching video " +
                                    "(files: ${children.joinToString(limit = 10) { it.name }})",
                        )
                    val chosen = children.firstOrNull { it.name == chosenName }
                        ?: throw OfflineDownloadRejectedException(
                            "pickVideoFile returned '$chosenName' which is not among the folder's children",
                        )
                    logger.info {
                        "[pikpak] season pack: picked '${chosen.name}' (${chosen.id}) " +
                                "out of ${children.size} children"
                    }
                    client.getFile(chosen.id)
                } else {
                    rootInfo
                }

                buildResolvedMedia(videoFile)
                // No cleanup on success; next resolve drains the slot.
            } catch (e: Throwable) {
                // Failure path: best-effort cleanup inside the slot so a
                // hung-up task doesn't pile up on retries.
                scheduleCleanup(client, failureCleanupId)
                throw e
            }
            }
        }

    /**
     * Flattens everything currently in the slot into a `(id, name)` list of
     * video-file candidates. Handles both layouts the slot can be in:
     *   * A single file (from a past single-file torrent resolve)
     *   * A folder with children (from a past season-pack resolve)
     * Nested folders beyond one level aren't expected in practice — PikPak
     * flattens a torrent's directory structure into a single pack folder.
     */
    private suspend fun collectSlotCandidates(
        client: PikPakClient,
        slotId: String,
    ): List<SlotEntry> {
        val top = client.listFiles(parentId = slotId)
        val out = mutableListOf<SlotEntry>()
        for (entry in top) {
            if (entry.isFolder) {
                val inner = client.listFiles(parentId = entry.id)
                inner.filter { it.isFile }.forEach { out += SlotEntry(it.id, it.name) }
            } else if (entry.isFile) {
                out += SlotEntry(entry.id, entry.name)
            }
        }
        return out
    }

    private data class SlotEntry(val id: String, val name: String)

    private fun scheduleCleanup(client: PikPakClient, fileId: String?) {
        if (fileId.isNullOrEmpty()) return
        scope.launch {
            runCatching { client.batchDelete(listOf(fileId)) }
                .onFailure { logger.warn(it) { "[pikpak] cleanup batchDelete failed id=$fileId" } }
        }
    }

    private fun clientFor(creds: PikPakCredentials): PikPakClient {
        val current = clientEntry
        if (current != null && current.first == creds) return current.second
        val fresh = PikPakClient(
            account = creds.username,
            password = creds.password,
            sessionStore = sessionStore,
            httpClient = sharedHttp,
        )
        clientEntry = creds to fresh
        return fresh
    }

    /**
     * Poll the offline-task list until our task leaves the active phases. The
     * SDK intentionally exposes no built-in polling loop — the "task
     * disappeared from the RUNNING+ERROR+PENDING filter" heuristic below is
     * an app-layer policy that doesn't belong in the SDK.
     */
    private suspend fun awaitCompletion(
        client: PikPakClient,
        taskId: String,
        initialFileId: String,
        onFileIdObserved: (String) -> Unit = {},
    ): String {
        var fileId = initialFileId
        var attempt = 0
        // Include PENDING so a freshly queued task doesn't look "already gone"
        // and trip the "Task completed but no file_id" branch below.
        val activePhases = "PHASE_TYPE_PENDING,PHASE_TYPE_RUNNING,PHASE_TYPE_ERROR"
        while (true) {
            delay(pollInterval)
            attempt++
            val list = client.listOfflineTasks(phaseFilter = activePhases)
            val match = list.tasks.firstOrNull { it.id == taskId }
            if (match == null) {
                // Task left the PENDING/RUNNING/ERROR filter => completed.
                logger.info { "[pikpak] task $taskId completed after $attempt polls" }
                return fileId.ifEmpty {
                    throw OfflineDownloadRejectedException(
                        "Task completed but no file_id was observed; re-submit may be needed",
                    )
                }
            }
            if (match.fileId.isNotEmpty() && match.fileId != fileId) {
                fileId = match.fileId
                onFileIdObserved(fileId)
            }
            if (match.phase == "PHASE_TYPE_ERROR") {
                throw OfflineDownloadRejectedException(
                    "PikPak task failed: phase=${match.phase} message=${match.message}",
                )
            }
            logger.debug {
                "[pikpak] poll $attempt: phase=${match.phase} progress=${match.progress} file_id=$fileId"
            }
        }
    }

}

/**
 * Maps a PikPak [FileDetail] to the [ResolvedMedia] we hand to mediamp.
 * Prefers `links.octet-stream` over the legacy `web_content_link`; throws
 * [OfflineDownloadRejectedException] when neither is present (a file still
 * being processed server-side, or an access-denied record). Exposed
 * `internal` so commonTest can exercise URL selection without spinning up
 * the engine.
 */
internal fun buildResolvedMedia(file: FileDetail): ResolvedMedia {
    val streamUrl = file.downloadUrl
        ?: file.webContentLink.takeIf { it.isNotEmpty() }
        ?: throw OfflineDownloadRejectedException(
            "PikPak file has no playable link (file_id=${file.id})",
        )

    val expiresAt: Instant? = file.links.octetStream.expire
        .takeIf { it.isNotEmpty() }
        ?.let { runCatching { Instant.parse(it) }.getOrNull() }

    return ResolvedMedia(
        streamUrl = streamUrl,
        expiresAt = expiresAt,
        fileName = file.name.takeIf { it.isNotEmpty() },
        fileSize = file.size.toLongOrNull(),
        providerFileId = file.id.takeIf { it.isNotEmpty() },
    )
}

/**
 * Deterministic cache key for a resolve source. Used as a sub-folder name
 * under the engine's working folder, so it (a) has to be a valid PikPak
 * filename and (b) must not collide across distinct sources — a collision
 * would let one source's slot reuse another's cached files.
 *
 * Rules:
 *  - Magnet: take the infohash after `xt=urn:btih:` and canonicalise it.
 *    Magnet URIs can carry the infohash as 40-char hex, 32-char RFC-4648
 *    base32, or 64-char hex (BEP-52 v2). All three are normalised to
 *    uppercase hex so two magnets pointing at the same torrent — even
 *    through different encodings — map to the same bucket.
 *  - HTTP `.torrent` URL (or a magnet with a missing/unrecognised infohash):
 *    SHA-256 of the URL, take the first 16 hex chars (64-bit). The `h-`
 *    prefix avoids clashing with hex-encoded infohashes. `String.hashCode()`
 *    used to live here; 32-bit is not wide enough to rule out a wrong-bucket
 *    hit at scale — SHA-256 eliminates the risk.
 *
 * Exposed `internal` so commonTest can exercise these rules without spinning
 * up the engine.
 */
internal fun sourceKeyFor(uri: String): String {
    val rawInfoHash = Regex("xt=urn:btih:([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)
        .find(uri)?.groupValues?.get(1)
    val canonical = rawInfoHash?.let { canonicalizeBtih(it) }
    if (canonical != null) return canonical
    return "h-" + sha256HexShort(uri)
}

private fun canonicalizeBtih(raw: String): String? {
    val upper = raw.uppercase()
    return when {
        // BEP-9 (SHA-1) infohash — already canonical.
        upper.length == 40 && upper.all { it in HEX_ALPHABET } -> upper
        // BEP-52 (SHA-256) infohash for v2 torrents.
        upper.length == 64 && upper.all { it in HEX_ALPHABET } -> upper
        // RFC-4648 base32, no padding — the other form BEP-9 allows.
        upper.length == 32 && upper.all { it in BASE32_ALPHABET } -> base32ToHexUpper(upper)
        else -> null
    }
}

private const val HEX_ALPHABET = "0123456789ABCDEF"
private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

private fun base32ToHexUpper(s: String): String {
    // 32 base32 chars × 5 bits = 160 bits = 20 bytes = 40 hex chars.
    val out = StringBuilder(s.length * 5 / 4)
    var buffer = 0
    var bitsInBuffer = 0
    for (c in s) {
        val v = BASE32_ALPHABET.indexOf(c)
        buffer = (buffer shl 5) or v
        bitsInBuffer += 5
        while (bitsInBuffer >= 4) {
            bitsInBuffer -= 4
            val nibble = (buffer shr bitsInBuffer) and 0xF
            out.append(HEX_ALPHABET[nibble])
        }
    }
    return out.toString()
}

private fun sha256HexShort(input: String): String {
    // 64-bit (16 hex chars) is overkill for a user's slot namespace but keeps
    // the key short enough to read in logs. Lowercase for easy visual
    // distinction from the uppercase-hex infohash bucket names above.
    val digest = input.encodeToByteString().digest(DigestAlgorithm.SHA256)
    val hexLower = "0123456789abcdef"
    val sb = StringBuilder(16)
    for (i in 0 until 8) {
        val b = digest[i].toInt() and 0xFF
        sb.append(hexLower[b ushr 4])
        sb.append(hexLower[b and 0x0F])
    }
    return sb.toString()
}

/**
 * Decides which top-level slot entries to [batchDelete][io.github.nihildigit.pikpak.batchDelete]
 * based on the user-configured queue length. Pure function — no network, no
 * mutation — so commonTest can cover the policy edges (single-slot, N-slot,
 * unlimited, entry ordering) without touching PikPakClient.
 *
 * Semantics:
 *  - `queueLength >= SLOT_QUEUE_UNLIMITED_SENTINEL` → return empty (no eviction).
 *  - Otherwise: keep the current-source bucket (never evicted, even if it's
 *    the oldest) plus the `(queueLength - 1)` newest of the remaining
 *    buckets; evict everything else.
 *  - Bucket age is read from [FileStat.createdTime], which PikPak returns as
 *    ISO-8601 strings — lexicographic sort matches chronological order.
 *
 * @return the `id`s of entries to delete. May be empty.
 */
internal fun pickEvictions(
    topEntries: List<FileStat>,
    currentSourceKey: String,
    queueLength: Int,
): List<String> {
    if (queueLength >= PikPakOfflineDownloadEngine.SLOT_QUEUE_UNLIMITED_SENTINEL) return emptyList()
    val others = topEntries.filter { it.name != currentSourceKey }
    val keepCount = (queueLength - 1).coerceAtLeast(0)
    return others
        .sortedByDescending { it.createdTime }
        .drop(keepCount)
        .map { it.id }
}
