/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.tracking.anilist

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AniListRateLimiterTest {
    @Test
    fun pluginHonorsRetryAfterFromHttp429() = runTest {
        var requests = 0
        val limiter = AniListRateLimiter(
            permits = 25,
            windowMillis = 60_000,
            nowMillis = { testScheduler.currentTime },
        )
        val client = HttpClient(MockEngine {
            requests++
            respond(
                content = "",
                status = if (requests == 1) HttpStatusCode.TooManyRequests else HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.RetryAfter, "30"),
            )
        }) {
            install(createAniListRateLimitPlugin(limiter))
        }

        try {
            client.get("https://graphql.anilist.co")
            val nextRequest = async { client.get("https://graphql.anilist.co") }
            runCurrent()
            assertFalse(nextRequest.isCompleted)
            assertEquals(1, requests)

            advanceTimeBy(30_000)
            runCurrent()
            nextRequest.await()
            assertEquals(2, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun limitsRequestsAcrossARollingWindow() = runTest {
        val limiter = AniListRateLimiter(
            permits = 2,
            windowMillis = 60_000,
            nowMillis = { testScheduler.currentTime },
        )
        limiter.acquire()
        limiter.acquire()

        val thirdRequest = async { limiter.acquire() }
        runCurrent()
        assertFalse(thirdRequest.isCompleted)

        advanceTimeBy(59_999)
        runCurrent()
        assertFalse(thirdRequest.isCompleted)

        advanceTimeBy(1)
        runCurrent()
        assertTrue(thirdRequest.isCompleted)
    }

    @Test
    fun delaysRequestsForServerRetryAfter() = runTest {
        val limiter = AniListRateLimiter(
            permits = 25,
            windowMillis = 60_000,
            nowMillis = { testScheduler.currentTime },
        )
        limiter.deferFor(30_000)

        val request = async { limiter.acquire() }
        runCurrent()
        assertFalse(request.isCompleted)

        advanceTimeBy(30_000)
        runCurrent()
        assertTrue(request.isCompleted)
    }
}
