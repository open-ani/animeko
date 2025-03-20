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
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.json.Json
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.resolve
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class KtorM3u8DownloaderTest {
    private lateinit var testScope: TestScope
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var mockClient: HttpClient
    private lateinit var mockClock: TestClock
    private lateinit var tempDir: String
    private lateinit var downloader: KtorM3u8Downloader
    private val fileSystem = SystemFileSystem

    @BeforeTest
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        testScheduler = testDispatcher.scheduler
        testScope = TestScope(testDispatcher)
        mockClock = TestClock(testScheduler)
        tempDir = SystemTemporaryDirectory.resolve("test-m3u8-downloads-${Clock.System.now().toEpochMilliseconds()}")
            .toString()

        // Create directories
        runBlocking {
            if (!fileSystem.exists(Path(tempDir))) {
                fileSystem.createDirectories(Path(tempDir))
            }

            if (!fileSystem.exists(Path("$tempDir/persistence"))) {
                fileSystem.createDirectories(Path("$tempDir/persistence"))
            }
        }

        // Create mock client with preset responses
        mockClient = HttpClient(MockEngine) {
            engine {
                // Master playlist
                addHandler { request ->
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
                            respond(
                                content = MEDIA_PLAYLIST.replace("segment1.ts", "missing-segment.ts"),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/vnd.apple.mpegurl"),
                            )
                        }

                        "https://example.com/error.m3u8" -> {
                            respond(
                                content = "Not found",
                                status = HttpStatusCode.NotFound,
                            )
                        }

                        "https://example.com/timeout.m3u8" -> {
                            delay(10.seconds) // Simulate timeout
                            respond(
                                content = "Timeout",
                                status = HttpStatusCode.OK,
                            )
                        }

                        else -> {
                            // Handle segment URLs
                            val urlString = request.url.toString()
                            if (urlString.startsWith("https://example.com/segment") && urlString.endsWith(".ts")) {
                                val segmentNum = urlString.substringAfterLast("segment").substringBefore(".ts").toInt()
                                respond(
                                    content = ByteArray(1024 * segmentNum) { it.toByte() },
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "video/mp2t"),
                                )
                            } else {
                                respond(
                                    content = "Unknown request: $urlString",
                                    status = HttpStatusCode.BadRequest,
                                )
                            }
                        }
                    }
                }
            }
        }

        downloader = KtorM3u8Downloader(
            client = mockClient,
            json = Json { ignoreUnknownKeys = true },
            ioDispatcher = testDispatcher,
            computeDispatcher = testDispatcher,
            persistenceDir = "$tempDir/persistence",
            clock = mockClock,
        )
    }

    @AfterTest
    fun cleanup() {
        runBlocking {
            downloader.close()
            mockClient.close()

            // Clean up test files
            if (fileSystem.exists(Path(tempDir))) {
                fileSystem.deleteRecursively(Path(tempDir))
            }
        }
    }

    //
    // Basic functionality tests
    //

    @Test
    fun `download - should complete successfully`() = testScope.runTest {
        // Start download
        val downloadId = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/output.ts",
        )

        // Advance time to allow downloads to complete
        advanceUntilIdle()

        // Verify final state
        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.COMPLETED, state.status)
        assertEquals(3, state.totalSegments)

        // Verify output file exists
        assertTrue(fileSystem.exists(Path("$tempDir/output.ts")))

        // Verify segments directory was cleaned up
        assertFalse(fileSystem.exists(Path("$tempDir/output.ts.segments")))
    }

    @Test
    fun `downloadWithId - should use provided ID`() = testScope.runTest {
        // Create a custom ID
        val customId = DownloadId("custom-test-id")

        // Start download with custom ID
        downloader.downloadWithId(
            downloadId = customId,
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/custom-output.ts",
        )

        // Advance time
        advanceUntilIdle()

        // Verify download was registered with custom ID
        val state = downloader.getState(customId)
        assertNotNull(state)
        assertEquals(customId, state.downloadId)
    }

    @Test
    fun `pause - should pause download and save state`() = testScope.runTest {
        // Start download
        val downloadId = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/pausable.ts",
        )

        // Let the download start but not finish
        advanceTimeBy(500.milliseconds)

        // Pause download
        val result = downloader.pause(downloadId)
        assertTrue(result)

        // Verify state is paused
        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.PAUSED, state.status)

        // Verify download is no longer active
        assertFalse(downloadId in downloader.getActiveDownloadIds())
    }

    @Test
    fun `resume - should continue from paused state`() = testScope.runTest {
        // Start and pause a download
        val downloadId = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/resumable.ts",
        )
        advanceTimeBy(500.milliseconds)
        downloader.pause(downloadId)

        // Now resume it
        val result = downloader.resume(downloadId)
        assertTrue(result)

        // Let it complete
        advanceUntilIdle()

        // Verify download completed
        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.COMPLETED, state.status)
    }

    @Test
    fun `cancel - should stop download and clean up`() = testScope.runTest {
        // Start download
        val downloadId = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/cancellable.ts",
        )

        // Let the download start but not finish
        advanceTimeBy(500.milliseconds)

        // Cancel download
        val result = downloader.cancel(downloadId)
        assertTrue(result)

        // Verify state is canceled
        val state = downloader.getState(downloadId)
        assertNotNull(state)
        assertEquals(DownloadStatus.CANCELED, state.status)

        // Verify download is no longer active
        assertFalse(downloadId in downloader.getActiveDownloadIds())

        // Verify temporary directory was cleaned up
        assertFalse(fileSystem.exists(Path("$tempDir/cancellable.ts.segments")))
    }

    //
    // Progress reporting tests
    //

    @Test
    fun `progressFlow - should emit progress updates`() = testScope.runTest {
        // Collect progress updates
        val progressUpdates = mutableListOf<DownloadProgress>()
        val collectJob = launch {
            downloader.progressFlow.collect { progressUpdates.add(it) }
        }

        // Start download
        val downloadId = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/progress-test.ts",
        )

        // Let the download complete
        advanceUntilIdle()

        // Cancel collection
        collectJob.cancel()

        // Verify we got progress updates
        assertTrue(progressUpdates.isNotEmpty())
        assertEquals(downloadId, progressUpdates.first().downloadId)

        // At least one update should show INITIALIZING
        assertTrue(progressUpdates.any { it.status == DownloadStatus.INITIALIZING })

        // The last update should show COMPLETED
        assertEquals(DownloadStatus.COMPLETED, progressUpdates.last().status)

        // Verify we see segment progress
        val downloadingUpdates = progressUpdates.filter { it.status == DownloadStatus.DOWNLOADING }
        assertTrue(downloadingUpdates.isNotEmpty())

        // Check for increasing progress if we have multiple updates
        if (downloadingUpdates.size > 1) {
            val segmentCounts = downloadingUpdates.map { it.downloadedSegments }
            assertTrue(segmentCounts.zipWithNext { a, b -> b >= a }.all { it })
        }
    }

    @Test
    fun `getProgressFlow - should provide flow for specific download`() = testScope.runTest {
        // Start download
        val downloadId = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/specific-progress.ts",
        )

        // Collect from specific progress flow
        downloader.getProgressFlow(downloadId).test {
            // Check initial state
            val initial = awaitItem()
            assertEquals(downloadId, initial.downloadId)

            // Let download run and check more updates
            advanceTimeBy(100.milliseconds)

            // Progress items can come quickly, so we check most recent rather than waiting for the next
            val progress = expectMostRecentItem()
            assertTrue(progress.status == DownloadStatus.INITIALIZING || progress.status == DownloadStatus.DOWNLOADING)

            // Let download complete
            advanceUntilIdle()
            val final = expectMostRecentItem()
            assertEquals(DownloadStatus.COMPLETED, final.status)

            // Cancel collection
            cancelAndIgnoreRemainingEvents()
        }
    }

    //
    // Error handling tests
    //

    @Test
    fun `download - should handle HTTP errors properly`() = testScope.runTest {
        // Try to download from error URL
        assertFailsWith<M3u8NetworkException> {
            downloader.download(
                url = "https://example.com/error.m3u8",
                outputPath = "$tempDir/error.ts",
            )

            advanceUntilIdle()
        }
    }

    @Test
    fun `download - should handle timeouts`() = testScope.runTest {
        // Set timeout in download options
        val options = DownloadOptions(
            connectTimeoutMs = 1000,
            readTimeoutMs = 1000,
        )

        // Try to download with timeout
        assertFailsWith<M3u8NetworkException> {
            downloader.download(
                url = "https://example.com/timeout.m3u8",
                outputPath = "$tempDir/timeout.ts",
                options = options,
            )

            advanceUntilIdle()
        }
    }

    @Test
    fun `download - should handle missing segments`() = testScope.runTest {
        // Try to download with bad segments
        assertFailsWith<M3u8DownloaderException> {
            downloader.download(
                url = "https://example.com/bad-segments.m3u8",
                outputPath = "$tempDir/bad-segments.ts",
            )

            advanceUntilIdle()
        }
    }

    //
    // State management tests
    //

    @Test
    fun `saveState - should persist download state`() = testScope.runTest {
        // Start download
        val downloadId = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/saveable.ts",
        )

        // Let it start but not finish
        advanceTimeBy(500.milliseconds)

        // Save state
        val saved = downloader.saveState(downloadId)
        assertTrue(saved)

        // Verify state file exists
        assertTrue(fileSystem.exists(Path("$tempDir/persistence/${downloadId.value}.json")))
    }

    @Test
    fun `loadSavedStates - should find all saved states`() = testScope.runTest {
        // Create and save multiple downloads
        val id1 = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/load-test1.ts",
        )

        val id2 = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/load-test2.ts",
        )

        // Advance time enough for states to be saved
        advanceTimeBy(500.milliseconds)

        // Save states
        downloader.saveAllStates()

        // Create a new downloader instance to test loading
        downloader.close()
        val newDownloader = KtorM3u8Downloader(
            client = mockClient,
            ioDispatcher = testDispatcher,
            computeDispatcher = testDispatcher,
            persistenceDir = "$tempDir/persistence",
            clock = mockClock,
        )

        // Load all states
        val savedIds = newDownloader.loadSavedStates()

        // Verify both IDs are present
        assertTrue(id1 in savedIds)
        assertTrue(id2 in savedIds)

        // Cleanup
        newDownloader.close()
    }

    @Test
    fun `hasSavedState - should check for existing state`() = testScope.runTest {
        // Start and save download
        val downloadId = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/state-check.ts",
        )

        advanceTimeBy(500.milliseconds)
        downloader.saveState(downloadId)

        // Check for existing state
        assertTrue(downloader.hasSavedState(downloadId))

        // Check for non-existent state
        assertFalse(downloader.hasSavedState(DownloadId("non-existent")))
    }

    //
    // Multiple downloads tests
    //

    @Test
    fun `multiple downloads - should handle concurrently`() = testScope.runTest {
        // Start multiple downloads
        val id1 = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/concurrent1.ts",
        )

        val id2 = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/concurrent2.ts",
        )

        // Check that both are active
        val activeIds = downloader.getActiveDownloadIds()
        assertTrue(id1 in activeIds)
        assertTrue(id2 in activeIds)

        // Let them complete
        advanceUntilIdle()

        // Verify both completed
        assertEquals(DownloadStatus.COMPLETED, downloader.getState(id1)?.status)
        assertEquals(DownloadStatus.COMPLETED, downloader.getState(id2)?.status)
    }

    @Test
    fun `pauseAll - should pause all downloads`() = testScope.runTest {
        // Start multiple downloads
        val id1 = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/pause-all1.ts",
        )

        val id2 = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/pause-all2.ts",
        )

        // Let them start but not finish
        advanceTimeBy(500.milliseconds)

        // Pause all
        val pausedIds = downloader.pauseAll()

        // Verify all were paused
        assertEquals(2, pausedIds.size)
        assertTrue(id1 in pausedIds)
        assertTrue(id2 in pausedIds)

        // Verify no active downloads
        assertTrue(downloader.getActiveDownloadIds().isEmpty())

        // Verify states are paused
        assertEquals(DownloadStatus.PAUSED, downloader.getState(id1)?.status)
        assertEquals(DownloadStatus.PAUSED, downloader.getState(id2)?.status)
    }

    @Test
    fun `cancelAll - should cancel all downloads`() = testScope.runTest {
        // Start multiple downloads
        val id1 = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/cancel-all1.ts",
        )

        val id2 = downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/cancel-all2.ts",
        )

        // Let them start but not finish
        advanceTimeBy(500.milliseconds)

        // Cancel all
        downloader.cancelAll()

        // Verify no active downloads
        assertTrue(downloader.getActiveDownloadIds().isEmpty())

        // Verify states are canceled
        assertEquals(DownloadStatus.CANCELED, downloader.getState(id1)?.status)
        assertEquals(DownloadStatus.CANCELED, downloader.getState(id2)?.status)
    }

    @Test
    fun `close - should clean up resources`() = testScope.runTest {
        // Start a download
        downloader.download(
            url = "https://example.com/master.m3u8",
            outputPath = "$tempDir/close-test.ts",
        )

        // Let it start but not finish
        advanceTimeBy(500.milliseconds)

        // Close the downloader
        downloader.close()

        // Verify no active downloads
        assertTrue(downloader.getActiveDownloadIds().isEmpty())

        // Verify the isActive flag is false (test internal state)
        assertFailsWith<M3u8DownloaderException> {
            // This should fail because the downloader is closed
            downloader.download(
                url = "https://example.com/master.m3u8",
                outputPath = "$tempDir/after-close.ts",
            )
        }
    }

    companion object {
        // Test data
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
    }

    /**
     * A test implementation of Clock that uses the TestCoroutineScheduler's currentTime.
     */
    private class TestClock(private val scheduler: TestCoroutineScheduler) : Clock {
        override fun now(): Instant {
            return Instant.fromEpochMilliseconds(scheduler.currentTime)
        }
    }
}