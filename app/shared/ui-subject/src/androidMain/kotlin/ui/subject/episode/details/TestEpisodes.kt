/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.details

import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.domain.episode.SubjectRecommendation
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

@Stable
val TestEpisodes = buildList {
    repeat(12) { id ->
        add(
            EpisodeInfo(
                id, EpisodeType.MainStory,
                nameCn =
                    if (id.rem(2) == 0) "中文剧集名称中文剧集名称中文剧集名称中文剧集名称"
                    else "中文剧集名称",
                name = "Episode Name $id",
                sort = EpisodeSort((24 + id).toString()),
                ep = EpisodeSort(id.toString()),
            ),
        )
    }
}

@Stable
val TestEpisodeCollections = TestEpisodes.map {
    EpisodeCollectionInfo(
        it,
        when ((it.ep?.number ?: 0).toInt().rem(3)) {
            0 -> UnifiedCollectionType.DONE
            1 -> UnifiedCollectionType.WISH
            else -> UnifiedCollectionType.DOING
        },
    )
}

// Preview only
@Stable
val PreviewScope = CoroutineScope(
    CoroutineExceptionHandler { _, _ ->
    },
)

@Stable
val TestSubjectRecommendations = buildList {
    repeat(10) {
        add(
            SubjectRecommendation(
                subjectId = it.toLong(),
                name = "Subject Recommendation $it",
                nameCn = "推荐条目中文名称 $it",
                imageUrl = "",
                uri = null,
                desc1 = "2021 年 10 月",
                desc2 = "2 万收藏 · 8.0 分",
            ),
        )
    }
}