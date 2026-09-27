/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediafetch

import me.him188.ani.datasources.api.Media
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.logger

/**
 * BT 页 debug 构建下的两个按钮: 把当前列表里的条目名 / 剧集范围打到日志, 用于补充标题解析的测试数据.
 */
object MediaSelectorDebugTools {
    private val logger = logger<MediaSelectorDebugTools>()

    fun dumpSubjectNames(mediaList: List<Media>) {
        val result = mediaList.distinctBy { it.properties.subjectName }

        logger.debug {
            val joinToString = result.joinToString("\n") { media ->
                media.properties.subjectName?.let { "\"$it\"," }.toString()
            }
            "Dumping subject names: \n\n$joinToString"
        }
    }

    fun dumpEpisodeRanges(mediaList: List<Media>) {
        val ranges = mediaList.mapNotNull { it.episodeRange }
        logger.debug {
            val joinToString = ranges
                .distinctBy { it.knownSorts.toList() }
                .joinToString("\n") { range ->
                    range.toString()
                }
            "Dumping episode ranges: \n\n$joinToString"
        }
    }
}
