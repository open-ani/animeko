/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.tracking.anilist

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.him188.ani.tracking.api.TrackingDate
import me.him188.ani.tracking.api.TrackingDateField

internal class AniListApi(private val client: HttpClient) {
    suspend fun viewer(token: String): AniListViewer = execute<ViewerData>(
        VIEWER,
        JsonObject(emptyMap()),
        token,
    ).viewer

    suspend fun search(query: String, token: String): List<AniListMedia> = execute<SearchData>(
        SEARCH,
        buildJsonObject { put("search", query) },
        token,
    ).page.media

    suspend fun getMedia(id: Int, token: String): AniListMedia? = execute<MediaData>(
        MEDIA,
        buildJsonObject { put("id", id) },
        token,
    ).media

    suspend fun save(mediaId: Int, entryId: Int?, status: String, score: Int, progress: Int, token: String): AniListEntry =
        execute<SaveEntryData>(
            SAVE,
            buildJsonObject {
                entryId?.let { put("id", it) }
                put("mediaId", mediaId)
                put("status", status)
                put("score", score)
                put("progress", progress)
            },
            token,
        ).entry

    suspend fun delete(entryId: Int, token: String): Boolean = execute<DeleteEntryData>(
        DELETE,
        buildJsonObject { put("id", entryId) },
        token,
    ).result.deleted

    suspend fun updateDate(entryId: Int, field: TrackingDateField, date: TrackingDate?, token: String): AniListEntry =
        execute<SaveEntryData>(
            if (field == TrackingDateField.STARTED) SAVE_START_DATE else SAVE_COMPLETION_DATE,
            buildJsonObject {
                put("id", entryId)
                putJsonObject("date") {
                    put("year", date?.year?.let(::JsonPrimitive) ?: JsonNull)
                    put("month", date?.month?.let(::JsonPrimitive) ?: JsonNull)
                    put("day", date?.day?.let(::JsonPrimitive) ?: JsonNull)
                }
            },
            token,
        ).entry

    suspend fun updateVisibility(entryId: Int, isPrivate: Boolean, token: String): AniListEntry =
        execute<SaveEntryData>(
            SAVE_VISIBILITY,
            buildJsonObject {
                put("id", entryId)
                put("private", isPrivate)
            },
            token,
        ).entry

    private suspend inline fun <reified T> execute(query: String, variables: JsonObject, token: String): T {
        val response = client.post(ENDPOINT) {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(GraphQLRequest(query, variables))
        }.body<GraphQLResponse<T>>()
        if (response.errors.isNotEmpty()) throw AniListGraphQLException(response.errors.joinToString("; ") { it.message })
        return response.data ?: throw AniListGraphQLException("AniList returned neither data nor errors")
    }

    private companion object {
        const val ENDPOINT = "https://graphql.anilist.co"

        val VIEWER = """
            query ViewerAccount {
              Viewer { id name avatar { large } mediaListOptions { scoreFormat } }
            }
        """.trimIndent()

        val MEDIA_FIELDS = """
            id
            siteUrl
            title { userPreferred romaji english native }
            coverImage { large }
            episodes
            mediaListEntry { id mediaId status score(format: POINT_100) progress private startedAt { year month day } completedAt { year month day } }
        """.trimIndent()

        val SEARCH = """
            query SearchAnime(${'$'}search: String!) {
              Page(page: 1, perPage: 20) {
                media(search: ${'$'}search, type: ANIME) { $MEDIA_FIELDS }
              }
            }
        """.trimIndent()

        val MEDIA = """
            query AnimeWithEntry(${'$'}id: Int!) {
              Media(id: ${'$'}id, type: ANIME) { $MEDIA_FIELDS }
            }
        """.trimIndent()

        val SAVE = """
            mutation SaveEntry(${'$'}id: Int, ${'$'}mediaId: Int!, ${'$'}status: MediaListStatus!, ${'$'}score: Int!, ${'$'}progress: Int!) {
              SaveMediaListEntry(id: ${'$'}id, mediaId: ${'$'}mediaId, status: ${'$'}status, scoreRaw: ${'$'}score, progress: ${'$'}progress) {
                id mediaId status score(format: POINT_100) progress private startedAt { year month day } completedAt { year month day }
              }
            }
        """.trimIndent()

        val SAVE_START_DATE = """
            mutation SaveStartDate(${'$'}id: Int!, ${'$'}date: FuzzyDateInput) {
              SaveMediaListEntry(id: ${'$'}id, startedAt: ${'$'}date) {
                id mediaId status score(format: POINT_100) progress private startedAt { year month day } completedAt { year month day }
              }
            }
        """.trimIndent()

        val SAVE_COMPLETION_DATE = """
            mutation SaveCompletionDate(${'$'}id: Int!, ${'$'}date: FuzzyDateInput) {
              SaveMediaListEntry(id: ${'$'}id, completedAt: ${'$'}date) {
                id mediaId status score(format: POINT_100) progress private startedAt { year month day } completedAt { year month day }
              }
            }
        """.trimIndent()

        val SAVE_VISIBILITY = """
            mutation SaveVisibility(${'$'}id: Int!, ${'$'}private: Boolean!) {
              SaveMediaListEntry(id: ${'$'}id, private: ${'$'}private) {
                id mediaId status score(format: POINT_100) progress private startedAt { year month day } completedAt { year month day }
              }
            }
        """.trimIndent()

        val DELETE = """
            mutation DeleteEntry(${'$'}id: Int!) {
              DeleteMediaListEntry(id: ${'$'}id) { deleted }
            }
        """.trimIndent()
    }
}

internal class AniListGraphQLException(message: String) : Exception(message)
