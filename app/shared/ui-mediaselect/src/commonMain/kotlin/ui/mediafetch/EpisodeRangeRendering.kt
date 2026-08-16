/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediafetch

import me.him188.ani.datasources.api.topic.EpisodeRange

/**
 * 将 [EpisodeRange] 渲染为用于 UI 展示的紧凑文本, 例如 `"01..12"`.
 */
fun renderEpisodeRange(range: EpisodeRange): String {
    val sorts = range.knownSorts.toList()
    return when (sorts.size) {
        0 -> range.toString() // Season 等未知具体集数的类型
        1 -> sorts.first().toString()
        else -> "${sorts.min()}..${sorts.max()}"
    }
}
