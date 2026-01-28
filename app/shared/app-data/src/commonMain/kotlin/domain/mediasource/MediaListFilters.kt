/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource

import androidx.collection.IntSet
import androidx.collection.MutableIntSet
import me.him188.ani.app.domain.mediasource.MediaListFilters.charsToDelete
import me.him188.ani.app.domain.mediasource.MediaListFilters.charsToReplaceWithWhitespace
import me.him188.ani.app.domain.mediasource.MediaListFilters.keepWords
import me.him188.ani.app.domain.mediasource.MediaListFilters.minimumLength
import me.him188.ani.datasources.api.EpisodeSort
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
    /**
     * 要求包含条目名称. 支持模糊匹配.
     */
    val ContainsSubjectName = BasicMediaListFilter { media ->
        subjectNamesWithoutSpecial.any { subjectName ->
            val originalTitle = removeSpecials(media.subjectName, removeWhitespace = true, replaceNumbers = true)
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
        if (episodeSort is EpisodeSort.Normal) {
            return@BasicMediaListFilter false // 对于普通剧集, 不考虑名称匹配. #1738. See also test MediaListFilterEpisodeFilterTest
        }
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

    val charsToDelete = """""".toCharCodeIntSet()
    val charsToDeleteForSearch get() = charsToReplaceWithWhitespace // 放在这里, 这样你改 [charsToDelete] 时会注意到

    private val charsToReplaceWithWhitespace = """。、，·・[]～“”~—-!@#$%^&*()_+{}|\;':",.<>/?【】：「」！""".toCharCodeIntSet()
    private val whitespaceChars = " \t\u3000".toCharCodeIntSet()

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
     * 2. 无条件删除的特殊字符. 例如 "电影", "剧场版" 等, 这些字符总是会被删除.
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
     * @param removeMarkers 如果为 true, 则会删除 "电影", "剧场版", "OVA" 等仅有标记作用的词. 注意, 不能在搜索到之后做匹配的时候使用. 详见 #1794
     */
    fun removeSpecials(
        string: String,
        removeWhitespace: Boolean,
        replaceNumbers: Boolean,
        removeMarkers: Boolean = false,
    ): String {
        // 1. 将 keepWords 替换成掩码
        var result = keepWords.fold(string) { acc, keepWord ->
            acc.replace(keepWord.originalWord, keepWord.mask)
        }

        // 2. 无条件删除 "电影", "剧场版", "OVA" 等
        val sb = StringBuilder(result).apply {
            if (removeMarkers) {
                deletePrefix("电影")
                deleteInfix("电影")
                deletePrefix("剧场版")
                deleteInfix("剧场版")
                deletePrefix("OVA")
                deleteInfix("OVA")
                deleteInfix("OAD")
                deleteInfix("总集篇")
            }
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
                replaceNumberConditional(processed, match)
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

    fun specialEquals(first: String, second: String): Boolean {
        return removeSpecials(first, removeWhitespace = true, replaceNumbers = true)
            .equals(removeSpecials(second, removeWhitespace = true, replaceNumbers = true), ignoreCase = true)
    }

    fun specialContains(string: String, sub: String): Boolean {
        return removeSpecials(string, removeWhitespace = true, replaceNumbers = true)
            .contains(removeSpecials(sub, removeWhitespace = true, replaceNumbers = true), ignoreCase = true)
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

    private fun replaceNumberConditional(original: String, match: MatchResult): String {
        // 前后有字符或数字
        if (original.isLetterOrDigit(match.range.first - 1) ||
            original.isLetterOrDigit(match.range.last + 1)
        ) {
            if (match.value.isChineseNumber()) {
                // 如果是中文数字, 则需要考虑前后情况, 比如
                // - "五等份的花嫁" 中的 "五" 就不应该被替换成 "5"
                // - "中二病也要谈恋爱" 中的 "二" 就不应该被替换成 "2"
                // - "第三季" 中的 "三" 应该被替换成 "3"

                // 如果数字在开头并且后面是中文字符, 则不替换
                if (match.range.first == 0 && original.isChineseCharacter(match.range.last + 1)) {
                    return match.value
                }
                if (match.range.first > 0 && match.range.last < original.lastIndex) {
                    if (original[match.range.first - 1] != '第') {
                        return match.value
                    }
                }
            } else if (original.isJapaneseCharacter(match.range.first - 1)) {
                // 如果不是汉字, 那可能是日本字或者其他字
                // 日本字的中如果用罗马数字表示系列数字, 那也可能会连在一起写
                // 只考虑数字在最后的情况, 例如 ソードアート・オンラインII
                // 这种情况也要替换
            } else {
                // 英文或者其他的作品名的系列数字一定有空格
                return match.value
            }

        }

        // 是否是 O V A 这种情况
        // 这种情况中间的 V 也不应该替换成数字
        if (match.value == "V") {
            val prevLetter = original.reverseFirstLetterOrDigit(match.range.first - 1)
            val nextLetter = original.traverseFirstLetterOrDigit(match.range.last + 1)
            if ((prevLetter == 'O' || prevLetter == 'o') &&
                (nextLetter == 'A' || nextLetter == 'a')
            ) {
                return match.value
            }
        }

        return numberMappings[match.value] ?: match.value
    }

    private fun Char.isSpecialChar(): Boolean {
        val code = code
        return charsToDelete.contains(code) || charsToReplaceWithWhitespace.contains(code)
    }
}

private fun String.toCharCodeIntSet(): IntSet {
    val chars = toCharArray()
    return MutableIntSet(chars.size).apply {
        chars.forEach { add(it.code) }
    }
}

private fun String.isLetterOrDigit(index: Int): Boolean {
    return getOrNull(index)?.isLetterOrDigit() == true
}

private fun String.isChineseCharacter(index: Int): Boolean {
    return getOrNull(index)?.code in 0x4E00..0x9FFF
}

private fun String.isJapaneseCharacter(index: Int): Boolean {
    val code = getOrNull(index)?.code ?: return false
    return (code in 0x3040..0x309F) || // 平假名
            (code in 0x30A0..0x30FF) || // 片假名
            (code in 0x4E00..0x9FBF) // 汉字
}

private fun String.isChineseNumber(): Boolean {
    if (length != 1) return false
    return first() in '一'..'十'
}

private fun String.reverseFirstLetterOrDigit(index: Int): Char? {
    if (index !in 0..<length) return null
    for (i in index downTo 0) {
        val c = get(i)
        if (c.isLetterOrDigit()) {
            return c
        }
    }
    return null
}

private fun String.traverseFirstLetterOrDigit(index: Int): Char? {
    if (index !in 0..<length) return null
    for (i in index until length) {
        val c = get(i)
        if (c.isLetterOrDigit()) {
            return c
        }
    }
    return null
}