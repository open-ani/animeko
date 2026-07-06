/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.source

import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch

internal fun List<MediaMatch>.sortedForRequest(request: MediaFetchRequest): List<MediaMatch> {
    return sortedByDescending { candidateSelectionScore(it, request) }
}

internal fun candidateSelectionScore(match: MediaMatch, request: MediaFetchRequest): Int {
    var score = 0
    if (match.kind == MatchKind.EXACT) {
        score += 100
    }

    val title = match.media.originalTitle
    val subjectNames = request.subjectNames.filter(String::isNotBlank)
    if (subjectNames.any { title.contains(it) }) {
        score += 30
    }

    val candidateSeason = detectSeason(title)
    val requestSeason = subjectNames.firstNotNullOfOrNull(::detectSeason)
    score += when {
        requestSeason != null && candidateSeason == requestSeason -> 40
        requestSeason != null && candidateSeason != null -> -40
        requestSeason == null && candidateSeason == null -> 25
        requestSeason == null && candidateSeason == 1 -> 20
        else -> -30
    }

    return score
}

private fun detectSeason(text: String): Int? {
    val season = SEASON_REGEX.find(text)?.groupValues?.get(1) ?: return null
    return parseSeasonNumber(season)
}

private fun parseSeasonNumber(value: String): Int? {
    return value.toIntOrNull() ?: CHINESE_SEASON_NUMBERS[value]
}

private val SEASON_REGEX = Regex("""第\s*([0-9一二三四五六七八九十]+)\s*季""")

private val CHINESE_SEASON_NUMBERS = mapOf(
    "一" to 1,
    "二" to 2,
    "三" to 3,
    "四" to 4,
    "五" to 5,
    "六" to 6,
    "七" to 7,
    "八" to 8,
    "九" to 9,
    "十" to 10,
)
