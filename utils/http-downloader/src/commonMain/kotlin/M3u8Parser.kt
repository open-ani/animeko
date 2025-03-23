/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.httpdownloader

/**
 * Naive M3U8 parser that looks for `#EXTINF` lines and extracts the next
 * non-`#` line as the segment URL. Uses a one-line buffer to handle the
 * scenario where `#EXTINF` is directly followed by another `#EXTINF`.
 */
object M3u8Parser {

    /**
     * Parses the given M3U8 text and returns a list of [SegmentInfo].
     *
     * - Uses [lineSequence] to handle large M3U8 files in a memory-friendly way.
     * - If the line immediately following `#EXTINF` is not a valid URL
     *   (i.e., it starts with `#` or we run out of lines), that segment is skipped.
     * - Handles the scenario where two `#EXTINF` lines appear consecutively;
     *   the second one is not swallowed incorrectly.
     */
    fun parse(text: String): List<SegmentInfo> {
        val segments = mutableListOf<SegmentInfo>()
        val linesIterator = text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .iterator()

        var segmentIndex = 0
        var buffer: String? = null

        while (true) {
            // If we have a buffered line, use it; otherwise read the next line from the iterator
            val line = buffer ?: if (linesIterator.hasNext()) linesIterator.next() else null
            buffer = null  // reset buffer
            if (line == null) break

            // If line is #EXTINF, attempt to read the next line as a URL
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                // Look at the subsequent line
                val potentialUrl = if (linesIterator.hasNext()) linesIterator.next() else null

                // If that line does not start with '#', treat it as a URL
                if (potentialUrl != null && !potentialUrl.startsWith("#")) {
                    segments.add(
                        SegmentInfo(
                            index = segmentIndex++,
                            url = potentialUrl,
                            isDownloaded = false,
                            byteSize = -1,
                            tempFilePath = null,
                        ),
                    )
                } else {
                    // If the "URL" line we just read is itself another #EXTINF,
                    // push it back into the buffer so the loop processes it in the next iteration
                    if (potentialUrl != null && potentialUrl.startsWith("#EXTINF", ignoreCase = true)) {
                        buffer = potentialUrl
                    }
                    // Otherwise, just skip this incomplete or invalid entry
                }
            }
            // Else: this line doesn't begin with #EXTINF => ignore
        }

        return segments
    }
}
