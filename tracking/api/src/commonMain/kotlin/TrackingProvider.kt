/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tracking.api

import kotlin.jvm.JvmInline

/**
 * A remote service that stores a user's anime list.
 *
 * Authentication and credential persistence are supplied to an adapter separately. Callers use this
 * interface only for remote anime and list-entry operations.
 *
 * Implementations must propagate [kotlin.coroutines.cancellation.CancellationException]. Other
 * failures are reported as [TrackingProviderException].
 */
interface TrackingProvider {
    val id: TrackingProviderId

    suspend fun searchAnime(query: String): List<TrackingMedia>

    /** Returns the remote anime and the user's list entry, or `null` when the anime does not exist. */
    suspend fun getAnime(mediaId: TrackingMediaId): TrackingMediaWithEntry?

    /** Creates or replaces the represented fields of the user's list entry. */
    suspend fun saveListEntry(entry: TrackingListEntry): TrackingListEntry

    /** Deletes the remote list entry. This does not delete Animeko's local binding. */
    suspend fun deleteListEntry(mediaId: TrackingMediaId)
}

@JvmInline
value class TrackingProviderId(val value: String) {
    init {
        require(value.isNotBlank()) { "Tracking provider ID must not be blank" }
    }
}

/** An opaque ID interpreted only by the provider that returned it. */
@JvmInline
value class TrackingMediaId(val value: String) {
    init {
        require(value.isNotBlank()) { "Tracking media ID must not be blank" }
    }
}

data class TrackingMedia(
    val id: TrackingMediaId,
    val title: String,
    val coverImageUrl: String?,
    val totalEpisodes: Int?,
)

data class TrackingMediaWithEntry(
    val media: TrackingMedia,
    val listEntry: TrackingListEntry?,
)

data class TrackingListEntry(
    val mediaId: TrackingMediaId,
    val status: TrackingStatus,
    val progress: Int,
    /** Normalized to 0–100. Zero means unrated. */
    val score: TrackingScore = TrackingScore.Unrated,
) {
    init {
        require(progress >= 0) { "Tracking progress must not be negative" }
    }
}

enum class TrackingStatus {
    PLANNING,
    CURRENT,
    COMPLETED,
    PAUSED,
    DROPPED,
    REPEATING,
}

/** Provider-independent score normalized to the same 0–100 scale Mihon uses internally. */
@JvmInline
value class TrackingScore(val value: Int) {
    init {
        require(value in 0..100) { "Tracking score must be between 0 and 100" }
    }

    companion object {
        val Unrated = TrackingScore(0)
    }
}

sealed class TrackingProviderException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unauthorized(cause: Throwable? = null) :
        TrackingProviderException("Tracking provider authorization is required", cause)

    class RateLimited(
        val retryAfterMillis: Long?,
        cause: Throwable? = null,
    ) : TrackingProviderException("Tracking provider rate limit exceeded", cause)

    class Remote(message: String, cause: Throwable? = null) : TrackingProviderException(message, cause)
}
