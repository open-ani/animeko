/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode

import kotlin.math.ceil

/** A cancelled chapter stays cancelled until the media/episode changes. */
internal class TvAutoSkipController {
    private val handled = mutableSetOf<Long>()
    private var pending: TvChapter? = null
    private var skipAt = 0L

    fun reset() {
        handled.clear()
        pending = null
    }

    fun cancel() {
        pending?.let { handled += it.offsetMillis }
        pending = null
    }

    fun update(
        position: Long,
        duration: Long,
        chapters: List<TvChapter>,
        enabled: Boolean,
        onSkip: (Long) -> Unit,
    ): TvSkipPrompt? {
        if (!enabled) {
            pending = null
            return null
        }
        val lengthRange = when {
            duration > 1_200_000 -> 80_000L..95_000L
            duration > 600_000 -> 55_000L..65_000L
            else -> return null
        }
        val chapter = pending?.takeIf {
            position in it.offsetMillis - 5_000..skipAt + 1_000 && position < it.offsetMillis + it.durationMillis
        } ?: chapters.firstOrNull {
            it.durationMillis in lengthRange && it.offsetMillis !in handled &&
                    it.offsetMillis in position - 1_000..position + 5_000 &&
                    it.offsetMillis + it.durationMillis <= duration
        }
        if (chapter != pending && chapter != null) {
            // Even rules received at the chapter boundary must leave time to cancel.
            skipAt = maxOf(chapter.offsetMillis, position + 5_000)
        }
        pending = chapter
        if (chapter == null) return null
        if (position >= skipAt) {
            handled += chapter.offsetMillis
            pending = null
            onSkip(chapter.offsetMillis + chapter.durationMillis)
            return null
        }
        return TvSkipPrompt(chapter.name, ceil((skipAt - position) / 1000.0).toInt())
    }
}
