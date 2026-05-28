/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database.dao

import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.subject.CharacterRole
import me.him188.ani.app.data.models.subject.PersonPosition
import me.him188.ani.app.data.persistent.database.entity.CharacterActorEntity
import me.him188.ani.app.data.persistent.database.entity.CharacterEntity
import me.him188.ani.app.data.persistent.database.entity.PersonEntity
import me.him188.ani.app.data.persistent.database.entity.SubjectCharacterRelationEntity
import me.him188.ani.app.data.persistent.database.entity.SubjectPersonRelationEntity

/**
 * @since 4.0.0-alpha04
 */
@Dao
interface SubjectRelationsDao {
    @Upsert
    suspend fun upsertSubjectCharacterRelations(list: List<SubjectCharacterRelationEntity>)

    @Upsert
    suspend fun upsertSubjectPersonRelations(list: List<SubjectPersonRelationEntity>)

    @Upsert
    suspend fun upsertPersons(list: List<PersonEntity>)

    @Upsert
    suspend fun upsertCharacters(list: List<CharacterEntity>)

    @Upsert
    suspend fun upsertCharacterActors(list: List<CharacterActorEntity>)

    @Query(
        """
        SELECT * FROM person NATURAL JOIN subject_person as s
        WHERE s.subjectId = :subjectId
    """,
    )
    @Transaction
    fun subjectRelatedPersonsFlow(subjectId: Int): Flow<List<RelatedPersonView>>

    @Query(
        """
        SELECT * FROM character NATURAL JOIN subject_character as s
        WHERE s.subjectId = :subjectId
    """,
    )
    @Transaction
    fun subjectRelatedCharactersFlow(subjectId: Int): Flow<List<RelatedCharacterView>>

    @Query(
        """
        SELECT * FROM character_actor JOIN person ON character_actor.actorPersonId = person.personId
        WHERE characterId IN (:characterIds)
    """,
    )
    @Transaction
    fun characterActorsFlow(characterIds: IntArray): Flow<List<CharacterActorView>>
}

//data class RelatedPersonView(
//    val personId: Int = 0,
//    val name: String,
//    val nameCn: String,
//    val imageLarge: String,
//    val imageMedium: String,
//    val type: PersonType,
//    val summary: String,
//
//
//    val subjectId: Int,
//    val index: Int,
//    val position: PersonPosition,
//)

data class RelatedPersonView(
    val subjectId: Int,
    val index: Int,
    val position: PersonPosition,

    @Embedded
    val person: PersonEntity,
)

data class RelatedCharacterView(
    val subjectId: Int,
    val index: Int, // 在作品中的序号, 一般主角靠前
    val role: CharacterRole,

    @Embedded
    val character: CharacterEntity,
)

data class CharacterActorView(
    val characterId: Int,
    val actorPersonId: Int,
    @Embedded
    val person: PersonEntity,
)
