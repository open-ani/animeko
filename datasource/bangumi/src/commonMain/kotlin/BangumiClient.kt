/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.bangumi

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.him188.ani.datasources.api.paging.Paged
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.bangumi.apis.DefaultApi
import me.him188.ani.datasources.bangumi.client.BangumiSearchApi
import me.him188.ani.datasources.bangumi.models.BangumiSubjectType
import me.him188.ani.datasources.bangumi.models.BangumiTag
import me.him188.ani.datasources.bangumi.models.BangumiUser
import me.him188.ani.datasources.bangumi.models.search.BangumiSort
import me.him188.ani.datasources.bangumi.models.subjects.BangumiLegacySubject
import me.him188.ani.datasources.bangumi.models.subjects.BangumiSubjectImageSize
import me.him188.ani.datasources.bangumi.next.apis.CollectionBangumiNextApi
import me.him188.ani.datasources.bangumi.next.apis.EpisodeBangumiNextApi
import me.him188.ani.datasources.bangumi.next.apis.SubjectBangumiNextApi
import me.him188.ani.datasources.bangumi.next.apis.TrendingBangumiNextApi
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.ktor.HttpTokenChecker
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.thisLogger
import me.him188.ani.utils.serialization.toJsonArray

interface BangumiClient {
    // Bangumi open API: https://github.com/bangumi/api/blob/master/open-api/api.yml

    val api: ApiInvoker<DefaultApi>
    val nextSubjectApi: ApiInvoker<SubjectBangumiNextApi>
    val nextEpisodeApi: ApiInvoker<EpisodeBangumiNextApi>
    val nextTrendingApi: ApiInvoker<TrendingBangumiNextApi>
    val nextCollectionApi: ApiInvoker<CollectionBangumiNextApi>
    val searchApi: ApiInvoker<BangumiSearchApi>

    /**
     * @param actionName 本次操作的调试名称. 用于在日志中记录. 方便在安卓等协程无法正确显示调用栈的地方定位问题
     */
    suspend fun executeGraphQL(actionName: String, query: String, variables: JsonObject? = null): JsonObject

    suspend fun getSelfInfoByToken(accessToken: String?): BangumiUser?

    /**
     * 测试与 Bangumi 主站的连接
     */
    suspend fun testConnectionMaster(): ConnectionStatus

    /**
     * 测试与 Bangumi Next 的连接
     */
    suspend fun testConnectionNext(): ConnectionStatus
}

val BangumiClient.turnstileBaseUrl: String
    get() = "$BANGUMI_NEXT_API_HOST/p1/turnstile"

private const val BANGUMI_API_HOST = "https://api.bgm.tv"
private const val BANGUMI_NEXT_API_HOST = "https://next.bgm.tv" // dev.bgm38.com for testing
private const val BANGUMI_HOST = "https://bgm.tv"

class BangumiClientImpl(
    /**
     * 总是带有 token
     */
    private val client: ScopedHttpClient,
) : BangumiClient {
    private val logger = thisLogger()

    override suspend fun executeGraphQL(actionName: String, query: String, variables: JsonObject?): JsonObject {
        return try {
            client.use {
                post("$BANGUMI_API_HOST/v0/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        buildJsonObject {
                            put("query", query)
                            if (variables != null) {
                                put("variables", variables)
                            }
                        },
                    )
                }.body<JsonObject>()
            }
        } catch (e: Exception) {
            // POST https://api.bgm.tv/v0/graphql [Authorized]: 400  in 317.809375ms
            throw e.apply {
                addSuppressed(IllegalStateException("Failed to execute GraphQL query action'$actionName', the query is: \n$query"))
            }
        }
    }

    override suspend fun getSelfInfoByToken(accessToken: String?): BangumiUser? {
        if (accessToken == null) {
            return null
        }
        if (!HttpTokenChecker.isValidToken(accessToken)) {
            logger.error { "Invalid access token: $accessToken" }
            return null
        }
        try {
            return client.use {
                get("$BANGUMI_API_HOST/v0/me") {
                    bearerAuth(accessToken)
                }.body<BangumiUser>()
            }
        } catch (e: IllegalStateException) {
            val message = e.message ?: throw e
            if (message.contains("Unexpected char") && message.contains("in Authorization value")) {
                return null
            }
            throw e
        }
    }

    override suspend fun testConnectionMaster(): ConnectionStatus {
        return testConnection(BANGUMI_API_HOST)
    }

    override suspend fun testConnectionNext(): ConnectionStatus {
        return testConnection(BANGUMI_NEXT_API_HOST)
    }

    private suspend fun testConnection(host: String): ConnectionStatus {
        return client.use {
            get(host).run {
                if (status.isSuccess() || status == HttpStatusCode.NotFound)
                    ConnectionStatus.SUCCESS
                else ConnectionStatus.FAILED
            }
        }
    }

    override val api = ApiInvoker(client) { DefaultApi(BANGUMI_API_HOST, it) }
    override val nextSubjectApi = ApiInvoker(client) { SubjectBangumiNextApi(BANGUMI_NEXT_API_HOST, it) }
    override val nextEpisodeApi = ApiInvoker(client) { EpisodeBangumiNextApi(BANGUMI_NEXT_API_HOST, it) }
    override val nextTrendingApi: ApiInvoker<TrendingBangumiNextApi> =
        ApiInvoker(client) { TrendingBangumiNextApi(BANGUMI_NEXT_API_HOST, it) }
    override val nextCollectionApi: ApiInvoker<CollectionBangumiNextApi> =
        ApiInvoker(client) { CollectionBangumiNextApi(BANGUMI_NEXT_API_HOST, it) }
    override val searchApi: ApiInvoker<BangumiSearchApi> = ApiInvoker(client) { BangumiSearchApiImpl(it) }

    @Serializable
    private data class SearchSubjectByKeywordsResponse(
        val total: Int,
        val data: List<BangumiSearchSubjectNewApi>? = null,
    )

    private class BangumiSearchApiImpl(
        private val httpClient: HttpClient,
    ) : BangumiSearchApi {
        override suspend fun searchSubjectByKeywords(
            keyword: String,
            offset: Int?,
            limit: Int?,
            sort: BangumiSort?,
            types: List<BangumiSubjectType>?,
            tags: List<String>?,
            airDates: List<String>?,
            ratings: List<String>?,
            ranks: List<String>?,
            nsfw: Boolean?,
        ): List<BangumiSearchSubjectNewApi>? = withContext(Dispatchers.IO_) {
            val resp = httpClient.post("$BANGUMI_API_HOST/v0/search/subjects") {
                parameter("offset", offset)
                parameter("limit", limit)

                contentType(ContentType.Application.Json)
                val req = buildJsonObject {
                    put("keyword", keyword)
                    sort?.let { sort ->
                        put("sort", sort.id)
                    }

                    put(
                        "filter",
                        buildJsonObject {
                            types?.let { types -> put("type", types.map { it.value }.toJsonArray()) }
                            ranks?.let { put("rank", it.toJsonArray()) }
                            tags?.let { put("tag", it.toJsonArray()) }
                            airDates?.let { put("air_date", it.toJsonArray()) }
                            ratings?.let { put("rating", it.toJsonArray()) }
                            nsfw?.let { put("nsfw", it) }
                        },
                    )
                }
                setBody(req)
            }

            if (!resp.status.isSuccess()) {
                throw IllegalStateException("Failed to search subject by keywords: $resp")
            }

            val body = resp.body<SearchSubjectByKeywordsResponse>()
            return@withContext body.data
//            return body.run {
//                Paged(
//                    total,
//                    data == null || (offset ?: 0) + data.size < total,
//                    data.orEmpty(),
//                )
//            }
        }

        override suspend fun searchSubjectsByKeywordsWithOldApi(
            keyword: String,
            type: BangumiSubjectType,
            responseGroup: BangumiSubjectImageSize?,
            start: Int?,
            maxResults: Int?
        ): Paged<BangumiLegacySubject> = withContext(Dispatchers.IO_) {
            val resp = httpClient.get("$BANGUMI_API_HOST/search/subject".plus("/")) {
                url {
                    appendPathSegments(keyword)
                }
                parameter("type", type.value)
                parameter("responseGroup", responseGroup?.toString())
                parameter("start", start)
                parameter("max_results", maxResults)
            }

            if (!resp.status.isSuccess()) {
                throw IllegalStateException("Failed to search subject by keywords with old api: $resp")
            }

            if (resp.status == HttpStatusCode.NotFound) {
                return@withContext Paged.empty()
            }

            if (resp.contentType() == ContentType.Text.Html) {
                // 对不起，您在  秒内只能进行一次搜索，请返回。
                val text = resp.bodyAsText()
                if (text.contains("内只能进行一次搜索")) {
                    /*
                    用客户端 get, 如果没有搜索结果, 会返回一个 HTML 提示"对不起，您在  秒内只能进行一次搜索，请返回。", 但是用 chrome 就正常返回了 404

                    https://api.bgm.tv/search/subject/%E6%90%9C%E7%B4%A2%E4%B8%8D%E5%88%B0%E6%90%9C%E7%B4%A2%E4%B8%8D%E5%88%B0%E6%90%9C%E7%B4%A2%E4%B8%8D%E5%88%B0%E6%B5%8B%E8%AF%95?type=2&responseGroup=SMALL&start=0&max_results=90
                     */
                    // 所以我们这里当它为空
                    return@withContext Paged.empty()
//                    throw BangumiRateLimitedException()
                }
            }

            val jsonObj = resp.body<JsonObject>()
            return@withContext jsonObj.run {
                // results: subject total
                val results: Int = jsonObj["results"]?.toString()?.toInt() ?: 0
                // code: exception code
                val code: String = jsonObj["code"]?.toString() ?: "-1"
                // return empty when code exists and not -1 and is 404
                if ("-1" != code && "404" == code) return@run Paged.empty()
                val legacySubjectsJson: String = jsonObj["list"].toString()
                val legacySubjects: List<BangumiLegacySubject> =
                    json.decodeFromString(legacySubjectsJson)
                Paged(
                    results,
                    results > legacySubjects.size,
                    legacySubjects,
                )
            }
        }

        override fun getSubjectImageUrl(id: Int, size: BangumiSubjectImageSize): String {
            return Companion.getSubjectImageUrl(id, size)
        }
    }

    companion object {
        fun getSubjectImageUrl(id: Int, size: BangumiSubjectImageSize): String {
            return "$BANGUMI_API_HOST/v0/subjects/${id}/image?type=${size.id.lowercase()}"
        }
    }
}

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}


class BangumiRateLimitedException : Exception("Rate limited by Bangumi API")


// Caused by: kotlinx.serialization.MissingFieldException: Fields [locked, platform, images, volumes, eps, total_episodes, rating, collection] are required for type with serial name 'me.him188.ani.datasources.bangumi.models.BangumiSubject', but they were missing at path: $.data[0]
@Serializable
data class BangumiSearchSubjectNewApi(
    @SerialName(value = "id") @Required val id: Int,
    @SerialName(value = "type") @Required val type: BangumiSubjectType,
    @SerialName(value = "name") @Required val name: String,
    @SerialName(value = "name_cn") @Required val nameCn: String,
    @SerialName(value = "summary") @Required val summary: String,
    @SerialName(value = "nsfw") @Required val nsfw: Boolean,
    @SerialName(value = "tags") @Required val tags: List<BangumiTag>,
    /* air date in `YYYY-MM-DD` format */
    @SerialName(value = "date") val date: String? = null,
)
