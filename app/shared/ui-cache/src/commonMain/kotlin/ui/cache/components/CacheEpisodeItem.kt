/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.tools.Progress
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.mediafetch.rememberTestMediaSourceInfoProvider
import me.him188.ani.datasources.api.topic.FileSize.Companion.Unspecified
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.utils.platform.annotations.TestOnly

@Immutable
enum class CacheEpisodePaused {
    IN_PROGRESS,
    PAUSED,
    FAILED,
    COMPLETED,
}

@OptIn(TestOnly::class)
@Preview
@Composable
private fun PreviewCacheEpisodeRowVariants() = ProvideCompositionLocalsForPreview {
    Column {
        CacheEpisodeRowForPreview(
            createTestCacheEpisode(
                1,
                progress = 1f.toProgress(),
                downloadSpeed = Unspecified,
                totalSize = 888.megaBytes,
                initialState = CacheEpisodePaused.COMPLETED,
            ),
        )
        CacheEpisodeRowForPreview(
            createTestCacheEpisode(
                2,
                progress = 0.67f.toProgress(),
                downloadSpeed = 233.megaBytes,
                totalSize = 888.megaBytes,
                initialState = CacheEpisodePaused.IN_PROGRESS,
            ),
        )
        CacheEpisodeRowForPreview(
            createTestCacheEpisode(
                3,
                progress = 0.22f.toProgress(),
                downloadSpeed = Unspecified,
                totalSize = 888.megaBytes,
                initialState = CacheEpisodePaused.PAUSED,
            ),
        )
        CacheEpisodeRowForPreview(
            createTestCacheEpisode(
                4,
                progress = 0.7f.toProgress(),
                downloadSpeed = Unspecified,
                totalSize = 888.megaBytes,
                initialState = CacheEpisodePaused.FAILED,
            ),
        )
        CacheEpisodeRowForPreview(
            createTestCacheEpisode(
                5,
                progress = Progress.Unspecified,
                downloadSpeed = 233.megaBytes,
                totalSize = Unspecified,
                initialState = CacheEpisodePaused.IN_PROGRESS,
            ),
        )
    }
}

@OptIn(TestOnly::class)
@Preview
@Composable
private fun PreviewCacheEpisodeRowSelection() = ProvideCompositionLocalsForPreview {
    Column {
        CacheEpisodeRowForPreview(
            createTestCacheEpisode(1, initialState = CacheEpisodePaused.COMPLETED, progress = 1f.toProgress()),
            selectionMode = true,
            selected = true,
        )
        CacheEpisodeRowForPreview(
            createTestCacheEpisode(2, initialState = CacheEpisodePaused.IN_PROGRESS),
            selectionMode = true,
            selected = false,
        )
    }
}

@OptIn(TestOnly::class)
@Composable
private fun CacheEpisodeRowForPreview(
    episode: CacheEpisodeState,
    selectionMode: Boolean = false,
    selected: Boolean = false,
) {
    CacheEpisodeRow(
        episode = episode,
        mediaSourceInfoProvider = rememberTestMediaSourceInfoProvider(),
        selectionMode = selectionMode,
        selected = selected,
        onToggleSelected = {},
        onEnterSelection = {},
        onPlay = {},
        onResume = {},
        onPause = {},
        onDelete = {},
        onViewDetail = {},
    )
}
