/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.PreviewLightDark
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.tools.Progress
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.ui.cache.components.CacheEpisodePaused
import me.him188.ani.app.ui.cache.components.CacheEpisodeState
import me.him188.ani.app.ui.cache.components.CacheGroupCommonInfo
import me.him188.ani.app.ui.cache.components.CacheGroupState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.utils.platform.annotations.TestOnly

@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
fun PreviewCacheManagementPage() {
    ProvideCompositionLocalsForPreview {
        CacheManagementScreen(
            state = remember {
                CacheManagementState(
                    stateOf(createTestMediaStats()),
                    stateOf(TestCacheGroupSates),
                )
            },
            navigationIcon = { BackNavigationIconButton({ }) },
        )
    }
}

fun createTestMediaStats(): MediaStats {
    return MediaStats.Unspecified
}

@TestOnly
internal val TestCacheEpisodes = listOf(
    createTestCacheEpisode(1, "翻转孤独", 1),
    createTestCacheEpisode(2, "明天见", 1),
    createTestCacheEpisode(3, "火速增员", 1),
)

@OptIn(DelicateCoroutinesApi::class)
@Suppress("SameParameterValue")
@TestOnly
internal fun createTestCacheEpisode(
    sort: Int,
    displayName: String = "第 $sort 话",
    subjectId: Int = 1,
    episodeId: Int = sort,
    initialState: CacheEpisodePaused = when (sort % 2) {
        0 -> CacheEpisodePaused.PAUSED
        else -> CacheEpisodePaused.IN_PROGRESS
    },
    downloadSpeed: FileSize = 233.megaBytes,
    progress: Progress = 0.3f.toProgress(),
    totalSize: FileSize = 888.megaBytes,
): CacheEpisodeState {
    val state = mutableStateOf(initialState)
    return CacheEpisodeState(
        subjectId = subjectId,
        episodeId = episodeId,
        cacheId = "1",
        sort = EpisodeSort(sort),
        displayName = displayName,
        creationTime = 100,
        screenShots = stateOf(emptyList()),
        stats = stateOf(
            CacheEpisodeState.Stats(
                downloadSpeed = downloadSpeed,
                progress = progress,
                totalSize = totalSize,
            ),
        ),
        state = state,
        onPause = { state.value = CacheEpisodePaused.PAUSED },
        onResume = { state.value = CacheEpisodePaused.IN_PROGRESS },
        onDelete = {},
        backgroundScope = GlobalScope,
    )
}

@TestOnly
internal val TestCacheGroupSates = listOf(
    CacheGroupState(
        media = TestMediaList[0],
        commonInfo = stateOf(
            CacheGroupCommonInfo(
                subjectId = 1,
                "孤独摇滚",
                mediaSourceId = "mikan-mikanime-tv",
                allianceName = "某某字幕组",
            ),
        ),
        episodes = TestCacheEpisodes,
        stats = stateOf(
            CacheGroupState.Stats(
                downloadSpeed = 233.megaBytes,
                downloadedSize = 233.megaBytes,
                uploadSpeed = 233.megaBytes,
            ),
        ),
    ),
)
