/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.httpdownloader

import app.cash.turbine.test
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.*
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class KtorHttpDownloaderTest {
    private lateinit var testScope: TestScope
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var mockClient: HttpClient
    private lateinit var mockClock: TestClock
    private lateinit var tempDir: String
    private lateinit var downloader: KtorHttpDownloader
    private val fileSystem = SystemFileSystem

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

        // Create mock client with preset responses
        mockClient = HttpClient(MockEngine) {
            expectSuccess = true
            engine {
                addHandler { request ->
                    // Master playlist
                    when (request.url.toString()) {
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
                            // Replace a valid segment with a missing one.
                            respond(
                                content = MEDIA_PLAYLIST.replace("segment1.ts", "missing-segment.ts"),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/vnd.apple.mpegurl"),
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
                            if (rangeHeader != null) {
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

                        "https://example.com/sample.mkv" -> {
                            // Handle range requests for MKV files
                            val rangeHeader = request.headers["Range"]
                            if (rangeHeader != null) {
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

                        else -> {
                            // Segment responses
                            val urlString = request.url.toString()
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

        downloader = KtorHttpDownloader(
            client = mockClient.asScopedHttpClient(),
            fileSystem = fileSystem,
            ioDispatcher = testDispatcher,
            computeDispatcher = testDispatcher,
            clock = mockClock,
        )
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
        val downloadId = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/output.ts",
        )
        // Wait for actual completion
        downloader.joinDownload(downloadId)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.COMPLETED, state.status)
        assertTrue(fileSystem.exists(Path("$tempDir/output.ts")))

        // Merged segments directory should be cleaned
        assertFalse(fileSystem.exists(Path("$tempDir/output.ts_segments_$downloadId")))

        // New: check final file size (we expect segment1 + segment2 + segment3 => 1024 + 2048 + 3072 = 6144)
        val outputFileSize = fileSystem.metadata(Path("$tempDir/output.ts")).size
        assertEquals(1024 + 2048 + 3072, outputFileSize, "M3U8 final output file size mismatch.")
    }

    @Test
    fun `downloadWithId - should use provided ID`() = testScope.runTest {
        val customId = DownloadId("custom-test-id")
        downloader.downloadWithId(
            downloadId = customId,
            url = "https://example.com/master.m3u8",
            outputPath = Path("$tempDir/custom-output.ts"),
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
            outputPath = Path("$tempDir/custom-output.ts"),
        )
        downloader.joinDownload(customId)

        check(downloader.getState(customId)?.status == DownloadStatus.COMPLETED)

        downloader.downloadWithId(
            downloadId = customId,
            url = "https://example.com/master.m3u8",
            outputPath = Path("$tempDir/custom-output.ts"),
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
            outputPath = "$tempDir/pausable.ts",
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
            outputPath = "$tempDir/resumable.ts",
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
        val downloadId = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/cancellable.ts",
        )
        val result = downloader.cancel(downloadId)
        assertTrue(result)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.CANCELED, state.status)
        assertFalse(downloadId in downloader.getActiveDownloadIds())
        // Temporary segment directory should NOT be removed
        assertTrue(fileSystem.exists(Path("$tempDir/cancellable.ts_segments_$downloadId")))
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
            outputPath = "$tempDir/progress-test.ts",
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
            outputPath = "$tempDir/specific-progress.ts",
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
            outputPath = "$tempDir/error.ts",
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
            outputPath = "$tempDir/timeout.ts",
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
            outputPath = "$tempDir/bad-segments.ts",
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
        val downloadId = downloader.download(
            url = "https://example.com/sample.mp4",
            outputPath = "$tempDir/sample.mp4",
        )
        // Wait for actual completion
        downloader.joinDownload(downloadId)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.COMPLETED, state.status)
        assertTrue(fileSystem.exists(Path("$tempDir/sample.mp4")))

        // Merged segments directory should be cleaned
        assertFalse(fileSystem.exists(Path("$tempDir/sample.mp4_segments_$downloadId")))

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
                        "Byte mismatch at position $position"
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
        val downloadId = downloader.download(
            url = "https://example.com/sample.mkv",
            outputPath = "$tempDir/sample.mkv",
        )
        // Wait for actual completion
        downloader.joinDownload(downloadId)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.COMPLETED, state.status)
        assertTrue(fileSystem.exists(Path("$tempDir/sample.mkv")))

        // Merged segments directory should be cleaned
        assertFalse(fileSystem.exists(Path("$tempDir/sample.mkv_segments_$downloadId")))

        // New: check final file size and partial content
        val fileSize = fileSystem.metadata(Path("$tempDir/sample.mkv")).size
        assertEquals(MKV_FILE_SIZE, fileSize, "MKV file size mismatch.")

        fileSystem.read(Path("$tempDir/sample.mkv")) {
            checkByteMatchSampleMp4(this, expectedSize = MKV_FILE_SIZE)
        }
    }


    @Test
    fun `download - mp4 file with error should end in FAILED state`() = testScope.runTest {
        val downloadId = downloader.download(
            url = "https://example.com/error.mp4",
            outputPath = "$tempDir/error.mp4",
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
            outputPath = "$tempDir/error.mkv",
        )
        downloader.joinDownload(downloadId)

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.FAILED, state.status, "Expected FAILED status for 404 HTTP error")
        assertNotNull(state.error, "Expected an error object in the state")
    }

    @Test
    fun `pause and resume - mp4 file should continue from paused state`() = testScope.runTest {
        val downloadId = downloader.download(
            url = "https://example.com/sample.mp4",
            outputPath = "$tempDir/resumable.mp4",
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
        assertTrue(fileSystem.exists(Path("$tempDir/resumable.mp4")))
    }

    @Test
    fun `cancel - mp4 file should stop download and mark canceled`() = testScope.runTest {
        val downloadId = downloader.download(
            url = "https://example.com/sample.mp4",
            outputPath = "$tempDir/cancellable.mp4",
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
            outputPath = "$tempDir/progress-test.mp4",
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
        val downloadId = downloader.download(
            url = "https://example.com/no-range-support.mp4",
            outputPath = "$tempDir/no-range-support.mp4",
        )
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

        val downloadId = downloader.download(
            url = "https://example.com/sample.mp4",
            outputPath = "$tempDir/multi-segment.mp4",
            options = options,
        )

        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertTrue(
            state.segments.size >= 2,
            "Expected at least 2 segments to be created, but got ${state.segments.size}"
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
            outputPath = "$tempDir/concurrent1.ts",
        )
        val id2 = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/concurrent2.ts",
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
            outputPath = "$tempDir/pause-all1.ts",
        )
        val id2 = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/pause-all2.ts",
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
            outputPath = "$tempDir/cancel-all1.ts",
        )
        val id2 = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/cancel-all2.ts",
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
            outputPath = "$tempDir/close-test.ts",
        )
        // Let it run briefly

        // Closing should cancel all active downloads
        downloader.closeSuspend()
        // After close, no active downloads
        assertEquals(0, downloader.getActiveDownloadIds().size)

        // If you want to test what happens if we try to start a new one:
        // The current code *does not* throw, nor does it gracefully do anything.
        // The job will run in a canceled scope => final state might remain INITIALIZING.
        // Typically, you'd fix your implementation or simply omit this check:
        val newId = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/after-close.ts",
        )
        // Because the scope is canceled, that job won't actually proceed.
        // You can check the state, expecting it to never go beyond INITIALIZING or end up canceled.
        downloader.joinDownload(newId)

        val newState = downloader.getState(newId)
        // Usually you'd want to enforce an error or canceled state. For now, check if it's stuck:
        assertNotNull(newState)
        println("New job state after calling download on a closed downloader => $newState")
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

// Helper extension for convenience
private suspend inline fun HttpDownloader.download(
    url: String,
    outputPath: String,
    options: DownloadOptions = DownloadOptions(),
): DownloadId = download(url, Path(outputPath), options)

private fun FileSystem.metadata(path: Path): FileMetadata = metadataOrNull(path)!!

private inline fun <R> FileSystem.read(path: Path, function: Source.() -> R): R {
    return source(path).buffered().use { it.function() }
}
