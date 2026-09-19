/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.datasources.api.Media

/**
 * 线路内区分条目用的名称: 数据源给出的条目名; BT 资源没有, 从标题里取: 去掉扩展名、"★04月新番★" 这类装饰后,
 * 方括号等标签外的文字与标签里的文字都去掉集数、按 "/"、" - " 分段 (如 "[Group] 日常 / Nichijou - 02 [1080p].mkv" 得到 "日常" 与 "Nichijou",
 * "【幻樱字幕组】【4月新番】【日常 Nichijou】【01】【GB_MP4】" 得到 "日常 Nichijou"); 开头的标签 (字幕组)、分辨率、编码等常见标签不算,
 * 但 "[S2]"、"[第二季]"、"[剧场版]" 这类区分续作、衍生作的标记保留 (见 [isSeasonMarker]).
 * 得到的段里可能混有 "简日双语" 这类没收进常见标签的字样, 由 [isSameLineAs] 用条目名甄别.
 */
internal fun Media.lineSubjectNames(): Set<String> {
    properties.subjectName?.takeIf { it.isNotBlank() }?.let { return setOf(it.trim()) }
    val title = originalTitle.replace(TITLE_EXTENSION, "").replace(TITLE_DECORATIONS, " ")
    val plain = title.replace(TITLE_TAGS, " ").titleSegments()
    // 开头的标签是字幕组: 数据源给的字幕组名可能是另一种写法 (如 "喵萌奶茶屋" 与 "[Nekomoe kissaten]")
    val leadingTagStart = title.indexOfFirst { !it.isWhitespace() }
    val tagged = TITLE_TAGS.findAll(title)
        .filter { it.range.first != leadingTagStart }
        .map { it.value.drop(1).dropLast(1) }
        .filter { !it.equals(properties.alliance, ignoreCase = true) && (it.trim().isSeasonMarker() || !it.matches(TITLE_TAG_WORDS)) }
        .flatMap { it.titleSegments() }
    return plain + tagged
}

private fun String.titleSegments(): Set<String> = split(TITLE_SEPARATORS)
    .map { it.replace(TITLE_EPISODE_TOKENS, " ").trim() }
    .filterTo(hashSetOf()) { it.contains(TITLE_WORD_CHAR) }

/**
 * 是否与 [other] 同一线路: 同一数据源、同一字幕组, 且是同一条目.
 * 季号、剧场版这类区分续作与衍生作的标记 (如 "[S2]"、"【我推的孩子】第二季" 里的 "第二季") 不同的不是同一条目, 标题其余部分再像也不算.
 * 去掉标签与集数后名字段完全相同的标题是同一条目; 一方的名字段是另一方的子集也算 (差的只是 "(完)"、"繁體" 这类没收进常见标签的字样,
 * 繁体标题与 Bangumi 的简体名对不上时只能这样判断); 否则以条目的名字为锚: 候选标题里有一段就是条目的某个名字,
 * 或有一段与所选资源的某段相同且那一段含条目的名字 (同一字幕组写法一致, 如 "日常 Nichijou"), 两者共有的 "简日双语"、"★04月新番" 这类字样不算;
 * 只与条目名的一部分相同的段 (如为 "XX 第二季" 下载时同字幕组的 "XX" 第一季) 也不算.
 * 标题里分不出名字的资源 (如条目名是纯数字 "86") 以标题是否含条目的名字判断.
 *
 * @param otherNames [other] 的 [lineSubjectNames], 由调用方算一次
 * @param subjectNames 条目的全部名字
 */
internal fun Media.isSameLineAs(other: Media, otherNames: Set<String>, subjectNames: Collection<String>): Boolean {
    if (mediaSourceId != other.mediaSourceId || properties.alliance != other.properties.alliance) return false
    if (mediaId == other.mediaId) return true
    val names = lineSubjectNames()
    val markers = names.seasonMarkers()
    val otherMarkers = otherNames.seasonMarkers()
    if (markers != otherMarkers) return false
    if (names.isNotEmpty() && names == otherNames) return true
    val subjects = subjectNames.filter { it.isNotBlank() }
    if (names.isEmpty() || otherNames.isEmpty()) {
        return subjects.any { MediaListFilters.specialContains(originalTitle, it) } &&
                subjects.any { MediaListFilters.specialContains(other.originalTitle, it) }
    }
    val core = names.filter { it !in markers }
    val otherCore = otherNames.filter { it !in otherMarkers }
    if (core.isNotEmpty() && otherCore.isNotEmpty() && (core.containsAll(otherCore) || otherCore.containsAll(core))) return true
    fun String.isSubjectName() = subjects.any { MediaListFilters.specialEquals(this, it) }
    fun String.mentionsSubject() = subjects.any { MediaListFilters.specialContains(this, it) }
    return names.any { name ->
        name.isSubjectName() || otherNames.any { MediaListFilters.specialEquals(name, it) && it.mentionsSubject() }
    }
}

private fun Set<String>.seasonMarkers(): Set<String> = filterTo(hashSetOf()) { it.isSeasonMarker() }

/**
 * 区分续作与衍生作的标记: 季号 ("S2"、"第二季"、"2nd Season"、"II")、剧场版、OVA、特别篇等.
 */
private fun String.isSeasonMarker(): Boolean = matches(TITLE_SEASON_MARKER)

private val TITLE_EXTENSION = Regex("""\.(mp4|mkv|avi|mpeg|mov|flv|wmv|webm|rm|rmvb|ts|m2ts)$""", RegexOption.IGNORE_CASE)
// 右括号都转义: Android 的正则引擎不接受裸的 "]"、"}"
private val TITLE_TAGS = Regex("""\[[^\]]*\]|【[^】]*】|\([^)]*\)|（[^）]*）|\{[^}]*\}""")
private val TITLE_SEPARATORS = Regex("""\s*[/|｜／]\s*|\s+[-–—－]\s+""")

/**
 * 标题里的装饰: "★04月新番★"、"★" 等.
 */
private val TITLE_DECORATIONS = Regex("""★[^★\[【(（]*★|★|☆|\d+\s*月\s*新番""")

/**
 * 集数写法, 含 "28(完)" 里的 "(完)". "第3季"、"第4期" 是季号不是集数, 不去掉; 裸数字要两位以上 (集数通常补零, 如 "01"; "Mushoku Tensei 2" 的 "2" 是季号).
 */
private val TITLE_EPISODE_TOKENS = Regex(
    """第\s*\d+(?:\.\d+)?(?:\s*[-~至]\s*\d+(?:\.\d+)?)?(?!\s*[季期部])\s*[话話集]?(?:\s*v\d)?|全\s*\d+\s*[话話集]|全集|合集|完结|[(（]\s*(?:完|完结|终|終|end|fin)\s*[)）]|""" +
            """(?<![A-Za-z0-9])(?:ep|e|sp|ova|oad)\s*\d{1,4}(?:\.\d)?(?:v\d)?(?:\s*[-~至]\s*\d{1,4})?(?![A-Za-z0-9])|""" +
            """(?<![A-Za-z0-9])\d{2,4}(?:\.\d)?(?:v\d)?(?:\s*[-~至]\s*\d{1,4})?(?![A-Za-z0-9])|""" +
            """(?<![A-Za-z])(?:fin|end|complete)(?![A-Za-z])""",
    RegexOption.IGNORE_CASE,
)

private val TITLE_SEASON_MARKER = Regex(
    """S\d{1,2}|Season\s*\d{1,2}|\d{1,2}(?:st|nd|rd|th)\s*Season|II|III|IV|第\s*[\d一二三四五六七八九十]+\s*[季期部]|[\d一二三四五六七八九十]+\s*[季期]|""" +
            """剧场版|劇場版|电影|電影|Movie|OVA|OAD|SP|特别篇|特別篇|总集篇|總集篇|番外篇?""",
    RegexOption.IGNORE_CASE,
)

/**
 * 名字段至少要有一个汉字、假名、谚文或字母. 用码位范围而不是 `\p{IsHan}` 这类脚本属性: Android 的正则引擎不支持后者.
 */
private val TITLE_WORD_CHAR = Regex("""[\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF\u3005\u3007\u3040-\u30FF\u31F0-\u31FF\uAC00-\uD7AF\uFF66-\uFF9FA-Za-z]""")

private const val TITLE_TAG_WORD = """(?:\d{3,4}\s*[pPiI]|\d{3,4}\s*[xX×]\s*\d{3,4}|4K|x264|x265|hevc|avc|aac|flac|opus|webrip|web-dl|bdrip|bd|dvdrip|baha|crunchyroll|bilibili|b-global|netflix|10bit|8bit|hdr|mp4|mkv|gb|big5|cht|chs|jp|jpn|eng|ass|srt|""" +
        """简繁|简体|繁体|簡體|繁體|簡繁|简日|繁日|簡日|简日双语|繁日双语|简繁日多语|日语中字|中字|内封|内嵌|外挂|外掛|简繁内封|简繁外挂|簡繁外掛|简繁日|招募.*|合集|全集|完结|完|先行版本?|先行|修正.*|fin|end|ver\.?\s*\d+|v\d+.*|\d+\s*月\s*新番.*|""" +
        """.*(?:字幕组|字幕社|字幕組|工作室|制作组|压制|發佈|发布|搬运组|搬運組|raws|fansub|sub))"""

/**
 * 标签里不是条目名的常见内容: 全大写的缩写 (GB_MP4、CHT; 五个字母以上的单个全大写单词如 "BLEACH" 可能是条目名, 不算),
 * 分辨率、编码、语言、来源、月份新番、字幕组名等.
 */
private val TITLE_TAG_WORDS = Regex(
    """\s*(?:(?-i:(?![A-Z]{5,}$)[A-Z0-9_\-+&.\s]+)|$TITLE_TAG_WORD(?:[\s_&+\-]+$TITLE_TAG_WORD)*)\s*""",
    RegexOption.IGNORE_CASE,
)
