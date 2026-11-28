/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player

import me.him188.ani.app.data.models.preference.SubtitleTextConversion
import me.him188.ani.app.domain.danmaku.DanmakuTextConverter
import me.him188.ani.danmaku.ui.DanmakuTextConversion

/** 播放会话内复用简繁词典与转换缓存，转换结果只用于显示。 */
class SubtitleTextConverter {
    private val converter = DanmakuTextConverter()

    suspend fun convert(text: String, target: SubtitleTextConversion): String {
        if (text.isEmpty() || target == SubtitleTextConversion.ORIGINAL) return text
        val conversion = when (target) {
            SubtitleTextConversion.ORIGINAL -> DanmakuTextConversion.ORIGINAL
            SubtitleTextConversion.SIMPLIFIED -> DanmakuTextConversion.SIMPLIFIED
            SubtitleTextConversion.TRADITIONAL -> DanmakuTextConversion.TRADITIONAL
        }
        return converter.convert(listOf(text), conversion).single()
    }
}
