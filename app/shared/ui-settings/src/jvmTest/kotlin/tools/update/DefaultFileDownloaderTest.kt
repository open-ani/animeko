/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.toKtPath
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A test class for [DefaultFileDownloader].
 */
class DefaultFileDownloaderTest {
    // The content we want to serve for "/file".
    private val fileContent = "Hello, this is a test file!"

    // The content for "/corrupted-file".
    private val corruptedFileContent = "Corrupted content - not matching the checksum"

    /**
     * Example test #1: test a successful download of "/file".
     */
    @Test
    fun `test successful download`() = testApplication {
        setupRouting()

        val downloader = DefaultFileDownloader(
            createClient {
                expectSuccess = true
                install(HttpTimeout)
            },
        )
        val tempDir = createTempDirectory(prefix = "file-downloader-test").toFile()

        // We'll remove any leftover file if it exists from a prior run
        val targetFile = File(tempDir, "test-file.txt").apply { delete() }

        // We pass a single URL which should succeed
        val succeeded = downloader.download(
            alternativeUrls = listOf("/file"),
            filenameProvider = { "test-file.txt" },
            saveDir = tempDir.toKtPath().inSystem,
        )

        // Basic assertion: The download should start and complete.
        assertNotNull(succeeded, "Download method should return true for a fresh download.")

        // Wait for the state to become "Succeed" or "Failed"
        val finalState = downloader.state.first { it is FileDownloaderState.Completed }
        when (finalState) {
            is FileDownloaderState.Succeed -> {
                assertTrue(targetFile.exists(), "File must exist after successful download.")
                // Check that the downloaded content is the same as the server content
                assertEquals(fileContent, targetFile.readText(), "Downloaded file contents mismatch.")
                assertEquals(true, finalState.checked)
            }

            else -> fail("Expected Succeed but got $finalState")
        }

        // Clean up
        targetFile.delete()
        tempDir.deleteRecursively()
    }

    @Test
    fun `test no checksum`() = testApplication {
        routing {
            get("/file") {
                call.respondText(fileContent, ContentType.Text.Plain)
            }
        }

        val downloader = DefaultFileDownloader(
            createClient {
                expectSuccess = true
                install(HttpTimeout)
            },
        )
        val tempDir = createTempDirectory(prefix = "file-downloader-test").toFile()

        // We'll remove any leftover file if it exists from a prior run
        val targetFile = File(tempDir, "test-file.txt").apply { delete() }

        // We pass a single URL which should succeed
        val succeeded = downloader.download(
            alternativeUrls = listOf("/file"),
            filenameProvider = { "test-file.txt" },
            saveDir = tempDir.toKtPath().inSystem,
        )

        // Basic assertion: The download should start and complete.
        assertNotNull(succeeded, "Download method should return true for a fresh download.")

        // Wait for the state to become "Succeed" or "Failed"
        val finalState = downloader.state.first { it is FileDownloaderState.Completed }
        when (finalState) {
            is FileDownloaderState.Succeed -> {
                assertTrue(targetFile.exists(), "File must exist after successful download.")
                // Check that the downloaded content is the same as the server content
                assertEquals(fileContent, targetFile.readText(), "Downloaded file contents mismatch.")
                assertEquals(false, finalState.checked)
            }

            else -> fail("Expected Succeed but got $finalState")
        }

        // Clean up
        targetFile.delete()
        tempDir.deleteRecursively()
    }

    private fun ApplicationTestBuilder.setupRouting() {
        routing {
            // Normal file with correct .sha1
            get("/file") {
                call.respondText(fileContent, ContentType.Text.Plain)
            }
            get("/file.sha1") {
                // Pre-computed SHA-1 of `fileContent`
                // For instance, use an online SHA1 tool or write a snippet to compute it:
                // echo -n "Hello, this is a test file!" | sha1sum
                // => 6963709dcf9c2be481cba0498a4900a919feab84
                call.respondText("0fc7c10d0d0193c654b2654dba75c319bcdc6edb")
            }

            // Corrupted file with mismatched .sha1
            get("/corrupted-file") {
                call.respondText(corruptedFileContent, ContentType.Text.Plain)
            }
            get("/corrupted-file.sha1") {
                // Some random SHA-1 that definitely won't match
                call.respondText("ffffffffffffffffffffffffffffffffffffffff")
            }

            // An endpoint that always fails
            get("/unavailable") {
                call.respond(HttpStatusCode.InternalServerError, "Internal Server Error")
            }
        }
    }

    /**
     * Example test #2: test an existing file with correct checksum (should skip re-download).
     */
    @Test
    fun `test skip download if file already exists and matches checksum`() = testApplication {
        setupRouting()

        val downloader = DefaultFileDownloader(
            createClient {
                expectSuccess = true
                install(HttpTimeout)
            },
        )
        val tempDir = createTempDirectory(prefix = "file-downloader-test").toFile()

        val targetFile = File(tempDir, "test-file.txt").apply {
            writeText(fileContent) // Write the correct content
        }

        // Let's ensure it won't re-download
        val succeeded = downloader.download(
            alternativeUrls = listOf("/file"),
            filenameProvider = { "test-file.txt" },
            saveDir = tempDir.toKtPath().inSystem,
        )

        // The second call to "download" should detect the correct local file
        // and skip re-download, returning true as well.
        assertNotNull(succeeded, "Should skip download for correct existing file.")

        // The final state should become "Succeed" quickly
        val finalState = downloader.state.first { it is FileDownloaderState.Completed }
        assertTrue(finalState is FileDownloaderState.Succeed)

        // No changes to the local file
        assertEquals(fileContent, targetFile.readText(), "File content changed unexpectedly.")

        // Clean up
        targetFile.delete()
        tempDir.deleteRecursively()
    }

    /**
     * Example test #3: file already exists but wrong content => should be re-downloaded.
     */
    @Test
    fun `test re-download if local file is corrupted`() = testApplication {
        setupRouting()

        val downloader = DefaultFileDownloader(
            createClient {
                expectSuccess = true
                install(HttpTimeout)
            },
        )
        val tempDir = createTempDirectory(prefix = "file-downloader-test").toFile()
        val targetFile = File(tempDir, "test-file.txt").apply {
            // Write some random content, so the SHA1 won't match
            writeText("Corrupted local content")
        }

        val succeeded = downloader.download(
            alternativeUrls = listOf("/file"),
            filenameProvider = { "test-file.txt" },
            saveDir = tempDir.toKtPath().inSystem,
        )
        assertNotNull(succeeded, "Download should succeed after re-downloading.")

        val finalState = downloader.state.first { it is FileDownloaderState.Completed }
        when (finalState) {
            is FileDownloaderState.Succeed -> {
                assertEquals(fileContent, targetFile.readText(), "File didn't get replaced with correct content.")
            }

            else -> fail("Expected Succeed but got $finalState")
        }

        // Clean up
        targetFile.delete()
        tempDir.deleteRecursively()
    }

    /**
     * Example test #4: test if the file is actually corrupted from server => should fail.
     */
    @Test
    fun `test server corrupted file fails checksum`() = testApplication {
        setupRouting()
        val downloader = DefaultFileDownloader(
            createClient {
                expectSuccess = true
                install(HttpTimeout)
            },
        )
        val tempDir = createTempDirectory(prefix = "file-downloader-test").toFile()
        val targetFile = File(tempDir, "corrupted-file.txt")

        // Provide the route which is guaranteed to fail the checksum check
        val succeeded = downloader.download(
            alternativeUrls = listOf("/corrupted-file"),
            filenameProvider = { "corrupted-file.txt" },
            saveDir = tempDir.toKtPath().inSystem,
        )

        // The call might return true only if there's a fallback that eventually succeeds,
        // but here we only provide one URL => must fail. Or it might bubble an exception.
        // In this implementation, if no fallback is found, it ends in "Failed" and returns false.
        // Depending on how you wrote your code, you might see `true` or `false` here.
        // For the posted code: it will eventually throw the last collected exception, so it's never returning normally.
        // We can verify the final state directly.
        assertNull(succeeded, "Should fail because the file's checksum won't match.")

        val finalState = downloader.state.first { it is FileDownloaderState.Completed }
        assertTrue(finalState is FileDownloaderState.Failed, "Expected final state to be Failed.")

        // The corrupted file should have been deleted
        assertTrue(!targetFile.exists(), "File should be deleted after checksum mismatch.")

        // Clean up
        targetFile.delete()
        tempDir.deleteRecursively()
    }

    /**
     * Example test #5: test progress updates.
     *
     * This test demonstrates verifying that the progress flow moves
     * from 0.0 up to ~1.0 for a normal download scenario.
     *
     * This is a bit artificial because the local test server is *very fast*.
     * We artificially throttle or just collect a couple steps in normal usage.
     */
    @Test
    fun `test progress updates`() = testApplication {
        setupRouting()
        val downloader = DefaultFileDownloader(
            createClient {
                expectSuccess = true
                install(HttpTimeout)
            },
        )
        val tempDir = createTempDirectory(prefix = "file-downloader-test").toFile()
        val targetFile = File(tempDir, "test-file-progress.txt").apply { delete() }

        // We'll collect progress from the flow. 
        // The flow emits 0.0 at the start, and if file length is known we get partial updates until 1.0
        // We'll just takeWhile < 1.0 in a separate coroutine.
        val progressValues = mutableListOf<Float>()

        coroutineScope {
            val job = launch {
                downloader.progress
                    .takeWhile { it < 1.0f }
                    .collect { progress ->
                        println(progress)
                        progressValues.add(progress)
                    }
            }

            val succeeded = downloader.download(
                alternativeUrls = listOf("/file"),
                filenameProvider = { "test-file-progress.txt" },
                saveDir = tempDir.toKtPath().inSystem,
            )

            // Wait for final state
            val finalState = downloader.state.first { it is FileDownloaderState.Completed }
            job.join()

            assertNotNull(succeeded, "Progress test: download should succeed.")

            when (finalState) {
                is FileDownloaderState.Succeed -> {
                    assertTrue(progressValues.isNotEmpty(), "We should have collected some progress updates.")
                    // The last recorded progress is typically just before 1.0,
                    // the final state is signaled with a separate mechanism.
                }

                else -> fail("Expected Succeed but got $finalState")
            }

            // Clean up
            targetFile.delete()
            tempDir.deleteRecursively()
        }
    }
}
