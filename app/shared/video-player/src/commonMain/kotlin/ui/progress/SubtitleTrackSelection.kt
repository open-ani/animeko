/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.progress

import me.him188.ani.app.data.models.preference.SubtitleTrackPreference
import org.openani.mediamp.metadata.SubtitleTrack

/**
 * 依据用户偏好对新加载视频的字幕轨道做出的决定.
 */
sealed class SubtitleTrackSelection {
    /** 不干预播放器的默认选择: 用户还没有表达过偏好, 或没有轨道匹配上偏好. */
    data object KeepCurrent : SubtitleTrackSelection()

    /** 关闭字幕. */
    data object Off : SubtitleTrackSelection()

    data class Select(val track: SubtitleTrack) : SubtitleTrackSelection()
}

/**
 * 记录用户手动选择的轨道 (`null` 表示关闭字幕) 为偏好.
 */
fun SubtitleTrack?.toSubtitleTrackPreference(): SubtitleTrackPreference {
    if (this == null) return SubtitleTrackPreference(off = true)
    return SubtitleTrackPreference(
        off = false,
        label = labels.firstOrNull { it.value.isNotBlank() }?.value?.trim(),
        language = language?.trim()?.takeIf { it.isNotEmpty() },
    )
}

/**
 * 在 [candidates] 中挑选最符合 [preference] 的轨道.
 *
 * 优先精确匹配标签原文, 其次匹配规范化后的语言, 最后退到中文各变体之间的粗略匹配.
 * 分数相同时取列表中靠前的轨道.
 */
fun subtitleTrackSelectionFor(
    candidates: List<SubtitleTrack>,
    preference: SubtitleTrackPreference,
): SubtitleTrackSelection {
    if (!preference.isRecorded) return SubtitleTrackSelection.KeepCurrent
    if (preference.off) return SubtitleTrackSelection.Off

    var best: SubtitleTrack? = null
    var bestScore = 0
    for (track in candidates) {
        val score = matchScore(track, preference)
        if (score > bestScore) {
            bestScore = score
            best = track
        }
    }
    return best?.let { SubtitleTrackSelection.Select(it) } ?: SubtitleTrackSelection.KeepCurrent
}

private const val SCORE_LABEL_EXACT = 100
private const val SCORE_LANGUAGE_EXACT = 50
private const val SCORE_CHINESE_VARIANT = 25

private fun matchScore(track: SubtitleTrack, preference: SubtitleTrackPreference): Int {
    val preferredLabel = preference.label?.trim()?.takeIf { it.isNotEmpty() }
    if (preferredLabel != null &&
        track.labels.any { it.value.trim().equals(preferredLabel, ignoreCase = true) }
    ) {
        return SCORE_LABEL_EXACT
    }

    val preferredLanguage = SubtitleTrackLanguage.parse(preference.language)
        ?: SubtitleTrackLanguage.parse(preference.label)
    if (preferredLanguage == null) {
        // 语言代码无法规范化时只能比字面, 例如某些冷门语言.
        val raw = preference.language?.trim()?.takeIf { it.isNotEmpty() } ?: return 0
        return if (track.language?.trim().equals(raw, ignoreCase = true)) SCORE_LANGUAGE_EXACT else 0
    }

    val trackLanguage = SubtitleTrackLanguage.parse(track.language)
        ?: track.labels.firstNotNullOfOrNull { SubtitleTrackLanguage.parse(it.value) }
        ?: return 0
    return when {
        trackLanguage == preferredLanguage -> SCORE_LANGUAGE_EXACT
        trackLanguage.isChinese && preferredLanguage.isChinese -> SCORE_CHINESE_VARIANT
        else -> 0
    }
}

private val SubtitleTrackLanguage.isChinese: Boolean
    get() = when (this) {
        SubtitleTrackLanguage.CHINESE,
        SubtitleTrackLanguage.CHINESE_SIMPLIFIED,
        SubtitleTrackLanguage.CHINESE_TRADITIONAL,
        SubtitleTrackLanguage.CANTONESE,
            -> true

        else -> false
    }
