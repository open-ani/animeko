/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.navigation

import androidx.core.bundle.Bundle
import androidx.navigation.NavType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

open class SerializableNavType<T : Any>(
    private val serializer: KSerializer<T>,
    override val isNullableAllowed: Boolean = true,
) : NavType<T?>(isNullableAllowed) {
    override fun get(bundle: Bundle, key: String): T? {
        val value = bundle.getString(key) ?: return null
        return json.decodeFromString(serializer, value)
    }

    override fun parseValue(value: String): T? {
        if (value == "null") return null
        return json.decodeFromString(serializer, value)
    }

    override fun serializeAsValue(value: T?): String {
        if (value == null) return "null"
        return json.encodeToString(serializer, value)
    }

    override fun put(bundle: Bundle, key: String, value: T?) {
        if (value == null) {
            bundle.putString(key, "null")
            return
        }
        bundle.putString(key, json.encodeToString(serializer, value))
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}