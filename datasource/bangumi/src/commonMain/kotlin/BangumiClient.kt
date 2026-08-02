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
import me.him188.ani.utils.ktor.ScopedHttpClient

/**
 * 只用于连通性探测. 客户端已不再直接请求 Bangumi 的任何数据接口 —— 剧集评论等内容由 Ani 服务器合并后下发.
 */
interface BangumiClient {
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
}
