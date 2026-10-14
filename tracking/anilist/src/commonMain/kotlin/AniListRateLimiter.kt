/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.tracking.anilist

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

private const val MAX_REQUESTS_PER_MINUTE = 25
private const val RATE_LIMIT_WINDOW_MILLIS = 60_000L

/** A rolling-window request limiter with an optional server-provided cooldown. */
internal class AniListRateLimiter(
    private val permits: Int = MAX_REQUESTS_PER_MINUTE,
    private val windowMillis: Long = RATE_LIMIT_WINDOW_MILLIS,
    nowMillis: (() -> Long)? = null,
) {
    private val origin = TimeSource.Monotonic.markNow()
    private val nowMillis = nowMillis ?: { origin.elapsedNow().inWholeMilliseconds }
    private val mutex = Mutex()
    private val requestTimes = ArrayDeque<Long>()
    private var retryNotBeforeMillis = 0L

    init {
        require(permits > 0) { "permits must be positive" }
        require(windowMillis > 0) { "windowMillis must be positive" }
    }

    suspend fun acquire() {
        while (true) {
            val waitMillis = mutex.withLock {
                val now = nowMillis()
                while (requestTimes.firstOrNull()?.let { now - it >= windowMillis } == true) {
                    requestTimes.removeFirst()
                }

                val oldestRequest = requestTimes.firstOrNull()
                val windowWaitMillis = if (requestTimes.size >= permits && oldestRequest != null) {
                    windowMillis - (now - oldestRequest)
                } else {
                    0L
                }
                val retryWaitMillis = retryNotBeforeMillis - now
                val wait = maxOf(windowWaitMillis, retryWaitMillis)
                if (wait <= 0) {
                    requestTimes.addLast(now)
                    0L
                } else {
                    wait
                }
            }
            if (waitMillis <= 0) return
            delay(waitMillis)
        }
    }

    suspend fun deferFor(retryAfterMillis: Long) {
        if (retryAfterMillis <= 0) return
        mutex.withLock {
            val now = nowMillis()
            val notBefore = if (retryAfterMillis > Long.MAX_VALUE - now) {
                Long.MAX_VALUE
            } else {
                now + retryAfterMillis
            }
            retryNotBeforeMillis = maxOf(retryNotBeforeMillis, notBefore)
        }
    }
}

internal fun createAniListRateLimitPlugin(
    limiter: AniListRateLimiter = AniListRateLimiter(),
) = createClientPlugin("AniListRateLimit") {
    onRequest { _, _ -> limiter.acquire() }
    onResponse { response ->
        if (response.status == HttpStatusCode.TooManyRequests) {
            val retryAfterSeconds = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()
            if (retryAfterSeconds != null && retryAfterSeconds > 0) {
                val retryAfterMillis = if (retryAfterSeconds > Long.MAX_VALUE / 1_000) {
                    Long.MAX_VALUE
                } else {
                    retryAfterSeconds * 1_000
                }
                limiter.deferFor(retryAfterMillis)
            }
        }
    }
}
