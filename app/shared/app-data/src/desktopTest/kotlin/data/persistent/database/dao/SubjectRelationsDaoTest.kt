/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database.dao

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.subject.PersonPosition
import me.him188.ani.app.data.models.subject.PersonType
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.persistent.database.createTestAniDatabase
import me.him188.ani.app.data.persistent.database.entity.PersonEntity
import me.him188.ani.app.data.persistent.database.entity.SubjectPersonRelationEntity
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.test.Test
import kotlin.test.assertEquals

class SubjectRelationsDaoTest {
    private fun runDatabaseTest(block: suspend (AniDatabase) -> Unit) = runBlocking {
        val database = createTestAniDatabase()
        try {
            database.subjectCollection().upsert(subjectCollection(SUBJECT_ID))
            block(database)
        } finally {
            database.close()
        }
    }

    private fun subjectCollection(subjectId: Int) = SubjectCollectionEntity(
        subjectId = subjectId,
        name = "test",
        nameCn = "测试",
        summary = "",
        nsfw = false,
        imageLarge = "",
        totalEpisodes = 12,
        airDate = PackedDate.Invalid,
        aliases = emptyList(),
        tags = emptyList(),
        collectionStats = SubjectCollectionStats.Zero,
        ratingInfo = RatingInfo.Empty,
        completeDate = PackedDate.Invalid,
        selfRatingInfo = SelfRatingInfo.Empty,
        collectionType = UnifiedCollectionType.DOING,
        recurrence = null,
        lastUpdated = 0,
        lastFetched = 0,
        cachedStaffUpdated = 0,
        cachedCharactersUpdated = 0,
    )

    private fun person(personId: Int) = PersonEntity(
        personId = personId,
        name = "person$personId",
        nameCn = "",
        imageLarge = "",
        imageMedium = "",
        type = PersonType.Individual,
        summary = "",
    )

    private fun relation(index: Int, personId: Int, position: PersonPosition) = SubjectPersonRelationEntity(
        subjectId = SUBJECT_ID,
        index = index,
        personId = personId,
        position = position,
    )

    @Test
    fun `同一人在同一条目的多个职位都保留`() = runDatabaseTest { database ->
        val dao = database.subjectRelations()
        dao.upsertPersons(listOf(person(100)))
        // 服务端按职位 id 升序返回, 导演在前, 分镜/原画在后
        dao.upsertSubjectPersonRelations(
            listOf(
                relation(index = 1, personId = 100, position = PersonPosition.Director),
                relation(index = 8, personId = 100, position = PersonPosition.Storyboard),
                relation(index = 67, personId = 100, position = PersonPosition.KeyAnimation),
            ),
        )

        val rows = dao.subjectRelatedPersonsFlow(SUBJECT_ID).first()
        assertEquals(
            setOf(PersonPosition.Director, PersonPosition.Storyboard, PersonPosition.KeyAnimation),
            rows.map { it.position }.toSet(),
        )
        assertEquals(setOf(100), rows.map { it.person.personId }.toSet())
    }

    @Test
    fun `同一人同一职位重复upsert只保留一行并更新index`() = runDatabaseTest { database ->
        val dao = database.subjectRelations()
        dao.upsertPersons(listOf(person(100)))
        dao.upsertSubjectPersonRelations(listOf(relation(index = 5, personId = 100, position = PersonPosition.Director)))
        dao.upsertSubjectPersonRelations(listOf(relation(index = 1, personId = 100, position = PersonPosition.Director)))

        val rows = dao.subjectRelatedPersonsFlow(SUBJECT_ID).first()
        assertEquals(1, rows.size)
        assertEquals(1, rows.single().index)
    }

    private companion object {
        const val SUBJECT_ID = 1
    }
}
