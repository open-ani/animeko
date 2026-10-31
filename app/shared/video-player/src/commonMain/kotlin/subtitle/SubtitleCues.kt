/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.subtitle

internal data class SubtitleCue(val startMillis: Long, val endMillis: Long, val text: String)

/** SRT 与 WebVTT 文本字幕；结束时间不包含在对白区间内。 */
internal fun parseSubtitleCues(text: String): List<SubtitleCue> {
    fun time(value: String): Long? {
        val parts = value.replace(',', '.').split(':')
        if (parts.size !in 2..3) return null
        val seconds = parts.last().toDoubleOrNull() ?: return null
        val minutes = parts[parts.lastIndex - 1].toLongOrNull() ?: return null
        val hours = if (parts.size == 3) parts.first().toLongOrNull() ?: return null else 0
        return hours * 3_600_000 + minutes * 60_000 + (seconds * 1000).toLong()
    }
    return text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        .split(Regex("\n[ \t]*\n")).mapNotNull { block ->
            val lines = block.lines()
            val timing = lines.indexOfFirst { "-->" in it }
            if (timing < 0) return@mapNotNull null
            val bounds = lines[timing].split("-->")
            if (bounds.size != 2) return@mapNotNull null
            val start = time(bounds[0].trim()) ?: return@mapNotNull null
            val end = time(bounds[1].trim().substringBefore(' ')) ?: return@mapNotNull null
            if (end <= start) return@mapNotNull null
            val content = lines.drop(timing + 1).joinToString("\n")
                .replace(Regex("<[^>]*>|\\{[^}]*}"), "")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&")
            SubtitleCue(start, end, content)
        }.sortedBy { it.startMillis }
}

/** 二分定位当前对白，前缀结束时间支持重叠对白与任意方向 seek。 */
internal class SubtitleCueTimeline(cues: List<SubtitleCue>) {
    private val cues = cues.sortedBy { it.startMillis }
    private val latestEnd = LongArray(cues.size).also { ends ->
        this.cues.forEachIndexed { index, cue ->
            ends[index] = maxOf(cue.endMillis, ends.getOrElse(index - 1) { 0L })
        }
    }

    fun textAt(positionMillis: Long): String {
        var low = 0
        var high = cues.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (cues[mid].startMillis <= positionMillis) low = mid + 1 else high = mid
        }
        var index = low - 1
        val active = mutableListOf<String>()
        while (index >= 0 && latestEnd[index] > positionMillis) {
            val cue = cues[index]
            if (cue.endMillis > positionMillis) active += cue.text
            index--
        }
        return active.asReversed().joinToString("\n")
    }
}
