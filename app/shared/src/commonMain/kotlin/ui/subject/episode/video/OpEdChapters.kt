/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import me.him188.ani.datasources.api.MediaChapter
import me.him188.ani.datasources.api.MediaChapterKind
import org.openani.mediamp.InternalMediampApi
import org.openani.mediamp.metadata.Chapter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal data class OpEdChapterFlows(
    val skipChapters: Flow<List<Chapter>>,
    val progressChapters: Flow<List<Chapter>>,
)

@OptIn(InternalMediampApi::class)
internal fun createOpEdChapterFlows(
    playerChapters: Flow<List<Chapter>>,
    videoLength: Flow<Duration>,
    autoSkipTimes: Flow<List<Long>>,
    opEdSkipDuration: Flow<Duration>,
    mediaChapters: Flow<List<MediaChapter>>,
): OpEdChapterFlows {
    val autoSkipChapters = combine(
        autoSkipTimes,
        videoLength,
        opEdSkipDuration,
        ::createAutoSkipChapters,
    )
    val fallbackSkipChapters = combine(
        playerChapters,
        autoSkipChapters.onStart { emit(emptyList()) },
        videoLength,
    ) { chapters, autoSkip, length ->
        chapters.filter { isLikelyOpEdChapter(it, length) } + autoSkip
    }
    val mediaChaptersWithInitial = mediaChapters.onStart { emit(emptyList()) }
    val skipChapters = combine(
        mediaChaptersWithInitial,
        fallbackSkipChapters,
        ::selectSkipChapters,
    )
    val regularMediaChapters = mediaChaptersWithInitial.map { chapters ->
        chapters.filter { it.kind == MediaChapterKind.CHAPTER }.map { it.toChapter() }
    }
    val progressChapters = combine(
        playerChapters,
        regularMediaChapters,
        skipChapters,
    ) { player, media, skip ->
        (player + media + skip)
            .distinctBy { Pair(it.name, it.offsetMillis) }
    }
    return OpEdChapterFlows(skipChapters, progressChapters)
}

@OptIn(InternalMediampApi::class)
private fun createAutoSkipChapters(
    times: List<Long>,
    videoLength: Duration,
    opEdSkipDuration: Duration,
): List<Chapter> {
    val durationMillis = when {
        videoLength > 20.minutes -> opEdSkipDuration.inWholeMilliseconds
        videoLength > 10.minutes -> 55_000L
        else -> return emptyList()
    }
    return times.sorted().mapIndexed { index, timeMillis ->
        Chapter(
            name = when {
                times.size != 2 -> "Ch ${index + 1}"
                index == 0 -> "OP"
                else -> "ED"
            },
            durationMillis = durationMillis,
            offsetMillis = timeMillis,
        )
    }
}

@OptIn(InternalMediampApi::class)
internal fun selectSkipChapters(
    mediaChapters: List<MediaChapter>,
    fallback: List<Chapter>,
): List<Chapter> {
    return mediaChapters
        .filter { it.kind != MediaChapterKind.CHAPTER }
        .map { it.toChapter() }
        .ifEmpty { fallback }
}

@OptIn(InternalMediampApi::class)
private fun MediaChapter.toChapter(): Chapter = Chapter(name, durationMillis, offsetMillis)

internal fun isLikelyOpEdChapter(chapter: Chapter, videoLength: Duration): Boolean {
    val expectedDurationMillis = when {
        videoLength > 20.minutes -> 80_000L..95_000L
        videoLength > 10.minutes -> 55_000L..65_000L
        else -> return false
    }
    return chapter.durationMillis in expectedDurationMillis
}
