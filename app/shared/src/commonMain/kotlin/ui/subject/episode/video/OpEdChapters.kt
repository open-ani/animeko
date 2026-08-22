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
import kotlinx.coroutines.flow.onStart
import me.him188.ani.datasources.api.MediaChapter
import me.him188.ani.datasources.api.MediaChapterKind
import org.openani.mediamp.InternalMediampApi
import org.openani.mediamp.metadata.Chapter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
        ::createAutoSkipChapterCandidates,
    )
    val explicitSkipChapters = combine(
        autoSkipChapters.onStart { emit(emptyList()) },
        mediaChapters.onStart { emit(emptyList()) },
    ) { fallback, primary ->
        mergeSkipChapterCandidates(
            primary = primary.mapNotNull { it.toSkipChapterCandidateOrNull() },
            fallback = fallback,
        )
    }
    val playerSkipChapters = combine(playerChapters, videoLength) { chapters, length ->
        chapters.mapNotNull { chapter ->
            chapter.takeIf { isLikelyOpEdChapter(it, length) }
                ?.let { SkipChapterCandidate(it) }
        }
    }
    val skipChapters = combine(explicitSkipChapters, playerSkipChapters) { explicit, fallback ->
        mergeSkipChapterCandidates(explicit, fallback).map { it.chapter }
    }
    val progressChapters = combine(
        playerChapters,
        mediaChapters.onStart { emit(emptyList()) },
        explicitSkipChapters,
    ) { player, media, skip ->
        val regularMediaChapters = media
            .filter { it.kind == MediaChapterKind.CHAPTER }
            .map { Chapter(it.name, it.durationMillis, it.offsetMillis) }
        (player + regularMediaChapters + skip.map { it.chapter })
            .distinctBy { Pair(it.name, it.offsetMillis) }
    }
    return OpEdChapterFlows(skipChapters, progressChapters)
}

@OptIn(InternalMediampApi::class)
private fun createAutoSkipChapterCandidates(
    times: List<Long>,
    videoLength: Duration,
    opEdSkipDuration: Duration,
): List<SkipChapterCandidate> {
    val durationMillis = when {
        videoLength > 20.minutes -> opEdSkipDuration.inWholeMilliseconds
        videoLength > 10.minutes -> 55_000L
        else -> return emptyList()
    }
    return times.sorted().mapIndexed { index, timeMillis ->
        val kind = if (times.size == 2) {
            if (index == 0) MediaChapterKind.OPENING else MediaChapterKind.ENDING
        } else {
            null
        }
        SkipChapterCandidate(
            chapter = Chapter(
                name = when (kind) {
                    MediaChapterKind.OPENING -> "OP"
                    MediaChapterKind.ENDING -> "ED"
                    else -> "Ch ${index + 1}"
                },
                durationMillis = durationMillis,
                offsetMillis = timeMillis,
            ),
            kind = kind,
        )
    }
}

internal data class SkipChapterCandidate(
    val chapter: Chapter,
    val kind: MediaChapterKind? = null,
)

@OptIn(InternalMediampApi::class)
internal fun MediaChapter.toSkipChapterCandidateOrNull(): SkipChapterCandidate? {
    if (kind == MediaChapterKind.CHAPTER) return null
    return SkipChapterCandidate(
        chapter = Chapter(name, durationMillis, offsetMillis),
        kind = kind,
    )
}

internal fun mergeSkipChapterCandidates(
    primary: List<SkipChapterCandidate>,
    fallback: List<SkipChapterCandidate>,
): List<SkipChapterCandidate> = buildList {
    addAll(primary)
    fallback.forEach { candidate ->
        val alreadyCovered = candidate.kind?.let { kind ->
            any { it.kind == kind }
        } ?: any { chaptersOverlap(it.chapter, candidate.chapter) }
        if (!alreadyCovered) add(candidate)
    }
}

private fun chaptersOverlap(first: Chapter, second: Chapter): Boolean {
    val firstEnd = first.offsetMillis + first.durationMillis
    val secondEnd = second.offsetMillis + second.durationMillis
    return first.offsetMillis < secondEnd && second.offsetMillis < firstEnd
}

internal fun isLikelyOpEdChapter(chapter: Chapter, videoLength: Duration): Boolean {
    return OpEdLength.fromVideoLengthOrNull(videoLength)
        ?.isOpEdChapter(chapter.durationMillis.milliseconds) == true
}

fun interface OpEdLength {
    fun isOpEdChapter(chapterLength: Duration): Boolean

    companion object {
        private val Normal = OpEdLength { it in 80.seconds..95.seconds }
        private val Short = OpEdLength { it in 55.seconds..65.seconds }

        fun fromVideoLengthOrNull(length: Duration): OpEdLength? {
            return when {
                length > 20.minutes -> Normal
                length > 10.minutes -> Short
                else -> null
            }
        }
    }
}
