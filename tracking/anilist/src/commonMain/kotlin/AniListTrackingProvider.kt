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
import me.him188.ani.tracking.api.TrackingListEntry
import me.him188.ani.tracking.api.TrackingMedia
import me.him188.ani.tracking.api.TrackingMediaId
import me.him188.ani.tracking.api.TrackingMediaWithEntry
import me.him188.ani.tracking.api.TrackingProvider
import me.him188.ani.tracking.api.TrackingProviderException
import me.him188.ani.tracking.api.TrackingProviderId
import me.him188.ani.tracking.api.TrackingProviderInfo
import me.him188.ani.tracking.api.TrackingScore
import me.him188.ani.tracking.api.TrackingStatus

class AniListTrackingProvider(
    client: HttpClient,
    private val accessToken: suspend () -> String,
) : TrackingProvider {
    private val api = AniListApi(client)
    override val info = INFO

    override suspend fun searchAnime(query: String): List<TrackingMedia> = call {
        require(query.isNotBlank()) { "Search query must not be blank" }
        api.search(query, accessToken()).map { it.toTrackingMedia() }
    }

    override suspend fun getAnime(mediaId: TrackingMediaId): TrackingMediaWithEntry? = call {
        api.getMedia(mediaId.asAniListId(), accessToken())?.let {
            TrackingMediaWithEntry(it.toTrackingMedia(), it.mediaListEntry?.toTrackingEntry())
        }
    }

    override suspend fun saveListEntry(entry: TrackingListEntry): TrackingListEntry = call {
        api.save(
            mediaId = entry.mediaId.asAniListId(),
            status = entry.status.toAniListStatus(),
            score = entry.score.value,
            progress = entry.progress,
            token = accessToken(),
        ).toTrackingEntry()
    }

    override suspend fun deleteListEntry(mediaId: TrackingMediaId): Unit = call {
        val token = accessToken()
        val entryId = api.getMedia(mediaId.asAniListId(), token)?.mediaListEntry?.id ?: return@call
        if (!api.delete(entryId, token)) {
            throw TrackingProviderException.Remote("AniList did not delete list entry $entryId")
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
        val INFO = TrackingProviderInfo(
            id = TrackingProviderId("anilist"),
            displayName = "AniList",
            websiteUrl = "https://anilist.co",
        )
    }
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
