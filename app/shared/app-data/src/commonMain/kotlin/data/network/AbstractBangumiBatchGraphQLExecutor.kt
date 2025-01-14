/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import androidx.collection.IntList
import androidx.collection.IntSet
import androidx.collection.mutableIntObjectMapOf
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.datasources.bangumi.BangumiClient
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.serialization.getOrFail

abstract class AbstractBangumiBatchGraphQLExecutor {
    protected val logger = logger<AbstractBangumiBatchGraphQLExecutor>()

    /**
     * 返回对应每个 id
     */
    protected fun processResponse(
        rawGraphQLResponse: JsonObject,
    ): List<JsonObject?> = when (val element = rawGraphQLResponse.getOrFail("data")) {
        is JsonObject -> {
            element.values.map {
                if (it is JsonNull) null else it.jsonObject
            }
        }

        is JsonNull -> throw IllegalStateException("Bangumi GraphQL response data is null: $rawGraphQLResponse")
        else -> throw IllegalStateException("Unexpected Bangumi GraphQL response: $element")
    }
}

data class BangumiGraphQLResponse(
    val data: List<JsonObject?>,
    val errors: String?,
)

fun IntSet.toIntArray(): IntArray {
    val array = IntArray(size)
    var i = 0
    forEach { array[i++] = it }
    return array
}

/**
 * @param fragment 里面需要定义 `MyFragment`
 */
class BangumiBatchGraphQLExecutorEngine(
    private val actionName: String,
    private val targetSchema: String,
    private val fragment: String,
) {
    private fun buildQueryOfSize(fragments: String, size: Int): String {
        return buildString(
            capacity = fragment.length + 30 + 55 * size, // big enough to avoid resizing
        ) {
            appendLine(fragments)

            appendLine("query BatchQuery(")
            repeat(size) { i ->
                append("\$id").append(i).append(": Int!")
                if (i != size - 1) {
                    append(", ")
                }
            }
            appendLine(") {")

            repeat(size) { i ->
                append('s')
                append(i)
                append(":$targetSchema(id: \$id").append(i).append("){...MyFragment}")
                appendLine()
            }

            append("}")
        }
    }

    private val cachedQueries = mutableIntObjectMapOf<String>()

    init {
        cacheQuery(1)
        cacheQuery(Repository.defaultPagingConfig.pageSize)
    }

    /**
     * Pre-builds a query of the given size so that we can reuse the same query for multiple requests.
     */
    fun cacheQuery(size: Int) {
        cachedQueries[size] = buildQueryOfSize(fragment, size)
    }

    /**
     * 返回对应每个 id
     */
    private fun processResponse(
        rawGraphQLResponse: JsonObject,
    ): List<JsonObject?> = when (val element = rawGraphQLResponse.getOrFail("data")) {
        is JsonObject -> {
            element.values.map {
                if (it is JsonNull) null else it.jsonObject
            }
        }

        is JsonNull -> throw IllegalStateException("Bangumi GraphQL response data is null: $rawGraphQLResponse")
        else -> throw IllegalStateException("Unexpected Bangumi GraphQL response: $element")
    }

    suspend fun execute(client: BangumiClient, ids: IntList): BangumiGraphQLResponse {
        if (ids.size == 0) {
            return BangumiGraphQLResponse(emptyList(), "")
        }

        val cachedQuery = cachedQueries[ids.size]

        // 尽量使用 variables
        val resp = if (cachedQuery == null) {
            client.executeGraphQL(
                actionName,
                buildQueryOfSize(
                    fragment,
                    ids.size,
                ),
                variables = buildJsonObject {
                    repeat(ids.size) { i ->
                        put("id$i", ids[i])
                    }
                },
            )
        } else {
            client.executeGraphQL(
                actionName,
                cachedQuery,
                variables = buildJsonObject {
                    repeat(ids.size) { i ->
                        put("id$i", ids[i])
                    }
                },
            )
        }
        return try {
            BangumiGraphQLResponse(
                processResponse(resp),
                errors = resp["errors"]?.toString(),
            )
        } catch (e: Exception) {
            throw IllegalStateException(
                "Exception while processing Bangumi GraphQL response for action $actionName, ids $ids, see cause",
                e,
            )
        }
    }

}
