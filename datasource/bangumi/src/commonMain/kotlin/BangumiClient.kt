/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.bangumi

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.bangumi.next.apis.EpisodeBangumiNextApi
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.ktor.ScopedHttpClient

interface BangumiClient {
    // Bangumi open API: https://github.com/bangumi/api/blob/master/open-api/api.yml

    val nextEpisodeApi: ApiInvoker<EpisodeBangumiNextApi>

    /**
     * 测试与 Bangumi 主站的连接
     */
    suspend fun testConnectionMaster(): ConnectionStatus

    /**
     * 测试与 Bangumi Next 的连接
     */
    suspend fun testConnectionNext(): ConnectionStatus
}

private const val BANGUMI_API_HOST = "https://api.bgm.tv"
private const val BANGUMI_NEXT_API_HOST = "https://next.bgm.tv" // dev.bgm38.com for testing

class BangumiClientImpl(
    /**
     * 不带 token, 所有请求都是匿名的
     */
    private val client: ScopedHttpClient,
) : BangumiClient {
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

    override val nextEpisodeApi = ApiInvoker(client) { EpisodeBangumiNextApi(BANGUMI_NEXT_API_HOST, it) }
}
