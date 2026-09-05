/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.datetime.LocalTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectRecurrence
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.toLocalDateOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * 一集的播出时刻.
 *
 * @property instant 播出时刻.
 * @property exact `true` 表示 [instant] 是由连载周期 ([SubjectRecurrence]) 推算出的精确播出时刻;
 * `false` 表示无法推算出精确时刻 (没有连载周期, 周期短于一天, 或者 Bangumi 的上映日期与连载周期对不上),
 * 此时 [instant] 为 Bangumi 上映日期 (日本日历日) 当天 00:00 (UTC+9), 只有日期精度.
 */
data class EpisodeAirTime(
    val instant: Instant,
    val exact: Boolean,
)

/**
 * 用于支持判断剧集是否已经播出.
 *
 * 核心是 [resolveEpisodeAirTime]: 它与服务端 `/schedule/airing` 使用完全相同的算法,
 * 由 Bangumi 的上映日期和 bangumi-data 的连载周期计算一集的播出时刻.
 */
object EpisodeCompletionContext {
    private val clock = Clock.System
    private val UTC9 = UtcOffset(hours = 9)

    /**
     * 候选播出时刻允许晚于上映日期当天 00:00 (UTC+9) 的最大时长, 含边界.
     *
     * 深夜档 (例如周四 01:00 播出) 在 Bangumi 上通常记录为前一天 (周三), 此时候选时刻比 00:00 晚 24h..27.25h;
     * 整点 00:00 播出的节目恰好为 +24h, 因此边界必须为闭区间并用整数毫秒比较. 30h 对应 "30 小时制" 的上限.
     * 超过此值说明两个数据源对日期的说法不一致, 以 Bangumi 的日期为准, 退化为日期精度.
     */
    private val UPPER_BOUND_MILLIS: Long = 30.hours.inWholeMilliseconds
    private val ONE_DAY_MILLIS: Long = 1.days.inWholeMilliseconds

    /**
     * 计算一集的播出时刻. 算法与服务端完全一致, 修改时必须同步修改服务端
     * (生成的测试向量 `EpisodeAirTimeTestVectors` 在两个仓库中相同).
     *
     * - [airDate] 无效 (空, 只有年份, 或不是合法日期) 时返回 `null`;
     * - 没有 [recurrence], 或其周期短于一天时, 返回上映日当天 00:00 (UTC+9), [EpisodeAirTime.exact] 为 `false`;
     * - 否则取从 [SubjectRecurrence.startTime] 起, 不早于上映日 00:00 (UTC+9) 的第一个周期时刻
     *   (上映日早于首播时则取首播时刻): 若它晚于 00:00 不超过 30 小时 (含), 即为精确播出时刻;
     *   否则以 Bangumi 的日期为准, 退化为日期精度.
     *
     * 不会返回早于上映日 00:00 (UTC+9) 的时刻.
     */
    fun resolveEpisodeAirTime(
        airDate: PackedDate,
        recurrence: SubjectRecurrence?,
    ): EpisodeAirTime? {
        val localDate = airDate.toLocalDateOrNull() ?: return null
        val dayStart = localDate.atTime(LocalTime(0, 0)).toInstant(UTC9)
        if (recurrence == null) return EpisodeAirTime(dayStart, exact = false)

        val intervalMillis = recurrence.interval.inWholeMilliseconds
        // 周期短于一天 (含 bangumi-data 的 P0D 与非法的负周期) 无法定位到具体一集, 必须在任何除法之前处理
        if (intervalMillis < ONE_DAY_MILLIS) return EpisodeAirTime(dayStart, exact = false)

        val dayStartMillis = dayStart.toEpochMilliseconds()
        val startMillis = recurrence.startTime.toEpochMilliseconds()
        val diffMillis = dayStartMillis - startMillis // 可能为负 (上映日早于首播)

        // ceilDiv: 不早于 dayStart 的第一个周期序号; 上映日早于首播时取首播 (k = 0)
        val k = (-(-diffMillis).floorDiv(intervalMillis)).coerceAtLeast(0)
        val candidateMillis = startMillis + k * intervalMillis

        return if (candidateMillis - dayStartMillis <= UPPER_BOUND_MILLIS) {
            EpisodeAirTime(Instant.fromEpochMilliseconds(candidateMillis), exact = true)
        } else {
            EpisodeAirTime(dayStart, exact = false)
        }
    }

    /**
     * [resolveEpisodeAirTime] 的简写, 只返回播出时刻 (无论是否精确). 无法计算时返回 `null`.
     */
    fun SubjectRecurrence?.mapAirDate(
        airDate: PackedDate,
    ): Instant? = resolveEpisodeAirTime(airDate, this)?.instant

    /**
     * 是否一定已经播出了: 能计算出播出时刻 ([mapAirDate]) 且 [now] 不早于它.
     * 无法计算播出时刻 (例如没有上映日期) 时返回 `false`.
     */
    fun EpisodeInfo.isKnownCompleted(recurrence: SubjectRecurrence?, now: Instant): Boolean {
        val airTime = recurrence.mapAirDate(airDate) ?: return false
        return now >= airTime
    }

    /**
     * 以当前时间判断 [isKnownCompleted].
     */
    fun EpisodeInfo.isKnownCompleted(recurrence: SubjectRecurrence?): Boolean =
        isKnownCompleted(recurrence, clock.now())

    /**
     * 是否一定还未播出: 能计算出播出时刻 ([mapAirDate]) 且 [now] 早于它.
     * 无法计算播出时刻时返回 `false`.
     */
    fun EpisodeInfo.isKnownOnAir(recurrence: SubjectRecurrence?, now: Instant): Boolean {
        val airTime = recurrence.mapAirDate(airDate) ?: return false
        return now < airTime
    }

    /**
     * 以当前时间判断 [isKnownOnAir].
     */
    fun EpisodeInfo.isKnownOnAir(recurrence: SubjectRecurrence?): Boolean =
        isKnownOnAir(recurrence, clock.now())
}
