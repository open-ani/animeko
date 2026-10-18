/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource

import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import me.him188.ani.utils.logging.Logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatform

object MediaSourceEngineHelpers {
    fun encodeUrlSegment(value: String) =
        URLBuilder().appendPathSegments(value).encodedPathSegments.first()

    /**
     * 配置声明了只支持某些播放器 ([onlySupportsPlayers], 值为 `mpv`, `vlc`, `exoplayer`, `avkit`) 时,
     * 当前平台的播放器是否在其中. 为空表示不限制. 不在则记录日志, 自动匹配不发起任何请求.
     */
    fun isSupportedByPlatformPlayer(onlySupportsPlayers: List<String>, sourceName: String, logger: Logger): Boolean {
        if (onlySupportsPlayers.isEmpty()) return true
        val currentPlayerNames = when (currentPlatform()) {
            // 桌面端已迁移至 mpv, 但许多现有订阅仍声明 "vlc", 暂时保持兼容
            is Platform.Desktop -> listOf("mpv", "vlc")
            is Platform.Android -> listOf("exoplayer")
            Platform.Ios -> listOf("avkit")
        }
        if (currentPlayerNames.any { it in onlySupportsPlayers }) return true
        logger.warn {
            val supports = onlySupportsPlayers.joinToString(prefix = "[", postfix = "]")
            "MediaSource '$sourceName' is not supported by the platform player. " +
                    "Declared supported players: $supports, current players: $currentPlayerNames"
        }
        return false
    }

    fun getSearchKeyword(subjectName: String, removeSpecial: Boolean, useOnlyFirstWord: Boolean): String {
        val finalName = if (removeSpecial) {
            MediaListFilters.removeSpecials(
                subjectName,
                removeWhitespace = false,
                replaceNumbers = false,
                removeMarkers = true, // Note: 删除 "剧场版" 等标记, 否则 "剧场版 命运石之门" 会变为 "剧场版"
            ) // keep whitespace for getFirstWord
        } else {
            subjectName
        }

        return if (useOnlyFirstWord) {
            getFirstWord(finalName)
        } else {
            finalName
        }
    }

    private fun getFirstWord(string: String): String {
        if (!(string.contains(' '))) return string
        return string.substringBefore(' ').ifBlank { string }
    }
}