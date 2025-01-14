/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.episode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.toList
import me.him188.ani.app.data.models.schedule.AnimeScheduleInfo
import me.him188.ani.app.data.models.schedule.AnimeSeasonId
import me.him188.ani.app.data.models.subject.SubjectRecurrence
import me.him188.ani.app.data.network.AnimeScheduleService
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryServiceUnavailableException
import me.him188.ani.utils.logging.error
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class AnimeScheduleRepository(
    private val animeScheduleService: AnimeScheduleService,
//    private val subjectCollectionRepository: SubjectCollectionRepository,
    private val updatePeriod: Duration = 1.hours,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : Repository(defaultDispatcher) {
    private val refreshTicker = flow {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(updatePeriod)
        }
    }

    /**
     * 获取所有新番季度的 ID
     */
    private fun animeSeasonIdsFlow(): Flow<List<AnimeSeasonId>> =
        refreshTicker.mapLatest { animeScheduleService.getSeasonIds() }

    /**
     * 获取指定季度的新番时间表
     */
    private fun animeScheduleFlow(seasonId: AnimeSeasonId): Flow<AnimeScheduleInfo?> =
        refreshTicker.mapLatest { animeScheduleService.getScheduleInfo(seasonId) }

    suspend fun getSubjectRecurrence(subjectId: Int): SubjectRecurrence? {
        try {
            return batchGetSubjectRecurrence(listOf(subjectId)).first()
//            val seasons = animeSeasonIdsFlow().firstOrNull()?.take(4) ?: return null
//            val schedules = seasons.asFlow().mapNotNull { animeScheduleFlow(it).firstOrNull() }
//            return schedules.mapNotNull { it.findRecurrence(subjectId) }.firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: RepositoryServiceUnavailableException) {
            logger.error(e) { "Failed to get subject recurrence due to RepositoryServiceUnavailableException. Ignoring." }
            return null
        }
    }

    suspend fun batchGetSubjectRecurrence(subjectIds: List<Int>): List<SubjectRecurrence?> {
        return animeScheduleService.batchGetSubjectRecurrences(subjectIds)
    }

    /**
     * 获取最近两季度的新番时间表
     */
    fun recentSchedulesFlow(): Flow<List<AnimeScheduleInfo>> =
        animeSeasonIdsFlow().mapLatest { seasons ->
            seasons.take(2)
                .map {
                    suspend { animeScheduleService.getScheduleInfo(it) }.asFlow() // emits 1 item
                }
                .merge() // launches 4 coroutines, emits at most 4 items
                .filterNotNull() // should not be null, just defensive programming
                .toList()
        }.flowOn(defaultDispatcher)


//    fun recentScheduleSubjectsFlow(): Flow<List<SubjectCollectionInfo>> =
//        recentSchedulesFlow()
//            .mapLatest { schedules ->
//                schedules.flatMap { it.list }
//            }.flatMapLatest { list ->
//                combine(
//                    list.map {
//                        subjectCollectionRepository.subjectCollectionFlow(it.bangumiId)
//                    },
//                ) {
//                    it.toList()
//                }
//            }
}
