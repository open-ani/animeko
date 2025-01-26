/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.dandanplay

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.him188.ani.danmaku.dandanplay.data.DandanplayDanmaku
import me.him188.ani.danmaku.dandanplay.data.DandanplayDanmakuListResponse
import me.him188.ani.danmaku.dandanplay.data.DandanplayGetBangumiResponse
import me.him188.ani.danmaku.dandanplay.data.DandanplayMatchVideoResponse
import me.him188.ani.danmaku.dandanplay.data.DandanplaySearchEpisodeResponse
import me.him188.ani.danmaku.dandanplay.data.DandanplaySeasonSearchResponse
import me.him188.ani.utils.io.DigestAlgorithm
import me.him188.ani.utils.io.digest
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration


internal class DandanplayClient(
    private val client: HttpClient,
    private val appId: String,
    private val appSecret: String,
) {
    /**
     * Must be used at the last step of building the request.
     */
    private fun HttpRequestBuilder.addAuthorizationHeaders() {
        header("X-AppId", appId)
        val time = currentTimeMillis() / 1000
        header("X-Timestamp", time)
        header("X-Signature", generateSignature(appId, time, url.encodedPath, appSecret))
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun generateSignature(appId: String, timestamp: Long, path: String, appSecret: String): String {
        val data = appId + timestamp + path + appSecret
        return Base64.encode(data.encodeToByteString().digest(DigestAlgorithm.SHA256))
    }

    suspend fun getSeasonAnimeList(
        year: Int,
        month: Int,
    ): DandanplaySeasonSearchResponse {
        // https://api.dandanplay.net/api/v2/bangumi/season/anime/2024/04
        val response = client.get("https://api.dandanplay.net/api/v2/bangumi/season/anime/$year/$month") {
            accept(ContentType.Application.Json)
            addAuthorizationHeaders()
        }

        return response.body<DandanplaySeasonSearchResponse>()
    }

    suspend fun searchSubject(
        subjectName: String,
    ): DandanplaySearchEpisodeResponse {
        val response = client.get("https://api.dandanplay.net/api/v2/search/subject") {
            accept(ContentType.Application.Json)
            parameter("keyword", subjectName)
            addAuthorizationHeaders()
        }

        if (response.status == HttpStatusCode.NotFound) {
            return DandanplaySearchEpisodeResponse()
        }

        return response.body<DandanplaySearchEpisodeResponse>()
    }

    suspend fun searchEpisode(
        subjectName: String,
        episodeName: String?,
    ): DandanplaySearchEpisodeResponse {
        val response = client.get("https://api.dandanplay.net/api/v2/search/episodes") {
            accept(ContentType.Application.Json)
            parameter("anime", subjectName)
            parameter("episode", episodeName)
            addAuthorizationHeaders()
        }

        return response.body<DandanplaySearchEpisodeResponse>()
    }

    suspend fun getBangumiEpisodes(
        bangumiId: Int, // 注意, 这是 dandanplay 的 id, 不是 Bangumi.tv 的 id
    ): DandanplayGetBangumiResponse {
        val response = client.get("https://api.dandanplay.net/api/v2/bangumi/$bangumiId") {
            accept(ContentType.Application.Json)
            addAuthorizationHeaders()
        }

        return response.body<DandanplayGetBangumiResponse>()
    }

    suspend fun matchVideo(
        filename: String,
        fileHash: String?,
        fileSize: Long?,
        videoDuration: Duration
    ): DandanplayMatchVideoResponse {
        val response = client.post("https://api.dandanplay.net/api/v2/match") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("fileName", filename)
                    put("fileHash", fileHash)
                    put("fileSize", fileSize)
                    put("videoDuration", videoDuration.inWholeSeconds)
                    put("matchMode", "hashAndFileName")
                },
            )
            addAuthorizationHeaders()
        }

        return response.body<DandanplayMatchVideoResponse>()
    }

    suspend fun getDanmakuList(
        episodeId: Long,
    ): List<DandanplayDanmaku> {
        // See #122
//        val chConvert = when (getSystemChineseVariant()) {
//            ChineseVariant.SIMPLIFIED -> 1
//            ChineseVariant.TRADITIONAL -> 2
//            null -> 0
//        }
        val chConvert = 0
        val response =
            client.get("https://api.dandanplay.net/api/v2/comment/${episodeId}?chConvert=$chConvert&withRelated=true") {
                accept(ContentType.Application.Json)
                addAuthorizationHeaders()
            }.body<DandanplayDanmakuListResponse>()
        return response.comments
    }

    enum class ChineseVariant {
        SIMPLIFIED,
        TRADITIONAL
    }

//    private fun getSystemChineseVariant(): ChineseVariant? {
//        val locale = Locale.getDefault()
//        if (locale.language != "zh") return null
//        return when (locale.country) {
//            "CN" -> ChineseVariant.SIMPLIFIED
//            "TW", "HK", "MO" -> ChineseVariant.TRADITIONAL
//            else -> null
//        }
//    }
}