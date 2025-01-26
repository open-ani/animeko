/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.dmhy.impl.protocol

import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments
import me.him188.ani.datasources.dmhy.DmhyTopic
import me.him188.ani.datasources.dmhy.impl.cache.Cache
import me.him188.ani.datasources.dmhy.impl.cache.CacheImpl
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.ktor.bodyAsDocument

class Network(
    private val client: ScopedHttpClient,
) {
    private object Paths {
        const val host: String = "www.dmhy.org"
        val userPathSegments: List<String> =
            listOf("topics", "list", "user_id")  // https://www.dmhy.org/topics/list/user_id/637871
        val alliancePathSegments: List<String> =
            listOf("topics", "list", "team_id") // https://www.dmhy.org/topics/list/team_id/801
    }

    data class ListResponse(
        val context: Cache,
        val list: List<DmhyTopic>,
        val currentPage: Int,
        val hasPreviousPage: Boolean,
        val hasNextPage: Boolean,
    )

    // https://www.dmhy.org/topics/list?keyword=lyc&sort_id=2&team_id=823&order=date-asc
    // page starts from 1
    suspend fun list(
        page: Int? = null,
        keyword: String? = null,
        sortId: String? = null,
        teamId: String? = null,
        orderId: String? = null,
    ): ListResponse {
        require(page == null || page >= 1) { "page must be >= 1" }
        client.use {
            val resp = get {
                url {
                    protocol = URLProtocol.HTTP
                    host = Paths.host
                    appendPathSegments("topics", "list")
                    if (page != null && page != 1) {
                        appendPathSegments("page", page.toString())
                    }
                }
                parameter("keyword", keyword)
                parameter("sort_id", sortId)
                parameter("team_id", teamId)
                parameter("order", orderId)
            }
            val document = resp.bodyAsDocument()
            val context = CacheImpl()
            return ListResponse(
                context = context,
                list = ListParser.parseList(context, document).orEmpty(),
                currentPage = 0,
                hasPreviousPage = false,
                hasNextPage = false,
            )
        }
    }
}
