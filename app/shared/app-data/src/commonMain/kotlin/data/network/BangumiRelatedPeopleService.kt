/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.him188.ani.app.data.models.subject.CharacterType
import me.him188.ani.app.data.models.subject.Images
import me.him188.ani.app.data.models.subject.PersonCareer
import me.him188.ani.app.data.models.subject.PersonType
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectRelation
import me.him188.ani.datasources.bangumi.BangumiClient
import me.him188.ani.datasources.bangumi.models.BangumiCharacterType
import me.him188.ani.datasources.bangumi.models.BangumiPersonCareer
import me.him188.ani.datasources.bangumi.models.BangumiPersonImages
import me.him188.ani.datasources.bangumi.models.BangumiPersonType
import me.him188.ani.datasources.bangumi.models.BangumiSubjectType
import me.him188.ani.datasources.bangumi.models.BangumiV0SubjectRelation

private val json = Json {
    ignoreUnknownKeys = true
}

class BangumiRelatedPeopleService(
    private val client: BangumiClient,
) {
    @Serializable
    private data class QInfobox(
        val key: String,
        val values: List<QInfoboxValue> = emptyList(),
    )

    @Serializable
    private data class QInfoboxValue(
        val k: String?,
        val v: String,
    )

    @Serializable
    private data class QCharacterOrPerson(
        val id: Int,
        val infobox: List<QInfobox>
    ) {
        val chineseName: String?
            get() {
                return infobox.find { it.key == "简体中文名" }?.values?.firstOrNull()?.v
            }
    }

//    /**
//     * 查询该条目的角色列表. 返回的 flow 至少会 emit 一个 list.
//     */
//    fun relatedCharactersFlow(subjectId: Int): Flow<List<RelatedCharacterInfo>> {
//        return flow {
//            val characters = withContext(Dispatchers.IO) {
//                client.getApi().getRelatedCharactersBySubjectId(subjectId).body()
//            }
//
//            // 查 GraphQL 要一秒, 先按日文显示 
//            emit(
//                characters.map { character ->
//                    character.toRelatedCharacterInfo(
//                        chineseName = "",
//                        getPersonChineseName = { "" },
//                    )
//                },
//            )
//            coroutineScope {
//                val qCharacters = async { queryGraphQLCharacters(characters.asSequence().map { it.id }) }
//                val qPersons = queryGraphQLPersons(
//                    characters.asSequence().flatMap { c -> c.actors.orEmpty().map { it.id } },
//                )
//                emit(
//                    characters.map { character ->
//                        character.toRelatedCharacterInfo(
//                            chineseName = qCharacters.await().find { it.id == character.id }?.chineseName ?: "",
//                            getPersonChineseName = { person ->
//                                qPersons.find { it.id == person }?.chineseName ?: ""
//                            },
//                        )
//                    },
//                )
//            }
//        }
//    }

    private suspend fun queryGraphQLCharacters(
        ids: Sequence<Int>,
    ): List<QCharacterOrPerson> {
        val distinctIds = ids.distinct().toList()
        if (distinctIds.isEmpty()) return emptyList()
        return client.executeGraphQL(
            "BangumiRelatedPeopleService.queryGraphQLCharacters",
            """
                query MyQuery {
                  ${distinctIds.joinToString("\n") { "c${it}: character(id: ${it}) { ...CharacterFragment }" }}
                }
        
                fragment CharacterFragment on Character {
                  infobox {
                    values {
                      k
                      v
                    }
                    key
                  }
                  id
                }
            """.trimIndent(),
        )["data"]!!.jsonObject.values.map {
            json.decodeFromJsonElement(QCharacterOrPerson.serializer(), it)
        }
    }

    private suspend fun queryGraphQLPersons(
        ids: Sequence<Int>,
    ): List<QCharacterOrPerson> {
        val distinctIds = ids.distinct().toList()
        if (distinctIds.isEmpty()) return emptyList()
        return client.executeGraphQL(
            "BangumiRelatedPeopleService.queryGraphQLPersons",
            """
                query MyQuery {
                  ${distinctIds.joinToString("\n") { "c${it}: person(id: ${it}) { ...CharacterFragment }" }}
                }
        
                fragment CharacterFragment on Person {
                  infobox {
                    values {
                      k
                      v
                    }
                    key
                  }
                  id
                }
            """.trimIndent(),
        )["data"]!!.jsonObject.values.map {
            json.decodeFromJsonElement(QCharacterOrPerson.serializer(), it)
        }
    }


//    /**
//     * 查询该条目的 staff.
//     */
//    fun relatedPersonsFlow(subjectId: Int): Flow<List<RelatedPersonInfo>> {
//        return flow {
//            val persons = withContext(Dispatchers.IO) {
//                client.getApi().getRelatedPersonsBySubjectId(subjectId).body()
//            }
//            emit(
//                persons.mapIndexed { index, character ->
//                    character.toRelatedPersonInfo(
//                        index,
//                        chineseName = "",
//                    )
//                },
//            )
//            // 查 GraphQL 要一秒, 先按日文显示 
//            val q = queryGraphQLPersons(persons.asSequence().map { it.id })
//            emit(
//                persons.mapIndexed { index, character ->
//                    character.toRelatedPersonInfo(
//                        index,
//                        chineseName = q.find { it.id == character.id }?.chineseName ?: "",
//                    )
//                },
//            )
//        }
//    }

    fun relatedSubjectsFlow(subjectId: Int): Flow<List<RelatedSubjectInfo>> {
        return flow {
            val subjects = withContext(Dispatchers.IO) {
                client.api { getRelatedSubjectsBySubjectId(subjectId).body() }
            }
            emit(subjects.mapNotNull { it.toRelatedSubjectInfo() })
        }
    }
}

private fun BangumiV0SubjectRelation.toRelatedSubjectInfo(): RelatedSubjectInfo? {
    if (type != BangumiSubjectType.Anime.value) return null
    return RelatedSubjectInfo(id, convertBangumiSubjectRelation(relation), name, nameCn, images?.large)
}

private fun convertBangumiSubjectRelation(relation: String): SubjectRelation? = when (relation) {
    "续集" -> SubjectRelation.SEQUEL
    "前传" -> SubjectRelation.PREQUEL
    "衍生" -> SubjectRelation.DERIVED
    "番外篇" -> SubjectRelation.SPECIAL
    else -> null
}

//private fun BangumiRelatedPerson.toRelatedPersonInfo(index: Int, chineseName: String): RelatedPersonInfo {
//    return RelatedPersonInfo(
//        index = index,
//        personInfo = PersonInfo(
//            id = id,
//            name = name,
//            type = type.toPersonType(),
//            careers = career.map { it.toPersonCareer() },
//            summary = null,
//            locked = null,
//            nameCn = chineseName,
//            imageLarge = images?.large ?: "",
//            imageMedium = images?.medium ?: "",
//        ),
//        position = PersonPosition.fromRelation(relation),
//    )
//}
//
//private fun BangumiRelatedCharacter.toRelatedCharacterInfo(
//    chineseName: String,
//    getPersonChineseName: (Int) -> String = { "" },
//): RelatedCharacterInfo {
//    return RelatedCharacterInfo(
//        id = id,
//        originalName = name,
//        type = type.toCharacterType(),
//        role = relation,
//        images = images?.toImages(),
//        actors = actors?.map { it.toPersonInfo(getPersonChineseName(it.id)) }.orEmpty(),
//        chineseName = chineseName,
//    )
//}

private fun BangumiCharacterType.toCharacterType(): CharacterType {
    return when (this) {
        BangumiCharacterType.Character -> CharacterType.CHARACTER
        BangumiCharacterType.Mechanic -> CharacterType.MECHANIC
        BangumiCharacterType.Ship -> CharacterType.SHIP
        BangumiCharacterType.Organization -> CharacterType.ORGANIZATION
    }
}

private fun BangumiPersonImages.toImages(): Images {
    return Images(
        large = large,
        medium = medium,
        small = small,
        grid = grid,
    )
}
//
//private fun BangumiPerson.toPersonInfo(chineseName: String): PersonInfo {
//    return PersonInfo(
//        id = id,
//        name = name,
//        type = type.toPersonType(),
//        careers = career.map { it.toPersonCareer() }, ,
//        shortSummary = shortSummary,
//        locked = locked,
//        nameCn = chineseName,
//    )
//}

private fun BangumiPersonCareer.toPersonCareer(): PersonCareer {
    return when (this) {
        BangumiPersonCareer.PRODUCER -> PersonCareer.PRODUCER
        BangumiPersonCareer.MANGAKA -> PersonCareer.MANGAKA
        BangumiPersonCareer.ARTIST -> PersonCareer.ARTIST
        BangumiPersonCareer.SEIYU -> PersonCareer.SEIYU
        BangumiPersonCareer.WRITER -> PersonCareer.WRITER
        BangumiPersonCareer.ILLUSTRATOR -> PersonCareer.ILLUSTRATOR
        BangumiPersonCareer.ACTOR -> PersonCareer.ACTOR
    }
}

private fun BangumiPersonType.toPersonType(): PersonType {
    return when (this) {
        BangumiPersonType.Individual -> PersonType.Individual
        BangumiPersonType.Corporation -> PersonType.Corporation
        BangumiPersonType.Association -> PersonType.Association
    }
}
