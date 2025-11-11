/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells.Adaptive
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.domain.media.cache.engine.sum
import me.him188.ani.app.torrent.api.files.averageRate
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.cache.components.CacheEpisodePaused
import me.him188.ani.app.ui.cache.components.CacheEpisodeState
import me.him188.ani.app.ui.cache.components.CacheGroupCard
import me.him188.ani.app.ui.cache.components.CacheGroupCommonInfo
import me.him188.ani.app.ui.cache.components.CacheGroupState
import me.him188.ani.app.ui.cache.components.CacheManagementOverallStats
import me.him188.ani.app.ui.cache.components.TestCacheGroupSates
import me.him188.ani.app.ui.cache.components.createTestMediaStats
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.ifNotNullThen
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isHeightAtLeastMedium
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.unwrapCached
import me.him188.ani.utils.coroutines.flows.flowOfEmptyList
import me.him188.ani.utils.coroutines.sampleWithInitial
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.hasScrollingBug
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

// 为了未来万一要改, 方便
typealias CacheGroupGridLayoutState = LazyStaggeredGridState

@Stable
class CacheManagementViewModel : AbstractViewModel(), KoinComponent {
    private val cacheManager: MediaCacheManager by inject()

    val lazyGridState: CacheGroupGridLayoutState = LazyStaggeredGridState()

    val stateFlow = run {
        val overallStatsFlow = cacheManager.enabledStorages
            .flatMapLatest { list ->
                list.map { it.stats }.sum()
            }
            .sampleWithInitial(1.seconds)
            .stateInBackground(MediaStats.Unspecified)

        val groupsFlow = cacheManager.enabledStorages
            .flatMapLatest { storages ->
                if (storages.isEmpty()) {
                    flowOfEmptyList()
                } else {
                    val listFlow = storages.map { it.listFlow }
                    if (listFlow.isEmpty()) {
                        flowOfEmptyList()
                    } else {
                        combine(listFlow) { it.asSequence().flatten().toList() }
                            .transformLatest { allCaches ->
                                supervisorScope {
                                    emitAll(createCacheGroupStates(allCaches))
                                } // supervisorScope won't finish itself
                            }
                    }
                }
            }
            .shareInBackground()

        combine(overallStatsFlow, groupsFlow, ::CacheManagementState)
            .stateInBackground(CacheManagementState.Placeholder) // has distinctUntilChanged
    }

    private fun createCacheGroupStates(allCaches: List<MediaCache>): Flow<List<CacheGroupState>> {
        val groupStateFlows = allCaches
            .groupBy { it.origin.unwrapCached().mediaId }
            .map { (_, mediaCaches) ->
                check(mediaCaches.isNotEmpty())

                val firstCache = mediaCaches.first()

                val groupId = firstCache.origin.unwrapCached().mediaId
                val statsFlow = firstCache.sessionStats
                    .combine(
                        firstCache.sessionStats.map { it.downloadedBytes.inBytes }.averageRate(),
                    ) { stats, downloadSpeed ->
                        CacheGroupState.Stats(
                            downloadSpeed = downloadSpeed.bytes,
                            downloadedSize = stats.downloadedBytes,
                            uploadSpeed = stats.uploadSpeed,
                        )
                    }
                    .sampleWithInitial(1.seconds)
                    .onStart {
                        emit(
                            CacheGroupState.Stats(
                                FileSize.Unspecified,
                                FileSize.Unspecified,
                                FileSize.Unspecified,
                            ),
                        )
                    }

                val episodeFlows = mediaCaches.map { mediaCache ->
                    createCacheEpisodeFlow(mediaCache)
                }.let { episodeFlows ->
                    if (episodeFlows.isEmpty()) {
                        flowOfEmptyList()
                    } else {
                        combine(episodeFlows) { it.toList() }
                    }
                }.shareInBackground()

                val commonInfo = createGroupCommonInfo(
                    subjectId = firstCache.metadata.subjectId.toInt(),
                    firstCache = firstCache,
                    subjectDisplayName = firstCache.metadata.subjectNameCN
                        ?: firstCache.metadata.subjectNames.firstOrNull()
                        ?: firstCache.origin.originalTitle,
                    imageUrl = null,
                )

                combine(statsFlow, episodeFlows) { stats, episodes ->
                    CacheGroupState(
                        id = groupId,
                        commonInfo = commonInfo,
                        episodes = episodes,
                        stats = stats,
                    )
                }
            }

        if (groupStateFlows.isEmpty()) {
            return flowOfEmptyList()
        }
        return combine(groupStateFlows) { array ->
            array.sortedWith(
                compareByDescending<CacheGroupState> { it.latestCreationTime }
                    .thenByDescending { it.cacheId }, // 只有旧的缓存会没有时间, 才会走这个
            )
        }
    }

    private fun createGroupCommonInfo(
        subjectId: Int,
        firstCache: MediaCache,
        subjectDisplayName: String,
        imageUrl: String?,
    ) = CacheGroupCommonInfo(
        subjectId = subjectId,
        subjectDisplayName,
        mediaSourceId = firstCache.origin.unwrapCached().mediaSourceId,
        allianceName = firstCache.origin.unwrapCached().properties.alliance,
        imageUrl = imageUrl,
    )

    private fun createCacheEpisodeFlow(mediaCache: MediaCache): Flow<CacheEpisodeState> {
        val statsFlow = mediaCache.fileStats
            .combine(
                mediaCache.fileStats
                    .shareInBackground(replay = 1).map { it.downloadedBytes.inBytes }.averageRate(),
            ) { stats, downloadSpeed ->
                CacheEpisodeState.Stats(
                    downloadSpeed = downloadSpeed.bytes,
                    progress = stats.downloadProgress,
                    totalSize = stats.totalSize,
                )
            }
            .sampleWithInitial(1.seconds)
            .stateInBackground(CacheEpisodeState.Stats.Unspecified)
        // stateInBackground has distinctUntilChanged

        val stateFlow = mediaCache.state
            .map {
                when (it) {
                    MediaCacheState.IN_PROGRESS -> CacheEpisodePaused.IN_PROGRESS
                    MediaCacheState.PAUSED -> CacheEpisodePaused.PAUSED
                }
            }
            .stateInBackground(CacheEpisodePaused.IN_PROGRESS)

        val metadata = mediaCache.metadata
        return combine(statsFlow, stateFlow, mediaCache.canPlay) { stats, state, canPlay ->
            val subjectId = metadata.subjectId.toInt()
            val episodeId = metadata.episodeId.toInt()
            CacheEpisodeState(
                subjectId = subjectId,
                episodeId = episodeId,
                cacheId = mediaCache.cacheId,
                sort = metadata.episodeSort,
                displayName = metadata.episodeName,
                creationTime = metadata.creationTime,
                screenShots = emptyList(),
                stats = stats,
                state = state,
                playability = when {
                    subjectId == 0 || episodeId == 0 -> CacheEpisodeState.Playability.INVALID_SUBJECT_EPISODE_ID
                    !canPlay -> CacheEpisodeState.Playability.STREAMING_NOT_SUPPORTED
                    else -> CacheEpisodeState.Playability.PLAYABLE
                },
            )
        }
    }

    fun pauseCache(cache: CacheEpisodeState) {
        backgroundScope.launch {
            cacheManager.findFirstCache { it.cacheId == cache.cacheId }?.pause()
        }
    }

    fun resumeCache(cache: CacheEpisodeState) {
        backgroundScope.launch {
            cacheManager.findFirstCache { it.cacheId == cache.cacheId }?.resume()
        }
    }

    fun deleteCache(cache: CacheEpisodeState) {
        backgroundScope.launch {
            cacheManager.deleteFirstCache { it.cacheId == cache.cacheId }
        }
    }
}

/**
 * 全局缓存管理页面状态
 */
@Immutable
data class CacheManagementState(
    val overallStats: MediaStats,
    val groups: List<CacheGroupState>,
    val isPlaceholder: Boolean = false,
) {
    companion object {
        val Placeholder = CacheManagementState(
            MediaStats.Unspecified,
            emptyList(),
            isPlaceholder = true,

            )
    }
}

/**
 * 全局缓存管理页面
 */
@Composable
fun CacheManagementScreen(
    vm: CacheManagementViewModel,
    onPlay: (CacheEpisodeState) -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val state by vm.stateFlow.collectAsStateWithLifecycle()
    CacheManagementScreen(
        state,
        onPlay,
        onResume = {
            vm.resumeCache(it)
        },
        onPause = {
            vm.pauseCache(it)
        },
        onDelete = {
            vm.deleteCache(it)
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        lazyGridState = vm.lazyGridState,
        contentWindowInsets = contentWindowInsets,
    )
}


@Composable
fun CacheManagementScreen(
    state: CacheManagementState,
    onPlay: (CacheEpisodeState) -> Unit,
    onResume: (CacheEpisodeState) -> Unit,
    onPause: (CacheEpisodeState) -> Unit,
    onDelete: (CacheEpisodeState) -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    lazyGridState: CacheGroupGridLayoutState = rememberLazyStaggeredGridState(),
    contentWindowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val appBarColors = AniThemeDefaults.topAppBarColors()

    val isHeightAtLeastMedium = currentWindowAdaptiveInfo1().windowSizeClass.isHeightAtLeastMedium
    val scrollBehavior = if (LocalPlatform.current.hasScrollingBug()
        || isHeightAtLeastMedium
    ) {
        null
    } else {
        // 在紧凑高度时收起 Top bar
        TopAppBarDefaults.enterAlwaysScrollBehavior()
    }
    val stickyHeader = isHeightAtLeastMedium

    Scaffold(
        modifier,
        topBar = {
            AniTopAppBar(
                title = { AniTopAppBarDefaults.Title("缓存管理") },
                navigationIcon = navigationIcon,
                colors = appBarColors,
                windowInsets = AniWindowInsets.forTopAppBarWithoutDesktopTitle(),
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = Color.Unspecified,
        contentWindowInsets = contentWindowInsets,
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            if (stickyHeader) {
                Surface(
                    Modifier.ifThen(lazyGridState.canScrollBackward) {
                        shadow(2.dp, clip = false)
                    },
                    color = appBarColors.containerColor,
                    contentColor = contentColorFor(appBarColors.containerColor),
                ) {
                    CacheManagementOverallStats(
                        { state.overallStats },
                        Modifier
                            .padding(horizontal = 16.dp).padding(bottom = 16.dp)
                            .fillMaxWidth(),
                    )
                }
            }

            LazyVerticalStaggeredGrid(
                Adaptive(320.dp),
                Modifier
                    .fillMaxWidth()
                    .wrapContentWidth()
                    .widthIn(max = 1300.dp)
                    .ifNotNullThen(scrollBehavior) {
                        nestedScroll(it.nestedScrollConnection)
                    },
                state = lazyGridState,
                verticalItemSpacing = 20.dp,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            ) {
                if (!stickyHeader) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Surface(
                            color = appBarColors.containerColor,
                            contentColor = contentColorFor(appBarColors.containerColor),
                        ) {
                            CacheManagementOverallStats(
                                { state.overallStats },
                                Modifier
                                    .padding(horizontal = 16.dp)
                                    .fillMaxWidth(),
                            )
                        }
                    }
                }

                items(state.groups, key = { "CacheManagementScreen-" + it.id }) { group ->
                    CacheGroupCard(
                        group,
                        onPlay,
                        onResume,
                        onPause,
                        onDelete,
                        Modifier, // 动画很怪, 等 1.7.0 的 animateItem 再看看
                    )
                }
            }
        }
    }
}


@OptIn(TestOnly::class)
@Preview
@Composable
private fun PreviewCacheManagementScreen() {
    ProvideCompositionLocalsForPreview {
        CacheManagementScreen(
            state = remember {
                CacheManagementState(
                    createTestMediaStats(),
                    TestCacheGroupSates,
                )
            },
            onPlay = { },
            onResume = {},
            onPause = {},
            onDelete = {},
            navigationIcon = { BackNavigationIconButton({ }) },
        )
    }
}
