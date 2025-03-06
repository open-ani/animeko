/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.mediaFetch

import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.UnsafeOriginalMediaAccess
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.logger

object MediaSelectorDebugTools {
    private val logger = logger<MediaSelectorDebugTools>()

    @OptIn(UnsafeOriginalMediaAccess::class)
    fun dumpSubjectNames(filteredCandidates: List<MaybeExcludedMedia>) {
        val result = filteredCandidates
            .map { it.original }
            .distinctBy { it.properties.subjectName }

        logger.debug {
            val joinToString = result.joinToString("\n") { media ->
                media.properties.subjectName?.let { "\"$it\"," }.toString()
            }
            "Dumping subject names: \n\n$joinToString"
        }
    }

    @OptIn(UnsafeOriginalMediaAccess::class)
    fun dumpMediaSubjectNameAndEpisodes(filteredCandidates: List<MaybeExcludedMedia>) {
        val result = filteredCandidates
            .map { it.original }

        logger.debug {
            val joinToString = result.mapNotNull { media ->
                val subjectName = media.properties.subjectName ?: return@mapNotNull null
                val sort = media.episodeRange ?: return@mapNotNull null
                "\"$subjectName\" to ${sortsToString(sort)},"
            }.joinToString("\n")
            "Dump: \n\n$joinToString"
        }
    }

    private fun sortsToString(sort: EpisodeRange): String? {
        val knownSorts = sort.knownSorts.toList()
        if (knownSorts.isEmpty()) {
            return null
        }
        knownSorts.singleOrNull()?.let {
            return "EpisodeSort(\"$it\")"
        }

        return sort.knownSorts
            .joinToString(", ") { "\"$it\"" }
            .let { "listOf($it)" }
    }
}
