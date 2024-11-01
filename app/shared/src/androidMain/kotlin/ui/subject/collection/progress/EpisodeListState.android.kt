/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection.progress

import kotlinx.coroutines.CoroutineScope
import me.him188.ani.app.data.models.episode.EpisodeProgressInfo
import me.him188.ani.app.data.models.preference.EpisodeListProgressTheme
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.subject.episode.details.TestEpisodes
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly


@TestOnly
fun createTestEpisodeListState(
    subjectId: Int,
    backgroundScope: CoroutineScope,
): EpisodeListState {
    return EpisodeListState(
        subjectId = stateOf(subjectId),
        theme = stateOf(EpisodeListProgressTheme.Default),
        episodeProgressInfoList = stateOf(
            TestEpisodes.map {
                EpisodeProgressInfo(
                    it,
                    UnifiedCollectionType.DONE,
                    EpisodeCacheStatus.NotCached,
                )
            },
        ),
        onSetEpisodeWatched = { _, _, _ -> },
        backgroundScope = backgroundScope,
    )
}
