/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.httpdownloader

import app.cash.turbine.test
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.core.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.FileMetadata
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import me.him188.ani.utils.io.copyTo
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.ktor.asScopedHttpClient
import org.openani.mediamp.ffmpeg.FFmpegResult
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalEncodingApi::class)
class KtorHttpDownloaderTest {
    private lateinit var testScope: TestScope
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var mockClient: HttpClient
    private lateinit var mockClock: TestClock
    private lateinit var tempDir: String
    private lateinit var downloader: KtorHttpDownloader
    private val fileSystem = SystemFileSystem
    private var lastFfmpegArgs: List<String>? = null
    private var lastInputPlaylistContent: String? = null
    private var forceFfmpegFailure = false

    // We use this to track how many times a given URL has been requested.
    // This allows us to simulate "fails first time, succeeds second time", etc.
    private val attempts = mutableMapOf<String, Int>().withDefault { 0 }

    @BeforeTest
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        testScheduler = testDispatcher.scheduler
        testScope = TestScope(testDispatcher)
        mockClock = TestClock(testScheduler)
        tempDir = SystemTemporaryDirectory
            .resolve("test-m3u8-downloads-${Clock.System.now().toEpochMilliseconds()}")
            .toString()

        // Create directories
        if (!fileSystem.exists(Path(tempDir))) {
            fileSystem.createDirectories(Path(tempDir))
        }
        if (!fileSystem.exists(Path("$tempDir/persistence"))) {
            fileSystem.createDirectories(Path("$tempDir/persistence"))
        }
        lastFfmpegArgs = null
        lastInputPlaylistContent = null
        forceFfmpegFailure = false

        // Create mock client with preset responses
        mockClient = HttpClient(MockEngine) {
            expectSuccess = true
            engine {
                addHandler { request ->
                    val urlString = request.url.toString()
                    // Bump attempt counter for this URL
                    val currentAttempt = attempts.getValue(urlString)
                    attempts[urlString] = currentAttempt + 1

                    // Master playlist
                    when (urlString) {
                        "https://example.com/master.m3u8" -> {
                            respond(
                                content = MASTER_PLAYLIST,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/vnd.apple.mpegurl"),
                            )
                        }

                        "https://example.com/playlist.m3u8" -> {
                            respond(
                                content = MEDIA_PLAYLIST,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/vnd.apple.mpegurl"),
                            )
                        }

                        "https://example.com/bad-segments.m3u8" -> {
                            // Replace a valid segment with a missing one => 404
                            respond(
                                content = MEDIA_PLAYLIST.replace("segment1.ts", "missing-segment.ts"),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/vnd.apple.mpegurl"),
                            )
                        }

                        "https://example.com/encrypted.m3u8" -> {
                            respond(
                                content = ENCRYPTED_MEDIA_PLAYLIST,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/vnd.apple.mpegurl"),
                            )
                        }

                        "https://example.com/tagged.m3u8" -> {
                            respond(
                                content = TAGGED_MEDIA_PLAYLIST,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/vnd.apple.mpegurl"),
                            )
                        }

                        "https://example.com/key.bin" -> {
                            respond(
                                content = HLS_ENCRYPTION_KEY,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"),
                            )
                        }

                        "https://example.com/encrypted-segment0.ts" -> {
                            respond(
                                content = Base64.Default.decode(ENCRYPTED_TS_SEGMENT_BASE64),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "video/mp2t"),
                            )
                        }

                        "https://example.com/error.m3u8" -> {
                            // 404 response
                            respond("Not found", HttpStatusCode.NotFound)
                        }

                        "https://example.com/timeout.m3u8" -> {
                            // Simulate a long delay
                            withContext(testDispatcher) {
                                delay(10.seconds)
                            }
                            respond("Timeout", HttpStatusCode.OK)
                        }

                        "https://example.com/sample.mp4" -> {
                            // Handle range requests for MP4 files
                            val rangeHeader = request.headers["Range"]
                            handleMp4(rangeHeader)
                        }

                        "https://example.com/sample.mkv" -> {
                            // Handle range requests for MKV files
                            val rangeHeader = request.headers["Range"]
                            handleMkv(rangeHeader)
                        }

                        "https://example.com/error.mp4" -> {
                            // 404 response for MP4
                            respond("Not found", HttpStatusCode.NotFound)
                        }

                        "https://example.com/error.mkv" -> {
                            // 404 response for MKV
                            respond("Not found", HttpStatusCode.NotFound)
                        }

                        "https://example.com/no-range-support.mp4" -> {
                            // Server that doesn't support range requests
                            val headers = headersOf(
                                HttpHeaders.ContentType to listOf("video/mp4"),
                                HttpHeaders.ContentLength to listOf("$MP4_FILE_SIZE"),
                            )
                            respond(
                                content = ByteArray(MP4_FILE_SIZE.toInt()) { it.toByte() },
                                status = HttpStatusCode.OK,
                                headers = headers,
                            )
                        }

                        "https://example.com/unstable-playlist1.m3u8" -> {
                            // Fails the first time (attempt==1 => 500), succeeds second time => return a valid playlist
                            if (currentAttempt == 0) {
                                respond("Server error", HttpStatusCode.InternalServerError)
                            } else {
                                respond(
                                    content = MEDIA_PLAYLIST,
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "application/vnd.apple.mpegurl"),
                                )
                            }
                        }

                        "https://example.com/unstable-playlist2.m3u8" -> {
                            // Always fail => 500
                            respond("Server error", HttpStatusCode.InternalServerError)
                        }

                        // A playlist referencing "unstable-segment1.ts" & "unstable-segment2.ts"
                        "https://example.com/unstable-segments.m3u8" -> {
                            respond(
                                content = UNSTABLE_SEGMENTS_PLAYLIST,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/vnd.apple.mpegurl"),
                            )
                        }

                        // Unstable segments
                        "https://example.com/unstable-segment1.ts" -> {
                            // fails on first attempt => 500, succeeds afterwards => returns 512 bytes
                            if (currentAttempt == 1) {
                                respond("Internal server error", HttpStatusCode.InternalServerError)
                            } else {
                                val bytes = ByteArray(512) { it.toByte() }
                                respond(
                                    content = bytes,
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "video/mp2t"),
                                )
                            }
                        }

                        "https://example.com/unstable-segment2.ts" -> {
                            // always fails => 500
                            respond("Internal server error", HttpStatusCode.InternalServerError)
                        }

                        // Segment responses or unknown
                        else -> {
                            if (urlString.startsWith("https://example.com/segment") && urlString.endsWith(".ts")) {
                                val num = urlString.substringAfter("segment").substringBefore(".ts").toInt()
                                respond(
                                    content = ByteArray(1024 * num) { it.toByte() },
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "video/mp2t"),
                                )
                            } else {
                                respond("Unknown request: $urlString", HttpStatusCode.BadRequest)
                            }
                        }
                    }
                }
            }
        }

        downloader = object : KtorHttpDownloader(
            client = mockClient.asScopedHttpClient(),
            fileSystem = fileSystem,
            clock = mockClock,
            baseSaveDir = Path(tempDir),
            parentScope = CoroutineScope(SupervisorJob() + testDispatcher),
            ioDispatcher = testDispatcher,
        ) {
            override suspend fun executeFfmpeg(args: List<String>): FFmpegResult {
                lastFfmpegArgs = args
                val inputIndex = args.indexOf("-i")
                if (inputIndex >= 0 && inputIndex + 1 < args.size) {
                    val inputPath = Path(args[inputIndex + 1])
                    if (fileSystem.exists(inputPath)) {
                        lastInputPlaylistContent = fileSystem.read(inputPath) {
                            readByteArray().decodeToString()
                        }
                    }
                }

                if (forceFfmpegFailure) {
                    return FFmpegResult(exitCode = 1)
                }

                val outputPath = Path(args.last())
                if (lastInputPlaylistContent.orEmpty().contains("#EXT-X-KEY:")) {
                    writeBytes(
                        outputPath,
                        byteArrayOf(
                            0x00, 0x00, 0x00, 0x18,
                            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
                            'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
                        ) + ByteArray(16),
                    )
                } else {
                    val inputPath = Path(args[inputIndex + 1])
                    val inputDir = inputPath.parent ?: error("Missing parent dir for $inputPath")
                    fileSystem.sink(outputPath).buffered().use { out ->
                        lastInputPlaylistContent.orEmpty().lines()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() && !it.startsWith("#") }
                            .forEach { relativeSegmentPath ->
                                fileSystem.source(inputDir.resolve(relativeSegmentPath)).buffered().use { input ->
                                    input.copyTo(out)
                                }
                            }
                    }
                }
                return FFmpegResult(exitCode = 0)
            }
        }
    }

    // Helper to handle MP4 partial or full
    private fun MockRequestHandleScope.handleMp4(rangeHeader: String?): HttpResponseData {
        return if (rangeHeader != null) {
            // Parse range header (format: "bytes=start-end")
            val range = rangeHeader.removePrefix("bytes=").split("-")
            val start = range[0].toLong()
            val end = if (range[1].isNotEmpty()) range[1].toLong() else MP4_FILE_SIZE - 1
            val length = end - start + 1

            val headers = headersOf(
                HttpHeaders.ContentType to listOf("video/mp4"),
                HttpHeaders.ContentRange to listOf("bytes $start-$end/$MP4_FILE_SIZE"),
                HttpHeaders.ContentLength to listOf("$length"),
            )

            respond(
                content = ByteArray(length.toInt()) { (it + start).toByte() },
                status = HttpStatusCode.PartialContent,
                headers = headers,
            )
        } else {
            // Full file response
            val headers = headersOf(
                HttpHeaders.ContentType to listOf("video/mp4"),
                HttpHeaders.ContentLength to listOf("$MP4_FILE_SIZE"),
            )

            respond(
                content = ByteArray(MP4_FILE_SIZE.toInt()) { it.toByte() },
                status = HttpStatusCode.OK,
                headers = headers,
            )
        }
    }

    // Helper to handle MKV partial or full
    private fun MockRequestHandleScope.handleMkv(rangeHeader: String?): HttpResponseData {
        return if (rangeHeader != null) {
            // Parse range header (format: "bytes=start-end")
            val range = rangeHeader.removePrefix("bytes=").split("-")
            val start = range[0].toLong()
            val end = if (range[1].isNotEmpty()) range[1].toLong() else MKV_FILE_SIZE - 1
            val length = end - start + 1

            val headers = headersOf(
                HttpHeaders.ContentType to listOf("video/x-matroska"),
                HttpHeaders.ContentRange to listOf("bytes $start-$end/$MKV_FILE_SIZE"),
                HttpHeaders.ContentLength to listOf("$length"),
            )

            respond(
                content = ByteArray(length.toInt()) { (it + start).toByte() },
                status = HttpStatusCode.PartialContent,
                headers = headers,
            )
        } else {
            // Full file response
            val headers = headersOf(
                HttpHeaders.ContentType to listOf("video/x-matroska"),
                HttpHeaders.ContentLength to listOf("$MKV_FILE_SIZE"),
            )

            respond(
                content = ByteArray(MKV_FILE_SIZE.toInt()) { it.toByte() },
                status = HttpStatusCode.OK,
                headers = headers,
            )
        }
    }

    @AfterTest
    fun cleanup() {
        runBlocking {
            downloader.close()
            mockClient.close()
            if (fileSystem.exists(Path(tempDir))) {
                fileSystem.deleteRecursively(Path(tempDir))
            }
        }
    }

    // ----------------------------------------------------
    // Basic functionality tests
    // ----------------------------------------------------

    @Test
    fun `download - should complete successfully`() = testScope.runTest {
        val downloadId = downloader.downloadWithId(
            url = "https://example.com/master.m3u8",
            downloadId = DownloadId("output"),
        )?.downloadId
        assertNotNull(downloadId)
        // Wait for actual completion
        downloader.joinDownload(downloadId)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.COMPLETED, state.status)
        assertTrue(fileSystem.exists(Path("$tempDir/output.mp4")))

        // Merged segments directory should be cleaned
        assertFalse(fileSystem.exists(Path("$tempDir/segments_$downloadId")))

        // New: check final file size (segment1 + segment2 + segment3 => 1024 + 2048 + 3072 = 6144)
        val outputFileSize = fileSystem.metadata(Path("$tempDir/output.mp4")).size
        assertEquals(1024 + 2048 + 3072, outputFileSize, "M3U8 final output file size mismatch.")
        assertTrue(lastFfmpegArgs?.contains("-allowed_extensions") == true)
        assertTrue(lastFfmpegArgs?.contains("-protocol_whitelist") == true)
        assertTrue(lastInputPlaylistContent?.contains("0.ts") == true)
    }

    @Test
    fun `download - should merge AES-128 encrypted HLS`() = testScope.runTest {
        val downloadId = downloader.downloadWithId(
            url = "https://example.com/encrypted.m3u8",
            downloadId = DownloadId("encrypted-output"),
        )?.downloadId
        assertNotNull(downloadId)

        downloader.joinDownload(downloadId)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.COMPLETED, state.status)

        val outputPath = Path("$tempDir/encrypted-output.mp4")
        assertTrue(fileSystem.exists(outputPath), "Expected decrypted HLS output to exist")
        assertFalse(
            fileSystem.exists(Path("$tempDir/segments_$downloadId")),
            "Merged encrypted cache dir should be removed",
        )
        assertEquals(1, attempts["https://example.com/key.bin"], "Expected key to be downloaded exactly once")
        assertTrue(lastInputPlaylistContent?.contains("URI=\"keys/") == true)
        assertFalse(lastInputPlaylistContent?.contains("https://example.com/key.bin") == true)
        assertFalse(lastInputPlaylistContent?.contains("https://example.com/encrypted-segment0.ts") == true)

        val header = fileSystem.read(outputPath) { readByteArray(8) }
        assertEquals("ftyp", header.decodeToString(startIndex = 4, endIndex = 8))
    }

    @Test
    fun `download - should preserve upstream playlist metadata in local HLS input`() = testScope.runTest {
        val downloadId = downloader.downloadWithId(
            url = "https://example.com/tagged.m3u8",
            downloadId = DownloadId("tagged-output"),
        )?.downloadId
        assertNotNull(downloadId)

        downloader.joinDownload(downloadId)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.COMPLETED, state.status)
        assertEquals(lastInputPlaylistContent?.contains("#EXT-X-PLAYLIST-TYPE:VOD"), true)
        assertEquals(lastInputPlaylistContent?.contains("#EXT-X-INDEPENDENT-SEGMENTS"), true)
        assertEquals(lastInputPlaylistContent?.contains("#EXT-X-MEDIA-SEQUENCE:10"), true)
        assertEquals(lastInputPlaylistContent?.contains("10.ts"), true)
        assertNotEquals(lastInputPlaylistContent?.contains("segment1.ts"), true)
    }

    @Test
    fun `download - ffmpeg failure should end in FAILED state and keep cache`() = testScope.runTest {
        forceFfmpegFailure = true

        val downloadId = downloader.downloadWithId(
            url = "https://example.com/master.m3u8",
            downloadId = DownloadId("ffmpeg-failure"),
        )?.downloadId
        assertNotNull(downloadId)

        downloader.joinDownload(downloadId)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.FAILED, state.status)
        assertTrue(fileSystem.exists(Path("$tempDir/segments_$downloadId")))
        assertFalse(fileSystem.exists(Path("$tempDir/ffmpeg-failure.mp4")))
    }

    @Test
    fun `downloadWithId - should use provided ID`() = testScope.runTest {
        val customId = DownloadId("custom-test-id")
        downloader.downloadWithId(
            downloadId = customId,
            url = "https://example.com/master.m3u8",
        )
        downloader.joinDownload(customId)

        val state = downloader.getState(customId)
        assertNotNull(state)
        assertEquals(customId, state.downloadId)
        assertEquals(DownloadStatus.COMPLETED, state.status)
    }

    @Test
    fun `downloadWithId - dont start if completed`() = testScope.runTest {
        val customId = DownloadId("custom-test-id")
        downloader.downloadWithId(
            downloadId = customId,
            url = "https://example.com/master.m3u8",
        )
        downloader.joinDownload(customId)

        check(downloader.getState(customId)?.status == DownloadStatus.COMPLETED)

        downloader.downloadWithId(
            downloadId = customId,
            url = "https://example.com/master.m3u8",
        )
        val state = downloader.getState(customId)
        assertNotNull(state)
        assertEquals(customId, state.downloadId)
        assertEquals(DownloadStatus.COMPLETED, state.status)
    }

    @Test
    fun `pause - should pause download and save state`() = testScope.runTest {
        val downloadId = downloader.download(
            url = "https://example.com/master.m3u8",
        )

        val result = downloader.pause(downloadId)
        assertTrue(result)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.PAUSED, state.status)
        assertFalse(downloadId in downloader.getActiveDownloadIds())
    }

    @Test
    fun `resume - should continue from paused state`() = testScope.runTest {
        val downloadId = downloader.download(
            url = "https://example.com/master.m3u8",
        )
        downloader.pause(downloadId)

        // Resume
        val resumed = downloader.resume(downloadId)
        assertTrue(resumed)

        // Wait until finished
        downloader.joinDownload(downloadId)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.COMPLETED, state.status)
    }

    @Test
    fun `cancel - should stop download and mark canceled`() = testScope.runTest {
        val downloadId = DownloadId("cancellable")
        downloader.downloadWithId(
            url = "https://example.com/unstable-playlist1.m3u8",
            downloadId = downloadId,
        )

        val result = downloader.cancel(downloadId)
        assertTrue(result)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.CANCELED, state.status)
        assertFalse(downloadId in downloader.getActiveDownloadIds())
        // Temporary segment directory should NOT be removed
        assertTrue(fileSystem.exists(Path("$tempDir/segments_$downloadId")))
    }

    @Test
    fun `remove - should delete completed output file and state`() = testScope.runTest {
        val downloadId = DownloadId("removable-complete")
        downloader.downloadWithId(
            url = "https://example.com/master.m3u8",
            downloadId = downloadId,
        )
        downloader.joinDownload(downloadId)

        assertTrue(fileSystem.exists(Path("$tempDir/removable-complete.mp4")))
        assertEquals(DownloadStatus.COMPLETED, downloader.getState(downloadId)?.status)

        val removed = downloader.remove(downloadId)

        assertTrue(removed)
        assertFalse(fileSystem.exists(Path("$tempDir/removable-complete.mp4")))
        assertNull(downloader.getState(downloadId))
    }

    @Test
    fun `remove - should delete active cache dir and state`() = testScope.runTest {
        val downloadId = DownloadId("removable-active")
        downloader.downloadWithId(
            url = "https://example.com/unstable-playlist1.m3u8",
            downloadId = downloadId,
        )

        assertTrue(fileSystem.exists(Path("$tempDir/segments_$downloadId")))

        val removed = downloader.remove(downloadId)

        assertTrue(removed)
        assertFalse(fileSystem.exists(Path("$tempDir/segments_$downloadId")))
        assertNull(downloader.getState(downloadId))
    }

    // ----------------------------------------------------
    // Progress reporting
    // ----------------------------------------------------

    @Test
    fun `progressFlow - should emit progress updates`() = testScope.runTest {
        val progressUpdates = mutableListOf<DownloadProgress>()
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            downloader.progressFlow.collect { progressUpdates.add(it) }
        }

        val downloadId = downloader.download(
            url = "https://example.com/master.m3u8",
        )
        downloader.joinDownload(downloadId)

        // Cancel flow collection
        collectJob.cancel()

        assertTrue(progressUpdates.isNotEmpty())
        assertEquals(downloadId, progressUpdates.first().downloadId)
        assertTrue(progressUpdates.any { it.status == DownloadStatus.INITIALIZING })
        assertEquals(DownloadStatus.COMPLETED, progressUpdates.last().status)

        val downloadingUpdates = progressUpdates.filter { it.status == DownloadStatus.DOWNLOADING }
        assertTrue(downloadingUpdates.isNotEmpty())
        // Check for increasing segment counts (if multiple updates)
        if (downloadingUpdates.size > 1) {
            val segments = downloadingUpdates.map { it.downloadedSegments }
            // consecutive segments should be non-decreasing
            assertTrue(segments.zipWithNext { a, b -> b >= a }.all { it })
        }
    }

    @Test
    fun `getProgressFlow - should provide flow for a single download`() = testScope.runTest {
        val downloadId = downloader.download(
            url = "https://example.com/master.m3u8",
        )

        downloader.getProgressFlow(downloadId).test {
            // First emission
            val first = awaitItem()
            assertEquals(downloadId, first.downloadId)

            downloader.joinDownload(downloadId)
            // The last item should be COMPLETED
            val last = expectMostRecentItem()
            assertEquals(DownloadStatus.COMPLETED, last.status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ----------------------------------------------------
    // Error handling
    // ----------------------------------------------------

    @Test
    fun `download - 404 error should end in FAILED state`() = testScope.runTest {
        val downloadId = downloader.download(
            url = "https://example.com/error.m3u8",
        )
        downloader.joinDownload(downloadId)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.FAILED, state.status, "Expected FAILED status for 404 HTTP error")
        assertNotNull(state.error, "Expected an error object in the state")
    }

    @Test
    fun `download - timeouts should end in FAILED state`() = testScope.runTest {
        // Short timeouts
        val options = DownloadOptions(
            connectTimeoutMs = 500,
            readTimeoutMs = 500,
        )
        val downloadId = downloader.download(
            url = "https://example.com/timeout.m3u8",
            options = options,
        )
        downloader.joinDownload(downloadId)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.FAILED, state.status)
        assertNotNull(state.error)
    }

    @Test
    fun `download - missing segments should end in FAILED state`() = testScope.runTest {
        val downloadId = downloader.download(
            url = "https://example.com/bad-segments.m3u8",
        )
        downloader.joinDownload(downloadId)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.FAILED, state.status)
        assertNotNull(state.error)
    }

    // ----------------------------------------------------
    // Regular media file tests (MP4, MKV)
    // ----------------------------------------------------

    @Test
    fun `download - mp4 file should complete successfully`() = testScope.runTest {
        val downloadId = downloader.downloadWithId(
            url = "https://example.com/sample.mp4",
            downloadId = DownloadId("sample"),
        )?.downloadId
        assertNotNull(downloadId)
        // Wait for actual completion
        downloader.joinDownload(downloadId)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.COMPLETED, state.status)
        assertTrue(fileSystem.exists(Path("$tempDir/sample.mp4")))

        // Merged segments directory should be cleaned
        assertFalse(fileSystem.exists(Path("$tempDir/segments_$downloadId")))

        // New: check final file size and partial content
        val fileSize = fileSystem.metadata(Path("$tempDir/sample.mp4")).size
        assertEquals(MP4_FILE_SIZE, fileSize, "MP4 file size mismatch.")

        fileSystem.read(Path("$tempDir/sample.mp4")) {
            checkByteMatchSampleMp4(this)
        }
    }

    private fun checkByteMatchSampleMp4(source: Source, expectedSize: Long = MP4_FILE_SIZE) {
        assertTrue(!source.exhausted(), "Source should not be exhausted at the beginning.")
        var position = 0
        val chunk = ByteArray(4096) // choose a buffer size
        while (true) {
            val readCount = source.readAvailable(chunk)
            if (readCount == -1) {
                break
            }
            if (readCount > 0) {
                for (i in 0 until readCount) {
                    val byte = chunk[i]
                    assertEquals(
                        position.toByte(),
                        byte,
                        "Byte mismatch at position $position",
                    )
                    position++
                }
            }
            if (source.exhausted()) {
                break
            }
        }
        assertEquals(expectedSize, position.toLong(), "Total byte count mismatch.")
    }

    @Test
    fun `download - mkv file should complete successfully`() = testScope.runTest {
        val downloadId = downloader.downloadWithId(
            url = "https://example.com/sample.mkv",
            downloadId = DownloadId("sample-mkv"),
        )?.downloadId
        assertNotNull(downloadId)
        // Wait for actual completion
        downloader.joinDownload(downloadId)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.COMPLETED, state.status)
        assertTrue(fileSystem.exists(Path("$tempDir/sample-mkv.mkv")))

        // Merged segments directory should be cleaned
        assertFalse(fileSystem.exists(Path("$tempDir/segments_$downloadId")))

        // New: check final file size and partial content
        val fileSize = fileSystem.metadata(Path("$tempDir/sample-mkv.mkv")).size
        assertEquals(MKV_FILE_SIZE, fileSize, "MKV file size mismatch.")

        fileSystem.read(Path("$tempDir/sample-mkv.mkv")) {
            checkByteMatchSampleMp4(this, expectedSize = MKV_FILE_SIZE)
        }
    }


    @Test
    fun `download - mp4 file with error should end in FAILED state`() = testScope.runTest {
        val downloadId = downloader.download(
            url = "https://example.com/error.mp4",
        )
        downloader.joinDownload(downloadId)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.FAILED, state.status, "Expected FAILED status for 404 HTTP error")
        assertNotNull(state.error, "Expected an error object in the state")
    }

    @Test
    fun `download - mkv file with error should end in FAILED state`() = testScope.runTest {
        val downloadId = downloader.download(
            url = "https://example.com/error.mkv",
        )
        downloader.joinDownload(downloadId)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.FAILED, state.status, "Expected FAILED status for 404 HTTP error")
        assertNotNull(state.error, "Expected an error object in the state")
    }

    @Test
    fun `pause and resume - mp4 file should continue from paused state`() = testScope.runTest {
        val downloadId = downloader.downloadWithId(
            url = "https://example.com/sample.mp4",
            downloadId = DownloadId("resumable"),
        )?.downloadId
        assertNotNull(downloadId)
        downloader.pause(downloadId)

        // Resume
        val resumed = downloader.resume(downloadId)
        assertTrue(resumed)

        // Wait until finished
        downloader.joinDownload(downloadId)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.COMPLETED, state.status)
        assertTrue(fileSystem.exists(Path("$tempDir/resumable.mp4")))
    }

    @Test
    fun `cancel - mp4 file should stop download and mark canceled`() = testScope.runTest {
        val downloadId = downloader.download(
            url = "https://example.com/sample.mp4",
        )
        val result = downloader.cancel(downloadId)
        assertTrue(result)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.CANCELED, state.status)
        assertFalse(downloadId in downloader.getActiveDownloadIds())
    }

    @Test
    fun `progressFlow - mp4 file should emit progress updates`() = testScope.runTest {
        val progressUpdates = mutableListOf<DownloadProgress>()
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            downloader.progressFlow.collect { progressUpdates.add(it) }
        }

        val downloadId = downloader.download(
            url = "https://example.com/sample.mp4",
        )
        downloader.joinDownload(downloadId)

        // Cancel flow collection
        collectJob.cancel()

        assertTrue(progressUpdates.isNotEmpty())
        assertEquals(downloadId, progressUpdates.first().downloadId)
        assertTrue(progressUpdates.any { it.status == DownloadStatus.INITIALIZING })
        assertEquals(DownloadStatus.COMPLETED, progressUpdates.last().status)

        val downloadingUpdates = progressUpdates.filter { it.status == DownloadStatus.DOWNLOADING }
        assertTrue(downloadingUpdates.isNotEmpty())
    }

    @Test
    fun `download - server without range support should still complete successfully`() = testScope.runTest {
        val downloadId = downloader.downloadWithId(
            url = "https://example.com/no-range-support.mp4",
            downloadId = DownloadId("no-range-support"),
        )?.downloadId
        assertNotNull(downloadId)
        // Wait for actual completion
        downloader.joinDownload(downloadId)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.COMPLETED, state.status)
        assertTrue(fileSystem.exists(Path("$tempDir/no-range-support.mp4")))

        // The download should complete even without range support
        // New: check final file size (no partial content check to keep it simple)
        val fileSize = fileSystem.metadata(Path("$tempDir/no-range-support.mp4")).size
        assertEquals(MP4_FILE_SIZE, fileSize, "File size mismatch for non-range-support mp4.")
    }

    @Test
    fun `download - should create multiple segments for mp4 file`() = testScope.runTest {
        // Set a small max concurrent segments value to ensure multiple segments are created
        val options = DownloadOptions(maxConcurrentSegments = 3)

        val downloadId = DownloadId("multi-segment")
        downloader.downloadWithId(
            url = "https://example.com/sample.mp4",
            downloadId = downloadId,
            options = options,
        )

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertTrue(
            state.segments.size >= 2,
            "Expected at least 2 segments to be created, but got ${state.segments.size}",
        )

        // Complete the download
        downloader.joinDownload(downloadId)

        val finalState = downloader.getState(downloadId)
        assertNotNull(finalState)
        assertEquals(DownloadStatus.COMPLETED, finalState.status)
        assertTrue(fileSystem.exists(Path("$tempDir/multi-segment.mp4")))

        // New: check final file size and partial content
        val fileSize = fileSystem.metadata(Path("$tempDir/multi-segment.mp4")).size
        assertEquals(MP4_FILE_SIZE, fileSize, "Multi-segment MP4 file size mismatch.")

        fileSystem.read(Path("$tempDir/multi-segment.mp4")) {
            checkByteMatchSampleMp4(this)
        }
    }

    // ----------------------------------------------------
    // Multiple downloads
    // ----------------------------------------------------

    @Test
    fun `multiple downloads - should complete concurrently`() = testScope.runTest {
        val id1 = downloader.download(
            url = "https://example.com/master.m3u8",
        )
        val id2 = downloader.download(
            url = "https://example.com/master.m3u8",
        )

        // Wait for both
        downloader.joinDownload(id1)
        downloader.joinDownload(id2)

        assertEquals(DownloadStatus.COMPLETED, downloader.getState(id1)?.status)
        assertEquals(DownloadStatus.COMPLETED, downloader.getState(id2)?.status)
    }

    @Test
    fun `pauseAll - should pause all downloads`() = testScope.runTest {
        val id1 = downloader.download(
            url = "https://example.com/master.m3u8",
        )
        val id2 = downloader.download(
            url = "https://example.com/master.m3u8",
        )

        assertEquals(2, downloader.getActiveDownloadIds().size)
        val paused = downloader.pauseAll()

        assertEquals(2, paused.size)
        assertTrue(id1 in paused)
        assertTrue(id2 in paused)
        assertTrue(downloader.getActiveDownloadIds().isEmpty())

        assertEquals(DownloadStatus.PAUSED, downloader.getState(id1)?.status)
        assertEquals(DownloadStatus.PAUSED, downloader.getState(id2)?.status)
    }

    @Test
    fun `cancelAll - should cancel all downloads`() = testScope.runTest {
        val id1 = downloader.download(
            url = "https://example.com/master.m3u8",
        )
        val id2 = downloader.download(
            url = "https://example.com/master.m3u8",
        )
        assertEquals(2, downloader.getActiveDownloadIds().size)

        downloader.cancelAll()

        assertTrue(downloader.getActiveDownloadIds().isEmpty())

        assertEquals(DownloadStatus.CANCELED, downloader.getState(id1)?.status)
        assertEquals(DownloadStatus.CANCELED, downloader.getState(id2)?.status)
    }

    @Test
    fun `close - should clean up resources and cancel active jobs`() = testScope.runTest {
        val downloadId = downloader.download(
            url = "https://example.com/master.m3u8",
        )
        // Let it run briefly

        // Closing should cancel all active downloads
        downloader.closeSuspend()
        // After close, no active downloads
        assertEquals(0, downloader.getActiveDownloadIds().size)

        // If you want to test what happens if we try to start a new one:
        val newId = downloader.download(
            url = "https://example.com/master.m3u8",
        )
        // Because the scope is canceled, that job won't actually proceed.
        downloader.joinDownload(newId)

        val newState = downloader.getState(newId)
        assertNotNull(newState)
        println("New job state after calling download on a closed downloader => $newState")
    }

    // ----------------------------------------------------
    // NEW TESTS for "retry segment creation" scenario
    // ----------------------------------------------------

    @Test
    fun `resume - segment creation fails first time - success second time`() = testScope.runTest {
        // 1) Download (which will fail on first attempt because of internal server error).
        val downloadId = DownloadId("unstable1")
        downloader.downloadWithId(
            url = "https://example.com/unstable-playlist1.m3u8",
            downloadId = downloadId,
        )
        downloader.joinDownload(downloadId)

        // Expect FAILED
        val failedState = downloader.getState(downloadId)
        assertNotNull(failedState)
        assertEquals(DownloadStatus.FAILED, failedState.status, "Expected first attempt to fail")

        // 2) Resume => it should attempt segment creation again => now returns valid playlist => should succeed
        val resumed = downloader.resume(downloadId)
        assertTrue(resumed, "resume() should return true from a FAILED state")
        downloader.joinDownload(downloadId)

        val finalState = downloader.getState(downloadId)
        assertNotNull(finalState)
        assertEquals(DownloadStatus.COMPLETED, finalState.status, "Expected second attempt to succeed")
        assertTrue(fileSystem.exists(Path("$tempDir/unstable1.mp4")), "Expected final file to exist")
    }

    @Test
    fun `resume - segment creation fails first time - fails second time then remain FAILED`() = testScope.runTest {
        // 1) Download => always fails
        val downloadId = downloader.download(
            url = "https://example.com/unstable-playlist2.m3u8",
        )
        downloader.joinDownload(downloadId)

        // Expect FAILED
        val failedState = downloader.getState(downloadId)
        assertNotNull(failedState)
        assertEquals(DownloadStatus.FAILED, failedState.status, "Expected first attempt to fail")

        // 2) Resume => creation fails again => remain FAILED
        val resumed = downloader.resume(downloadId)
        assertFalse(resumed)
        downloader.joinDownload(downloadId)

        val finalState = downloader.getState(downloadId)
        assertNotNull(finalState)
        assertEquals(DownloadStatus.FAILED, finalState.status, "Expected to remain FAILED after second fail")
        assertFalse(fileSystem.exists(Path("$tempDir/unstable2.mp4")), "File should not exist after repeated failures")
    }

    /**
     * This test references "unstable-segments.m3u8" which has:
     *  - unstable-segment1.ts => fails the first time, then succeeds
     *  - unstable-segment2.ts => always fails
     *
     * We use a custom [DownloadOptions] with some small [maxRetriesPerSegment].
     * We verify that even though segment1 recovers, the entire download eventually fails
     * because segment2 never succeeds (all retries will fail).
     */
    @Test
    fun `download - segment always fails - marks as FAILED after max retries`() = testScope.runTest {
        // We'll set maxRetriesPerSegment to 2 => each segment can fail up to 2 times.
        val options = DownloadOptions(
            maxConcurrentSegments = 2,
            maxRetriesPerSegment = 2,
            baseRetryDelayMillis = 10, // shorten for test
        )

        val downloadId = downloader.download(
            url = "https://example.com/unstable-segments.m3u8",
            options = options,
        )
        downloader.joinDownload(downloadId)

        val state = downloader.getState(downloadId)
        assertNotNull(state, "Expected to find a state for $downloadId")
        assertEquals(DownloadStatus.FAILED, state.status, "Expected the entire download to fail in the end.")
        assertNotNull(state.error, "Expected error details on final state")
    }

    /**
     * If we remove the second always-failing segment from the playlist, we can test
     * that a single segment which fails once but succeeds on second attempt *does not*
     * break the entire download. This test uses a custom playlist with only "unstable-segment1.ts".
     *
     * We show it inline for clarity.
     */
    @Test
    fun `download - partial segment failure - recovers on second attempt`() = testScope.runTest {
        // Single-segment playlist => only "unstable-segment1.ts"
        val singleSegmentPlaylist = """
            #EXTM3U
            #EXT-X-TARGETDURATION:5
            #EXT-X-MEDIA-SEQUENCE:0
            #EXTINF:4.0,
            https://example.com/unstable-segment1.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        // We will trick the engine by storing the content into the attempts map:
        // We can just handle it in-place: "https://example.com/unstable-single.m3u8".
        attempts["https://example.com/unstable-single.m3u8"] = 0  // reset attempts

        // Register the single-segment playlist as well
        val originalHandler = (mockClient.engine as MockEngine).config.requestHandlers.first()
        (mockClient.engine as MockEngine).config.requestHandlers.clear()
        (mockClient.engine as MockEngine).config.addHandler { request ->
            val urlString = request.url.toString()
            val currentAttempt = attempts.getValue(urlString)
            attempts[urlString] = currentAttempt + 1

            when (urlString) {
                "https://example.com/unstable-single.m3u8" -> {
                    respond(
                        content = singleSegmentPlaylist,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.apple.mpegurl"),
                    )
                }

                "https://example.com/unstable-segment1.ts" -> {
                    // first attempt => fail, subsequent => success
                    if (currentAttempt == 0) {
                        respond("Internal server error", HttpStatusCode.InternalServerError)
                    } else {
                        val bytes = ByteArray(512) { it.toByte() }
                        respond(
                            content = bytes,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "video/mp2t"),
                        )
                    }
                }

                else -> {
                    // fall back to original handler for other requests (if any)
                    originalHandler.invoke(this, request)
                }
            }
        }

        val options = DownloadOptions(
            maxConcurrentSegments = 1,
            maxRetriesPerSegment = 2,
            baseRetryDelayMillis = 10, // short delay for test
        )
        val downloadId = downloader.downloadWithId(
            url = "https://example.com/unstable-single.m3u8",
            downloadId = DownloadId("unstable-single"),
            options = options,
        )?.downloadId
        assertNotNull(downloadId)

        downloader.joinDownload(downloadId)

        // Final state => should be COMPLETED since the single segment recovers on 2nd attempt
        val finalState = downloader.getState(downloadId)
        assertNotNull(finalState)
        assertEquals(DownloadStatus.COMPLETED, finalState.status, "Expected success after segment eventually recovers")

        // Check final file's existence and size
        assertTrue(fileSystem.exists(Path("$tempDir/unstable-single.mp4")), "Output file should exist")
        val size = fileSystem.metadata(Path("$tempDir/unstable-single.mp4")).size
        assertEquals(512, size, "File should contain the final recovered segment")
    }

    companion object {
        private const val MASTER_PLAYLIST = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-STREAM-INF:BANDWIDTH=1400000,RESOLUTION=1280x720
            playlist.m3u8
        """

        private const val MEDIA_PLAYLIST = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:5
            #EXT-X-MEDIA-SEQUENCE:0

            #EXTINF:4.0,
            segment1.ts
            #EXTINF:4.0,
            segment2.ts
            #EXTINF:4.0,
            segment3.ts
            #EXT-X-ENDLIST
        """

        private const val ENCRYPTED_MEDIA_PLAYLIST = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:2
            #EXT-X-MEDIA-SEQUENCE:0
            #EXT-X-KEY:METHOD=AES-128,URI="https://example.com/key.bin",IV=0x0102030405060708090A0B0C0D0E0F10

            #EXTINF:1.0,
            https://example.com/encrypted-segment0.ts
            #EXT-X-ENDLIST
        """

        private const val TAGGED_MEDIA_PLAYLIST = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-TARGETDURATION:5
            #EXT-X-MEDIA-SEQUENCE:10
            #EXT-X-PLAYLIST-TYPE:VOD
            #EXT-X-INDEPENDENT-SEGMENTS

            #EXTINF:4.0,
            segment1.ts
            #EXTINF:4.0,
            segment2.ts
            #EXT-X-ENDLIST
        """

        private val HLS_ENCRYPTION_KEY = byteArrayOf(
            0x00, 0x11, 0x22, 0x33,
            0x44, 0x55, 0x66, 0x77,
            0x88.toByte(), 0x99.toByte(), 0xAA.toByte(), 0xBB.toByte(),
            0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte(),
        )

        private val ENCRYPTED_TS_SEGMENT_BASE64 = """
            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
        """.trimIndent().replace("\n", "")

        // ------------------------------------------------------------
        // NEW: references 2 segments:
        //  - unstable-segment1.ts => fails first time, then success
        //  - unstable-segment2.ts => always fails
        // ------------------------------------------------------------
        private const val UNSTABLE_SEGMENTS_PLAYLIST = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:5
            #EXT-X-MEDIA-SEQUENCE:0

            #EXTINF:4.0,
            https://example.com/unstable-segment1.ts
            #EXTINF:4.0,
            https://example.com/unstable-segment2.ts

            #EXT-X-ENDLIST
        """

        // File sizes for testing regular media files
        private const val MP4_FILE_SIZE = 10 * 1024 * 1024L // 10MB
        private const val MKV_FILE_SIZE = 15 * 1024 * 1024L // 15MB
    }

    private class TestClock(private val scheduler: TestCoroutineScheduler) : Clock {
        override fun now(): Instant {
            return Instant.fromEpochMilliseconds(scheduler.currentTime)
        }
    }
}

private fun FileSystem.metadata(path: Path): FileMetadata = metadataOrNull(path)!!

private inline fun <R> FileSystem.read(path: Path, function: Source.() -> R): R {
    return source(path).buffered().use { it.function() }
}

private fun writeBytes(path: Path, data: ByteArray) {
    SystemFileSystem.sink(path).buffered().use { sink ->
        sink.write(data, startIndex = 0, endIndex = data.size)
    }
}
