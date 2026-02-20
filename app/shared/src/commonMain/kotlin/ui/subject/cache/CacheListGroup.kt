/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.cache

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.domain.media.cache.requester.CacheRequestStage
import me.him188.ani.app.domain.media.cache.requester.EpisodeCacheRequester
import me.him188.ani.app.domain.media.selector.MediaSelectorFactory
import me.him188.ani.app.tools.Progress
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.ui.cache.subject.EpisodeCacheActionIcon
import me.him188.ani.app.ui.cache.subject.EpisodeCacheInfo
import me.him188.ani.app.ui.cache.subject.EpisodeCacheItem
import me.him188.ani.app.ui.cache.subject.EpisodeCacheState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly

@Composable
@Preview
private fun PreviewEpisodeCacheActionIcon() {
    ProvideCompositionLocalsForPreview {
        Column(Modifier.width(IntrinsicSize.Min)) {
            Text(text = "isLoadingIndefinitely")
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                EpisodeCacheActionIcon(
                    isLoadingIndefinitely = true,
                    hasActionRunning = true,
                    cacheStatus = null,
                    canCache = true,
                    onClick = {},
                    onCancel = {},
                )
            }
            HorizontalDivider()

            Text(text = "NotCached")
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                EpisodeCacheActionIcon(
                    isLoadingIndefinitely = false,
                    hasActionRunning = true,
                    cacheStatus = EpisodeCacheStatus.NotCached,
                    canCache = true,
                    onClick = {},
                    onCancel = {},
                )
            }
            HorizontalDivider()

            Text(text = "Caching - progress null")
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                EpisodeCacheActionIcon(
                    isLoadingIndefinitely = false,
                    hasActionRunning = true,
                    cacheStatus = EpisodeCacheStatus.Caching(
                        Progress.Unspecified,
                        FileSize.Unspecified,
                    ),
                    canCache = true,
                    onClick = {},
                    onCancel = {},
                )
            }
            HorizontalDivider()

            Text(text = "Caching - progress not null")
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                EpisodeCacheActionIcon(
                    isLoadingIndefinitely = false,
                    hasActionRunning = true,
                    cacheStatus = EpisodeCacheStatus.Caching(
                        0.3f.toProgress(),
                        FileSize.Unspecified,
                    ),
                    canCache = true,
                    onClick = {},
                    onCancel = {},
                )
            }
            HorizontalDivider()
        }
    }
}


@Composable
@Preview
private fun PreviewEpisodeCacheActionIconHasActionRunningChange() {
    ProvideCompositionLocalsForPreview {
        var running by remember {
            mutableStateOf(false)
        }
        Column {
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                EpisodeCacheActionIcon(
                    isLoadingIndefinitely = true,
                    hasActionRunning = running,
                    cacheStatus = null,
                    canCache = true,
                    onClick = { },
                    onCancel = { running = false },
                )

            }
            Button(onClick = { running = true }) {
                Text(text = "running = true")
            }
        }
    }
}

@OptIn(TestOnly::class)
@Preview
@Composable
private fun PreviewEpisodeItem() = ProvideCompositionLocalsForPreview {
    SettingsTab {
        var id = 0
        listOf(true, false).forEach { hasPublished ->
            UnifiedCollectionType.entries.forEach { watchStatus ->
                EpisodeCacheItem(
                    episode = rememberTestEpisodeCacheState(
                        info = EpisodeCacheInfo(
                            sort = EpisodeSort(++id),
                            ep = null,
                            title = "$watchStatus - ${if (hasPublished) "已开播" else "未开播"}",
                            watchStatus = watchStatus,
                            hasPublished = true,
                        ),
                    ),
                    onClick = {},
                    isRequestHidden = false,
                    dropdown = { },
                )
            }
        }
    }
}

@OptIn(TestOnly::class)
@Preview
@Composable
private fun PreviewEpisodeItemVeryLong() = ProvideCompositionLocalsForPreview {
    SettingsTab {
        EpisodeCacheItem(
            rememberTestEpisodeCacheState(
                EpisodeCacheInfo(
                    sort = EpisodeSort(1),
                    ep = null,
                    title = "测试标题".repeat(10),
                    watchStatus = UnifiedCollectionType.WISH,
                    hasPublished = true,
                ),
                EpisodeCacheStatus.NotCached,
            ),
            onClick = {},
            isRequestHidden = false,
            dropdown = { },
        )
    }
}

@Composable
@OptIn(DelicateCoroutinesApi::class)
@TestOnly
fun rememberTestEpisodeCacheState(
    info: EpisodeCacheInfo,
    cacheStatus: EpisodeCacheStatus = EpisodeCacheStatus.NotCached,
) = remember {
    EpisodeCacheState(
        0,
        EpisodeCacheRequester(flowOf(), MediaSelectorFactory.withKoin(), flowOf()),
        currentStageState = stateOf(CacheRequestStage.Idle),
        infoState = stateOf(info),
        cacheStatusState = stateOf(cacheStatus),
        GlobalScope,
    )
}
