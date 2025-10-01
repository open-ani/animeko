/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

// @formatter:off
/**
 *
 * Manual client for AutoSkip APIs.
 * This file follows the style of generated APIs under me.him188.ani.client.apis.
 */

@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package me.him188.ani.client.apis

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import kotlinx.serialization.json.Json
import me.him188.ani.client.infrastructure.ApiClient
import me.him188.ani.client.infrastructure.HttpResponse
import me.him188.ani.client.infrastructure.RequestConfig
import me.him188.ani.client.infrastructure.RequestMethod
import me.him188.ani.client.infrastructure.wrap
import me.him188.ani.client.models.AutoSkipReportRequest
import me.him188.ani.client.models.AutoSkipRules

open class AutoSkipAniApi : ApiClient {

    constructor(
        baseUrl: String = ApiClient.BASE_URL,
        httpClientEngine: HttpClientEngine? = null,
        httpClientConfig: ((HttpClientConfig<*>) -> Unit)? = null,
        jsonSerializer: Json = ApiClient.JSON_DEFAULT
    ) : super(baseUrl = baseUrl, httpClientEngine = httpClientEngine, httpClientConfig = httpClientConfig, jsonBlock = jsonSerializer)

    constructor(
        baseUrl: String,
        httpClient: HttpClient
    ): super(baseUrl = baseUrl, httpClient = httpClient)

    /**
     * Report manual skip for autoskip aggregation
     * @param episodeId Episode ID
     * @param body AutoSkipReportRequest(mediaSourceId, time)
     * @return kotlin.Any
     */
    @Suppress("UNCHECKED_CAST")
    open suspend fun reportSkip(episodeId: kotlin.Long, body: AutoSkipReportRequest): HttpResponse<kotlin.Any> {
        val localVariableAuthNames = listOf<String>("auth-jwt")
        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.POST,
            "/v2/episodes/{episodeId}/skip-history".replace("{" + "episodeId" + "}", "$episodeId"),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
        )

        return jsonRequest(
            localVariableConfig,
            body,
            localVariableAuthNames
        ).wrap()
    }

    /**
     * Get computed autoskip rules for an episode
     * @param episodeId Episode ID
     * @return AutoSkipRules
     */
    @Suppress("UNCHECKED_CAST")
    open suspend fun getAutoSkipRules(episodeId: kotlin.Long): HttpResponse<AutoSkipRules> {
        val localVariableAuthNames = listOf<String>("auth-jwt")
        val localVariableBody = io.ktor.client.utils.EmptyContent
        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v2/episodes/{episodeId}/auto-skip".replace("{" + "episodeId" + "}", "$episodeId"),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
        )

        return request(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap()
    }
}

// @formatter:on

