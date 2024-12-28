/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.statistics

import me.him188.ani.app.domain.danmaku.AniDanmakuProvider
import me.him188.ani.danmaku.api.DanmakuMatchInfo
import me.him188.ani.danmaku.api.DanmakuMatchMethod
import me.him188.ani.danmaku.dandanplay.DandanplayDanmakuProvider
import me.him188.ani.datasources.api.Media


private fun noMatch() = DanmakuMatchInfo(
    providerId = DandanplayDanmakuProvider.ID,
    count = 200,
    method = DanmakuMatchMethod.NoMatch,
)

private fun exactId() = DanmakuMatchInfo(
    providerId = AniDanmakuProvider.ID,
    count = 200,
    method = DanmakuMatchMethod.ExactId(1, 2),
)

private fun exactMatch() = DanmakuMatchInfo(
    providerId = DandanplayDanmakuProvider.ID,
    count = 100,
    method = DanmakuMatchMethod.Exact("Subject Title", "Episode Title"),
)

private fun fuzzy() = DanmakuMatchInfo(
    providerId = DandanplayDanmakuProvider.ID,
    count = 100,
    method = DanmakuMatchMethod.Fuzzy("Subject Title", "Episode Title"),
)

private fun halfFuzzy() = DanmakuMatchInfo(
    providerId = DandanplayDanmakuProvider.ID,
    count = 100,
    method = DanmakuMatchMethod.ExactSubjectFuzzyEpisode("Subject Title", "Episode Title"),
)

fun testPlayerStatisticsState(
    playingMedia: Media? = null,
    playingFilename: String = "filename-filename-filename-filename-filename-filename-filename.mkv",
    videoLoadingState: VideoLoadingState = VideoLoadingState.Initial,
) = VideoStatistics(
    playingMedia = playingMedia,
    playingMediaSourceInfo = null,
    playingFilename = playingFilename,
    mediaSourceLoading = true,
    videoLoadingState = videoLoadingState,
)
