/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import io.github.nihildigit.pikpak.InMemorySessionStore
import io.github.nihildigit.pikpak.PikPakClient
import io.github.nihildigit.pikpak.asRangeSource
import io.github.nihildigit.pikpak.batchDelete
import io.github.nihildigit.pikpak.getFile
import io.github.nihildigit.pikpak.getOrCreateDeepFolderId
import io.github.nihildigit.pikpak.instantCreate
import io.github.nihildigit.pikpak.rangeReader
import io.github.nihildigit.pikpak.resolveMagnet
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.him188.ani.app.torrent.api.files.FilePriority
import me.him188.ani.app.torrent.readExactBytes
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.ktor.createDefaultHttpClient
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class PikPakTorrentEngineLiveTest {
    private suspend fun assertNoNewObjects(
        downloader: PikPakTorrentDownloader,
        before: Set<String>,
        message: String,
    ) {
        val deadline = TimeSource.Monotonic.markNow()
        var added = downloader.tempFolderObjectIds() - before
        while (added.isNotEmpty() && deadline.elapsedNow() < LISTING_SETTLE) {
            delay(1.seconds)
            added = downloader.tempFolderObjectIds() - before
        }
        assertTrue(added.isEmpty(), "$message: $added")
    }

    @Test
    fun `read through the engine then verify against the SDK and restore from disk`() = runBlocking {
        val username = System.getenv("PIKPAK_USERNAME")
        val password = System.getenv("PIKPAK_PASSWORD")
        if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
            println("[skip] PIKPAK_USERNAME / PIKPAK_PASSWORD not set")
            return@runBlocking
        }
        val magnet = DEFAULT_MAGNET

        val apiCalls = AtomicInteger(0)
        val http = countingClient(apiCalls)
        val credentials = MutableStateFlow(PikPakCredentials(username, password))
        val config = MutableStateFlow(PikPakEngineConfig())

        val root = SystemPaths.createTempDirectory("pikpak-engine-live").resolve("pikpak")

        fun downloader() = PikPakTorrentDownloader(
            httpClient = http,
            credentials = credentials,
            sessionStore = InMemorySessionStore(),
            rootDataDirectory = root,
            config = config,
            parentCoroutineContext = Dispatchers.IO,
        )

        val first = downloader()
        first.startupSweep.join()
        val objectsBefore = first.tempFolderObjectIds()

        val servedAt = TimeSource.Monotonic.markNow()
        assertFalse(first.canServe(magnet), "an ISO carries no video entry, so the engine must decline it")
        println("[live] canServe answered in ${servedAt.elapsedNow()}")

        val videoAt = TimeSource.Monotonic.markNow()
        val videoServed = first.canServe(INDEXED_VIDEO_MAGNET)
        println("[live] canServe on an indexed video pack: $videoServed in ${videoAt.elapsedNow()}")
        assertTrue(videoServed, "an indexed video pack must be accepted; see INDEXED_VIDEO_MAGNET")

        val encoded = first.fetchTorrent(magnet)
        val startedAt = TimeSource.Monotonic.markNow()
        val session = withTimeout(2.minutes) { first.startDownload(encoded) }
        val indexed = startedAt.elapsedNow()
        val entry = session.getFiles().maxBy { it.length } as PikPakFileEntry
        println("[live] indexed in $indexed: ${session.getName()} -> ${entry.pathInTorrent} (${entry.length} bytes)")
        assertTrue(entry.meta.gcid.isNotEmpty(), "the entry carries no gcid, so nothing could be recreated from it")

        val handle = entry.createHandle()
        handle.resume(FilePriority.HIGH)

        val head: ByteArray
        val middle: ByteArray
        val middleOffset = (entry.length * 6 / 10) / BLOCK * BLOCK
        entry.createInput().use { input ->
            val headMark = TimeSource.Monotonic.markNow()
            input.seekTo(0)
            head = input.readExactBytes(HEAD_LENGTH)
            println("[live] first $HEAD_LENGTH bytes in ${headMark.elapsedNow()}")

            val seekMark = TimeSource.Monotonic.markNow()
            input.seekTo(middleOffset)
            middle = input.readExactBytes(MIDDLE_LENGTH)
            println("[live] $MIDDLE_LENGTH bytes at $middleOffset in ${seekMark.elapsedNow()}")
        }

        val reference = PikPakClient(
            account = username,
            password = password,
            sessionStore = InMemorySessionStore(),
            httpClient = http,
            cdnHttpClient = PikPakClient.tunedCdnClient(),
        )
        val resolved = reference.resolveMagnet(magnet)
            ?: error("reference lookup: PikPak does not index $magnet")
        val referenceFile = resolved.files.single { it.path == entry.pathInTorrent }
        val referenceFolder = reference.getOrCreateDeepFolderId("", "Animeko-LiveTest-Reference")
        val referenceId = reference.instantCreate(referenceFile, parentId = referenceFolder)
        try {
            val source = reference.rangeReader(referenceId).asRangeSource()
            assertEquals(
                sha256(head),
                sha256(source.readBytes(0, HEAD_LENGTH.toLong())),
                "the first $HEAD_LENGTH bytes differ from what PikPak serves",
            )
            assertEquals(
                sha256(middle),
                sha256(source.readBytes(middleOffset, MIDDLE_LENGTH.toLong())),
                "the $MIDDLE_LENGTH bytes at $middleOffset differ from what PikPak serves",
            )
        } finally {
            reference.batchDelete(listOf(referenceId))
        }

        assertEquals(
            0L,
            entry.fileStats.first().downloadedBytes,
            "playback must not have downloaded anything to disk",
        )

        handle.close()
        session.close()

        assertNoNewObjects(first, objectsBefore, "closing the session left objects behind")
        first.close()

        val second = downloader()

        second.startupSweep.join()
        val callsBeforeRestore = apiCalls.get()
        val restored = second.startDownload(second.fetchTorrent(magnet))
        val restoredEntry = restored.getFiles().single { it.pathInTorrent == entry.pathInTorrent }
        val callsAfterRestore = apiCalls.get()

        assertEquals(
            callsBeforeRestore,
            callsAfterRestore,
            "restoring from disk must not talk to PikPak",
        )
        assertEquals(entry.length, restoredEntry.length)

        restored.close()
        second.close()
        http.close()
    }

    @Test
    fun `a magnet PikPak has never seen is answered quickly and negatively`() = runBlocking {
        val username = System.getenv("PIKPAK_USERNAME")
        val password = System.getenv("PIKPAK_PASSWORD")
        if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
            println("[skip] PIKPAK_USERNAME / PIKPAK_PASSWORD not set")
            return@runBlocking
        }

        val magnet = "magnet:?xt=urn:btih:0123456789ABCDEF0123456789ABCDEF01234567"

        val http = createDefaultHttpClient(installRetry = false, installContentNegotiation = false)
        val downloader = PikPakTorrentDownloader(
            httpClient = http,
            credentials = MutableStateFlow(PikPakCredentials(username, password)),
            sessionStore = InMemorySessionStore(),
            rootDataDirectory = SystemPaths.createTempDirectory("pikpak-engine-live-miss").resolve("pikpak"),
            config = MutableStateFlow(PikPakEngineConfig()),
            parentCoroutineContext = Dispatchers.IO,
        )

        val mark = TimeSource.Monotonic.markNow()
        assertFalse(downloader.canServe(magnet))
        println("[live] a miss took ${mark.elapsedNow()}")

        val failure = runCatching { downloader.startDownload(downloader.fetchTorrent(magnet)) }.exceptionOrNull()
        assertTrue(
            failure is PikPakNotIndexedException,
            "an unindexed magnet must fail as PikPakNotIndexedException, got $failure",
        )

        downloader.close()
        http.close()
    }

    private fun countingClient(counter: AtomicInteger): HttpClient =
        createDefaultHttpClient(installRetry = false, installContentNegotiation = false).apply {
            plugin(HttpSend).intercept { request ->

                if (request.url.host.endsWith("mypikpak.com")) counter.incrementAndGet()
                execute(request)
            }
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        val LISTING_SETTLE = 30.seconds

        const val HEAD_LENGTH = 4 * 1024 * 1024
        const val MIDDLE_LENGTH = 1024 * 1024

        const val BLOCK = 4096L
    }
}

private const val INDEXED_VIDEO_MAGNET =
    "magnet:?xt=urn:btih:7af771b417c55ebc86caa0cb82cdb7faac90c04c"

private const val DEFAULT_MAGNET =
    "magnet:?xt=urn:btih:157e0a57e1af0e1cfd46258ba6c62938c21b6ee8" +
            "&dn=archlinux-2026.04.01-x86_64.iso"
