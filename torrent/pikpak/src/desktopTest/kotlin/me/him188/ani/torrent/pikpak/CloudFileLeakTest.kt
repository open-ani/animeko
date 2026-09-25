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
import io.github.nihildigit.pikpak.MagnetResource
import io.github.nihildigit.pikpak.PikPakClient
import io.github.nihildigit.pikpak.RateLimiter
import io.github.nihildigit.pikpak.ResolvedFile
import io.github.nihildigit.pikpak.RetryPolicy
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.resolve
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

/**
 * A cloud object is a lease. It is deleted the moment a link has been minted
 * from it, and nothing else in the app knows its id afterwards. So every path
 * that creates one has to delete it, including the two that used not to: a read
 * that fails before it mints anything, and a shutdown landing between the mint
 * and the delete.
 *
 * Both leave an object whose timestamp is far newer than the startup sweep's
 * cutoff, so the sweep does not collect it for a day either.
 */
class CloudFileLeakTest {

    /** Serves the mint path and records what the drive was asked to do. */
    private class Drive(private val detailStatus: HttpStatusCode = HttpStatusCode.OK) {
        val created = CopyOnWriteArrayList<String>()
        val deleted = CopyOnWriteArrayList<String>()

        val http = HttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/v1/shield/captcha/init") -> json("""{"captcha_token":"CAP"}""")
                    path.endsWith("/v1/auth/signin") -> json(
                        """{"access_token":"AT","refresh_token":"RT","sub":"UID","expires_in":3600}""",
                    )

                    path.endsWith("/files:batchDelete") -> {
                        deleted += Json.parseToJsonElement((request.body as TextContent).text)
                            .jsonObject.getValue("ids").jsonArray.map { it.jsonPrimitive.content }
                        json("{}")
                    }

                    path.endsWith("/drive/v1/files") && request.method == HttpMethod.Get ->
                        if (request.url.parameters["parent_id"].isNullOrEmpty()) {
                            json("""{"files":[{"kind":"drive#folder","id":"folder","name":"Animeko-Temp"}]}""")
                        } else {
                            json("""{"files":[]}""")
                        }

                    path.endsWith("/drive/v1/files") && request.method == HttpMethod.Post -> {
                        created += "minted"
                        json(
                            """{"file":{"id":"minted","kind":"drive#file","size":"4",""" +
                                    """"phase":"PHASE_TYPE_COMPLETE"}}""",
                        )
                    }

                    // The detail lookup is the step between creating the object and minting its
                    // link. Failing it is the cheapest stand-in for every way that step can not
                    // finish: a timeout, a quota rejection, the user leaving the episode.
                    path.endsWith("/drive/v1/files/minted") -> when (detailStatus) {
                        HttpStatusCode.OK -> json(
                            """{"id":"minted","kind":"drive#file","size":"4",""" +
                                    """"links":{"application/octet-stream":{"url":"https://cdn.test/f","expire":""}}}""",
                        )

                        else -> respond("""{"error":"boom"}""", detailStatus, jsonHeaders)
                    }

                    request.url.host == "cdn.test" -> respond(
                        byteArrayOf(10, 20, 30, 40),
                        HttpStatusCode.PartialContent,
                        headersOf(
                            HttpHeaders.ContentRange to listOf("bytes 0-3/4"),
                            HttpHeaders.ContentLength to listOf("4"),
                        ),
                    )

                    else -> error("Unexpected request: ${request.method} ${request.url}")
                }
            },
        )

        val client = PikPakClient(
            account = "leak@example.com", password = "test", sessionStore = InMemorySessionStore(),
            httpClient = http, cdnHttpClient = http,
            rateLimiter = RateLimiter.unlimited(), retryPolicy = RetryPolicy.None,
        )

        fun close() {
            client.close()
            http.close()
        }
    }

    private fun CoroutineScope.cloudFile(drive: Drive, cleanupScope: CoroutineScope): CloudFile {
        val account = PikPakAccount(drive.client, this)
        return CloudFile(
            gcid = "A".repeat(40), size = 4, name = "episode.mkv", accountProvider = { account },
            connectionBudget = 1, scope = cleanupScope,
        )
    }

    /**
     * A delete is fire-and-forget on a scope of its own, and the HTTP call leaves whatever
     * dispatcher submitted it, so it has to be awaited rather than asserted on the spot.
     */
    private suspend fun Drive.awaitDeleted(id: String) {
        try {
            withTimeout(10_000) { while (!deleted.contains(id)) delay(10) }
        } catch (e: TimeoutCancellationException) {
            fail("$id was created but never deleted, and nothing else in the app knows its id")
        }
    }

    @Test
    fun `an object created for a read that never mints its link is deleted anyway`() = runBlocking {
        val drive = Drive(detailStatus = HttpStatusCode.InternalServerError)
        val cleanupScope = CoroutineScope(coroutineContext.minusKey(Job) + SupervisorJob())
        try {
            val cloud = cloudFile(drive, cleanupScope)
            assertFailsWith<Throwable> { cloud.readBytes(0, 4) }

            assertEquals(listOf("minted"), drive.created, "the read must have created an object to fail on")
            drive.awaitDeleted("minted")
        } finally {
            cleanupScope.cancel()
            drive.close()
        }
    }

    /**
     * Runs the real downloader, because what has to survive is the downloader's own shutdown:
     * close() cancels the scope playback runs on, and the delete must not be on it.
     */
    @Test
    fun `an object minted just before shutdown is still deleted`() = runBlocking {
        val drive = Drive()
        val downloader = PikPakTorrentDownloader(
            httpClient = drive.http,
            credentials = MutableStateFlow(PikPakCredentials("leak@example.com", "test")),
            sessionStore = InMemorySessionStore(),
            rootDataDirectory = SystemPaths.createTempDirectory("pikpak-leak").resolve("pikpak"),
            config = MutableStateFlow(PikPakEngineConfig()),
            parentCoroutineContext = coroutineContext,
        )
        downloader.magnetResolver = {
            MagnetResource(
                name = "leak",
                files = listOf(ResolvedFile(path = "episode.mkv", size = 4, gcid = "A".repeat(40))),
            )
        }
        try {
            val session = downloader.startDownload(downloader.fetchTorrent(MAGNET))
            val entry = session.getFiles().single() as PikPakFileEntry
            // Resolving the stream is the shortest path that mints a link, and the only step a
            // player reaches before the user can leave.
            entry.ensureCloudReady()
            assertEquals(listOf("minted"), drive.created, "the read must have minted a link")

            session.close()
            downloader.close()

            drive.awaitDeleted("minted")
        } finally {
            drive.close()
        }
    }

    private companion object {
        const val MAGNET = "magnet:?xt=urn:btih:BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"

        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

        fun MockRequestHandleScope.json(body: String) = respond(body, HttpStatusCode.OK, jsonHeaders)
    }
}
