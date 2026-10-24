/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.time

import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.math.abs
import kotlin.time.Instant

/**
 * 时区目录中的一个时区. [offsetSeconds] 是生成目录那一时刻的 UTC 偏移.
 */
data class TimeZoneOption(
    val zoneId: String,
    val offsetSeconds: Int,
) {
    /**
     * 例如 `UTC+08:00`.
     */
    val offsetLabel: String get() = formatUtcOffset(offsetSeconds)

    /**
     * IANA ID 中 `/` 之后的部分, 例如 `Shanghai`.
     */
    val city: String get() = zoneId.substringAfter('/', "")
}

/**
 * 同一 Region 下的时区, [options] 按偏移升序、同偏移按 ID 字典序排列.
 */
data class TimeZoneSection(
    val region: String,
    val options: List<TimeZoneOption>,
)

/**
 * 时区目录收录的 IANA Region.
 *
 * `Etc`、`SystemV`、`US` 等旧别名, 以及 `GMT*` 与不含 `/` 的 ID 都不在其中.
 */
private val timeZoneRegions: List<String> = listOf(
    "Africa",
    "America",
    "Antarctica",
    "Arctic",
    "Asia",
    "Atlantic",
    "Australia",
    "Europe",
    "Indian",
    "Pacific",
)

/**
 * 生成时区目录: 只保留 `Region/City` 形式的 IANA ID, 按 Region 分组, 组间按 Region 名排序.
 *
 * 组内按 [now] 时刻的 UTC 偏移升序排列, 偏移相同时按 ID 字典序. 设备当前时区 [systemZone] 不作为选项出现,
 * 因为它只以「跟随系统」置顶项的形式展示.
 */
fun timeZoneCatalog(now: Instant, systemZone: TimeZone): List<TimeZoneSection> {
    val systemZoneId = systemZone.id
    val byRegion = mutableMapOf<String, MutableList<TimeZoneOption>>()
    for (zoneId in TimeZone.availableZoneIds) {
        if (zoneId == systemZoneId) continue
        val region = zoneId.substringBefore('/', "")
        if (region !in timeZoneRegions) continue
        val offsetSeconds = TimeZone.of(zoneId).offsetAt(now).totalSeconds
        byRegion.getOrPut(region) { mutableListOf() }.add(TimeZoneOption(zoneId, offsetSeconds))
    }
    return byRegion.entries
        .sortedBy { it.key }
        .map { (region, options) ->
            TimeZoneSection(
                region = region,
                options = options
                    .distinctBy { it.zoneId }
                    .sortedWith(compareBy({ it.offsetSeconds }, { it.zoneId })),
            )
        }
}

/**
 * 按 [query] 过滤目录, 丢弃没有任何命中项的分组. 匹配规则见 [timeZoneQueryMatches].
 */
fun filterTimeZoneSections(sections: List<TimeZoneSection>, query: String): List<TimeZoneSection> {
    return sections.mapNotNull { section ->
        val options = section.options.filter { option ->
            timeZoneQueryMatches(query, option.zoneId, option.city, section.region, option.offsetLabel)
        }
        if (options.isEmpty()) null else section.copy(options = options)
    }
}

/**
 * [query] 是否命中 [fields] 中的任意一项. 忽略大小写与首尾空白; 空白查询命中一切.
 *
 * 目录条目以时区 ID、城市段、Region 名与偏移串作为 [fields]; 置顶项以显示文案与时区 ID 作为 [fields].
 */
fun timeZoneQueryMatches(query: String, vararg fields: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    return fields.any { it.contains(trimmed, ignoreCase = true) }
}

/**
 * 把 UTC 偏移格式化为 `UTC+08:00` 形式.
 */
fun formatUtcOffset(offsetSeconds: Int): String {
    val sign = if (offsetSeconds < 0) '-' else '+'
    val totalMinutes = abs(offsetSeconds) / 60
    return "UTC$sign${padTwoDigits(totalMinutes / 60)}:${padTwoDigits(totalMinutes % 60)}"
}

private fun padTwoDigits(value: Int): String = value.toString().padStart(2, '0')
