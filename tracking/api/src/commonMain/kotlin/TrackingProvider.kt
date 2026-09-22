/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tracking.api

import kotlinx.coroutines.flow.StateFlow
import kotlin.jvm.JvmInline

/**
 * A cohesive integration with one remote anime-tracking service.
 *
 * This follows Mihon's `Tracker` lifecycle: identity and capabilities, account state, provider score
 * presentation, search, bind, update, refresh, and delete live behind one provider façade. Credential
 * persistence and Animeko's local binding/reconciliation records remain injected implementation details.
 */
interface TrackingProvider {
    val info: TrackingProviderInfo
    val capabilities: TrackingCapabilities

    /** Observable account state used by the shared Accounts UI and provider registry. */
    val accountState: StateFlow<TrackingAccountState>

    /** Provider-specific choices shown by status and score editors. */
    val statusOptions: List<TrackingStatusOption>
    val scoreOptions: List<TrackingScoreOption>

    /**
     * Authenticate using the provider's credential exchange.
     *
     * Like Mihon's `login(username, password)`, fields are provider interpreted. OAuth providers use
     * [TrackingLoginCredentials.secret] for the returned token. Credentials must not be persisted by callers.
     */
    suspend fun login(credentials: TrackingLoginCredentials): TrackingAccount

    suspend fun refreshAccount(): TrackingAccount

    /** Clears both the remote-client session and the injected secure credential store. */
    suspend fun logout()

    suspend fun search(query: String): List<TrackingMedia>

    /** Reads current remote state before Animeko asks the user how to reconcile a binding. */
    suspend fun prepareBinding(mediaId: TrackingMediaId): TrackingMediaWithEntry?

    /** Creates or replaces the represented fields after reconciliation has been explicitly chosen. */
    suspend fun bind(entry: TrackingListEntry): TrackingListEntry

    /** Updates an existing binding; providers may apply their Mihon-style watched-episode transitions. */
    suspend fun update(entry: TrackingListEntry, didWatchEpisode: Boolean = false): TrackingListEntry

    suspend fun refresh(mediaId: TrackingMediaId): TrackingMediaWithEntry?

    /** Deletes only the remote entry. Local unbinding is a separate domain operation. */
    suspend fun delete(mediaId: TrackingMediaId)
}

data class TrackingProviderInfo(
    val id: TrackingProviderId,
    val displayName: String,
    val websiteUrl: String,
) {
    init {
        require(displayName.isNotBlank()) { "Tracking provider display name must not be blank" }
        require(websiteUrl.isNotBlank()) { "Tracking provider website URL must not be blank" }
    }
}

data class TrackingCapabilities(
    val supportsStartAndCompletionDates: Boolean = false,
    val supportsPrivateEntries: Boolean = false,
)

sealed interface TrackingAccountState {
    data object LoggedOut : TrackingAccountState
    data class LoggedIn(val account: TrackingAccount) : TrackingAccountState
    data class Refreshing(val previousAccount: TrackingAccount?) : TrackingAccountState
}

data class TrackingAccount(
    val remoteId: String,
    val displayName: String,
    val avatarUrl: String? = null,
) {
    init {
        require(remoteId.isNotBlank()) { "Tracking account ID must not be blank" }
        require(displayName.isNotBlank()) { "Tracking account display name must not be blank" }
    }
}

data class TrackingLoginCredentials(
    val username: String = "",
    val secret: String,
) {
    init {
        require(secret.isNotBlank()) { "Tracking login secret must not be blank" }
    }
}

data class TrackingStatusOption(
    val status: TrackingStatus,
    val displayName: String,
)

data class TrackingScoreOption(
    val score: TrackingScore,
    val displayValue: String,
)

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
    val siteUrl: String,
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
    /** Normalized to 0–100, matching Mihon's AniList representation. Zero means unrated. */
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
