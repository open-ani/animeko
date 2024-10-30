/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.bangumi

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.plugin
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.core.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
import me.him188.ani.datasources.bangumi.models.BangumiSubject
import me.him188.ani.datasources.bangumi.models.BangumiSubjectType
import me.him188.ani.datasources.bangumi.models.BangumiUser
import me.him188.ani.datasources.bangumi.models.search.BangumiSort
import me.him188.ani.datasources.bangumi.models.subjects.BangumiLegacySubject
import me.him188.ani.datasources.bangumi.models.subjects.BangumiSubjectImageSize
import me.him188.ani.datasources.bangumi.next.apis.SubjectBangumiNextApi
import me.him188.ani.utils.ktor.ClientProxyConfig
import me.him188.ani.utils.ktor.proxy
import me.him188.ani.utils.ktor.registerLogging
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.serialization.toJsonArray
import kotlin.coroutines.CoroutineContext

interface BangumiClient : Closeable {
    // Bangumi open API: https://github.com/bangumi/api/blob/master/open-api/api.yml

    /*
    {
    "access_token":"YOUR_ACCESS_TOKEN",
    "expires_in":604800,
    "token_type":"Bearer",
    "scope":null,
    "refresh_token":"YOUR_REFRESH_TOKEN"
    "user_id" : USER_ID
    }
     */

    @Serializable
    data class GetAccessTokenResponse(
        @SerialName("expires_in") val expiresIn: Long,
        @SerialName("user_id") val userId: Long,
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
    )

    suspend fun executeGraphQL(query: String, variables: JsonObject? = null): JsonObject

    @Serializable
    data class GetTokenStatusResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("client_id") val clientId: String,
        @SerialName("expires") val expires: Long, // timestamp
        @SerialName("user_id") val userId: Int,
    )

    suspend fun getSelfInfoByToken(accessToken: String?): BangumiUser

    suspend fun deleteSubjectCollection(
        subjectId: Int
    )

    suspend fun testConnection(): ConnectionStatus

    suspend fun getApi(): DefaultApi
    suspend fun getNextApi(): SubjectBangumiNextApi

    suspend fun getSearchApi(): BangumiSearchApi

    companion object Factory {
        fun create(
            bearerToken: Flow<String?>,
            parentCoroutineContext: CoroutineContext,
            httpClientConfiguration: HttpClientConfig<*>.() -> Unit = {}
        ): BangumiClient =
            BangumiClientImpl(bearerToken, parentCoroutineContext, httpClientConfiguration)
    }
}

fun createBangumiClient(
    bearerToken: Flow<String?>,
    proxyConfig: ClientProxyConfig?,
    parentCoroutineContext: CoroutineContext,
    userAgent: String,
): BangumiClient {
    return BangumiClient.create(
        bearerToken,
        parentCoroutineContext,
    ) {
        proxy(proxyConfig)
        install(UserAgent) {
            agent = userAgent
        }
    }
}

class DelegateBangumiClient(
    private val client: Flow<BangumiClient>,
) : BangumiClient {
    override suspend fun executeGraphQL(query: String, variables: JsonObject?): JsonObject =
        client.first().executeGraphQL(query, variables)

    override suspend fun getSelfInfoByToken(accessToken: String?): BangumiUser =
        client.first().getSelfInfoByToken(accessToken)

    override suspend fun deleteSubjectCollection(subjectId: Int) {
        client.first().deleteSubjectCollection(subjectId)
    }

    override suspend fun testConnection(): ConnectionStatus {
        return client.first().testConnection()
    }

    override suspend fun getApi(): DefaultApi = client.first().getApi()
    override suspend fun getNextApi(): SubjectBangumiNextApi = client.first().getNextApi()
    override suspend fun getSearchApi(): BangumiSearchApi = client.first().getSearchApi()

    override fun close() {
    }
}

private const val BANGUMI_API_HOST = "https://api.bgm.tv"
private const val BANGUMI_NEXT_API_HOST = "https://next.bgm.tv" // dev.bgm38.com for testing
private const val BANGUMI_HOST = "https://bgm.tv"

class BangumiClientImpl(
    private val bearerToken: Flow<String?>,
    parentCoroutineContext: CoroutineContext,
    httpClientConfiguration: HttpClientConfig<*>.() -> Unit = {},
) : BangumiClient {
    private val scope = CoroutineScope(parentCoroutineContext + SupervisorJob(parentCoroutineContext[Job]))

    private val logger = logger(this::class)

    override suspend fun executeGraphQL(query: String, variables: JsonObject?): JsonObject {
        val resp = httpClient.post("$BANGUMI_API_HOST/v0/graphql") {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("query", query)
                    if (variables != null) {
                        put("variables", variables)
                    }
                },
            )
        }

        return resp.body()
    }

    override suspend fun getSelfInfoByToken(accessToken: String?): BangumiUser {
        val resp = httpClient.get("$BANGUMI_API_HOST/v0/me") {
            accessToken?.let { bearerAuth(it) }
        }

        return resp.body<BangumiUser>()
    }

    override suspend fun deleteSubjectCollection(subjectId: Int) {
        // not implemented
    }

    override suspend fun testConnection(): ConnectionStatus {
        return httpClient.get(BANGUMI_API_HOST).run {
            if (status.isSuccess() || status == HttpStatusCode.NotFound)
                ConnectionStatus.SUCCESS
            else ConnectionStatus.FAILED
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient {
        httpClientConfiguration()
        install(HttpRequestRetry) {
            maxRetries = 3
            delayMillis { 2000 }
        }
        install(HttpTimeout)
        install(ContentNegotiation) {
            json(json)
        }
        expectSuccess = true
    }.apply {
        registerLogging(logger)
        plugin(HttpSend).intercept { request ->
            if (!request.headers.contains(HttpHeaders.Authorization)) {
                bearerToken.first()?.let {
                    request.bearerAuth(it)
                }
            }
            val originalCall = execute(request)
            if (originalCall.response.status.value !in 100..399) {
                execute(request)
            } else {
                originalCall
            }
        }
    }

    private val api = DefaultApi(BANGUMI_API_HOST, httpClient)
    override suspend fun getApi() = api

    private val nextApi = SubjectBangumiNextApi(BANGUMI_NEXT_API_HOST, httpClient)
    override suspend fun getNextApi(): SubjectBangumiNextApi = nextApi

    @Serializable
    private data class SearchSubjectByKeywordsResponse(
        val total: Int,
        val data: List<BangumiSubject>? = null,
    )

    private val subjects = object : BangumiSearchApi {
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
        ): Paged<BangumiSubject> {
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
            return body.run {
                Paged(
                    total,
                    data == null || (offset ?: 0) + data.size < total,
                    data.orEmpty(),
                )
            }
        }

        override suspend fun searchSubjectsByKeywordsWithOldApi(
            keyword: String,
            type: BangumiSubjectType,
            responseGroup: BangumiSubjectImageSize?,
            start: Int?,
            maxResults: Int?
        ): Paged<BangumiLegacySubject> {
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
                return Paged.empty()
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
                    return Paged.empty()
//                    throw BangumiRateLimitedException()
                }
            }

            val json = resp.body<JsonObject>()
            return json.run {
                // results: subject total
                val results: Int = json["results"]?.toString()?.toInt() ?: 0
                // code: exception code
                val code: String = json["code"]?.toString() ?: "-1"
                // return empty when code exists and not -1 and is 404
                if ("-1" != code && "404" == code) return Paged.empty()
                val legacySubjectsJson: String = json["list"].toString()
                val legacySubjects: List<BangumiLegacySubject> =
                    this@BangumiClientImpl.json.decodeFromString(legacySubjectsJson)
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

    override suspend fun getSearchApi(): BangumiSearchApi = subjects

    override fun close() {
        httpClient.close()
        scope.cancel()
    }

    companion object {
        fun getSubjectImageUrl(id: Int, size: BangumiSubjectImageSize): String {
            return "$BANGUMI_API_HOST/v0/subjects/${id}/image?type=${size.id.lowercase()}"
        }
    }
}

class BangumiRateLimitedException : Exception("Rate limited by Bangumi API")