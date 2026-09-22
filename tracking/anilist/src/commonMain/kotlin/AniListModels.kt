/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.tracking.anilist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class GraphQLRequest(val query: String, val variables: JsonObject)

@Serializable
internal data class GraphQLResponse<T>(val data: T? = null, val errors: List<GraphQLError> = emptyList())

@Serializable
internal data class GraphQLError(val message: String)

@Serializable
internal data class ViewerData(@SerialName("Viewer") val viewer: AniListViewer)

@Serializable
internal data class AniListViewer(
    val id: Int,
    val name: String,
    val avatar: AniListAvatar? = null,
    val mediaListOptions: AniListMediaListOptions,
)

@Serializable
internal data class AniListAvatar(val large: String? = null)

@Serializable
internal data class AniListMediaListOptions(val scoreFormat: String)

@Serializable
internal data class SearchData(@SerialName("Page") val page: MediaPage)

@Serializable
internal data class MediaPage(val media: List<AniListMedia>)

@Serializable
internal data class MediaData(@SerialName("Media") val media: AniListMedia?)

@Serializable
internal data class SaveEntryData(@SerialName("SaveMediaListEntry") val entry: AniListEntry)

@Serializable
internal data class DeleteEntryData(@SerialName("DeleteMediaListEntry") val result: DeleteResult)

@Serializable
internal data class DeleteResult(val deleted: Boolean)

@Serializable
internal data class AniListMedia(
    val id: Int,
    val title: AniListTitle,
    val siteUrl: String? = null,
    val coverImage: AniListCover? = null,
    val episodes: Int? = null,
    val mediaListEntry: AniListEntry? = null,
)

@Serializable
internal data class AniListTitle(
    val userPreferred: String? = null,
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
)

@Serializable
internal data class AniListCover(val large: String? = null)

@Serializable
internal data class AniListEntry(
    val id: Int,
    val mediaId: Int? = null,
    val status: String,
    val score: Int = 0,
    val progress: Int = 0,
)
