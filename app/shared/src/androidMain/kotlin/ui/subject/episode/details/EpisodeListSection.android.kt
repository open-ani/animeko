/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.details

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.theme.AniTheme
import me.him188.ani.app.ui.subject.createTestAiringLabelState
import me.him188.ani.utils.platform.annotations.TestOnly

/**
 * Preview - 窄屏剧集列表
 */
@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
internal fun PreviewEpisodeListSectionNarrow() = ProvideCompositionLocalsForPreview {
    AniTheme {
        Surface {
            EpisodeListSection(
                episodeCarouselState = remember {
                    EpisodeCarouselState(
                        episodes = mutableStateOf(TestEpisodeCollections),
                        playingEpisode = mutableStateOf(TestEpisodeCollections.getOrNull(2)),
                        cacheStatus = { EpisodeCacheStatus.NotCached },
                        onSelect = {},
                        onChangeCollectionType = { _, _ -> },
                        backgroundScope = CoroutineScope(Dispatchers.Default),
                    )
                },
                expanded = false,
                airingLabelState = createTestAiringLabelState(),
                onToggleExpanded = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}