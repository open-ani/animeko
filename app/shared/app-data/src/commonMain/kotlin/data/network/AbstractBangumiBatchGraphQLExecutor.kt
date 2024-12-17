/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import androidx.collection.IntSet
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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
