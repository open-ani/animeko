/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectRecurrence
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.toLocalDateOrNull
import kotlin.time.Duration.Companion.days

/**
 * 用于支持判断剧集是否已经播出.
 */
object EpisodeCompletionContext {
    private val clock = Clock.System
    private val UTC9 = UtcOffset(9)

    fun SubjectRecurrence?.mapAirDate(
        airDate: PackedDate,
    ): Instant? {
        if (this == null) {
            val localDate = airDate.toLocalDateOrNull() ?: return null
            return localDate.atTime(LocalTime(0, 0)).toInstant(UTC9)
        }
        return getExactAirTime(airDate, this)
    }

    /**
     * 是否一定已经播出了. ApproximatingEpisodeCompletionContext 可能会将还未播出的剧集判定为已经播出, 但不会相反.
     */
    fun EpisodeInfo.isKnownCompleted(recurrence: SubjectRecurrence?): Boolean {
        recurrence.mapAirDate(airDate)?.let {
            return clock.now() >= it
        }
        return false
    }

    /**
     * 是否一定还未播出
     */
    fun EpisodeInfo.isKnownOnAir(recurrence: SubjectRecurrence?): Boolean {
        recurrence.mapAirDate(airDate)?.let {
            return clock.now() < it
        }
        return false
    }

    fun getExactAirTime(
        airDate: PackedDate,
        recurrence: SubjectRecurrence,
    ): Instant? {
        if (airDate.isInvalid) return null
        if (recurrence.interval < 1.days) {
            return null // 误差在一天以内, 所以如果是每天更新的, 就无法支持
        }

        val airDateAsLocalDate = airDate.toLocalDateOrNull() ?: return null
        val target = airDateAsLocalDate.atTime(LocalTime(0, 0)).toInstant(UTC9)

        val subjectStart = recurrence.startTime
        if (target < subjectStart) {
            return subjectStart // 还没开播 
        }

        val interval = recurrence.interval
        val intervalsCount = ((target - subjectStart) / interval).toInt()

        // 找到最近的时间
        val closestTime = subjectStart.plus(interval * intervalsCount)
        val nextClosestTime = closestTime.plus(interval)

        // 判断是 closestTime 更接近还是 nextClosestTime 更接近
        return if ((closestTime - target).absoluteValue <= (nextClosestTime - target).absoluteValue) {
            closestTime
        } else {
            nextClosestTime
        }
    }
}
