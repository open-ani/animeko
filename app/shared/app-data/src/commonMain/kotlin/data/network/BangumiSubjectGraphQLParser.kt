/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.app.data.models.subject.CharacterInfo
import me.him188.ani.app.data.models.subject.CharacterRole
import me.him188.ani.app.data.models.subject.PersonInfo
import me.him188.ani.app.data.models.subject.PersonPosition
import me.him188.ani.app.data.models.subject.PersonType
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.utils.serialization.getIntOrFail
import me.him188.ani.utils.serialization.getOrFail
import me.him188.ani.utils.serialization.getString
import me.him188.ani.utils.serialization.getStringOrFail
import me.him188.ani.utils.serialization.jsonObjectOrNull

object BangumiSubjectGraphQLParser {
    private fun JsonElement.vSequence(): Sequence<String> {
        return when (this) {
            is JsonArray -> this.asSequence().flatMap { it.vSequence() }
            is JsonPrimitive -> sequenceOf(content)
            is JsonObject -> this["v"]?.vSequence() ?: emptySequence()
        }
    }

    private fun JsonObject.infobox(key: String): Sequence<String> = sequence {
        for (jsonElement in getOrFail("infobox").jsonArray) {
            if (jsonElement.jsonObject.getStringOrFail("key") == key) {
                yieldAll(jsonElement.jsonObject.getOrFail("values").vSequence())
            }
        }
    }

    fun parseBatchSubjectRelations(
        element: JsonObject,
        getActors: (characterId: Int) -> List<PersonInfo>,
    ): BatchSubjectRelations {
        try {
            val id = element.getIntOrFail("id")
            val characters = element.getOrFail("characters").jsonArray.mapIndexed { index, relatedCharacter ->
                check(relatedCharacter is JsonObject)

                val role = when (val type = relatedCharacter.getIntOrFail("type")) {
                    1 -> CharacterRole.MAIN
                    2 -> CharacterRole.SUPPORTING
                    3 -> CharacterRole.GUEST
                    else -> CharacterRole(type)
                }

                val character = relatedCharacter.getOrFail("character").jsonObject

                val characterId = character.getIntOrFail("id")
                RelatedCharacterInfo(
                    index = index,
                    character = CharacterInfo(
                        id = characterId,
                        name = character.getStringOrFail("name"),
                        nameCn = character.infobox("简体中文名").firstOrNull() ?: "",
                        actors = getActors(characterId),
                        imageMedium = character.getOrFail("images").jsonObjectOrNull?.getStringOrFail("medium") ?: "",
                        imageLarge = character.getOrFail("images").jsonObjectOrNull?.getStringOrFail("large") ?: "",
                    ),
                    role = role,
                )
            }

            val persons = element.getOrFail("persons").jsonArray.mapIndexed { index, relatedPerson ->
                check(relatedPerson is JsonObject)
                val person = relatedPerson.getOrFail("person").jsonObject
                RelatedPersonInfo(
                    index,
                    personInfo = parsePerson(person),
                    position = PersonPosition(relatedPerson.getIntOrFail("position")),
                )
            }

            return BatchSubjectRelations(
                subjectId = id,
                relatedCharacterInfoList = characters,
                relatedPersonInfoList = persons,
            )
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to parse subject relations for subject id ${element["id"]}: $element",
                e,
            )
        }
    }

    fun parsePerson(person: JsonObject) = PersonInfo(
        id = person.getIntOrFail("id"),
        name = person.getStringOrFail("name"),
        type = PersonType.fromId(person.getIntOrFail("type")),
        //                careers = person.infobox("职业").map { PersonCareer.valueOf(it) }.toList(),
        careers = emptyList(),
        imageLarge = person["images"]?.jsonObjectOrNull?.getStringOrFail("large") ?: "",
        imageMedium = person["images"]?.jsonObjectOrNull?.getStringOrFail("medium") ?: "",
        summary = person.getString("summary") ?: "",
        locked = person.getIntOrFail("lock") == 1,
        nameCn = person.infobox("简体中文名").firstOrNull() ?: "",
    )

    inline fun forEachCharacter(
        element: JsonObject,
        action: (subjectId: Int, characterId: Int) -> Unit,
    ) {
        val subjectId = element.getIntOrFail("id")
        element.getOrFail("characters").jsonArray
            .forEach { relatedCharacter ->
                val characterId = relatedCharacter.jsonObject
                    .getOrFail("character").jsonObject
                    .getIntOrFail("id")

                action(subjectId, characterId)
            }
    }
}
