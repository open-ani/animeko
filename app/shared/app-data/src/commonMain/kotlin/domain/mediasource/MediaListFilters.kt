/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource

import me.him188.ani.datasources.api.topic.contains
import me.him188.ani.utils.platform.deleteInfix
import me.him188.ani.utils.platform.deleteMatches
import me.him188.ani.utils.platform.deletePrefix
import me.him188.ani.utils.platform.replaceMatches
import me.him188.ani.utils.platform.trimSB

/**
 * 常用的过滤器
 *
 * @see MediaListFilter
 */
object MediaListFilters {
    val ContainsSubjectName = BasicMediaListFilter { media ->
        subjectNamesWithoutSpecial.any { subjectName ->
            removeSpecials(media.originalTitle, removeWhitespace = true)
                .contains(subjectName, ignoreCase = true)
        }
    }

    val ContainsEpisodeSort = BasicMediaListFilter { media ->
        val range = media.episodeRange ?: return@BasicMediaListFilter false
        range.contains(episodeSort)
    }
    val ContainsEpisodeEp = BasicMediaListFilter { media ->
        val range = media.episodeRange ?: return@BasicMediaListFilter false
        episodeEp != null && range.contains(episodeEp)
    }
    val ContainsEpisodeName = BasicMediaListFilter { media ->
        episodeName ?: return@BasicMediaListFilter false
        val name = episodeNameWithoutSpecial
        checkNotNull(name)
        if (name.isBlank()) return@BasicMediaListFilter false
        removeSpecials(media.originalTitle, removeWhitespace = true)
            .contains(name, ignoreCase = true)
    }

    val ContainsAnyEpisodeInfo = ContainsEpisodeSort or ContainsEpisodeName or ContainsEpisodeEp

    private val numberMappings = buildMap {
        put("X", "10")
        put("IX", "9")
        put("VIII", "8")
        put("VII", "7")
        put("VI", "6")
        put("V", "5")
        put("IV", "4")
        put("III", "3")
        put("II", "2")
        put("I", "1")

        put("十", "10")
        put("九", "9")
        put("八", "8")
        put("七", "7")
        put("六", "6")
        put("五", "5")
        put("四", "4")
        put("三", "3")
        put("二", "2")
        put("一", "1")
    }
    private val allNumbersRegex = numberMappings.keys.joinToString("|").toRegex()
    private val specialCharRegex = Regex("""[~!@#$%^&*()_+{}\[\]\\|;':",.<>/?【】：～「」]""")
    private val whitespaceRegex = Regex(""" 	\s+""")

    fun removeSpecials(string: String, removeWhitespace: Boolean): String {
        return StringBuilder(string).apply {
            deletePrefix("电影")
            deleteInfix("电影")
            deletePrefix("剧场版")
            deleteInfix("剧场版")
            deletePrefix("OVA")
            deleteInfix("OVA")
            deleteMatches(specialCharRegex)
            replaceMatches(allNumbersRegex) { numberMappings.getValue(it.value) }
            if (removeWhitespace) {
                deleteMatches(whitespaceRegex)
            }
            trimSB()
        }.toString()
    }
}
