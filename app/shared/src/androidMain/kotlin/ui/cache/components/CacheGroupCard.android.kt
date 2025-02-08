/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import me.him188.ani.app.tools.Progress
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.ui.cache.TestCacheGroupSates
import me.him188.ani.app.ui.cache.createTestCacheEpisode
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.datasources.api.topic.FileSize.Companion.Unspecified
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.utils.platform.annotations.TestOnly

@OptIn(TestOnly::class)
@Preview
@Composable
fun PreviewCacheGroupCardMissingTotalSize() = ProvideCompositionLocalsForPreview {
    Box(Modifier.background(Color.DarkGray)) {
        CacheEpisodeItem(
            createTestCacheEpisode(
                1,
                progress = 0.5f.toProgress(),
                downloadSpeed = 233.megaBytes,
                totalSize = Unspecified,
            ),
            onPlay = { _, _ -> },
        )
    }
}

@OptIn(TestOnly::class)
@Preview
@Composable
fun PreviewCacheGroupCardMissingProgress() = ProvideCompositionLocalsForPreview {
    Box(Modifier.background(Color.DarkGray)) {
        CacheEpisodeItem(
            createTestCacheEpisode(
                1,
                progress = Progress.Unspecified,
                downloadSpeed = 233.megaBytes,
                totalSize = 888.megaBytes,
            ),
            onPlay = { _, _ -> },
        )
    }
}

@OptIn(TestOnly::class)
@Preview
@Composable
fun PreviewCacheGroupCardMissingDownloadSpeed() = ProvideCompositionLocalsForPreview {
    Box(Modifier.background(Color.DarkGray)) {
        CacheEpisodeItem(
            createTestCacheEpisode(
                1,
                progress = 0.3f.toProgress(),
                downloadSpeed = Unspecified,
                totalSize = 888.megaBytes,
            ),
            onPlay = { _, _ -> },
        )
    }
}

@OptIn(TestOnly::class)
@PreviewLightDark
@PreviewFontScale
@Composable
fun PreviewCacheGroupCard() = ProvideCompositionLocalsForPreview {
    Box(Modifier.background(Color.DarkGray)) {
        CacheGroupCard(TestCacheGroupSates[0])
    }
}
