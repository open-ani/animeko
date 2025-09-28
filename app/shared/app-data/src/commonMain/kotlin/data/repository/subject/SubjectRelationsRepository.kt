/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.subject.CharacterInfo
import me.him188.ani.app.data.models.subject.PersonInfo
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectSeriesInfo
import me.him188.ani.app.data.network.AniSubjectRelationIndexService
import me.him188.ani.app.data.network.BatchSubjectRelations
import me.him188.ani.app.data.network.SubjectService
import me.him188.ani.app.data.persistent.database.dao.RelatedCharacterView
import me.him188.ani.app.data.persistent.database.dao.RelatedPersonView
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionDao
import me.him188.ani.app.data.persistent.database.dao.SubjectRelationsDao
import me.him188.ani.app.data.persistent.database.entity.CharacterActorEntity
import me.him188.ani.app.data.persistent.database.entity.CharacterEntity
import me.him188.ani.app.data.persistent.database.entity.PersonEntity
import me.him188.ani.app.data.persistent.database.entity.SubjectCharacterRelationEntity
import me.him188.ani.app.data.persistent.database.entity.SubjectPersonRelationEntity
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryServiceUnavailableException
import me.him188.ani.utils.platform.collections.mapToIntArray
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

sealed class SubjectRelationsRepository(
    defaultDispatcher: CoroutineContext = Dispatchers.Default
) : Repository(defaultDispatcher) {
    /**
     * 获取指定条目的所有续集 ID 列表, 包含正片和 SP 等特殊剧集. (仅包含 Anime 类型)
     */
    abstract fun subjectSequelSubjectIdsFlow(subjectId: Int): Flow<List<Int>>

    /**
     * 获取指定条目的所有续集列表, 包含正片和 SP 等特殊剧集. (仅包含 Anime 类型)
     */
    abstract fun subjectSequelSubjectsFlow(subjectId: Int): Flow<List<SubjectCollectionInfo>>

    /**
     * 获取指定条目的所有续集的名称列表, 包含正片和 SP 等特殊剧集, 并排除 [subjectId] 的名称. (仅包含 Anime 类型)
     */
    abstract fun subjectSeriesInfoFlow(subjectId: Int): Flow<SubjectSeriesInfo>

    abstract fun subjectRelatedPersonsFlow(subjectId: Int): Flow<List<RelatedPersonInfo>>
    abstract fun subjectRelatedCharactersFlow(subjectId: Int): Flow<List<RelatedCharacterInfo>>
}

class DefaultSubjectRelationsRepository(
    private val subjectCollectionDao: SubjectCollectionDao,
    private val subjectRelationsDao: SubjectRelationsDao,
    private val subjectService: SubjectService,
    private val subjectCollectionRepository: SubjectCollectionRepository,
    private val aniSubjectRelationIndexService: AniSubjectRelationIndexService,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
    private val autoRefreshPeriod: Duration = 1.hours,
    private val cacheExpiry: Duration = 1.hours,
) : SubjectRelationsRepository(defaultDispatcher) {
    override fun subjectSequelSubjectIdsFlow(subjectId: Int): Flow<List<Int>> = flow {
        emit(
            kotlinx.coroutines.withTimeoutOrNull(10_000) {
                // 这服务极快, 不会超时. 10 秒还没完, 只能是服务器重启了一下, 正在构造索引
                aniSubjectRelationIndexService.getSubjectRelationIndex(subjectId).sequelSubjects
            }
                ?: throw RepositoryServiceUnavailableException("Failed to fetch subject sequel subjects for $subjectId due to timeout"),
        )
    }.flowOn(defaultDispatcher) // no auto refresh

    override fun subjectSequelSubjectsFlow(subjectId: Int): Flow<List<SubjectCollectionInfo>> {
        // no auto refresh
        return subjectSequelSubjectIdsFlow(subjectId)
            .flatMapLatest { list ->
                if (list.isEmpty()) { // combine(emptyList()) 不会 emit
                    return@flatMapLatest flowOf(emptyList())
                }
                combine(
                    list.map { relatedSubjectId ->
                        subjectCollectionRepository.subjectCollectionFlow(relatedSubjectId)
                    },
                ) {
                    it.toList()
                }
            }.flowOn(defaultDispatcher)
    }

    override fun subjectSeriesInfoFlow(subjectId: Int): Flow<SubjectSeriesInfo> = flow {
        emit(
            aniSubjectRelationIndexService.getSubjectRelationIndex(subjectId),
        )
    }.combine(subjectCollectionRepository.subjectCollectionFlow(subjectId)) { relations, requestingSubject ->
        combine(
            (relations.sequelSubjects.toSet() + relations.seriesMainSubjectIds).map {
                subjectCollectionRepository.subjectCollectionFlow(it)
            },
        ) { subjectCollectionInfos ->
            SubjectSeriesInfo.compute(
                requestingSubject = requestingSubject,
            )
        }
    }.flatMapLatest {
        it
    }.flowOn(defaultDispatcher)

//    override fun subjectSequelSubjectNamesFlow(subjectId: Int): Flow<Set<String>> {
//        return subjectSequelSubjectsFlow(subjectId)
//            .combine(subjectCollectionRepository.subjectCollectionFlow(subjectId)) { list, requestingSubject ->
//                list.flatMapTo(mutableSetOf()) { it.subjectInfo.allNames }.apply {
//                    removeAll { sequelName ->
//                        // 如果续集名称存在于当前名称中, 则删除, 否则可能导致过滤掉当前季度的条目
//                        requestingSubject.subjectInfo.allNames.any { it.contains(sequelName, ignoreCase = true) }
//                    }
//                }
//            }.onEach {
//                logger.info { "subjectSequelSubjectNamesFlow($subjectId): " + it.joinToString() }
//            }.flowOn(defaultDispatcher)
//    }

    override fun subjectRelatedPersonsFlow(subjectId: Int): Flow<List<RelatedPersonInfo>> {
        return subjectCollectionRepository.subjectCollectionFlow(subjectId)
            .autoRefresh()
            .flatMapLatest { subjectCollection ->
                if ((currentTimeMillis() - subjectCollection.cachedCharactersUpdated).milliseconds > cacheExpiry) {
                    fetchAndSaveSubjectRelations(subjectId)
                }

                subjectRelationsDao.subjectRelatedPersonsFlow(subjectId).map { list ->
                    list.mapTo(ArrayList(list.size)) {
                        it.toRelatedPersonInfo()
                    }.apply {
                        sortWith(RelatedPersonInfo.ImportanceOrder)
                    }
                }
            }.flowOn(defaultDispatcher)
    }

    override fun subjectRelatedCharactersFlow(subjectId: Int): Flow<List<RelatedCharacterInfo>> {
        return subjectCollectionRepository.subjectCollectionFlow(subjectId)
            .autoRefresh()
            .flatMapLatest { subjectCollection ->
                if ((currentTimeMillis() - subjectCollection.cachedCharactersUpdated).milliseconds > cacheExpiry) {
                    fetchAndSaveSubjectRelations(subjectId)
                }

                subjectRelationsDao.subjectRelatedCharactersFlow(subjectId).flatMapLatest { list ->
                    subjectRelationsDao.characterActorsFlow(list.mapToIntArray { it.character.characterId })
                        .map { actors ->
                            list.mapTo(ArrayList(list.size)) { relatedCharacterView ->
                                val characterId = relatedCharacterView.character.characterId
                                relatedCharacterView.toRelatedCharacterInfo(
                                    actors = actors
                                        .asSequence()
                                        .filter { it.characterId == characterId }
                                        .map { it.person.toPersonInfo() }
                                        .toList(),
                                )
                            }.apply {
                                sortWith(RelatedCharacterInfo.ImportanceOrder)
                            }
                        }
                }
            }.flowOn(defaultDispatcher)
    }

    private fun <T> Flow<T>.autoRefresh() = refreshTicker().flatMapLatest { this@autoRefresh }

    private fun refreshTicker() = flow {
        while (true) {
            emit(Unit)
            delay(autoRefreshPeriod)
        }
    }

    private suspend fun fetchAndSaveSubjectRelations(subjectId: Int) {
        val batch = subjectService.getSubjectRelations(subjectId, withCharacterActors = true)
        subjectRelationsDao.upsertPersons(batch.allPersons.map { it.toEntity() }.toList())
        subjectRelationsDao.upsertCharacters(batch.relatedCharacterInfoList.map { it.character.toEntity() })
        subjectRelationsDao.upsertCharacterActors(batch.characterActorRelations().toList())

        // 必须先插入前三个, 再插入 relations, 否则会 violate foreign key constraint

        subjectRelationsDao.upsertSubjectPersonRelations(
            batch.relatedPersonInfoList.map { it.toRelationEntity(subjectId) },
        )
        subjectRelationsDao.upsertSubjectCharacterRelations(
            batch.relatedCharacterInfoList.map { it.toRelationEntity(subjectId) },
        )
        subjectCollectionDao.updateCachedRelationsUpdated(subjectId)
    }

}

private fun RelatedCharacterView.toRelatedCharacterInfo(
    actors: List<PersonInfo>,
): RelatedCharacterInfo {
    return RelatedCharacterInfo(
        index = index,
        character = character.toCharacterInfo(actors),
        role = role,
    )
}

private fun CharacterEntity.toCharacterInfo(actors: List<PersonInfo>): CharacterInfo {
    return CharacterInfo(
        id = characterId,
        name = name,
        nameCn = nameCn,
        actors = actors,
        imageLarge = imageLarge,
        imageMedium = imageMedium,
    )
}

private fun RelatedPersonView.toRelatedPersonInfo(): RelatedPersonInfo {
    return RelatedPersonInfo(
        index = index,
        personInfo = person.toPersonInfo(),
        position = position,
    )
}

private fun PersonEntity.toPersonInfo(): PersonInfo {
    return PersonInfo(
        id = personId,
        name = name,
        type = type,
        careers = emptyList(),
        imageLarge = imageLarge,
        imageMedium = imageMedium,
        summary = summary,
        locked = false,
        nameCn = nameCn,
    )
}


private fun BatchSubjectRelations.characterActorRelations() =
    relatedCharacterInfoList.asSequence().flatMap { relatedCharacterInfo ->
        relatedCharacterInfo.character.actors.asSequence().map { person ->
            CharacterActorEntity(relatedCharacterInfo.character.id, person.id)
        }
    }

private fun CharacterInfo.toEntity(): CharacterEntity {
    return CharacterEntity(
        characterId = id,
        name = name,
        nameCn = nameCn,
        imageLarge = imageLarge,
        imageMedium = imageMedium,
    )
}

private fun PersonInfo.toEntity(): PersonEntity {
    return PersonEntity(
        personId = id,
        name = name,
        nameCn = nameCn,
        type = type,
        imageLarge = imageLarge,
        imageMedium = imageMedium,
        summary = summary,
    )
}

private fun RelatedPersonInfo.toRelationEntity(subjectId: Int): SubjectPersonRelationEntity {
    return SubjectPersonRelationEntity(
        subjectId = subjectId,
        index = index,
        personId = personInfo.id,
        position = position,
    )
}

private fun RelatedCharacterInfo.toRelationEntity(subjectId: Int): SubjectCharacterRelationEntity {
    return SubjectCharacterRelationEntity(
        subjectId = subjectId,
        index = index,
        characterId = character.id,
        role = role,
    )
}
