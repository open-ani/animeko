/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource

import androidx.collection.IntSet
import androidx.collection.mutableIntSetOf
import me.him188.ani.app.domain.mediasource.MediaListFilters.charsToDelete
import me.him188.ani.app.domain.mediasource.MediaListFilters.charsToReplaceWithWhitespace
import me.him188.ani.app.domain.mediasource.MediaListFilters.keepWords
import me.him188.ani.app.domain.mediasource.MediaListFilters.minimumLength
import me.him188.ani.datasources.api.topic.contains
import me.him188.ani.utils.platform.deleteAnyCharIn
import me.him188.ani.utils.platform.deleteInfix
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
            val originalTitle = removeSpecials(media.originalTitle, removeWhitespace = true, replaceNumbers = true)
            fun exactlyContains() = originalTitle
                .contains(subjectName, ignoreCase = true)

            fun fuzzyMatches() = StringMatcher.calculateMatchRate(originalTitle, subjectName) >= 80

//            println(
//                when {
//                    exactlyContains() -> "'$originalTitle' included because exactlyContains()"
//                    fuzzyMatches() -> "'$originalTitle' included because fuzzyMatches() at " + StringMatcher.calculateMatchRate(
//                        originalTitle,
//                        subjectName,
//                    )
//
//                    else -> {}
//                },
//            )
            exactlyContains() || fuzzyMatches()
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
        val name = episodeNameForCompare
        checkNotNull(name)
        if (name.isBlank()) return@BasicMediaListFilter false
        removeSpecials(media.originalTitle, removeWhitespace = true, replaceNumbers = true)
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

    private val minimumLength: Int = 2
    private val allNumbersRegex = numberMappings.keys.joinToString("|").toRegex()

    val charsToDelete = """~!@#$%^&*()_+{}\|;':",.<>/?【】：「」！―""".toCharCodeIntSet()
    val charsToDeleteForSearch get() = charsToDelete // 放在这里, 这样你改 [charsToDelete] 时会注意到

    private val charsToReplaceWithWhitespace = """[。、，·[]～]""".toCharCodeIntSet()
    private val whitespaceChars = """ 	\s+""".toCharCodeIntSet()

    private data class KeepWords(
        val originalWord: String,
        val mask: String
    )

    /**
     * 这些词在标题中将保证被原封不动保留
     */
    private val keepWords = listOf("Re：").mapIndexed { index, s ->
        KeepWords(s, "\uE001$index\uE002") // \uE001 是一个不常用的字符
    }


    /**
     * 处理特殊字符.
     *
     * 我们定义几类特殊字符(串):
     *
     * 1. 无条件保留的特殊字符. [keepWords] 中的词总是会被保留.
     * 2. 无条件删除的特殊字符. 例如 "电影", "剧场版", "OVA" 等, 这些字符总是会被删除.
     *
     * 基于经过上面两类处理后的字符串, 我们会进一步处理:
     * 1. 条件删除 ([charsToDelete]) 和替换为空格 ([charsToReplaceWithWhitespace]).
     *   这些规则必须满足位置要求才会生效, 即只有当遇到了 [minimumLength] 个非特殊字符后,
     *   才会开始处理这类特殊字符.
     *   起始位置的特殊字符不受这个限制, 例如开头的 "~" 总是会被删除.
     * 2. 无条件替换为数字. 如果 [replaceNumbers] 为 `true`, 则会将中文数字替换为阿拉伯数字.
     *
     * @param removeWhitespace 如果为 true, 则会删除所有空格（包括通过 replaceWithWhitespace 替换出来的空格）
     * @param replaceNumbers 如果为 true, 则会将中文数字替换成阿拉伯数字 (例如 “三” -> “3”)
     */
    fun removeSpecials(
        string: String,
        removeWhitespace: Boolean,
        replaceNumbers: Boolean
    ): String {
        // 1. 将 keepWords 替换成掩码
        var result = keepWords.fold(string) { acc, keepWord ->
            acc.replace(keepWord.originalWord, keepWord.mask)
        }

        // 2. 无条件删除 "电影", "剧场版", "OVA" 等
        val sb = StringBuilder(result).apply {
            deletePrefix("电影")
            deleteInfix("电影")
            deletePrefix("剧场版")
            deleteInfix("剧场版")
            deletePrefix("OVA")
            deleteInfix("OVA")
        }

        // 3. 基于位置要求，处理特殊字符
        //   - 开头特殊字符可以直接删除 / 替换
        //   - 只有当我们“看到” >= minimumLength 个非特殊字符后，
        //     才会对后续出现的特殊字符应用 toDelete / replaceWithWhitespace

        // 通过扫描一遍的方式实现
        val processed = applyConditionalRules(sb.toString())

        // 4. 如果需要，把中文数字(以及定义的罗马数字)替换成阿拉伯数字
        val afterNumberReplace = if (replaceNumbers) {
            StringBuilder(processed).replaceMatches(allNumbersRegex) { match ->
                numberMappings[match.value] ?: match.value
            }.toString()
        } else {
            processed
        }

        // 5. 如果需要，删除所有空白字符 + 最后 trim
        val sb2 = StringBuilder(afterNumberReplace)
        if (removeWhitespace) {
            sb2.deleteAnyCharIn(whitespaceChars)
        }
        sb2.trimSB()

        // 6. 把 keepWords 的掩码还原回原词
        result = sb2.toString()
        result = keepWords.fold(result) { acc, keepWord ->
            acc.replace(keepWord.mask, keepWord.originalWord)
        }

        return result
    }

    /**
     * 逐字符扫描，按照 [minimumLength] 的规则处理 toDelete & replaceWithWhitespace.
     *  - 如果还是在“开头”，只要发现属于 toDelete 的字符，直接删；或属于 replaceWithWhitespace，直接替换成空格
     *  - 如果已累积达到 minimumLength 个非特殊字符，才对后续的特殊字符删除 / 替换
     *  - 如果非特殊字符不够，跳过“条件删除 / 替换”，直接保留字符
     */
    private fun applyConditionalRules(original: String): String {
        val sbResult = StringBuilder()
        var nonSpecialCount = 0

        // 是否已到达能处理条件字符的阶段
        var canProcess = false

        for (c in original) {
            if (c.isSpecialChar()) {
                val code = c.code
                if (nonSpecialCount == 0) {
                    // 开头特殊字符“无条件”处理
                    if (charsToDelete.contains(code)) {
                        // 删除
                        // => skip
                    } else if (charsToReplaceWithWhitespace.contains(code)) {
                        // 替换为空格
                        sbResult.append(' ')
                    } else {
                        // 不在 toDelete / replaceWithWhitespace 的规则里，就直接保留
                        sbResult.append(c)
                    }
                } else {
                    if (canProcess) {
                        // 可以处理 => 判断是否要删除 / 替换
                        if (charsToDelete.contains(code)) {
                            // 跳过
                        } else if (charsToReplaceWithWhitespace.contains(code)) {
                            sbResult.append(' ')
                        } else {
                            sbResult.append(c)
                        }
                    } else {
                        // 还未达标 => 直接保留
                        sbResult.append(c)
                    }
                }
            } else {
                // 非特殊字符
                sbResult.append(c)
                nonSpecialCount++
                if (!canProcess && nonSpecialCount >= minimumLength) {
                    // 到达临界值，之后出现的特殊字符就可以处理了
                    canProcess = true
                }
            }
        }

        return sbResult.toString()
    }

    private fun Char.isSpecialChar(): Boolean {
        val code = code
        return charsToDelete.contains(code) || charsToReplaceWithWhitespace.contains(code)
    }
}

private fun String.toCharCodeIntSet(): IntSet {
    val chars = toCharArray()
    return mutableIntSetOf(chars.size).apply {
        chars.forEach { add(it.code) }
    }
}