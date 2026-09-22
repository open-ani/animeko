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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

    suspend fun save(mediaId: Int, status: String, score: Int, progress: Int, token: String): AniListEntry =
        execute<SaveEntryData>(
            SAVE,
            buildJsonObject {
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
            mediaListEntry { id mediaId status score(format: POINT_100) progress }
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
            mutation SaveEntry(${'$'}mediaId: Int!, ${'$'}status: MediaListStatus!, ${'$'}score: Float!, ${'$'}progress: Int!) {
              SaveMediaListEntry(mediaId: ${'$'}mediaId, status: ${'$'}status, scoreRaw: ${'$'}score, progress: ${'$'}progress) {
                id mediaId status score(format: POINT_100) progress
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
