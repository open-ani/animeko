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
import io.github.nihildigit.pikpak.batchDelete
import io.github.nihildigit.pikpak.batchTrash
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import me.him188.ani.torrent.offline.ResolvedMedia
import me.him188.ani.utils.ktor.asScopedHttpClient
import me.him188.ani.utils.ktor.createDefaultHttpClient
import me.him188.ani.utils.ktor.registerLogging
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Hits the real PikPak service with credentials from env vars. Skipped in
 * CI / when creds absent.
 *
 * Env vars:
 *   PIKPAK_USERNAME
 *   PIKPAK_PASSWORD
 *   PIKPAK_MAGNET   optional; defaults to a well-seeded Arch Linux ISO magnet.
 *
 * Run with:
 *   ./gradlew :torrent:pikpak:desktopTest --tests '*PikPakLiveSmokeTest*' --info
 */
class PikPakLiveSmokeTest {

    @Test
    fun `resolve returns a playable stream url`() = runBlocking {
        val username = System.getenv("PIKPAK_USERNAME")
        val password = System.getenv("PIKPAK_PASSWORD")
        if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
            println("[skip] PIKPAK_USERNAME / PIKPAK_PASSWORD not set")
            return@runBlocking
        }
        val magnet = System.getenv("PIKPAK_MAGNET")?.takeIf { it.isNotBlank() }
            ?: DEFAULT_MAGNET

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val client = createDefaultHttpClient().apply { registerLogging() }
        val http = client.asScopedHttpClient()
        val credentialsFlow = MutableStateFlow(PikPakCredentials(username, password))

        val engine = PikPakOfflineDownloadEngine(
            scopedHttpClient = http,
            credentials = credentialsFlow,
            scope = scope,
            sessionStore = InMemorySessionStore(),
            pollInterval = 3.seconds,
            resolveTimeout = 3.minutes,
        )

        println("[1/2] first resolve: ${magnet.take(80)}...")
        val t0 = kotlin.time.TimeSource.Monotonic.markNow()
        val resolved: ResolvedMedia = engine.resolve(magnet)
        val d1 = t0.elapsedNow()
        println("[2/2] resolved in $d1 -> url=${resolved.streamUrl.take(120)}... fileName=${resolved.fileName} fileSize=${resolved.fileSize}")

        assertTrue(resolved.streamUrl.startsWith("http"), "streamUrl should be http(s)")

        // Second resolve in the same JVM — bearer cached + captcha cached,
        // so submit is a single HTTP round-trip.
        val t1 = kotlin.time.TimeSource.Monotonic.markNow()
        val resolved2 = engine.resolve(magnet)
        val d2 = t1.elapsedNow()
        println("[bonus] second resolve in $d2 (first was $d1)")
        assertTrue(resolved2.streamUrl.startsWith("http"))

        client.close()
        scope.coroutineContext[Job]?.cancel()
    }

    /**
     * Probe: once PikPak hands us a signed CDN URL, does that URL keep working
     * after the file is trashed / permanently deleted? Drives the choice of
     * fire-and-forget cleanup strategy. Prints each GET status so we can read
     * the result from stdout without making the test fail on any outcome.
     */
    @Test
    fun `probe - stream url survival after trash and delete`() = runBlocking {
        val username = System.getenv("PIKPAK_USERNAME")
        val password = System.getenv("PIKPAK_PASSWORD")
        if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
            println("[skip] PIKPAK_USERNAME / PIKPAK_PASSWORD not set")
            return@runBlocking
        }
        val magnet = System.getenv("PIKPAK_MAGNET")?.takeIf { it.isNotBlank() }
            ?: DEFAULT_MAGNET

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val rawClient = createDefaultHttpClient().apply { registerLogging() }
        val http = rawClient.asScopedHttpClient()
        val credentialsFlow = MutableStateFlow(PikPakCredentials(username, password))

        val engine = PikPakOfflineDownloadEngine(
            scopedHttpClient = http,
            credentials = credentialsFlow,
            scope = scope,
            sessionStore = InMemorySessionStore(),
            pollInterval = 3.seconds,
            resolveTimeout = 3.minutes,
        )

        println("[probe] resolving: ${magnet.take(80)}...")
        val resolved: ResolvedMedia = engine.resolve(magnet)
        val fileId = resolved.providerFileId
        println("[probe] resolved -> fileId=$fileId name=${resolved.fileName} size=${resolved.fileSize}")
        assertNotNull(fileId, "providerFileId should be populated by the PikPak engine")
        assertTrue(resolved.streamUrl.startsWith("http"), "streamUrl should be http(s)")

        // Build our own SDK client for direct trash/delete calls.
        val pikpakClient = PikPakClient(
            account = username,
            password = password,
            sessionStore = InMemorySessionStore(),
            httpClient = rawClient,
        )
        pikpakClient.login()

        // PikPak's CDN rejects HEAD (406). Use a ranged GET of the first byte
        // — same shape mediamp will use when it starts playback.
        suspend fun probe(label: String): Int {
            val status = runCatching {
                rawClient.get(resolved.streamUrl) {
                    expectSuccess = false
                    // PikPak's CDN is picky: it refuses `Accept: application/json`
                    // (the default from ContentNegotiation) with 406. mediamp /
                    // libvlc would never send that; mimic a generic player.
                    header("Accept", "*/*")
                    header("User-Agent", "Mozilla/5.0")
                    header("Range", "bytes=0-0")
                }.status.value
            }.getOrElse { ex ->
                println("[probe] exception: ${ex::class.simpleName}: ${ex.message}")
                -1
            }
            println("[probe] GET $label -> status=$status")
            return status
        }

        probe("fresh")

        println("[probe] batchTrash($fileId)...")
        pikpakClient.batchTrash(listOf(fileId))
        delay(2.seconds)
        probe("after-trash")

        println("[probe] batchDelete($fileId)...")
        runCatching { pikpakClient.batchDelete(listOf(fileId)) }
            .onFailure { println("[probe] batchDelete threw: ${it.message}") }
        delay(5.seconds)
        probe("after-delete")

        delay(20.seconds)
        probe("after-delete+20s")

        rawClient.close()
        scope.coroutineContext[Job]?.cancel()
    }

    companion object {
        // Arch Linux ISO — widely seeded, PikPak caches it, resolves in seconds.
        private const val DEFAULT_MAGNET =
            "magnet:?xt=urn:btih:157e0a57e1af0e1cfd46258ba6c62938c21b6ee8" +
                    "&dn=archlinux-2026.04.01-x86_64.iso"
    }
}
