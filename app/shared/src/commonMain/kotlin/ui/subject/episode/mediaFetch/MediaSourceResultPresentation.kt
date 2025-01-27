/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.mediaFetch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchResult
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.fetch.MediaSourceResultsFilterer
import me.him188.ani.app.domain.media.fetch.isDisabled
import me.him188.ani.app.domain.media.fetch.isFailedOrAbandoned
import me.him188.ani.app.domain.media.fetch.isWorking
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.mikan.MikanCNMediaSource
import me.him188.ani.datasources.mikan.MikanMediaSource
import me.him188.ani.utils.platform.annotations.TestOnly


/**
 * 单个数据源的搜索结果.
 *
 * @see MediaSourceResultListPresentation
 */
@Stable
data class MediaSourceResultPresentation(
    val instanceId: String,
    val mediaSourceId: String,
    val state: MediaSourceFetchState,
    val info: MediaSourceInfo,
    val kind: MediaSourceKind,
    val totalCount: Int,
) {
    val isWorking: Boolean get() = state.isWorking
    val isDisabled: Boolean get() = state.isDisabled
    val isFailedOrAbandoned: Boolean get() = state.isFailedOrAbandoned
}

/**
 * 在 [MediaSelectorView] 使用, 管理多个 [MediaSourceResultPresentation] 的结果
 *
 * 对应 UI 是 "BT" 和 "WEB" 的两行列表, 列表包含 [MediaSourceResultPresentation]
 */
@Immutable
data class MediaSourceResultListPresentation(
    val list: List<MediaSourceResultPresentation>,
) {
    val anyLoading: Boolean = list.any { it.isWorking }
    val webSources: List<MediaSourceResultPresentation> = list.filter { it.kind == MediaSourceKind.WEB }
    val btSources: List<MediaSourceResultPresentation> = list.filter { it.kind == MediaSourceKind.BitTorrent }
    val enabledSourceCount: Int = list.count { !it.isDisabled && it.kind != MediaSourceKind.LocalCache }
    val totalSourceCount: Int = list.count { it.kind != MediaSourceKind.LocalCache } // 缓存数据源属于内部的, 用户应当无感

    companion object {
        val Empty = MediaSourceResultListPresentation(emptyList())
    }
}

class MediaSourceResultListPresenter(
    resultListFlow: Flow<List<MediaSourceFetchResult>>,
    flowScope: CoroutineScope,
) {
    val presentationFlow: Flow<List<MediaSourceResultPresentation>> = resultListFlow
        .flatMapLatest { list ->
            combine(
                list.map { source ->
                    combine(source.state, source.results) { state, results ->
                        source.toPresentation(state, results.size)
                    }
                },
            ) {
                it.toList()
            }
        }
        .shareIn(flowScope, SharingStarted.WhileSubscribed(), replay = 1)

    private fun MediaSourceFetchResult.toPresentation(
        state: MediaSourceFetchState,
        totalCount: Int
    ): MediaSourceResultPresentation =
        MediaSourceResultPresentation(
            instanceId = instanceId,
            mediaSourceId = mediaSourceId,
            state = state,
            info = sourceInfo,
            kind = kind,
            totalCount = totalCount,
        )
}


///////////////////////////////////////////////////////////////////////////
// Testing
///////////////////////////////////////////////////////////////////////////

@TestOnly
@Composable
fun createTestMediaSourceResultsPresenter(
    flowScope: CoroutineScope,
): MediaSourceResultListPresenter {
    return MediaSourceResultListPresenter(
        MediaSourceResultsFilterer(
            results = flowOf(
                listOf(
                    TestMediaSourceResult(
                        MikanMediaSource.ID,
                        MikanMediaSource.INFO,
                        MediaSourceKind.BitTorrent,
                        initialState = MediaSourceFetchState.Working,
                        results = TestMediaList,
                    ),
                    TestMediaSourceResult(
                        "dmhy",
                        MediaSourceInfo("dmhy"),
                        MediaSourceKind.BitTorrent,
                        initialState = MediaSourceFetchState.Succeed(1),
                        results = TestMediaList,
                    ),
                    TestMediaSourceResult(
                        "acg.rip",
                        MediaSourceInfo("acg.rip"),
                        MediaSourceKind.BitTorrent,
                        initialState = MediaSourceFetchState.Disabled,
                        results = TestMediaList,
                    ),
                    TestMediaSourceResult(
                        "nyafun",
                        MediaSourceInfo("nyafun"),
                        MediaSourceKind.WEB,
                        initialState = MediaSourceFetchState.Succeed(1),
                        results = TestMediaList,
                    ),
                    TestMediaSourceResult(
                        MikanCNMediaSource.ID,
                        MikanCNMediaSource.INFO,
                        MediaSourceKind.BitTorrent,
                        initialState = MediaSourceFetchState.Failed(IllegalStateException(), 1),
                        results = emptyList(),
                    ),
                ),
            ),
            settings = flowOf(MediaSelectorSettings.Default),
            flowScope = flowScope,
        ).filteredSourceResults,
        flowScope = flowScope,
    )
}

@TestOnly
val TestMediaSourceResultListPresentation
    get() = MediaSourceResultListPresentation(
        listOf(
            MediaSourceResultPresentation(
                instanceId = MikanMediaSource.ID,
                mediaSourceId = MikanMediaSource.ID,
                state = MediaSourceFetchState.Working,
                info = MikanMediaSource.INFO,
                kind = MediaSourceKind.BitTorrent,
                totalCount = TestMediaList.size,
            ),
            MediaSourceResultPresentation(
                instanceId = "dmhy",
                mediaSourceId = "dmhy",
                state = MediaSourceFetchState.Succeed(1),
                info = MediaSourceInfo("dmhy"),
                kind = MediaSourceKind.BitTorrent,
                totalCount = TestMediaList.size,
            ),
            MediaSourceResultPresentation(
                instanceId = "acg.rip",
                mediaSourceId = "acg.rip",
                state = MediaSourceFetchState.Disabled,
                info = MediaSourceInfo("acg.rip"),
                kind = MediaSourceKind.BitTorrent,
                totalCount = TestMediaList.size,
            ),
            MediaSourceResultPresentation(
                instanceId = "nyafun",
                mediaSourceId = "nyafun",
                state = MediaSourceFetchState.Succeed(1),
                info = MediaSourceInfo("nyafun"),
                kind = MediaSourceKind.WEB,
                totalCount = TestMediaList.size,
            ),
            MediaSourceResultPresentation(
                instanceId = MikanCNMediaSource.ID,
                mediaSourceId = MikanCNMediaSource.ID,
                state = MediaSourceFetchState.Failed(IllegalStateException(), 1),
                info = MikanCNMediaSource.INFO,
                kind = MediaSourceKind.BitTorrent,
                totalCount = 0,
            ),
        ),
    )

private class TestMediaSourceResult(
    override val mediaSourceId: String,
    override val sourceInfo: MediaSourceInfo,
    override val kind: MediaSourceKind,
    initialState: MediaSourceFetchState,
    results: List<Media>,
    override val instanceId: String = mediaSourceId,
) : MediaSourceFetchResult {
    override val state: MutableStateFlow<MediaSourceFetchState> = MutableStateFlow(initialState)
    override val results: SharedFlow<List<Media>> = MutableStateFlow(results)
    private val restartCount = atomic(0)

    @OptIn(DelicateCoroutinesApi::class)
    override fun restart() {
        state.value = MediaSourceFetchState.Working
        GlobalScope.launch {
            delay(3000)
            state.value = MediaSourceFetchState.Succeed(restartCount.incrementAndGet())
        }
    }

    override fun enable() {
        if (state.value is MediaSourceFetchState.Disabled) {
            if (restartCount.compareAndSet(0, 1)) {
                state.value = MediaSourceFetchState.Idle
            }
        }
    }
}

