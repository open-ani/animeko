/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.tools.Progress
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.mediafetch.rememberTestMediaSourceInfoProvider
import me.him188.ani.datasources.api.topic.FileSize.Companion.Unspecified
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.utils.platform.annotations.TestOnly

@OptIn(TestOnly::class)
@Preview
@Composable
private fun PreviewDownloadRowVariants() = ProvideCompositionLocalsForPreview {
    Column {
        DownloadRowForPreview(
            createTestDownloadItem(
                1,
                progress = 1f.toProgress(),
                downloadSpeed = Unspecified,
                totalSize = 888.megaBytes,
                initialState = DownloadStatus.COMPLETED,
            ),
        )
        DownloadRowForPreview(
            createTestDownloadItem(
                2,
                progress = 0.67f.toProgress(),
                downloadSpeed = 233.megaBytes,
                totalSize = 888.megaBytes,
                initialState = DownloadStatus.IN_PROGRESS,
            ),
        )
        DownloadRowForPreview(
            createTestDownloadItem(
                3,
                progress = 0.22f.toProgress(),
                downloadSpeed = Unspecified,
                totalSize = 888.megaBytes,
                initialState = DownloadStatus.PAUSED,
            ),
        )
        DownloadRowForPreview(
            createTestDownloadItem(
                4,
                progress = 0.7f.toProgress(),
                downloadSpeed = Unspecified,
                totalSize = 888.megaBytes,
                initialState = DownloadStatus.FAILED,
            ),
        )
        DownloadRowForPreview(
            createTestDownloadItem(
                5,
                progress = Progress.Unspecified,
                downloadSpeed = 233.megaBytes,
                totalSize = Unspecified,
                initialState = DownloadStatus.IN_PROGRESS,
            ),
        )
    }
}

@OptIn(TestOnly::class)
@Preview
@Composable
private fun PreviewDownloadRowSelection() = ProvideCompositionLocalsForPreview {
    Column {
        DownloadRowForPreview(
            createTestDownloadItem(1, initialState = DownloadStatus.COMPLETED, progress = 1f.toProgress()),
            selectionMode = true,
            selected = true,
        )
        DownloadRowForPreview(
            createTestDownloadItem(2, initialState = DownloadStatus.IN_PROGRESS),
            selectionMode = true,
            selected = false,
        )
    }
}

@OptIn(TestOnly::class)
@Composable
private fun DownloadRowForPreview(
    episode: DownloadItem,
    selectionMode: Boolean = false,
    selected: Boolean = false,
) {
    DownloadRow(
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
