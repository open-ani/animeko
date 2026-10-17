/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.bangumi.merge

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.time.Instant

@OptIn(TestOnly::class)
@Composable
@Preview
@PreviewScreenSizes
private fun PreviewBangumiMergeScreen() = ProvideCompositionLocalsForPreview {
    val now = Instant.fromEpochMilliseconds(1_753_000_000_000)
    BangumiMergeScreen(
        state = createTestBangumiMergeUiState(now),
        onSelect = { _, _ -> },
        onAdoptNewer = {},
        onSelectAll = {},
        onApply = {},
        onRetry = {},
        onNavigateBack = {},
        getTimeNow = { now },
    )
}

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewBangumiMergeScreenSyncInProgress() = ProvideCompositionLocalsForPreview {
    val now = Instant.fromEpochMilliseconds(1_753_000_000_000)
    BangumiMergeScreen(
        state = createTestBangumiMergeUiState(
            now,
            mergeState = createTestBangumiMergeState(now).copy(syncInProgress = true),
        ),
        onSelect = { _, _ -> },
        onAdoptNewer = {},
        onSelectAll = {},
        onApply = {},
        onRetry = {},
        onNavigateBack = {},
        getTimeNow = { now },
    )
}

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewBangumiMergeScreenSynced() = ProvideCompositionLocalsForPreview {
    val now = Instant.fromEpochMilliseconds(1_753_000_000_000)
    BangumiMergeScreen(
        state = createTestBangumiMergeUiState(
            now,
            choices = emptyMap(),
            mergeState = createTestBangumiMergeSyncedState(now),
        ),
        onSelect = { _, _ -> },
        onAdoptNewer = {},
        onSelectAll = {},
        onApply = {},
        onRetry = {},
        onNavigateBack = {},
        getTimeNow = { now },
    )
}
