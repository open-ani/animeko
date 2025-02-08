/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.details

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.PreviewLightDark
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes


@Composable
@PreviewLightDark
fun PreviewEpisodeCarouselOnSurface() = ProvideCompositionLocalsForPreview {
    Surface {
        EpisodeCarousel(
            state = remember {
                EpisodeCarouselState(
                    episodes = mutableStateOf(TestEpisodeCollections),
                    playingEpisode = mutableStateOf(TestEpisodeCollections[2]),
                    cacheStatus = {
                        when ((it.episodeInfo.sort.number ?: 0).toInt().rem(3)) {
                            0 -> EpisodeCacheStatus.Cached(123.megaBytes)
                            1 -> EpisodeCacheStatus.Caching(0.3f.toProgress(), 123.megaBytes)
                            else -> EpisodeCacheStatus.NotCached
                        }
                    },
                    onSelect = {},
                    onChangeCollectionType = { _, _ -> },
                    backgroundScope = PreviewScope,
                )
            },
        )
    }
}
