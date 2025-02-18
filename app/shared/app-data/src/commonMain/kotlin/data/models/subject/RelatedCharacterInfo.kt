/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

import androidx.collection.mutableIntObjectMapOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import me.him188.ani.app.data.models.subject.CharacterRole.Companion.GUEST
import me.him188.ani.app.data.models.subject.CharacterRole.Companion.MAIN
import me.him188.ani.app.data.models.subject.CharacterRole.Companion.SUPPORTING
import kotlin.jvm.JvmInline

@Immutable
data class RelatedCharacterInfo(
    /**
     * 在条目中的序号, 每个条目的第一个角色为 `0`.
     */
    val index: Int,
    val character: CharacterInfo,
    val role: CharacterRole,
) {
    fun isMainCharacter() = role == MAIN

    companion object {
        val ImportanceOrder = compareBy<RelatedCharacterInfo> {
            when (it.role) {
                MAIN -> 0
                SUPPORTING -> 1
                GUEST -> 2
                else -> {
                    Int.MAX_VALUE
                }
            }
        }
    }
}

@Immutable
data class CharacterInfo(
    val id: Int,
    val name: String,
    val nameCn: String,
    val actors: List<PersonInfo>,
    val imageMedium: String,
    val imageLarge: String,
) {
    val displayName get() = nameCn.takeIf { it.isNotBlank() } ?: name
}

@JvmInline
@Immutable
value class CharacterRole(
    val id: Int
) {
    companion object {
        /**
         * 主角
         */
        val MAIN = CharacterRole(1)

        /**
         * 配角
         */
        val SUPPORTING = CharacterRole(2)

        /**
         * 客串
         */
        val GUEST = CharacterRole(3)
    }
}

private val names by lazy(LazyThreadSafetyMode.PUBLICATION) {
    mutableIntObjectMapOf<String>().apply {
        put(MAIN.id, "主角")
        put(SUPPORTING.id, "配角")
        put(GUEST.id, "客串")
    }
}

@Stable
val CharacterRole.nameCn
    get() = names[id] ?: "未知"

@Immutable
enum class CharacterType {
    CHARACTER,
    MECHANIC,
    SHIP,
    ORGANIZATION;
}

@Immutable
data class Images(
    val large: String,
    val medium: String,
    val small: String,
    val grid: String,
)

@Immutable
data class PersonInfo(
    val id: Int,
    val name: String,
    val type: PersonType,
    val careers: List<PersonCareer>,
    val imageLarge: String,
    val imageMedium: String,
    val summary: String,
    val locked: Boolean?,
    val nameCn: String = "",
) {
    val displayName get() = nameCn.takeIf { it.isNotBlank() } ?: name

    override fun toString(): String {
        return "PersonInfo(id=$id, name='$name', type=$type, careers=$careers, imageLarge='$imageLarge', imageMedium=<omitted>, summary=<omitted>, locked=$locked, nameCn='$nameCn')"
    }
}

@JvmInline
@Immutable
value class PersonType(
    val id: Int,
) {
    companion object {
        val Individual = PersonType(1)
        val Corporation = PersonType(2)
        val Association = PersonType(3)

        val entries by lazy(LazyThreadSafetyMode.PUBLICATION) {
            listOf(Individual, Corporation, Association)
        }

        fun fromId(id: Int) = PersonType(id)
    }
}

@Immutable
enum class PersonCareer { // TODO: 在进数据库之前, 改为 value class
    PRODUCER,
    MANGAKA,
    ARTIST,
    SEIYU,
    WRITER,
    ILLUSTRATOR,
    ACTOR;
}
