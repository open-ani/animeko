/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.tracking.anilist

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.him188.ani.tracking.api.TrackingAccountState
import me.him188.ani.tracking.api.TrackingCredentialStore
import me.him188.ani.tracking.api.TrackingListEntry
import me.him188.ani.tracking.api.TrackingLoginCredentials
import me.him188.ani.tracking.api.TrackingMediaId
import me.him188.ani.tracking.api.TrackingScore
import me.him188.ani.tracking.api.TrackingStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AniListTrackingProviderTest {
    @Test
    fun `login refreshes account and provider score choices`() = runTest {
        val store = InMemoryCredentialStore(null)
        val provider = provider(store) {
            respondJson(
                """{"data":{"Viewer":{"id":123,"name":"haru","avatar":{"large":"https://img/avatar.jpg"},"mediaListOptions":{"scoreFormat":"POINT_5"}}}}""",
            )
        }

        val account = provider.login(TrackingLoginCredentials(secret = "new-token"))

        assertEquals("haru", account.displayName)
        assertTrue(provider.accountState.value is TrackingAccountState.LoggedIn)
        assertEquals(listOf("0 ★", "1 ★", "2 ★", "3 ★", "4 ★", "5 ★"), provider.scoreOptions.map { it.displayValue })
        assertEquals("new-token", store.load()?.secret)
    }

    @Test
    fun `maps search result and sends bearer token`() = runTest {
        var authorization: String? = null
        val provider = provider { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            respondJson(
                """{"data":{"Page":{"media":[{"id":1,"siteUrl":"https://anilist.co/anime/1","title":{"userPreferred":"Frieren"},"coverImage":{"large":"https://img/1.jpg"},"episodes":28,"mediaListEntry":null}]}}}""",
            )
        }

        val result = provider.search("Frieren").single()

        assertEquals("Bearer token", authorization)
        assertEquals("AniList", provider.info.displayName)
        assertEquals(TrackingMediaId("1"), result.id)
        assertEquals("https://anilist.co/anime/1", result.siteUrl)
        assertEquals(28, result.totalEpisodes)
    }

    @Test
    fun `reads and saves normalized list entry`() = runTest {
        var calls = 0
        val provider = provider {
            calls++
            if (calls == 1) {
                respondJson(
                    """{"data":{"Media":{"id":1,"title":{"userPreferred":"Frieren"},"episodes":28,"mediaListEntry":{"id":9,"mediaId":1,"status":"CURRENT","score":85,"progress":12}}}}""",
                )
            } else {
                respondJson(
                    """{"data":{"SaveMediaListEntry":{"id":9,"mediaId":1,"status":"COMPLETED","score":90,"progress":28}}}""",
                )
            }
        }

        val existing = provider.refresh(TrackingMediaId("1"))!!.listEntry!!
        assertEquals(12, existing.progress)
        assertEquals(TrackingScore(85), existing.score)

        val saved = provider.update(
            TrackingListEntry(TrackingMediaId("1"), TrackingStatus.COMPLETED, 28, TrackingScore(90)),
        )
        assertEquals(28, saved.progress)
        assertEquals(TrackingStatus.COMPLETED, saved.status)
    }

    @Test
    fun `delete resolves entry id and is idempotent when no entry exists`() = runTest {
        var calls = 0
        val provider = provider {
            calls++
            respondJson(
                """{"data":{"Media":{"id":1,"title":{"userPreferred":"Frieren"},"mediaListEntry":null}}}""",
            )
        }

        provider.delete(TrackingMediaId("1"))

        assertEquals(1, calls)
    }

    @Test
    fun `missing media is represented as null`() = runTest {
        val provider = provider { respondJson("""{"data":{"Media":null}}""") }
        assertNull(provider.refresh(TrackingMediaId("404")))
    }

    @Test
    fun `graphql errors become provider failures`() = runTest {
        val provider = provider { respondJson("""{"errors":[{"message":"Invalid token"}]}""") }
        val failure = runCatching { provider.search("Frieren") }.exceptionOrNull()
        assertTrue(failure is me.him188.ani.tracking.api.TrackingProviderException.Remote)
    }

    private fun provider(
        store: TrackingCredentialStore = InMemoryCredentialStore(),
        handler: suspend MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> HttpResponseData,
    ): AniListTrackingProvider {
        val client = HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return AniListTrackingProvider(client, store)
    }

    private fun MockRequestHandleScope.respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )
}

private class InMemoryCredentialStore(
    private var credentials: TrackingLoginCredentials? = TrackingLoginCredentials(secret = "token"),
) : TrackingCredentialStore {

    override suspend fun load() = credentials

    override suspend fun save(credentials: TrackingLoginCredentials) {
        this.credentials = credentials
    }

    override suspend fun clear() {
        credentials = null
    }
}
