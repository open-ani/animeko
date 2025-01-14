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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.app.data.models.subject.CharacterInfo
import me.him188.ani.app.data.models.subject.CharacterRole
import me.him188.ani.app.data.models.subject.LightEpisodeInfo
import me.him188.ani.app.data.models.subject.LightSubjectAndEpisodes
import me.him188.ani.app.data.models.subject.LightSubjectInfo
import me.him188.ani.app.data.models.subject.PersonInfo
import me.him188.ani.app.data.models.subject.PersonPosition
import me.him188.ani.app.data.models.subject.PersonType
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.utils.serialization.BigNum
import me.him188.ani.utils.serialization.getBooleanOrFail
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
            else -> emptySequence()
        }
    }

    private fun JsonObject.infobox(key: String): Sequence<String> = sequence {
        for (jsonElement in getOrFail("infobox").jsonArray) {
            if (jsonElement.jsonObject.getStringOrFail("key") == key) {
                yieldAll(jsonElement.jsonObject.getOrFail("values").vSequence())
            }
        }
    }

    fun parseBatchSubjectDetails(
        element: JsonObject,
    ): BatchSubjectDetails {
        return element.toBatchSubjectDetails()
    }

    fun parseBatchSubjectRelations(
        element: JsonObject,
        getActors: (characterId: Int) -> List<PersonInfo>,
    ): BatchSubjectRelations {
        try {
            val id = element.getIntOrFail("id")
            val characters = element.getOrFail("characters").jsonArray.mapIndexed { index, relatedCharacter ->
                check(relatedCharacter is JsonObject)

                val role = when (relatedCharacter.getIntOrFail("type")) {
                    1 -> CharacterRole.MAIN
                    2 -> CharacterRole.SUPPORTING
                    3 -> CharacterRole.GUEST
                    else -> throw IllegalStateException("Unexpected character type: $relatedCharacter")
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

    private fun JsonObject.toBatchSubjectDetails(): BatchSubjectDetails {
        val completionDate = (this.infobox("播放结束") + this.infobox("放送结束"))
            .firstOrNull()
            ?.let {
                PackedDate.parseFromDate(
                    it.replace('年', '-')
                        .replace('月', '-')
                        .removeSuffix("日"),
                )
            }
            ?: PackedDate.Invalid

        val subjectId = getIntOrFail("id")

        val characters = getOrFail("characters").jsonArray.mapIndexed { _, relatedCharacter ->
            check(relatedCharacter is JsonObject)

            val role = when (relatedCharacter.getIntOrFail("type")) {
                1 -> CharacterRole.MAIN
                2 -> CharacterRole.SUPPORTING
                3 -> CharacterRole.GUEST
                else -> throw IllegalStateException("Unexpected character type: $relatedCharacter")
            }

            val character = relatedCharacter.getOrFail("character").jsonObject

            val characterId = character.getIntOrFail("id")
            LightRelatedCharacterInfo(
                id = characterId,
                name = character.getStringOrFail("name"),
                nameCn = character.infobox("简体中文名").firstOrNull() ?: "",
                role = role,
            )
        }

        val persons = buildList {
            for (jsonElement in getOrFail("infobox").jsonArray) {
                val position = PersonPosition.findByName(jsonElement.jsonObject.getStringOrFail("key"))
                if (position != PersonPosition.Invalid) {
                    jsonElement.jsonObject.getOrFail("values").vSequence().forEach {
                        add(LightRelatedPersonInfo(it, position))
                    }
                }
            }
        }

        return try {
            BatchSubjectDetails(
                SubjectInfo(
                    subjectId = subjectId,
                    subjectType = SubjectType.ANIME,
                    name = getStringOrFail("name"),
                    nameCn = getStringOrFail("name_cn"),
                    summary = getStringOrFail("summary"),
                    nsfw = getBooleanOrFail("nsfw"),
                    imageLarge = getOrFail("images").jsonObjectOrNull?.getString("large") ?: "", // 有少数没有
                    totalEpisodes = getIntOrFail("eps"),
                    airDate = PackedDate.parseFromDate(getOrFail("airtime").jsonObject.getStringOrFail("date")),
                    tags = getOrFail("tags").jsonArray.map {
                        val obj = it.jsonObject
                        Tag(
                            obj.getStringOrFail("name"),
                            obj.getIntOrFail("count"),
                        )
                    },
                    aliases = infobox("别名").filter { it.isNotEmpty() }.toList(),
                    ratingInfo = getOrFail("rating").jsonObject.let { rating ->
                        RatingInfo(
                            rank = rating.getIntOrFail("rank"),
                            total = rating.getIntOrFail("total"),
                            count = rating.getOrFail("count").jsonArray.let { array ->
                                RatingCounts(
                                    s1 = array[0].jsonPrimitive.int,
                                    s2 = array[1].jsonPrimitive.int,
                                    s3 = array[2].jsonPrimitive.int,
                                    s4 = array[3].jsonPrimitive.int,
                                    s5 = array[4].jsonPrimitive.int,
                                    s6 = array[5].jsonPrimitive.int,
                                    s7 = array[6].jsonPrimitive.int,
                                    s8 = array[7].jsonPrimitive.int,
                                    s9 = array[8].jsonPrimitive.int,
                                    s10 = array[9].jsonPrimitive.int,
                                )
                            },
                            score = rating.getStringOrFail("score"),
                        )
                    },
                    collectionStats = getOrFail("collection").jsonObject.let { collection ->
                        SubjectCollectionStats(
                            wish = collection.getIntOrFail("wish"),
                            doing = collection.getIntOrFail("doing"),
                            done = collection.getIntOrFail("collect"),
                            onHold = collection.getIntOrFail("on_hold"),
                            dropped = collection.getIntOrFail("dropped"),
                        )
                    },
                    completeDate = completionDate,
                ),
                mainEpisodeCount = getOrFail("episodes").jsonArray
                    .asSequence()
                    .map {
                        it.jsonObject
                    }
                    .filter { it.getIntOrFail("type") == 0 }
                    .count().let {
                        if (it == 100) {
                            getIntOrFail("eps") // 太多了所以没查完整, 退一步采用 eps (包含所有剧集类型)
                        } else {
                            it
                        }
                    },
                LightSubjectRelations(
                    lightRelatedPersonInfoList = persons,
                    lightRelatedCharacterInfoList = characters,
                ),
            )
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to parse subject details for subject id ${this["id"]}: $this",
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

    fun parseLightSubjectAndEpisodes(jsonObject: JsonObject): LightSubjectAndEpisodes {
        val episodes = jsonObject.getOrFail("episodes").jsonArray.map {
            val episode = it.jsonObject
            LightEpisodeInfo(
                episodeId = episode.getIntOrFail("id"),
                name = episode.getStringOrFail("name"),
                nameCn = episode.getStringOrFail("name_cn"),
                airDate = PackedDate.parseFromDate(episode.getStringOrFail("airdate")),
                sort = EpisodeSort(
                    BigNum(episode.getStringOrFail("sort")),
                    type = parseBangumiEpisodeType(episode.getIntOrFail("type")),
                ),
                ep = null, // TODO: parseLightSubjectAndEpisodes does not support EP because bangumi graphql API does not provide it
//                    EpisodeSort(
//                    episode.getIntOrFail("ep"),
//                    type = parseBangumiEpisodeType(episode.getIntOrFail("type")),
//                ),
            )
        }

        return LightSubjectAndEpisodes(
            subject = LightSubjectInfo(
                subjectId = jsonObject.getIntOrFail("id"),
                name = jsonObject.getStringOrFail("name"),
                nameCn = jsonObject.getStringOrFail("name_cn"),
                imageLarge = jsonObject.getOrFail("images").jsonObjectOrNull?.getStringOrFail("large") ?: "",
            ),
            episodes = episodes,
        )
    }

    private fun parseBangumiEpisodeType(type: Int): EpisodeType? {
        return when (type) {
            0 -> EpisodeType.MainStory
            1 -> EpisodeType.SP
            2 -> EpisodeType.OP
            3 -> EpisodeType.ED
            4 -> EpisodeType.PV
            5 -> EpisodeType.MAD
            else -> null
        }
    }
}
