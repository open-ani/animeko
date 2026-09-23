/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.tracking.anilist

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.him188.ani.tracking.api.TrackingAccount
import me.him188.ani.tracking.api.TrackingAccountState
import me.him188.ani.tracking.api.TrackingCapabilities
import me.him188.ani.tracking.api.TrackingCredentialStore
import me.him188.ani.tracking.api.TrackingDate
import me.him188.ani.tracking.api.TrackingDateField
import me.him188.ani.tracking.api.TrackingListEntry
import me.him188.ani.tracking.api.TrackingLoginCredentials
import me.him188.ani.tracking.api.TrackingMedia
import me.him188.ani.tracking.api.TrackingMediaId
import me.him188.ani.tracking.api.TrackingMediaWithEntry
import me.him188.ani.tracking.api.TrackingProvider
import me.him188.ani.tracking.api.TrackingProviderException
import me.him188.ani.tracking.api.TrackingProviderId
import me.him188.ani.tracking.api.TrackingProviderInfo
import me.him188.ani.tracking.api.TrackingScore
import me.him188.ani.tracking.api.TrackingScoreOption
import me.him188.ani.tracking.api.TrackingStatus
import me.him188.ani.tracking.api.TrackingStatusOption

class AniListTrackingProvider(
    client: HttpClient,
    private val credentialStore: TrackingCredentialStore,
) : TrackingProvider {
    private val api = AniListApi(client)
    private val mutableAccountState = MutableStateFlow<TrackingAccountState>(TrackingAccountState.LoggedOut)
    private var scoreFormat = POINT_100

    override val info = INFO
    override val capabilities = TrackingCapabilities(
        supportsStartAndCompletionDates = true,
        supportsPrivateEntries = true,
    )
    override val accountState: StateFlow<TrackingAccountState> = mutableAccountState
    override val statusOptions = listOf(
        TrackingStatusOption(TrackingStatus.PLANNING, "Plan to watch"),
        TrackingStatusOption(TrackingStatus.CURRENT, "Watching"),
        TrackingStatusOption(TrackingStatus.COMPLETED, "Completed"),
        TrackingStatusOption(TrackingStatus.PAUSED, "On hold"),
        TrackingStatusOption(TrackingStatus.DROPPED, "Dropped"),
        TrackingStatusOption(TrackingStatus.REPEATING, "Rewatching"),
    )
    override val scoreOptions: List<TrackingScoreOption>
        get() = scoreOptions(scoreFormat)

    override suspend fun login(credentials: TrackingLoginCredentials): TrackingAccount = call {
        val viewer = api.viewer(credentials.secret)
        credentialStore.save(credentials)
        viewer.applyToAccountState()
    }

    override suspend fun refreshAccount(): TrackingAccount = call {
        val credentials = credentialStore.load() ?: run {
            mutableAccountState.value = TrackingAccountState.LoggedOut
            throw TrackingProviderException.Unauthorized()
        }
        val previous = (mutableAccountState.value as? TrackingAccountState.LoggedIn)?.account
        mutableAccountState.value = TrackingAccountState.Refreshing(previous)
        try {
            api.viewer(credentials.secret).applyToAccountState()
        } catch (failure: Throwable) {
            mutableAccountState.value = previous?.let(TrackingAccountState::LoggedIn) ?: TrackingAccountState.LoggedOut
            throw failure
        }
    }

    override suspend fun logout() {
        credentialStore.clear()
        mutableAccountState.value = TrackingAccountState.LoggedOut
    }

    override suspend fun search(query: String): List<TrackingMedia> = call {
        require(query.isNotBlank()) { "Search query must not be blank" }
        api.search(query, token()).map { it.toTrackingMedia() }
    }

    override suspend fun prepareBinding(mediaId: TrackingMediaId): TrackingMediaWithEntry? = refresh(mediaId)

    override suspend fun bind(entry: TrackingListEntry): TrackingListEntry = call {
        if (api.getMedia(entry.mediaId.asAniListId(), token())?.mediaListEntry != null) {
            throw TrackingProviderException.Remote("AniList already has an entry for this title")
        }
        save(entry, null)
    }

    override suspend fun update(entry: TrackingListEntry, didWatchEpisode: Boolean): TrackingListEntry {
        if (!didWatchEpisode || entry.status == TrackingStatus.COMPLETED) return saveExisting(entry)
        val totalEpisodes = refresh(entry.mediaId)?.media?.totalEpisodes
        val transitioned = when {
            totalEpisodes != null && entry.progress >= totalEpisodes ->
                entry.copy(status = TrackingStatus.COMPLETED)
            entry.status != TrackingStatus.REPEATING -> entry.copy(status = TrackingStatus.CURRENT)
            else -> entry
        }
        return saveExisting(transitioned)
    }

    override suspend fun updateDate(entry: TrackingListEntry, field: TrackingDateField, date: TrackingDate?): TrackingListEntry = call {
        val token = token()
        val entryId = api.getMedia(entry.mediaId.asAniListId(), token)?.mediaListEntry?.id
            ?: throw TrackingProviderException.Remote("AniList list entry no longer exists")
        api.updateDate(entryId, field, date, token).toTrackingEntry()
    }

    override suspend fun updateVisibility(entry: TrackingListEntry, isPrivate: Boolean): TrackingListEntry = call {
        val token = token()
        val entryId = api.getMedia(entry.mediaId.asAniListId(), token)?.mediaListEntry?.id
            ?: throw TrackingProviderException.Remote("AniList list entry no longer exists")
        api.updateVisibility(entryId, isPrivate, token).toTrackingEntry()
    }

    override suspend fun refresh(mediaId: TrackingMediaId): TrackingMediaWithEntry? = call {
        api.getMedia(mediaId.asAniListId(), token())?.let {
            TrackingMediaWithEntry(it.toTrackingMedia(), it.mediaListEntry?.toTrackingEntry())
        }
    }

    override suspend fun delete(mediaId: TrackingMediaId): Unit = call {
        val token = token()
        val entryId = api.getMedia(mediaId.asAniListId(), token)?.mediaListEntry?.id ?: return@call
        if (!api.delete(entryId, token)) {
            throw TrackingProviderException.Remote("AniList did not delete list entry $entryId")
        }
    }

    private suspend fun saveExisting(entry: TrackingListEntry): TrackingListEntry = call {
        val token = token()
        val entryId = api.getMedia(entry.mediaId.asAniListId(), token)?.mediaListEntry?.id
            ?: throw TrackingProviderException.Remote("AniList list entry no longer exists")
        save(entry, entryId)
    }

    private suspend fun save(entry: TrackingListEntry, entryId: Int?): TrackingListEntry = call {
        api.save(
            mediaId = entry.mediaId.asAniListId(),
            entryId = entryId,
            status = entry.status.toAniListStatus(),
            score = entry.score.value,
            progress = entry.progress,
            token = token(),
        ).toTrackingEntry()
    }

    private suspend fun token(): String = credentialStore.load()?.secret
        ?: throw TrackingProviderException.Unauthorized()

    private fun AniListViewer.applyToAccountState(): TrackingAccount {
        scoreFormat = mediaListOptions.scoreFormat
        return TrackingAccount(id.toString(), name, avatar?.large).also {
            mutableAccountState.value = TrackingAccountState.LoggedIn(it)
        }
    }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: TrackingProviderException) {
        throw failure
    } catch (failure: ClientRequestException) {
        when (failure.response.status) {
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> throw TrackingProviderException.Unauthorized(failure)
            HttpStatusCode.TooManyRequests -> throw TrackingProviderException.RateLimited(null, failure)
            else -> throw TrackingProviderException.Remote("AniList request failed: ${failure.response.status}", failure)
        }
    } catch (failure: AniListGraphQLException) {
        throw TrackingProviderException.Remote(failure.message ?: "AniList GraphQL error", failure)
    } catch (failure: ResponseException) {
        throw TrackingProviderException.Remote("AniList request failed: ${failure.response.status}", failure)
    } catch (failure: Exception) {
        throw TrackingProviderException.Remote("AniList request failed", failure)
    }

    companion object {
        private const val POINT_100 = "POINT_100"
        val ID = TrackingProviderId("anilist")

        /** OAuth client registered on AniList with the redirect URI `ani://anilist-auth`. */
        const val CLIENT_ID = "51393"
        const val AUTHORIZE_URL = "https://anilist.co/api/v2/oauth/authorize?client_id=$CLIENT_ID&response_type=token"

        val INFO = TrackingProviderInfo(
            id = ID,
            displayName = "AniList",
            websiteUrl = "https://anilist.co",
        )
    }
}

private fun scoreOptions(format: String): List<TrackingScoreOption> = when (format) {
    "POINT_100" -> (0..100).map { TrackingScoreOption(TrackingScore(it), it.toString()) }
    "POINT_10" -> (0..10).map { TrackingScoreOption(TrackingScore(it * 10), it.toString()) }
    "POINT_10_DECIMAL" -> (0..100).map { TrackingScoreOption(TrackingScore(it), (it / 10f).toString()) }
    "POINT_5" -> (0..5).map { index ->
        TrackingScoreOption(TrackingScore(if (index == 0) 0 else index * 20 - 10), "$index ★")
    }
    "POINT_3" -> listOf(
        TrackingScoreOption(TrackingScore(0), "-"),
        TrackingScoreOption(TrackingScore(35), "😦"),
        TrackingScoreOption(TrackingScore(60), "😐"),
        TrackingScoreOption(TrackingScore(85), "😊"),
    )
    else -> throw TrackingProviderException.Remote("Unknown AniList score format: $format")
}

private fun AniListMedia.toTrackingMedia() = TrackingMedia(
    id = TrackingMediaId(id.toString()),
    title = title.userPreferred ?: title.romaji ?: title.english ?: title.native ?: "#$id",
    siteUrl = siteUrl ?: "https://anilist.co/anime/$id",
    coverImageUrl = coverImage?.large,
    totalEpisodes = episodes,
)

private fun AniListEntry.toTrackingEntry() = TrackingListEntry(
    mediaId = TrackingMediaId(requireNotNull(mediaId) { "AniList list entry omitted mediaId" }.toString()),
    status = status.toTrackingStatus(),
    progress = progress,
    score = TrackingScore(score),
    startedAt = startedAt?.takeIf { it.year != null || it.month != null || it.day != null }
        ?.let { TrackingDate(it.year, it.month, it.day) },
    completedAt = completedAt?.takeIf { it.year != null || it.month != null || it.day != null }
        ?.let { TrackingDate(it.year, it.month, it.day) },
    isPrivate = isPrivate,
)

private fun TrackingMediaId.asAniListId(): Int = value.toIntOrNull()
    ?: throw TrackingProviderException.Remote("AniList media ID must be an integer: $value")

private fun String.toTrackingStatus() = when (this) {
    "CURRENT" -> TrackingStatus.CURRENT
    "REPEATING" -> TrackingStatus.REPEATING
    "COMPLETED" -> TrackingStatus.COMPLETED
    "PAUSED" -> TrackingStatus.PAUSED
    "DROPPED" -> TrackingStatus.DROPPED
    "PLANNING" -> TrackingStatus.PLANNING
    else -> throw TrackingProviderException.Remote("Unknown AniList status: $this")
}

private fun TrackingStatus.toAniListStatus() = when (this) {
    TrackingStatus.CURRENT -> "CURRENT"
    TrackingStatus.COMPLETED -> "COMPLETED"
    TrackingStatus.PAUSED -> "PAUSED"
    TrackingStatus.DROPPED -> "DROPPED"
    TrackingStatus.PLANNING -> "PLANNING"
    TrackingStatus.REPEATING -> "REPEATING"
}
