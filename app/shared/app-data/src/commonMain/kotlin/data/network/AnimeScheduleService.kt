/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import io.ktor.util.reflect.typeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.him188.ani.app.data.models.schedule.AnimeRecurrence
import me.him188.ani.app.data.models.schedule.AnimeScheduleInfo
import me.him188.ani.app.data.models.schedule.AnimeSeason
import me.him188.ani.app.data.models.schedule.AnimeSeasonId
import me.him188.ani.app.data.models.schedule.OnAirAnimeInfo
import me.him188.ani.app.data.models.subject.SubjectRecurrence
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.client.apis.ScheduleAniApi
import me.him188.ani.client.models.AniAnimeRecurrence
import me.him188.ani.client.models.AniAnimeSeason
import me.him188.ani.client.models.AniAnimeSeasonId
import me.him188.ani.client.models.AniLatestAnimeSchedules
import me.him188.ani.client.models.AniOnAirAnimeInfo
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class AnimeScheduleService(
    private val scheduleApi: ApiInvoker<ScheduleAniApi>,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_
) {
    suspend fun getSeasonIds(): List<AnimeSeasonId> {
        return withContext(ioDispatcher) {
            try {
                scheduleApi {
                    getAnimeSeasons().body().list.map {
                        it.toAnimeSeasonId()
                    }
                }
            } catch (e: Exception) {
                throw RepositoryException.wrapOrThrowCancellation(e)
            }
        }
    }

    /**
     * @return `null` if not found.
     */
    suspend fun getScheduleInfo(seasonId: AnimeSeasonId): AnimeScheduleInfo? = withContext(ioDispatcher) {
        val resp = try {
            scheduleApi {
                getAnimeSeason(seasonId.id).body()
            }
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                return@withContext null
            }
            throw e
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
        AnimeScheduleInfo(
            seasonId,
            resp.list.map {
                it.toAnimeScheduleInfo()
            },
        )
    }

    /**
     * @return `null` if not found.
     */
    suspend fun batchGetSubjectRecurrences(subjectIds: List<Int>): List<SubjectRecurrence?> {
        if (subjectIds.isEmpty()) {
            return emptyList()
        }
        return withContext(ioDispatcher) {
            try {
                scheduleApi {
                    val resp = getSubjectRecurrences(subjectIds)
                    resp.typedBody<AniBatchGetSubjectRecurrenceResponse>(typeInfo<AniBatchGetSubjectRecurrenceResponse>()).recurrences.map {
                        it?.toAnimeRecurrence()
                    }
                }
            } catch (e: Exception) {
                throw RepositoryException.wrapOrThrowCancellation(e)
            }
        }
    }

    suspend fun getLatestAnimeScheduleInfos(): List<AnimeScheduleInfo> = withContext(ioDispatcher) {
        try {
            scheduleApi {
                val resp = getLatestAnimeSeasons()
                resp.typedBody<AniLatestAnimeSchedules>(typeInfo<AniLatestAnimeSchedules>()).list.map { item ->
                    AnimeScheduleInfo(item.seasonId.toAnimeSeasonId(), item.list.map { it.toAnimeScheduleInfo() })
                }
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    @Serializable
    private data class AniBatchGetSubjectRecurrenceResponse(
        @SerialName(value = "recurrences") @Required val recurrences: List<AniAnimeRecurrence?> // note: nullable
    )
}

private fun AniOnAirAnimeInfo.toAnimeScheduleInfo(): OnAirAnimeInfo {
    return OnAirAnimeInfo(
        bangumiId = bangumiId,
        name = name,
        aliases = aliases,
        begin = begin?.let { Instant.parse(it) },
        recurrence = recurrence?.toAnimeRecurrence(),
        end = end?.let { Instant.parse(it) },
        mikanId = mikanId,
    )
}

private fun AniAnimeRecurrence.toAnimeRecurrence(): AnimeRecurrence? {
    return AnimeRecurrence(
        startTime = Instant.parse(startTime),
        interval = intervalMillis.milliseconds,
    )
}

private fun AniAnimeSeasonId.toAnimeSeasonId(): AnimeSeasonId {
    return AnimeSeasonId(
        year = year,
        season = AnimeSeason.fromQuarterNumber(season.quarterNumber)
            ?: throw IllegalArgumentException("Unknown season: $season"),
    )
}

private val AniAnimeSeason.quarterNumber: Int
    get() = when (this) {
        AniAnimeSeason.WINTER -> 1
        AniAnimeSeason.SPRING -> 2
        AniAnimeSeason.SUMMER -> 3
        AniAnimeSeason.AUTUMN -> 4
    }
