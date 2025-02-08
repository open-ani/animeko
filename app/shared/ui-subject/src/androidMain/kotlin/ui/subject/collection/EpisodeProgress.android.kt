/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.PreviewLightDark
import me.him188.ani.app.data.models.episode.EpisodeProgressItem
import me.him188.ani.app.data.models.preference.EpisodeListProgressTheme
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.subject.episode.list.EpisodeListDefaults
import me.him188.ani.app.ui.subject.episode.list.EpisodeListDialog
import me.him188.ani.app.ui.subject.episode.list.EpisodeListFlowRow
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.api.topic.UnifiedCollectionType


private val airDate = PackedDate(2021, 1, 1)

private val testEpisodes = listOf(
    EpisodeProgressItem(
        episodeId = 0,
        episodeSort = "00",
        collectionType = UnifiedCollectionType.DONE,
        isOnAir = false,
        cacheStatus = EpisodeCacheStatus.Caching(0.3f.toProgress(), 300.megaBytes),
    ),
    EpisodeProgressItem(
        episodeId = 1,
        episodeSort = "01",
        collectionType = UnifiedCollectionType.DONE,
        isOnAir = false,
        cacheStatus = EpisodeCacheStatus.NotCached,
    ),
    EpisodeProgressItem(
        episodeId = 2,
        episodeSort = "02",
        collectionType = UnifiedCollectionType.DONE,
        isOnAir = false,
        cacheStatus = EpisodeCacheStatus.Cached(300.megaBytes),
    ),
    EpisodeProgressItem(
        episodeId = 3,
        episodeSort = "03",
        collectionType = UnifiedCollectionType.WISH,
        isOnAir = false,
        cacheStatus = EpisodeCacheStatus.Cached(300.megaBytes),
    ),
    EpisodeProgressItem(
        episodeId = 4,
        episodeSort = "04",
        collectionType = UnifiedCollectionType.WISH,
        isOnAir = false,
        cacheStatus = EpisodeCacheStatus.Caching(0.7f.toProgress(), 300.megaBytes),
    ),
    EpisodeProgressItem(
        episodeId = 5,
        episodeSort = "05",
        collectionType = UnifiedCollectionType.WISH,
        isOnAir = false,
        cacheStatus = EpisodeCacheStatus.NotCached,
    ),
    EpisodeProgressItem(
        episodeId = 6,
        episodeSort = "06",
        collectionType = UnifiedCollectionType.WISH,
        isOnAir = true,
        cacheStatus = EpisodeCacheStatus.NotCached,
    ),
    EpisodeProgressItem(
        episodeId = 7,
        episodeSort = "07",
        collectionType = UnifiedCollectionType.WISH,
        isOnAir = true,
        cacheStatus = EpisodeCacheStatus.Cached(300.megaBytes),
    ),
    EpisodeProgressItem(
        episodeId = 8,
        episodeSort = "08",
        collectionType = UnifiedCollectionType.WISH,
        isOnAir = true,
        cacheStatus = EpisodeCacheStatus.Caching(0.3f.toProgress(), 300.megaBytes),
    ),
)

@PreviewLightDark
@Composable
private fun PreviewEpisodeProgressDialog() {
    ProvideCompositionLocalsForPreview {
        EpisodeListDialog(
            onDismissRequest = {},
            title = { Text(text = "葬送的芙莉莲") },
            onClickCache = {},
        ) {
            val episodes = remember {
                testEpisodes
            }
            EpisodeListFlowRow(
                episodes = {
                    episodes
                },
                onClickEpisodeState = {},
                onLongClickEpisode = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewEpisodeProgressDialogLightUp() {
    ProvideCompositionLocalsForPreview {
        EpisodeListDialog(
            onDismissRequest = {},
            title = { Text(text = "葬送的芙莉莲") },
            onClickCache = {},
        ) {
            val episodes = remember {
                testEpisodes
            }
            EpisodeListFlowRow(
                episodes = {
                    episodes
                },
                onClickEpisodeState = {},
                onLongClickEpisode = {},
                colors = EpisodeListDefaults.colors(EpisodeListProgressTheme.LIGHT_UP),
            )
        }
    }
}

// 特别长需要限制高度并且滚动, #182
@PreviewLightDark
@Composable
private fun PreviewEpisodeProgressDialogVeryLong() {
    ProvideCompositionLocalsForPreview {
        EpisodeListDialog(
            onDismissRequest = {},
            title = { Text(text = "银魂") },
            onClickCache = {},
        ) {
            val episodes = remember {
                // 数字太大 preview 会很卡
                (0..70).map { item(it) }
            }
            EpisodeListFlowRow(
                episodes = {
                    episodes
                },
                onClickEpisodeState = {},
                onLongClickEpisode = {},
            )
        }
    }
}

private fun item(id: Int) = EpisodeProgressItem(
    episodeId = id,
    episodeSort = id.toString(),
    collectionType = UnifiedCollectionType.WISH,
    isOnAir = true,
    cacheStatus = EpisodeCacheStatus.Caching(0.3f.toProgress(), 300.megaBytes),
)
