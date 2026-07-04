/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.source

import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals

class CandidateSelectionTest {
    @Test
    fun sortedForRequest_prefers_first_season_over_later_seasons_when_request_has_no_season() {
        val request = MediaFetchRequest(
            subjectId = "329906",
            episodeId = "1088220",
            subjectNameCN = "间谍过家家",
            subjectNames = listOf("间谍过家家", "SPY×FAMILY"),
            episodeSort = EpisodeSort("1"),
            episodeName = "行动代号〈枭〉",
            episodeEp = EpisodeSort("1"),
        )

        val matches = listOf(
            candidate("间谍过家家第三季 第01集"),
            candidate("间谍过家家第一季 第01集"),
            candidate("间谍过家家 第01集"),
        )

        val ordered = matches.sortedForRequest(request)

        assertEquals("间谍过家家 第01集", ordered[0].media.originalTitle)
        assertEquals("间谍过家家第一季 第01集", ordered[1].media.originalTitle)
        assertEquals("间谍过家家第三季 第01集", ordered[2].media.originalTitle)
    }

    private fun candidate(title: String): MediaMatch {
        return MediaMatch(
            media = DefaultMedia(
                mediaId = title,
                mediaSourceId = "E-ACG",
                originalUrl = "https://example.com/$title",
                download = ResourceLocation.WebVideo("https://example.com/$title"),
                originalTitle = title,
                publishedTime = 0L,
                properties = MediaProperties(
                    subjectName = title,
                    episodeName = title,
                    subtitleLanguageIds = listOf("CHS"),
                    resolution = "1080P",
                    alliance = "E-ACG",
                    size = FileSize.Unspecified,
                    subtitleKind = SubtitleKind.EMBEDDED,
                ),
                episodeRange = null,
                location = MediaSourceLocation.Online,
                kind = MediaSourceKind.WEB,
            ),
            kind = MatchKind.FUZZY,
        )
    }
}
