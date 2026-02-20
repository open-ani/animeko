/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("PropertyName")

package me.him188.ani.app.ui.cache.subject

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.domain.media.fetch.MediaSourceResultsFilterer
import me.him188.ani.app.domain.media.fetch.restart
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.tools.getOrZero
import me.him188.ani.app.ui.foundation.layout.desktopTitleBar
import me.him188.ani.app.ui.foundation.layout.desktopTitleBarPadding
import me.him188.ani.app.ui.foundation.theme.stronglyWeaken
import me.him188.ani.app.ui.foundation.widgets.ProgressIndicatorHeight
import me.him188.ani.app.ui.mediafetch.MediaSelectorView
import me.him188.ani.app.ui.mediafetch.MediaSourceInfoProvider
import me.him188.ani.app.ui.mediafetch.MediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.MediaSourceResultListPresenter
import me.him188.ani.app.ui.mediafetch.ViewKind
import me.him188.ani.app.ui.mediafetch.rememberMediaSelectorState
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import org.koin.mp.KoinPlatform


@Immutable
data class EpisodeCacheInfo(
    val sort: EpisodeSort,
    val ep: EpisodeSort?,
    val title: String,
    val watchStatus: UnifiedCollectionType,
    /**
     * 是否已经上映了
     */
    val hasPublished: Boolean,
    val _placeholder: Int = 0,
) {
    val sortString = sort.toString()

    companion object {
        @Stable
        val Placeholder = EpisodeCacheInfo(
            EpisodeSort(0),
            null,
            "",
            UnifiedCollectionType.DONE,
            false,
            -1,
        )
    }
}

/**
 * 一个条目的所有剧集的缓存管理
 */
@Composable
fun SettingsScope.EpisodeCacheListGroup(
    state: EpisodeCacheListState,
    mediaSourceInfoProvider: MediaSourceInfoProvider,
    mediaSelectorSettingsProvider: () -> Flow<MediaSelectorSettings>,
    modifier: Modifier = Modifier,
) {
    state.currentSelectStorageTask?.let { task ->
        val attemptedTrySelect by task.attemptedTrySelect.collectAsStateWithLifecycle(false)
        if (!attemptedTrySelect) return@let

        SelectMediaStorageDialog(
            options = task.options,
            onSelect = { state.selectStorage(it) },
            onDismissRequest = { state.cancelStorageSelector(task) },
            Modifier,
        )
    }

    // 用户是否关闭了 media selector. 这种情况下优先考虑是想置于后台或者点错了, 之后重新点击可以复用查询结果
    var hideMediaSelector by remember(state.currentSelectMediaTask) {
        mutableStateOf(false)
    }
    state.currentSelectMediaTask?.let { task ->
        val attemptedTrySelect by task.attemptedTrySelect.collectAsStateWithLifecycle(false)
        if (!attemptedTrySelect) return@let

        val scope = rememberCoroutineScope()

        val filteredResults = remember(task.fetchSession.mediaSourceResults, mediaSelectorSettingsProvider, scope) {
            MediaSourceResultsFilterer(
                MutableStateFlow(task.fetchSession.mediaSourceResults),
                settings = mediaSelectorSettingsProvider(),
                flowScope = scope,
            ).filteredSourceResults
                .shareIn(scope, started = SharingStarted.WhileSubscribed(5000), replay = 1)
        }

        // 注意, 这里会一直 collect mediaSourceResults
        val sourceResults by remember(
            task.fetchSession.mediaSourceResults,
            mediaSelectorSettingsProvider,
            scope,
            filteredResults,
        ) {
            // TODO: shit
            MediaSourceResultListPresenter(
                filteredResults,
            ).presentationFlow.map {
                MediaSourceResultListPresentation(it)
            }.shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)
        }.collectAsStateWithLifecycle(MediaSourceResultListPresentation.Empty)
        if (!hideMediaSelector) {
            ModalBottomSheet(
                onDismissRequest = {
                    hideMediaSelector = true
                    // 不要取消任务, 用户可能是点错了, 之后重新点击可以复用查询结果
//                state.cancelMediaSelector(task) 
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                modifier = Modifier.desktopTitleBarPadding().statusBarsPadding(),
                contentWindowInsets = { BottomSheetDefaults.windowInsets.add(WindowInsets.desktopTitleBar()) },
            ) {
                val selectorPresentation =
                    rememberMediaSelectorState(mediaSourceInfoProvider, filteredResults) { task.mediaSelector }
                val (viewKind, onViewKindChange) = rememberSaveable { mutableStateOf(ViewKind.WEB) }

                // todo: shit
                val fetchRequest by task.fetchSession.request.collectAsState(null)

                MediaSelectorView(
                    selectorPresentation,
                    viewKind,
                    onViewKindChange,
                    fetchRequest,
                    {
                        task.fetchSession.setFetchRequest(it)
                    },
                    sourceResults,
                    onRestartSource = {
                        task.fetchSession.restart(it)
                    },
                    onRefresh = { task.fetchSession.restartAll() },
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
                        .navigationBarsPadding()
                        .fillMaxHeight() // 防止添加筛选后数量变少导致 bottom sheet 高度变化
                        .fillMaxWidth(),
                    stickyHeaderBackgroundColor = BottomSheetDefaults.ContainerColor,
                    onClickItem = {
                        state.selectMedia(it)
                    },
                )
            }
        }
    }

    Group(
        title = { Text("单集缓存") },
        modifier = modifier,
    ) {
        state.episodes.fastForEachIndexed { i, episodeCacheState ->
            var showDropdown by remember { mutableStateOf(false) }

            val uiScope = rememberCoroutineScope()
            val context = LocalContext.current
            EpisodeCacheItem(
                episodeCacheState,
                onClick = {
                    if (episodeCacheState.cacheStatus is EpisodeCacheStatus.Caching ||
                        episodeCacheState.cacheStatus is EpisodeCacheStatus.Cached
                    ) {
                        showDropdown = true
                    } else {
                        if (episodeCacheState == state.currentSelectMediaTask?.episode) {
                            // 同一个任务, 重新展开 sheet 就行 (恢复结果), 不用替换 request
                            uiScope.launch {
                                hideMediaSelector = false
                            }
                        } else {
                            state.requestCache(episodeCacheState, autoSelectCached = true)
                            uiScope.launch {
                                KoinPlatform.getKoin().get<PermissionManager>().requestNotificationPermission(context)
                            }
                        }
                    }
                },
                isRequestHidden = hideMediaSelector,
                dropdown = {
                    ItemDropdown(
                        showDropdown = showDropdown,
                        onDismissRequest = { showDropdown = false },
                        onDeleteCache = { state.deleteCache(it) },
                        episodeCacheState = episodeCacheState,
                    )
                },
            )

            Box(Modifier.height(ProgressIndicatorHeight), contentAlignment = Alignment.Center) {
                if (i != state.episodes.lastIndex) {
                    HorizontalDividerItem() // 1.dp height
                }
//                FastLinearProgressIndicator(
//                    episodeCacheState.showProgressIndicator,
//                    Modifier.zIndex(1f).fillMaxWidth(),
//                )
            }
        }
    }
}

@Composable
private fun ItemDropdown(
    showDropdown: Boolean,
    onDismissRequest: () -> Unit,
    onDeleteCache: (EpisodeCacheState) -> Unit,
    episodeCacheState: EpisodeCacheState
) {
    DropdownMenu(
        showDropdown,
        onDismissRequest,
    ) {
        DropdownMenuItem(
            onClick = {
                onDeleteCache(episodeCacheState)
                onDismissRequest()
            },
            text = {
                Text("删除")
            },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Delete,
                    null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
        )
    }
}

/**
 * @param isRequestHidden 请求是否被置于后台. 例如当用户关闭弹出的 media selector bottom sheet 后, 为 `true`.
 */
@Composable
fun SettingsScope.EpisodeCacheItem(
    episode: EpisodeCacheState,
    onClick: () -> Unit,
    isRequestHidden: Boolean,
    modifier: Modifier = Modifier,
    dropdown: @Composable () -> Unit = {},
) {
    val colorByWatchStatus = contentColorForWatchStatus(episode.info.watchStatus, episode.info.hasPublished)
    TextItem(
        icon = {
            CompositionLocalProvider(LocalContentColor provides colorByWatchStatus) {
                Text(episode.info.sortString)
            }
        },
        action = {
            dropdown()

            CompositionLocalProvider(LocalContentColor provides colorByWatchStatus) {
                EpisodeCacheActionIcon(
                    isLoadingIndefinitely = !isRequestHidden && episode.showProgressIndicator.collectAsStateWithLifecycle().value,
                    hasActionRunning = episode.actionTasker.isRunning.collectAsStateWithLifecycle().value,
                    cacheStatus = episode.cacheStatus,
                    canCache = episode.canCache,
                    onClick = onClick,
                    onCancel = { episode.actionTasker.cancel() },
                )
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompositionLocalProvider(LocalContentColor provides colorByWatchStatus) {
                    Text(episode.info.title, Modifier.weight(1f), overflow = TextOverflow.Ellipsis)

                    when (episode.info.watchStatus) {
                        UnifiedCollectionType.DONE -> {
                            Label(Modifier.padding(start = 8.dp)) {
                                Text("看过")
                            }
                        }

                        UnifiedCollectionType.DROPPED -> {
                            Label(Modifier.padding(start = 8.dp)) {
                                Text("抛弃")
                            }
                        }

                        else -> {}
                    }
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
fun contentColorForWatchStatus(
    collectionType: UnifiedCollectionType,
    isKnownBroadcast: Boolean
) =
    if (collectionType.isDoneOrDropped() || !isKnownBroadcast) {
        LocalContentColor.current.stronglyWeaken()
    } else {
        LocalContentColor.current
    }

@Composable
fun EpisodeCacheActionIcon(
    isLoadingIndefinitely: Boolean,
    hasActionRunning: Boolean,
    cacheStatus: EpisodeCacheStatus?,
    canCache: Boolean,
    onClick: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) = Box(modifier) {
    val progressIndicatorSize = 20.dp
    val strokeWidth = 2.dp
    val trackColor = MaterialTheme.colorScheme.primaryContainer
    if (isLoadingIndefinitely || hasActionRunning) {
        var showCancel by remember { mutableStateOf(false) }
        LaunchedEffect(showCancel) {
            if (showCancel) {
                delay(2000)
                showCancel = false
            }
        }
        Crossfade(showCancel) {
            if (it) {
                IconButton(
                    onClick = {
                        onCancel()
                        showCancel = false
                    },
                ) {
                    Icon(Icons.Rounded.Close, "取消")
                }
            } else {
                if (hasActionRunning) {
                    IconButton({ showCancel = true }) {
                        CircularProgressIndicator(
                            Modifier.size(progressIndicatorSize),
                            strokeWidth = strokeWidth,
                            trackColor = trackColor,
                        )
                    }
                } else {
                    Box(Modifier.minimumInteractiveComponentSize()) {
                        CircularProgressIndicator(
                            Modifier.size(progressIndicatorSize),
                            strokeWidth = strokeWidth,
                            trackColor = trackColor,
                        )
                    }
                }
            }
        }
        return@Box
    }
    when (cacheStatus) {
        is EpisodeCacheStatus.Cached ->
            IconButton(onClick) {
                Icon(Icons.Rounded.DownloadDone, null)
            }

        is EpisodeCacheStatus.Caching -> {
            IconButton(onClick) {
                val progressIsUnspecified by remember(cacheStatus) {
                    derivedStateOf {
                        cacheStatus.progress.isUnspecified
                    }
                }
                if (progressIsUnspecified) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(progressIndicatorSize),
                        strokeWidth = strokeWidth,
                        trackColor = trackColor,
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { cacheStatus.progress.getOrZero() },
                        modifier = Modifier.size(progressIndicatorSize),
                        strokeWidth = strokeWidth,
                        trackColor = trackColor,
                    )
                }
            }
        }

        EpisodeCacheStatus.NotCached -> {
            if (canCache) {
                CompositionLocalProvider(LocalContentColor providesDefault MaterialTheme.colorScheme.primary) {
                    IconButton(onClick) {
                        Icon(Icons.Rounded.Download, "缓存")
                    }
                }
            }
        }

        null -> {}
    }
}

@Composable
private fun Label(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(4.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier.border(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
            shape = MaterialTheme.shapes.small,
        ),
    ) {
        Box(Modifier.padding(contentPadding)) {
            ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                content()
            }
        }
    }
}
