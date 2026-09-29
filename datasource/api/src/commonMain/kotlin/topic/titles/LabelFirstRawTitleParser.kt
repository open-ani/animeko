/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.topic.titles

import androidx.collection.intSetOf
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FrameRate
import me.him188.ani.datasources.api.topic.MediaOrigin
import me.him188.ani.datasources.api.topic.Resolution
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import me.him188.ani.datasources.api.topic.isSingleEpisode
import me.him188.ani.datasources.api.topic.orEmpty
import me.him188.ani.datasources.api.topic.plus

/**
 * 只解析剧集, 分辨率等必要信息, 不解析标题. 拥有更高正确率
 */
class LabelFirstRawTitleParser : RawTitleParser() {
    override fun parse(
        text: String,
        allianceName: String?,
        builder: ParsedTopicTitle.Builder,
    ) {
        Session(builder).run {
            val words = mutableListOf<String>()
            for (s in text.splitWords()) {
                if (s.isBlank()) continue
                if (newAnime.matches(s)) {
                    builder.tags.add(s)
                    continue
                }
                val word = s.remove("招募").remove("招新").trim()
                words.add(word)
            }

            // 第一遍, 解析剧集, 分辨率, 字幕等
            for (word in words) {
                parseWord(word)
            }

            // 第二遍, 如果没有解析到剧集, 找是不是有 "BDRip", 判定为季度全集
            if (builder.episodeRange == null) {
                words.forEach { word ->
                    if (word.contains("Movie", ignoreCase = true)
                        || word.contains("电影", ignoreCase = true)
                        || word.contains("剧场版", ignoreCase = true)
                    ) {
                        // #1193
                        builder.episodeRange = EpisodeRange.unknownSeason()
                    } else if (word.contains("BD", ignoreCase = true)
                        || word.contains("Blu-Ray", ignoreCase = true)
                    ) {
                        builder.episodeRange = EpisodeRange.unknownSeason()
                    }
                }
            }

            // #382 单集特典类型
            // 特典映像/[DBD-Raws] [龙猫] [特典映像] [01][1080P][BDRip][HEVC-10bit][AC3].mkv
            builder.episodeRange?.let { range ->
                if (range is EpisodeRange.Single) {
                    if (words.any { it == "特典" || it == "特典映像" }) {
                        builder.episodeRange =
                            EpisodeRange.single(EpisodeSort.Special(EpisodeType.SP, range.value.number))
                    }
                }
            }

            if (builder.subtitleLanguages.isEmpty()) {
                when {
                    "字幕组" in text -> {
                        // 如果标题只有 "字幕组", 则认为是简日内嵌.
                        builder.subtitleLanguages.add(SubtitleLanguage.ChineseSimplified)
                        if (builder.subtitleKind == null) {
                            builder.subtitleKind = SubtitleKind.EMBEDDED
                        }
                    }
                }
            }

            // 判断字幕类型
            if (builder.subtitleKind == null) {
                builder.subtitleKind = when {
                    "内嵌" in text || "內嵌" in text -> SubtitleKind.EMBEDDED
                    "内封" in text || "內封" in text -> SubtitleKind.CLOSED
                    "外挂" in text || "外掛" in text -> SubtitleKind.EXTERNAL_DISCOVER

                    // 将同时有超过两个非日语语言的资源，标记为非内嵌 #719
                    builder.subtitleLanguages.count { it != SubtitleLanguage.Japanese } >= 2 -> SubtitleKind.CLOSED
                    else -> null
                }
            }

            if (words.any { it == "无中文字幕" }) {
                builder.subtitleLanguages.clear()
                builder.subtitleKind = null
            }
        }
        return
    }

    class Session(
        private val builder: ParsedTopicTitle.Builder,
    ) {
        fun parseWord(word: String): Boolean {
            var anyMatched = false
            anyMatched = anyMatched or word.parseSubtitleLanguages()
            anyMatched = anyMatched or word.parseResolution()
            anyMatched = anyMatched or word.parseFrameRate()
            anyMatched = anyMatched or (parseEpisode(word)?.let {
                builder.emitEpisodeRange(it)
            } != null)
            anyMatched = anyMatched or word.parseMediaOrigin()

            return anyMatched
        }


        private fun String.parseSubtitleLanguages(): Boolean {
            var any = false
            if (this == "简体双语") {
                builder.subtitleLanguages.add(SubtitleLanguage.ChineseSimplified)
                builder.subtitleLanguages.add(SubtitleLanguage.Japanese)
                return true
            }
            if (this.splitToSequence(" ").any { it.equals("Baha", ignoreCase = true) }
                && builder.subtitleLanguages.isEmpty()) {
                builder.subtitleLanguages.add(SubtitleLanguage.ChineseTraditional)
            }
            for (entry in SubtitleLanguage.matchableEntries) {
                if (entry.matches(this)) {
                    builder.subtitleLanguages.add(entry)
                    any = true
                }
            }
            return any
        }

        private fun String.parseResolution(): Boolean {
            return Resolution.tryParse(this)?.let {
                builder.resolution = it
            } != null
        }

        private fun String.parseFrameRate(): Boolean {
            return FrameRate.tryParse(this)?.let {
                builder.frameRate = it
            } != null
        }

        private fun String.parseMediaOrigin(): Boolean {
            return MediaOrigin.tryParse(this)?.let {
                builder.mediaOrigin = it
            } != null
        }

        /**
         * 1080, 640, etc.
         */
        private fun isPossiblyResolution(range: EpisodeRange): Boolean {
            if (range.isSingleEpisode()) {
                return (range.knownSorts.first().number?.toInt() ?: 0) in resolutionNumbers
            }
            return false
        }

        private fun ParsedTopicTitle.Builder.emitEpisodeRange(
            new: EpisodeRange
        ) {

            val oldRange = episodeRange
            episodeRange = when {
                oldRange == null -> new
                isPossiblyResolution(oldRange) -> new
                isPossiblyResolution(new) -> return

                else -> {
                    if (oldRange.isSingleEpisode() && new is EpisodeRange.Season) {
                        // ignore
                        return
                    } else {
                        if (new.knownSorts.count() >= oldRange.knownSorts.count()) {
                            new
                        } else {
                            return
                        }
                    }
                }
            }
        }

        /**
         * 解析一连串剧集文本. 文本首先会被分割为 sections, 然后用 [parseEpisodeSection] 分别解析.
         *
         * ## Episode Patterns
         *
         * - `01`
         * - `1`
         *
         * ## Season Patterns
         *
         * - `S1+S2+Movie`
         * - `S1E1+S2+Movie`
         * - `S1E1+S2+SP`
         * - `S01E01+S2+SP`
         * - `S01E01+S02+SP`
         * - `S01E01+S02`
         * - `S01E01`
         * - `S01`
         * - `S1`
         */
        private fun parseEpisode(text: String): EpisodeRange? {
            val split = text.removeSuffix("-").split("+", " ")
            if (split.isEmpty()) {
                return null
            }
            val result = split.fold(EpisodeRange.empty()) { acc, section ->
                acc.plus(
                    parseEpisodeSection(section) ?: EpisodeRange.empty(),
                )
            }
            if (result.isEmpty()) {
                return null
            }
            return result
        }

        /**
         * 解析 `S1+S2+Movie` 按 "+" 分割出来的部分
         */
        private fun parseEpisodeSection(original: String): EpisodeRange? {
            if (original.contains("x264", ignoreCase = true)
                || original.contains("x265", ignoreCase = true)
            ) return null

            if (yearPattern.matchEntire(original) != null) {
                return null
            }

            if (original in movieKeywords) {
                // TODO: 2025/3/10 handle movie
                return EpisodeRange.unknownSeason()
            }

            seasonEpisodePattern.matchEntire(original)?.let { result ->
                // TODO: consider season
                // S00 是特典, 不是正片第 xx 集
                if (result.groupValues[1].toIntOrNull() == 0) {
                    val number = result.groupValues[2].toIntOrNull()
                        ?: return EpisodeRange.single(EpisodeSort(result.groupValues[2]))
                    return EpisodeRange.single(EpisodeSort(number, EpisodeType.SP))
                }
                return EpisodeRange.single(EpisodeSort(result.groupValues[2]))
            }

            seasonPattern.matchEntire(original)?.let { result ->
                return parseSeason(result)
            }

            seasonRangePattern.find(original)?.let { result ->
                val groupValues = result.groupValues

                fun String.seasonStringToIntOrNull(): Int? = dropWhile { it in "-S" }.toIntOrNull()

                if (groupValues.size == 3) {
                    val start = groupValues[1].seasonStringToIntOrNull()
                    val end = groupValues[2].seasonStringToIntOrNull()
                    if (start != null && end != null) {
                        return (start..end).fold(EpisodeRange.empty()) { acc, i ->
                            EpisodeRange.combined(acc, EpisodeRange.season(i))
                        }
                    }
                } else {
                    return groupValues.drop(1).mapNotNull {
                        it.seasonStringToIntOrNull()
                    }.fold(EpisodeRange.empty()) { acc, i -> EpisodeRange.combined(acc, EpisodeRange.season(i)) }
                }
            }

            val str = episodeRemove.fold(original) { acc, regex -> acc.remove(regex) }
            str.toFloatOrNull()?.let {
                if (it.toInt().toFloat() == it && '.' in str) {
                    // 没有小数位, 例如 "2.0", 一般不认为这是 EP
                } else {
                    return EpisodeRange.single(str)
                }
            }
//            collectionPattern.find(str)?.let { result ->
//                val startGroup = result.groups["start"]
//                val endGroup = result.groups["end"]
//                val extraGroup = result.groups["extra"]
//
//                if (extraGroup == null && (startGroup == null || endGroup == null)) {
//                    return@let
//                }
//                val start = startGroup?.value
//                val end = endGroup?.value
//
//                var range: EpisodeRange
//                if (start != null && end != null) {
//                    start.getPrefix()?.let { prefix ->
//                        if (!end.startsWith(prefix)) {
//                            // "SP1-5"
//                            builder.episodeRange = EpisodeRange.range(start, prefix + end)
//                            return true
//                        }
//                    }
//
//                    if (end.startsWith("0") && !start.startsWith("0")) {
//                        // "Hibike! Euphonium 3 - 02"
//                        builder.episodeRange = EpisodeRange.single(EpisodeSort(end))
//                        return true
//                    }
//
//                    range = EpisodeRange.range(start, end)
//                } else {
//                    range = EpisodeRange.empty()
//                }
//
//                if (extraGroup != null) {
//                    for (extra in result.groups.indexOf(extraGroup)..<result.groups.size) {
//                        range = EpisodeRange.combined(
//                            range,
//                            EpisodeRange.single(EpisodeSort(result.groups[extra]!!.value.removePrefix("+")))
//                        )
//                    }
//                }
//                builder.episodeRange = range
//                return true
//            }
            collectionPattern.find(str)?.let { result ->
                val start = result.groups["start"]?.value ?: return@let
                val end = result.groups["end"]?.value ?: return@let
                start.getPrefix()?.let { prefix ->
                    if (!end.startsWith(prefix)) {
                        // "SP1-5"
                        return EpisodeRange.range(start, prefix + end)
                    }
                }

                if (end.startsWith("0") && !start.startsWith("0")) {
                    // "Hibike! Euphonium 3 - 02"
                    return EpisodeRange.single(EpisodeSort(end))
                }

                val extra = result.groups["extra"]?.value
                return if (extra != null) {
                    EpisodeRange.combined(
                        EpisodeRange.range(start, end),
                        parseEpisode(extra).orEmpty(),
                    )
                } else {
                    EpisodeRange.range(start, end)
                }
            }
            if (singleSpecialEpisode.matchEntire(str) != null) {
                return EpisodeRange.single(original)
            }
            return null
        }

        private fun parseSeason(result: MatchResult) = EpisodeRange.combined(
            result.value.split("+")
                .map {
                    // expecting "S1" or "S1E5"
                    if (it.startsWith("SP", ignoreCase = true)) {
                        return@map EpisodeRange.single(it)
                    }
                    if (it.startsWith("Movie", ignoreCase = true)) {
                        // TODO: handle movie
                        return@map EpisodeRange.unknownSeason()
                    }
                    if (it.contains("E", ignoreCase = true)) {
                        val episode = it.substringAfter("E").toIntOrNull()
                        if (episode != null) {
                            return@map EpisodeRange.single(EpisodeSort(episode))
                        }
                    }

                    EpisodeRange.season(it.drop(1).toIntOrNull())
                }.toList(),
        )
    }
}

private fun String.getPrefix(): String? {
    if (this.isEmpty()) return null
    if (this[0].isDigit()) return null
    val index = this.indexOfFirst { it.isDigit() }
    if (index == -1) return null
    return this.substring(0, index)
}

// 第02話V2版
// 02V2
private val episodeRemove = listOf(
    Regex("""第"""),
    Regex("""_?(?:完|END)|\(完\)""", RegexOption.IGNORE_CASE),
    Regex("""[话集話]"""),
    Regex("""_?v[0-9]""", RegexOption.IGNORE_CASE),
    Regex("""版"""),
    Regex("""Fin|FIN"""),
)

// S01E05
private val seasonEpisodePattern = Regex("""S(\d+)E(\d+)""")

// 1998  2022  需要去除, 否则会被匹配为剧集
private val yearPattern = Regex("""19[0-9]{2}|20[0-3][0-9]""")
private val newAnime = Regex("(?:★?|★(.*)?)([0-9]|[一二三四五六七八九十]{0,4}) ?[月年] ?(?:新番|日剧)★?")

// 性能没问题, 测了一般就 100 steps
@Suppress("RegExpRedundantEscape") // required on android
private val brackets =
    Regex("""\[(?<v1>.+?)\]|\((?<v2>.+?)\)|\{(?<v3>.+?)\}|【(?<v4>.+?)】|（(?<v5>.+?)）|「(?<v6>.+?)」|『(?<v7>.+?)』""")

//private val brackets = listOf(
//    "[" to "]",
//    "【" to "】",
//    "（" to "）",
//    "(" to ")",
//    "『" to "』",
//    "「" to "」",
//    "〖" to "〗",
//    "〈" to "〉",
//    "《" to "》",
//    "〔" to "〕",
//    "〘" to "〙",
//    "〚" to "〛",
//)

private val collectionPattern = Regex(
//    """((?<start>(?:SP)?\d{1,4})\s?(?:-{1,2}|~|～)\s?(?<end>\d{1,4}))?(?:TV|BDrip|BD)?(?<extra>\+.+)*""",
    """(?<start>(?:SP)?\d{1,4})\s?(?:-{1,2}|~|～)\s?(?<end>\d{1,4})(?:TV|BDrip|BD)?(?<extra>\+?.+)?""",
    RegexOption.IGNORE_CASE,
)

private val singleSpecialEpisode = Regex(
    """(SP|Special|Moview|OVA|OAD|小剧场|特别篇?|番外篇?)[0-9]{0,3}""",
    RegexOption.IGNORE_CASE,
)

private val movieKeywords = setOf(
    "Movie",
    "MOVIE",
    "剧场版",
    "电影",
)

// S1
// S1+S2
// S1E5 // ep 5
private val seasonPattern = Regex("""S\d+|第\d+季""", RegexOption.IGNORE_CASE)

private val seasonRangePattern = Regex("""(S\d+)(-S\d+)*""", RegexOption.IGNORE_CASE)

private fun String.remove(str: String) = replace(str, "", ignoreCase = true)
private fun String.remove(regex: Regex) = replace(regex) { "" }

private val DEFAULT_SPLIT_WORDS_DELIMITER = charArrayOf('/', '\\', '|', ' ')

internal fun String.splitWords(vararg delimiters: Char = DEFAULT_SPLIT_WORDS_DELIMITER): Sequence<String> {
    val text = this
    return sequence {
        var index = 0
        for (result in brackets.findAll(text)) {
            if (index < result.range.first) {
                yieldAll(
                    text.substring(index until result.range.first)
                        .splitToSequence(delimiters = delimiters),
                )
            }
            index = result.range.last + 1


            val groups = result.groups
            val tag = groups["v1"]
                ?: groups["v2"]
                ?: groups["v3"]
                ?: groups["v4"]
                ?: groups["v5"]
                ?: groups["v6"]
                ?: groups["v7"]
            // can be "WebRip 1080p HEVC-10bit AAC" or "简繁内封字幕"
            yield(tag!!.value)
        }
        if (index < text.length) {
            yieldAll(
                text.substring(index until text.length)
                    .splitToSequence(delimiters = delimiters),
            )
        }
    }
}

private val resolutionNumbers = intSetOf(360, 480, 848, 1080, 1440, 1920, 2160)
