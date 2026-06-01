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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
import me.him188.ani.app.ui.foundation.LocalAniUiBehavior
import me.him188.ani.app.ui.foundation.layout.desktopTitleBar
import me.him188.ani.app.ui.foundation.layout.desktopTitleBarPadding
import me.him188.ani.app.ui.foundation.theme.stronglyWeaken
import me.him188.ani.app.ui.foundation.widgets.ProgressIndicatorHeight
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_filter_collection_done
import me.him188.ani.app.ui.lang.cache_filter_collection_dropped
import me.him188.ani.app.ui.lang.cache_subject_cache
import me.him188.ani.app.ui.lang.cache_subject_cancel
import me.him188.ani.app.ui.lang.cache_subject_delete
import me.him188.ani.app.ui.lang.cache_subject_episode_cache
import me.him188.ani.app.ui.mediafetch.MediaSelectorView
import me.him188.ani.app.ui.foundation.widgets.AniCenteredPanelDialog
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
import org.jetbrains.compose.resources.stringResource
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

    // 计算"数据源选择窗口当前是否真的可见"(用于 TV 焦点处理).
    // 注意: currentSelectMediaTask 在自动选择/直接缓存时也会被设置, 所以必须同时满足 attemptedTrySelect
    // (窗口才会真正渲染, 见下方 ?.let 块) 且未被隐藏. 否则会误判为"窗口开着"而永远不夺回焦点.
    val selectorTask = state.currentSelectMediaTask
    val selectorAttemptedFlow = remember(selectorTask) { selectorTask?.attemptedTrySelect ?: flowOf(false) }
    val selectorAttempted by selectorAttemptedFlow.collectAsStateWithLifecycle(false)
    val selectorVisibleEpisode = if (selectorTask != null && selectorAttempted && !hideMediaSelector) {
        selectorTask.episode
    } else {
        null
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
            val onDismissSelector = {
                hideMediaSelector = true
                // 不要取消任务, 用户可能是点错了, 之后重新点击可以复用查询结果
//                state.cancelMediaSelector(task)
            }
            val selectorContent: @Composable (containerColor: Color) -> Unit = { containerColor ->
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
                    stickyHeaderBackgroundColor = containerColor,
                    onClickItem = {
                        state.selectMedia(it)
                    },
                )
            }
            if (LocalAniUiBehavior.current.panelsAsCenteredDialogs) {
                // 底部抽屉改为半透明居中大弹窗 (与播放器数据源选择/详情页各弹窗统一), 返回键关闭
                AniCenteredPanelDialog(onDismissRequest = onDismissSelector) {
                    selectorContent(MaterialTheme.colorScheme.surfaceContainerHigh)
                }
            } else {
                ModalBottomSheet(
                    onDismissRequest = onDismissSelector,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    modifier = Modifier.desktopTitleBarPadding().statusBarsPadding(),
                    contentWindowInsets = { BottomSheetDefaults.windowInsets.add(WindowInsets.desktopTitleBar()) },
                ) {
                    selectorContent(BottomSheetDefaults.ContainerColor)
                }
            }
        }
    }

    // TV 焦点协调: 记录当前持有焦点的缓存按钮(以各自稳定 key 标识). 用于区分"焦点被切换/弹窗清空"
    // (归属为 null, 需夺回) 与"用户导航到了别的按钮"(归属为别的 key, 不能抢). 见 EpisodeCacheActionIcon.
    val cacheFocusOwner = remember { mutableStateOf<Any?>(null) }
    CompositionLocalProvider(LocalCacheFocusOwner provides cacheFocusOwner) {
    Group(
        title = { Text(stringResource(Lang.cache_subject_episode_cache)) },
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
                        if (episodeCacheState != state.currentSelectMediaTask?.episode) {
                            state.requestCache(episodeCacheState, autoSelectCached = true)
                            uiScope.launch {
                                KoinPlatform.getKoin().get<PermissionManager>().requestNotificationPermission(context)
                            }
                        }
                        hideMediaSelector = false
                    }
                },
                isRequestHidden = hideMediaSelector,
                // 本集是否有抢焦点的弹窗在前台: 数据源选择 sheet 或删除下拉菜单. 弹窗期间不夺回焦点.
                popupOpen = selectorVisibleEpisode == episodeCacheState || showDropdown,
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
                Text(stringResource(Lang.cache_subject_delete))
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
    popupOpen: Boolean = false,
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
                    popupOpen = popupOpen,
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
                                Text(stringResource(Lang.cache_filter_collection_done))
                            }
                        }

                        UnifiedCollectionType.DROPPED -> {
                            Label(Modifier.padding(start = 8.dp)) {
                                Text(stringResource(Lang.cache_filter_collection_dropped))
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
    /**
     * 本集是否有"会抢走焦点的弹窗"正在前台显示(数据源选择 bottom sheet 或删除下拉菜单).
     * 弹窗开着时不夺回焦点(要留给弹窗内导航), 弹窗关闭后才把焦点还给本按钮.
     */
    popupOpen: Boolean = false,
) = Box(modifier) {
    val progressIndicatorSize = 20.dp
    val strokeWidth = 2.dp
    val trackColor = MaterialTheme.colorScheme.primaryContainer

    // "加载中"(isLoadingIndefinitely) 与 "操作进行中"(hasActionRunning) 统一视为 running.
    // 按下下载后, 状态会先变成 isLoadingIndefinitely=true (准备缓存请求), 这个状态也必须
    // 走下面同一个可聚焦的 IconButton, 否则节点被替换会导致遥控器焦点丢失 (TV).
    val running = isLoadingIndefinitely || hasActionRunning

    // 既不在运行/加载, 又没有可显示/可点击的内容时, 才不显示按钮.
    if (!running && (cacheStatus == null || (cacheStatus is EpisodeCacheStatus.NotCached && !canCache))) {
        return@Box
    }

    // 所有会出现的状态(下载/加载中/缓存中/已缓存/操作进行中)共用同一个 IconButton.
    // 同一个 call site -> 节点稳定 -> 按下后焦点留在原处.
    var showCancel by remember { mutableStateOf(false) }
    LaunchedEffect(showCancel) {
        if (showCancel) {
            delay(2000)
            showCancel = false
        }
    }
    // 不再运行/加载时复位取消态, 避免残留.
    LaunchedEffect(running) {
        if (!running) showCancel = false
    }

    // TV: 点击后焦点会丢失(节点没被销毁但失焦, 或被弹窗夺走), 需要把焦点夺回本按钮. 要覆盖:
    //   1. 直接缓存(无弹窗): Download->进度圈->Caching 的视觉切换会清掉焦点;
    //   2. 数据源选择 bottom sheet / 删除下拉菜单: 弹窗夺走焦点, 关闭后不会自动还回.
    //
    // 完全事件驱动(不靠 wall-clock 轮询/定时窗口), 关键是区分两种失焦:
    //   (A) 焦点被"清空到无人持有"(切换/弹窗关闭所致) —— 要夺回;
    //   (B) 焦点被"移到了别的缓存按钮"(用户方向键导航) —— 绝不抢回.
    // 用 LocalCacheFocusOwner 这个跨按钮共享的"当前焦点归属"来区分: 归属为 null=被清空(夺回),
    // 归属为别的 key=用户导航走了(放弃). 夺回只由真实事件触发: 本按钮视觉切换、弹窗关闭; 重试也只
    // 等渲染帧(withFrameNanos)而非计时. 因此永不与用户导航/别的按钮抢焦点.
    val focusRequester = remember { FocusRequester() }
    val focusOwner = LocalCacheFocusOwner.current
    val myFocusKey = remember { Any() }
    var isFocused by remember { mutableStateOf(false) }
    var wantFocus by remember { mutableStateOf(false) }
    val currentPopupOpen by rememberUpdatedState(popupOpen)

    fun focusIsElsewhere(): Boolean {
        val owner = focusOwner?.value
        return owner != null && owner != myFocusKey
    }

    // 本按钮视觉状态标识; 变化即一次会清焦点的切换(progress 数值变化不计入, 只取 Caching 类型).
    val visualKey = "$running|" + when (cacheStatus) {
        is EpisodeCacheStatus.Cached -> "cached"
        is EpisodeCacheStatus.Caching -> "caching"
        is EpisodeCacheStatus.NotCached -> "notCached"
        null -> "null"
    } + "|$showCancel"

    // 兜底上限: 点击意图最多保持一段时间(只为防止"下载很久后完成的切换"在用户早已离开时把焦点拉回).
    // 不参与日常夺回时序, 日常夺回完全由下面的事件触发.
    // 注意: 弹窗(数据源选择)打开期间不能计时 —— 用户可能在 sheet 里浏览数据源很久(>8s), 若此时
    // 把 wantFocus 清掉, 选完关闭 sheet 时就不会夺回焦点了. 因此只在"无弹窗"时才计这 8s.
    LaunchedEffect(wantFocus, popupOpen) {
        if (wantFocus && !popupOpen) {
            delay(8000)
            wantFocus = false
        }
    }

    // 事件触发的夺回: 在随后的若干渲染帧内补请求焦点(直到拿回或条件失效). 用 withFrameNanos 等帧,
    // 能吃下"切换的失焦比事件回调晚一两帧"的情况.
    //
    // force 用于区分两类"焦点跑到别的按钮":
    //  - 切换(transition)夺回: force=false, 遵守 focusIsElsewhere —— 若用户已用方向键导航到别的按钮,
    //    则不抢(下载 settling 期间用户可能正在导航).
    //  - 弹窗关闭(popupClose)夺回: force=true, 即便焦点被交给了别的按钮也抢回来 —— 因为这是 ModalBottomSheet
    //    关闭瞬间系统/列表把焦点错误派发到别处(冷启动偶发), 而此刻刚选完数据源、用户来不及在几帧内导航,
    //    所以这几帧内的"别处"一定是系统误派而非用户操作.
    suspend fun reclaimByFrames(force: Boolean) {
        repeat(8) {
            withFrameNanos { }
            if (!wantFocus || currentPopupOpen) return
            if (!force && focusIsElsewhere()) return
            if (!isFocused) runCatching { focusRequester.requestFocus() }
        }
    }

    // 事件1: 本按钮视觉切换(下载状态变化), 可能清掉焦点 -> 夺回. 跳过首次组合(初始值, 不是切换).
    var visualKeySeen by remember { mutableStateOf(false) }
    LaunchedEffect(visualKey) {
        if (!visualKeySeen) {
            visualKeySeen = true
            return@LaunchedEffect
        }
        reclaimByFrames(force = false)
    }
    // 事件2: 弹窗(数据源 sheet / 删除下拉菜单)关闭 -> 夺回(force, 抢回被误派到别处的焦点).
    var popupWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(popupOpen) {
        val wasOpen = popupWasOpen
        popupWasOpen = popupOpen
        if (wasOpen && !popupOpen) reclaimByFrames(force = true)
    }

    val onButtonClick: () -> Unit = when {
        running -> if (showCancel) {
            { onCancel(); showCancel = false }
        } else {
            { showCancel = true }
        }

        else -> onClick
    }

    IconButton(
        onClick = {
            wantFocus = true // 点击后表达"希望焦点留在本按钮", 由事件驱动的夺回兑现
            onButtonClick()
        },
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused
                // 维护跨按钮的焦点归属: 拿到焦点记为本按钮; 失去焦点且归属仍是自己时置空(=被清空).
                focusOwner?.let { owner ->
                    if (it.isFocused) {
                        owner.value = myFocusKey
                    } else if (owner.value == myFocusKey) {
                        owner.value = null
                    }
                }
            },
    ) {
        Crossfade(
            when {
                running && showCancel -> CacheActionVisual.Cancel
                running -> CacheActionVisual.RunningSpinner
                cacheStatus is EpisodeCacheStatus.Cached -> CacheActionVisual.Cached
                cacheStatus is EpisodeCacheStatus.Caching -> CacheActionVisual.Caching
                else -> CacheActionVisual.Download
            },
        ) { visual ->
            when (visual) {
                CacheActionVisual.Cancel ->
                    Icon(Icons.Rounded.Close, stringResource(Lang.cache_subject_cancel))

                CacheActionVisual.RunningSpinner ->
                    CircularProgressIndicator(
                        Modifier.size(progressIndicatorSize),
                        strokeWidth = strokeWidth,
                        trackColor = trackColor,
                    )

                CacheActionVisual.Cached ->
                    Icon(Icons.Rounded.DownloadDone, null)

                CacheActionVisual.Caching -> {
                    val caching = cacheStatus as? EpisodeCacheStatus.Caching
                    if (caching == null || caching.progress.isUnspecified) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(progressIndicatorSize),
                            strokeWidth = strokeWidth,
                            trackColor = trackColor,
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { caching.progress.getOrZero() },
                            modifier = Modifier.size(progressIndicatorSize),
                            strokeWidth = strokeWidth,
                            trackColor = trackColor,
                        )
                    }
                }

                CacheActionVisual.Download ->
                    CompositionLocalProvider(
                        LocalContentColor providesDefault MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(Icons.Rounded.Download, stringResource(Lang.cache_subject_cache))
                    }
            }
        }
    }
}

/**
 * 跨缓存按钮共享的"当前持有焦点的按钮 key". 由 [EpisodeCacheActionIcon] 在 onFocusChanged 中维护,
 * 用于区分"焦点被清空"(归属为 null, 需夺回) 与"用户导航到了别的按钮"(归属为别的 key, 不可抢).
 * 仅在缓存列表处提供; 未提供时为 null(夺回逻辑退化为不跨按钮协调).
 */
private val LocalCacheFocusOwner = compositionLocalOf<MutableState<Any?>?> { null }

private enum class CacheActionVisual {
    Download,
    Caching,
    Cached,
    RunningSpinner,
    Cancel,
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

