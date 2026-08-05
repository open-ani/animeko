/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.openani.mediamp.metadata.SubtitleTrack

/**
 * 规范化后的字幕语言. 用于显示名称, 以及匹配用户的字幕轨道偏好.
 */
enum class SubtitleTrackLanguage(
    internal val displayNameRes: StringResource,
) {
    CHINESE(Lang.video_player_subtitle_language_chinese),
    CHINESE_SIMPLIFIED(Lang.video_player_subtitle_language_chinese_simplified),
    CHINESE_TRADITIONAL(Lang.video_player_subtitle_language_chinese_traditional),
    CANTONESE(Lang.video_player_subtitle_language_cantonese),
    JAPANESE(Lang.video_player_subtitle_language_japanese),
    ENGLISH(Lang.video_player_subtitle_language_english),
    KOREAN(Lang.video_player_subtitle_language_korean),
    RUSSIAN(Lang.video_player_subtitle_language_russian),
    FRENCH(Lang.video_player_subtitle_language_french),
    GERMAN(Lang.video_player_subtitle_language_german),
    SPANISH(Lang.video_player_subtitle_language_spanish),
    PORTUGUESE(Lang.video_player_subtitle_language_portuguese),
    ITALIAN(Lang.video_player_subtitle_language_italian),
    THAI(Lang.video_player_subtitle_language_thai),
    VIETNAMESE(Lang.video_player_subtitle_language_vietnamese),
    ARABIC(Lang.video_player_subtitle_language_arabic),
    ;

    companion object {
        // 键均为小写, 且 '_' 已规范化为 '-'. 带子标签的条目必须先于其主标签匹配.
        private val byTag: Map<String, SubtitleTrackLanguage> = buildMap {
            fun put(language: SubtitleTrackLanguage, vararg tags: String) {
                for (tag in tags) put(tag, language)
            }
            put(CHINESE_SIMPLIFIED, "zh-hans", "zh-cn", "zh-sg", "zh-hans-cn", "chs", "hans", "gb")
            put(CHINESE_TRADITIONAL, "zh-hant", "zh-tw", "zh-hk", "zh-mo", "cht", "hant", "big5")
            put(CANTONESE, "yue", "zh-yue", "yue-hk")
            put(CHINESE, "zh", "chi", "zho", "cmn")
            put(JAPANESE, "ja", "jpn", "jp")
            put(ENGLISH, "en", "eng")
            put(KOREAN, "ko", "kor")
            put(RUSSIAN, "ru", "rus")
            put(FRENCH, "fr", "fra", "fre")
            put(GERMAN, "de", "deu", "ger")
            put(SPANISH, "es", "spa")
            put(PORTUGUESE, "pt", "por")
            put(ITALIAN, "it", "ita")
            put(THAI, "th", "tha")
            put(VIETNAMESE, "vi", "vie")
            put(ARABIC, "ar", "ara")
        }

        /**
         * 将语言代码 (BCP 47 或 ISO 639-1/2) 规范化, 无法识别时返回 `null`.
         */
        fun parse(code: String?): SubtitleTrackLanguage? {
            if (code == null) return null
            val normalized = code.trim().replace('_', '-').lowercase()
            if (normalized.isEmpty()) return null
            byTag[normalized]?.let { return it }
            // "zh-Hans-CN" 这类三段标签: 逐段去掉末尾子标签再试, 最后落到主标签.
            var candidate = normalized
            while (true) {
                val lastDash = candidate.lastIndexOf('-')
                if (lastDash < 0) break
                candidate = candidate.substring(0, lastDash)
                byTag[candidate]?.let { return it }
            }
            return null
        }

        /**
         * 从 "chs[KitaujiSub]"、"[Sakurato] CHT" 这类标题中提取语言指代词并清洗掉, 返回
         * (识别出的语言, 清洗后的剩余文本). 没有指代词时语言为 `null`, 文本原样返回.
         *
         * 只清除作为独立词出现的指代词, 不会拆开 "UHA-WINGS" 这类恰好包含语言字母的组名.
         */
        fun extractFromTitle(title: String): Pair<SubtitleTrackLanguage?, String> {
            var found: SubtitleTrackLanguage? = null
            val stripped = WORD_RUN_REGEX.replace(title) { match ->
                val language = parse(match.value) ?: return@replace match.value
                found = moreSpecificOf(found, language)
                ""
            }
            if (found == null) return null to title.trim()
            var tidied = stripped
                .replace(EMPTY_BRACKETS_REGEX, " ")
                .trim { it.isWhitespace() || it in SEPARATOR_CHARS }
                .replace(WHITESPACE_RUN_REGEX, " ")
            // 剩余文本整体被一层括号包裹时 (如 "chs[KitaujiSub]" 清掉 chs 后剩 "[KitaujiSub]") 解开括号.
            WRAPPING_BRACKETS_REGEX.matchEntire(tidied)?.let { tidied = it.groupValues[1].trim() }
            return found to tidied
        }

        /**
         * [parse] 的宽松版: 纯语言代码或含指代词的复合标题 ("chs[KitaujiSub]") 都能识别出语言.
         */
        fun parseLoose(value: String?): SubtitleTrackLanguage? {
            if (value == null) return null
            parse(value)?.let { return it }
            return extractFromTitle(value).first
        }

        /**
         * 取两个语言中更具体的一个: 简中/繁中/粤语比笼统的 "中文" 更具体.
         * 都非空且不相容时取 [a] (先识别到的优先).
         */
        fun moreSpecificOf(a: SubtitleTrackLanguage?, b: SubtitleTrackLanguage?): SubtitleTrackLanguage? {
            if (a == null) return b
            if (b == null) return a
            if (a == CHINESE && b.isChineseVariant) return b
            return a
        }

        private val SubtitleTrackLanguage.isChineseVariant: Boolean
            get() = this == CHINESE_SIMPLIFIED || this == CHINESE_TRADITIONAL || this == CANTONESE

        // 连续的字母/数字/连字符/下划线作为一个词; "zh-Hans" 整体匹配, "UHA-WINGS" 也整体匹配而不会被拆开.
        private val WORD_RUN_REGEX = Regex("""[A-Za-z][A-Za-z0-9_-]*""")
        private val EMPTY_BRACKETS_REGEX = Regex("""[\[(（【]\s*[])）】]""")
        private val WRAPPING_BRACKETS_REGEX = Regex("""[\[(（【]([^\[\]()（）【】]+)[])）】]""")
        private val WHITESPACE_RUN_REGEX = Regex("""\s+""")
        private const val SEPARATOR_CHARS = "-·|/_.,、"
    }
}

/**
 * 字幕轨道的显示名称. 由 [subtitleTrackNamesOf] 计算, 通过 [asString] 或 [resolveString] 本地化为文本.
 */
@Immutable
sealed class SubtitleTrackName {
    /**
     * 同名轨道之间的序号, 从 1 开始; `null` 表示不存在同名轨道, 无需消歧.
     */
    abstract val index: Int?

    /** 轨道自带的可读标签, 例如 "简日双语". */
    data class Label(val value: String, override val index: Int? = null) : SubtitleTrackName()

    /** 由语言代码规范化而来. */
    data class Language(
        val language: SubtitleTrackLanguage,
        override val index: Int? = null,
    ) : SubtitleTrackName()

    /** 由 "chs[KitaujiSub]" 这类复合标签解析而来: 语言 + 备注 (通常是字幕组名). */
    data class LanguageWithNote(
        val language: SubtitleTrackLanguage,
        val note: String,
        override val index: Int? = null,
    ) : SubtitleTrackName()

    /** 既无可读标签也无法识别语言, 按轨道在列表中的位置编号. */
    data class Unnamed(val number: Int) : SubtitleTrackName() {
        override val index: Int? get() = null
    }
}

/**
 * 计算 [tracks] 的显示名称, 结果与 [tracks] 一一对应.
 *
 * 优先使用轨道自带的可读标签; 标签本身只是语言代码时改用规范化的语言名; 都没有则按位置编号.
 * 若多个轨道得到相同的名称, 会依次带上从 1 开始的序号以便区分.
 */
fun subtitleTrackNamesOf(tracks: List<SubtitleTrack>): List<SubtitleTrackName> {
    val raw = tracks.mapIndexed { position, track ->
        val metadataLanguage = SubtitleTrackLanguage.parse(track.language)
        val label = track.labels.firstOrNull { it.value.isNotBlank() }?.value?.trim()
        if (label == null) {
            return@mapIndexed if (metadataLanguage != null) {
                SubtitleTrackName.Language(metadataLanguage)
            } else {
                SubtitleTrackName.Unnamed(position + 1)
            }
        }

        val (labelLanguage, note) = SubtitleTrackLanguage.extractFromTitle(label)
        when {
            // 标签含 chs/cht 这类指代词: 语言取标签与 metadata 中更具体的一方, 剩余文本作为备注.
            labelLanguage != null -> {
                val language = SubtitleTrackLanguage.moreSpecificOf(labelLanguage, metadataLanguage)!!
                if (note.isEmpty()) {
                    SubtitleTrackName.Language(language)
                } else {
                    SubtitleTrackName.LanguageWithNote(language, note)
                }
            }
            // 人工可读标签 (如 "简日双语") 原样展示.
            else -> SubtitleTrackName.Label(label)
        }
    }

    val duplicated = raw.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    if (duplicated.isEmpty()) return raw

    val assigned = mutableMapOf<SubtitleTrackName, Int>()
    return raw.map { name ->
        if (name !in duplicated) return@map name
        val index = assigned.getOrElse(name) { 0 } + 1
        assigned[name] = index
        when (name) {
            is SubtitleTrackName.Label -> name.copy(index = index)
            is SubtitleTrackName.Language -> name.copy(index = index)
            is SubtitleTrackName.LanguageWithNote -> name.copy(index = index)
            is SubtitleTrackName.Unnamed -> name // 已按位置编号, 不会重复
        }
    }
}

@Composable
fun SubtitleTrackName.asString(): String {
    val base = when (this) {
        is SubtitleTrackName.Label -> value
        is SubtitleTrackName.Language -> stringResource(language.displayNameRes)
        is SubtitleTrackName.LanguageWithNote -> stringResource(
            Lang.video_player_subtitle_track_language_with_note,
            stringResource(language.displayNameRes), note,
        )
        is SubtitleTrackName.Unnamed -> stringResource(Lang.video_player_subtitle_track_unnamed, number)
    }
    val index = index ?: return base
    return stringResource(Lang.video_player_subtitle_track_indexed, base, index)
}

suspend fun SubtitleTrackName.resolveString(): String {
    val base = when (this) {
        is SubtitleTrackName.Label -> value
        is SubtitleTrackName.Language -> getString(language.displayNameRes)
        is SubtitleTrackName.LanguageWithNote -> getString(
            Lang.video_player_subtitle_track_language_with_note,
            getString(language.displayNameRes), note,
        )
        is SubtitleTrackName.Unnamed -> getString(Lang.video_player_subtitle_track_unnamed, number)
    }
    val index = index ?: return base
    return getString(Lang.video_player_subtitle_track_indexed, base, index)
}

@Immutable
class SubtitlePresentation(
    val subtitleTrack: SubtitleTrack,
    val displayName: String,
)
