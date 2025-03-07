/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.navigation

import androidx.navigation.NavType
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.write
import kotlin.enums.EnumEntries

class EnumNavType<E : Enum<E>>(
    private val entries: EnumEntries<E>,
) : NavType<E?>(true) {
    override val name: String get() = "enum"

    override fun put(bundle: SavedState, key: String, value: E?) {
        val name = value?.name
        check(name != "null") { "Enum value must not be \"null\"" }
        bundle.write {
            putString(key, name ?: "null")
        }
    }

    override fun get(bundle: SavedState, key: String): E? {
        bundle.read {
            val value = getString(key)
            return if (value == "null") null else entries.find { it.name == value }
        }
    }

    /**
     * Returns input value by default.
     *
     * If input value is "null", returns null as the reversion of Kotlin standard
     * library serializing null receivers of [kotlin.toString] into "null".
     */
    override fun parseValue(value: String): E? {
        return if (value == "null") null else entries.find { it.name == value }
    }

    /**
     * Returns default value of Uri.encode(value).
     *
     * If input value is null, returns "null" in compliance with Kotlin standard library
     * parsing null receivers of [kotlin.toString] into "null".
     */
    override fun serializeAsValue(value: E?): String {
        return value?.name ?: "null"
    }
}
