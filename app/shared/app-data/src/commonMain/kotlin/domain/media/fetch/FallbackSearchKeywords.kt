/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.fetch

import me.him188.ani.app.data.models.subject.SubjectSeriesInfo
import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.datasources.api.source.MediaFetchRequest

/**
 * 计算 [MediaFetchRequest.fallbackSearchKeywords].
 *
 * 站点给各季起名时通常以第一季的名字为基础再加「第二季」, 而 Bangumi 的中文名沿用官方译名, 可能与第一季毫无关系
 * (「更多 出包王女」). 只用后者搜索, 站点一无所获. 两类名字能搜到站点上的条目, 按此顺序给出:
 *
 * 1. 条目自己带季度标记的名字. Bangumi 常给这类条目补充「出包王女 第二季」别名, 站点用的正是这种形式.
 * 2. 系列基础名: 同系列其他条目的名字是当前条目某个名字的子串时, 它就是整个系列共用的基础名 (「出包王女」之于「更多 出包王女」).
 *    搜它会得到整个系列, 由选择器的续集规则挑出本季. 「伪物语」不含「化物语」, 这类改了名的系列推不出基础名, 站点也不会用「化物语 第二季」叫它.
 *
 * 顺序跟随 [subjectNames]: 主中文名推出的在前. 同一个名字可能推出多个基础名 (「出包王女Darkness 第二季」含「出包王女Darkness」与「出包王女」),
 * 长的更具体, 排在前面. 数据源按自己的关键词归一化规则去重, 这里不做.
 *
 * @param seriesSubjectNames 同系列其他条目的名字, 不含当前条目自己的, 见 [SubjectSeriesInfo.seriesSubjectNamesWithoutSelf].
 */
internal fun computeFallbackSearchKeywords(
    subjectNames: List<String>,
    seriesSubjectNames: Collection<String>,
): List<String> {
    val names = subjectNames.map { it.trim() }.filter { it.isNotEmpty() }
    val result = LinkedHashSet<String>()
    names.filterTo(result) { SEASON_MARKER.containsMatchIn(it) }

    val bases = seriesSubjectNames.asSequence()
        .map { it.trim() }
        .filter { base ->
            MediaListFilters.removeSpecials(base, removeWhitespace = true, replaceNumbers = true).length >= MIN_BASE_NAME_LENGTH
        }
        .distinct()
        .toList()
    for (name in names) {
        bases.asSequence()
            .filter { base -> MediaListFilters.specialContains(name, base) && !MediaListFilters.specialEquals(name, base) }
            .sortedByDescending { it.length }
            .forEach { result.add(it) }
    }
    return result.toList()
}

/** 「第二季」「第2期」「第三部」. */
private val SEASON_MARKER = Regex("""第\s*[一二三四五六七八九十\d]+\s*[季期部]""")

/** 「K」「C」这类单字系列名是任何名字的子串, 不能当基础名. */
private const val MIN_BASE_NAME_LENGTH = 2
