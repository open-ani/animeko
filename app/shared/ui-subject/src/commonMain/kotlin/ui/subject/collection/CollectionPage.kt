/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.toNavPlaceholder
import me.him188.ani.app.data.repository.subject.CollectionsFilterQuery
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isHeightAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.session.SelfAvatar
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.NsfwMask
import me.him188.ani.app.ui.foundation.widgets.PullToRefreshBox
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.app.ui.search.isLoadingFirstPageOrRefreshing
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.collection.progress.SubjectProgressButton
import me.him188.ani.app.ui.subject.collection.progress.SubjectProgressStateFactory
import me.him188.ani.app.ui.subject.collection.progress.rememberSubjectProgressState
import me.him188.ani.app.ui.subject.episode.list.EpisodeListDialog
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.app.ui.subject.episode.list.EpisodeListUiState
import me.him188.ani.app.ui.user.BangumiFullSyncStateDialog
import me.him188.ani.app.ui.user.SelfInfoUiState
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.restartable
import me.him188.ani.utils.platform.hasScrollingBug
import me.him188.ani.utils.platform.isDesktop
import me.him188.ani.utils.platform.isMobile


// 有顺序, https://github.com/Him188/ani/issues/73
@Stable
val COLLECTION_TABS_SORTED = listOf(
    UnifiedCollectionType.DROPPED,
    UnifiedCollectionType.WISH,
    UnifiedCollectionType.DOING,
    UnifiedCollectionType.ON_HOLD,
    UnifiedCollectionType.DONE,
)

@Stable
class UserCollectionsState(
    private val startSearch: (filterQuery: CollectionsFilterQuery) -> Flow<PagingData<SubjectCollectionInfo>>,
    collectionCountsState: State<SubjectCollectionCounts?>,
    val subjectProgressStateFactory: SubjectProgressStateFactory,
    fullSyncState: State<BangumiSyncState?>,
    val createEditableSubjectCollectionTypeState: (subjectCollection: SubjectCollectionInfo) -> EditableSubjectCollectionTypeState,
    val onPagerFetchingAnyRemoteSource: (Boolean) -> Unit,
    private val backgroundScope: CoroutineScope,
    defaultQuery: CollectionsFilterQuery = CollectionsFilterQuery(
        type = UnifiedCollectionType.DOING,
    ),
) {
    private var currentQuery by mutableStateOf(defaultQuery)

    val selectedTypeIndex by derivedStateOf { availableTypes.indexOf(currentQuery.type) }

    val fullSyncState by fullSyncState

    val collectionCounts: SubjectCollectionCounts? by collectionCountsState
    val tabRowScrollState = ScrollState(selectedTypeIndex)
    val pagerState = PagerState(selectedTypeIndex) { availableTypes.size }

    // Store LazyGridState for each tab
    private val gridStates = mutableMapOf<Int, LazyGridState>()

    // Cache data flows for each tab
    private val cachedLazyPagingItems = mutableMapOf<Int, LazyPagingItems<SubjectCollectionInfo>>()

    private val restarter = FlowRestarter()

    // true 表示这个 PagingData 的 remote mediator 正在加载, 即正在从 ani server 获取数据.
    // 从 server 获取收藏数据时可能会触发 user 的 full sync 操作, 需要等待 full sync 完成后才能获取到收藏数据.
    // 
    // 记录每个 type 的 remote mediator 是否正在加载
    // 只要有一个在加载, 我们就通知 view model 轮询获取 full sync 状态.
    private val remotePagingStates = mutableStateMapOf<Int, Boolean>()

    fun selectTypeIndex(index: Int) {
        currentQuery = currentQuery.copy(type = availableTypes[index])
    }

    @Suppress("INVISIBLE_REFERENCE")
    fun getCollectionLazyPagingItems(typeIndex: Int): LazyPagingItems<SubjectCollectionInfo> {
        return cachedLazyPagingItems.getOrPut(typeIndex) {
            val pagingFlow = flowOf(typeIndex)
                .restartable(restarter)
                .map { CollectionsFilterQuery(availableTypes[it]) }
                .transformLatest { query ->
                    emit(
                        PagingData.from(
                            emptyList<SubjectCollectionInfo>(),
                            LoadStates(
                                refresh = LoadState.Loading,
                                append = LoadState.NotLoading(false),
                                prepend = LoadState.NotLoading(false),
                            ),
                        ),
                    )
                    emitAll(startSearch(query))
                }
                .cachedIn(backgroundScope)

            LazyPagingItems(pagingFlow).apply {
                addLoadStateListener { checkPagingState(typeIndex, it) }
            }
        }
    }

    // listener is called on main thread
    private fun checkPagingState(typeIndex: Int, loadState: CombinedLoadStates) {
        val ms = loadState.mediator
        if (ms == null) {
            // 没有 remote mediator state, 说明没从 server 获取数据.
            remotePagingStates[typeIndex] = false
            return
        }
        remotePagingStates.put(
            typeIndex,
            // 加载中的状态, 正在从 server 获取数据.
            !ms.isIdle && !ms.hasError && ms.refresh is LoadState.Loading,
        )

        onPagerFetchingAnyRemoteSource(remotePagingStates.values.any { it })
    }

    fun refresh() {
        restarter.restart()
    }

    fun getGridState(pageIndex: Int): LazyGridState {
        return gridStates.getOrPut(pageIndex) { LazyGridState() }
    }

    suspend fun scrollToTop() {
        val currentGridState = gridStates[selectedTypeIndex]
        currentGridState?.animateScrollToItem(0)
    }

    companion object {
        private val availableTypes = COLLECTION_TABS_SORTED
    }
}

@Composable
fun CollectionPage(
    state: UserCollectionsState,
    selfInfo: SelfInfoUiState,
    onClickSearch: () -> Unit,
    onClickLogin: () -> Unit,
    onClickSettings: () -> Unit,
    onCollectionUpdate: (subjectId: Int, episode: EpisodeListItem) -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    enableAnimation: Boolean = true,

    ) {
    val scope = rememberCoroutineScope()
    var currentPageItems by remember {
        mutableStateOf<LazyPagingItems<SubjectCollectionInfo>?>(null)
    }

    val isCurrentPageRefreshing by remember {
        derivedStateOf { currentPageItems?.isLoadingFirstPageOrRefreshing == true }
    }
    val isBangumiSyncing by remember {
        derivedStateOf { state.fullSyncState != null && state.fullSyncState?.finished == false }
    }

    var showBangumiSyncStateDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.fullSyncState) {
        if (state.fullSyncState == BangumiSyncState.Finished) {
            showBangumiSyncStateDialog = false
        }
    }

    // 如果有缓存, 列表区域要展示缓存, 错误就用图标放在角落
    CollectionPageLayout(
        settingsIcon = {
            if (selfInfo.isSessionValid == false // #1269 游客模式下无法打开设置界面
                || currentWindowAdaptiveInfo1().windowSizeClass.isWidthAtLeastMedium
            ) {
                IconButton(onClick = onClickSettings) {
                    Icon(Icons.Rounded.Settings, "设置")
                }
            }
        },
        actions = {
            if (isBangumiSyncing) {
                val infiniteTransition = rememberInfiniteTransition(label = "rotation")
                val angle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(3000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "angle",
                )

                IconButton({ showBangumiSyncStateDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = "Bangumi 同步中, 点击查看详情",
                        modifier = Modifier.rotate(angle),
                    )
                }
            }
            actions()
        },
        avatar = { recommendedSize ->
            SelfAvatar(
                selfInfo,
                onClick = onClickLogin,
                size = recommendedSize,
            )
        },
        filters = {
            CollectionTypeScrollableTabRow(
                selectedIndex = state.selectedTypeIndex,
                onSelect = { index ->
                    state.selectTypeIndex(index)
                    scope.launch {
                        state.pagerState.animateScrollToPage(index)
                    }
                },
                Modifier.padding(horizontal = currentWindowAdaptiveInfo1().windowSizeClass.paneHorizontalPadding),
                { type ->
                    val size = state.collectionCounts
                    if (size == null) {
                        Text(
                            text = type.displayText(),
                            Modifier.width(IntrinsicSize.Max),
                            softWrap = false,
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = type.displayText(),
                                softWrap = false,
                            )
                            Badge(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ) {
                                Text(
                                    text = size.getCount(type).toString(),
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .wrapContentSize(align = Alignment.Center),
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                },
                scrollState = state.tabRowScrollState,
            )
        },
        isRefreshing = { isCurrentPageRefreshing || isBangumiSyncing },
        onRefresh = { currentPageItems?.refresh() },
        modifier,
        windowInsets,
    ) { nestedScrollConnection ->
        CollectionPageColumnLayout(
            state,
            onCurrentPagingItemChange = { currentPageItems = it },
            modifier = Modifier.fillMaxSize(),
        ) { items, pageIndex ->
            PullToRefreshBox(
                items.isLoadingFirstPageOrRefreshing,
                onRefresh = { items.refresh() },
                state = rememberPullToRefreshState(),
                enabled = LocalPlatform.current.isMobile() && !isBangumiSyncing,
            ) {
                SubjectCollectionsColumn(
                    items,
                    item = { collection ->
                        var nsfwModeState: NsfwMode by rememberSaveable(collection) { mutableStateOf(collection.nsfwMode) }
                        NsfwMask(
                            nsfwModeState,
                            onTemporarilyDisplay = { nsfwModeState = NsfwMode.DISPLAY },
                            shape = SubjectCollectionItemDefaults.shape,
                        ) {
                            SubjectCollectionItem(
                                collection,
                                { onCollectionUpdate(collection.subjectId, it) },
                                state.subjectProgressStateFactory,
                                state.createEditableSubjectCollectionTypeState(collection),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    enableAnimation = enableAnimation,
                    gridState = remember(pageIndex) { state.getGridState(pageIndex) },
                )
            }
        }
    }

    if (showBangumiSyncStateDialog && isBangumiSyncing) {
        BangumiFullSyncStateDialog(
            state = state.fullSyncState,
            onDismissRequest = { showBangumiSyncStateDialog = false },
        )
    }
}

/**
 * @param filters see [CollectionPageFilters]
 */
@Composable
private fun CollectionPageLayout(
    settingsIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    avatar: @Composable (recommendedSize: DpSize) -> Unit,
    filters: @Composable CollectionPageFilters.() -> Unit,
    isRefreshing: () -> Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    content: @Composable (nestedScrollConnection: NestedScrollConnection?) -> Unit,
) {
    val isHeightAtLeastMedium = currentWindowAdaptiveInfo1().windowSizeClass.isHeightAtLeastMedium
    val scrollBehavior = if (LocalPlatform.current.hasScrollingBug() || isHeightAtLeastMedium) {
        null // Can't use PinnedBehavior, because we have a TabRow in this page, which does not sync color
    } else {
        // 在紧凑高度时收起 Top bar
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }
    Scaffold(
        modifier,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AniTopAppBar(
                    title = { AniTopAppBarDefaults.Title("追番") },
                    modifier = Modifier,
                    actions = {
                        actions()

                        if (LocalPlatform.current.isDesktop()) {
                            // PC 无法下拉刷新
                            IconButton(
                                {
                                    onRefresh()
                                },
                                enabled = !isRefreshing(),
                            ) {
                                Icon(Icons.Rounded.Refresh, null)
                            }
                        }

                        settingsIcon()
                    },
                    avatar = avatar,
                    windowInsets = AniWindowInsets.forTopAppBarWithoutDesktopTitle(),
                    scrollBehavior = scrollBehavior,
                )

                filters(CollectionPageFilters)
            }
        },
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
    ) { topBarPaddings ->
        Box(
            modifier = Modifier.padding(topBarPaddings)
                .fillMaxSize()
                .wrapContentWidth()
                .widthIn(max = 1300.dp),
        ) {
            content(scrollBehavior?.nestedScrollConnection)
        }
    }
}

/**
 * 在移动端使用 [HorizontalPager] 支持左右滑动切换标签.
 */
@Composable
private fun CollectionPageColumnLayout(
    state: UserCollectionsState,
    onCurrentPagingItemChange: (LazyPagingItems<SubjectCollectionInfo>) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (items: LazyPagingItems<SubjectCollectionInfo>, pageIndex: Int) -> Unit,
) {
    val platform = LocalPlatform.current

    if (platform.isMobile()) {
        LaunchedEffect(state.pagerState.currentPage) {
            if (state.pagerState.currentPage != state.selectedTypeIndex) {
                state.selectTypeIndex(state.pagerState.currentPage)
            }
        }

        HorizontalPager(
            state = state.pagerState,
            modifier = modifier,
            beyondViewportPageCount = 1,
            pageSpacing = 0.dp,
        ) { pageIndex ->
            val items = state
                .getCollectionLazyPagingItems(pageIndex)
                .collectWithLifecycle()

            LaunchedEffect(state.selectedTypeIndex) {
                if (pageIndex == state.selectedTypeIndex) {
                    onCurrentPagingItemChange(items)
                }
            }

            Column(Modifier.fillMaxSize()) {
                content(items, pageIndex)
            }
        }
    } else {
        val items = remember(state.selectedTypeIndex) {
            state.getCollectionLazyPagingItems(state.selectedTypeIndex)
        }.collectWithLifecycle()

        LaunchedEffect(items) {
            onCurrentPagingItemChange(items)
        }

        Box(modifier) {
            content(items, state.selectedTypeIndex)
        }
    }
}

@Stable
object CollectionPageFilters {
    @Composable
    fun CollectionTypeFilterButtons(
        pagerState: PagerState,
        modifier: Modifier = Modifier,
        itemLabel: @Composable (UnifiedCollectionType) -> Unit = { type ->
            Text(type.displayText(), softWrap = false)
        },
    ) {
        val uiScope = rememberCoroutineScope()
        SingleChoiceSegmentedButtonRow(modifier) {
            COLLECTION_TABS_SORTED.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = pagerState.currentPage == index,
                    onClick = { uiScope.launch { pagerState.scrollToPage(index) } },
                    shape = SegmentedButtonDefaults.itemShape(index, COLLECTION_TABS_SORTED.size),
                    Modifier.wrapContentWidth(),
                ) {
                    itemLabel(type)
                }
            }
        }
    }

    @Composable
    fun CollectionTypeScrollableTabRow(
        selectedIndex: Int,
        onSelect: (Int) -> Unit,
        modifier: Modifier = Modifier,
        itemLabel: @Composable (UnifiedCollectionType) -> Unit = { type ->
            Text(type.displayText(), softWrap = false)
        },
        scrollState: ScrollState = rememberScrollState(),
    ) {
        val widths = remember { mutableStateListOf(*COLLECTION_TABS_SORTED.map { 24.dp }.toTypedArray()) }
        SecondaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            indicator = @Composable {
                TabRowDefaults.PrimaryIndicator(
                    Modifier.tabIndicatorOffset(selectedIndex, matchContentSize = false),
                    width = widths[selectedIndex],
                )
            },
            containerColor = Color.Unspecified,
            contentColor = MaterialTheme.colorScheme.onSurface,
            divider = {},
            modifier = modifier.fillMaxWidth(),
            scrollState = scrollState,
        ) {
            COLLECTION_TABS_SORTED.forEachIndexed { index, collectionType ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) },
                    text = {
                        val density = LocalDensity.current
                        Box(Modifier.onPlaced { widths[index] = with(density) { it.size.width.toDp() } }) {
                            itemLabel(collectionType)
                        }
                    },
                )
            }
        }
    }

}

@Composable
private fun SubjectCollectionItem(
    subjectCollection: SubjectCollectionInfo,
    onCollectionUpdate: (episode: EpisodeListItem) -> Unit,
    subjectProgressStateFactory: SubjectProgressStateFactory,
    editableSubjectCollectionTypeState: EditableSubjectCollectionTypeState,
    type: UnifiedCollectionType = subjectCollection.collectionType,
    modifier: Modifier = Modifier,
) {
    var showEpisodeProgressDialog by rememberSaveable { mutableStateOf(false) }

    // 即使对话框不显示也加载, 避免打开对话框要等待一秒才能看到进度
    val navigator = LocalNavigator.current
    if (showEpisodeProgressDialog) {
        EpisodeListDialog(
            remember(subjectCollection.episodes) {
                EpisodeListUiState.from(subjectCollection, Clock.System.now())
            },
            onDismissRequest = { showEpisodeProgressDialog = false },
            onCacheClick = {
                navigator.navigateSubjectCaches(subjectCollection.subjectId)
            },
            onEpisodeClick = {
                navigator.navigateEpisodeDetails(
                    subjectCollection.subjectId,
                    it.episodeId,
                )
            },
            onSubjectDetailsClick = {
                navigator.navigateSubjectDetails(
                    subjectCollection.subjectId,
                    placeholder = subjectCollection.subjectInfo.toNavPlaceholder(),
                )
            },
            onCollectionUpdate = onCollectionUpdate,
        )
    }

    val subjectProgressState = subjectProgressStateFactory
        .rememberSubjectProgressState(subjectCollection)

    val scope = rememberCoroutineScope()

    SubjectCollectionItem(
        subjectCollection,
        editableSubjectCollectionTypeState = editableSubjectCollectionTypeState,
        onClick = {
            navigator.navigateSubjectDetails(
                subjectCollection.subjectId,
                placeholder = subjectCollection.subjectInfo.toNavPlaceholder(),
            )
        },
        onShowEpisodeList = {
            showEpisodeProgressDialog = true
        },
        playButton = {
            val editableSubjectCollectionTypePresentation by editableSubjectCollectionTypeState.presentationFlow.collectAsStateWithLifecycle()
            val toaster = LocalToaster.current
            if (type != UnifiedCollectionType.DONE) {
                if (subjectProgressState.isDone) {
                    FilledTonalButton(
                        {
                            scope.launch {
                                val error =
                                    editableSubjectCollectionTypeState.setSelfCollectionType(UnifiedCollectionType.DONE)
                                error?.let { toaster.showLoadError(it) }
                            }
                        },
                        enabled = !editableSubjectCollectionTypePresentation.isSetSelfCollectionTypeWorking,
                    ) {
                        Text("移至\"看过\"", Modifier.requiredWidth(IntrinsicSize.Max), softWrap = false)
                    }
                } else {
                    SubjectProgressButton(
                        subjectProgressState,
                        onPlay = {
                            subjectProgressState.episodeIdToPlay?.let {
                                navigator.navigateEpisodeDetails(subjectCollection.subjectId, it)
                            }
                        },
                    )
                }
            }
        },
        colors = AniThemeDefaults.primaryCardColors(),
        modifier = modifier,
    )
}

@Stable
private fun UnifiedCollectionType.displayText(): String {
    return when (this) {
        UnifiedCollectionType.WISH -> "想看"
        UnifiedCollectionType.DOING -> "在看"
        UnifiedCollectionType.DONE -> "看过"
        UnifiedCollectionType.ON_HOLD -> "搁置"
        UnifiedCollectionType.DROPPED -> "抛弃"
        UnifiedCollectionType.NOT_COLLECTED -> "未收藏"
    }
}


@Composable
private fun GuestTips(
    onClickSearch: () -> Unit,
    onClickLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text("游客模式下请搜索后观看，或登录后使用收藏功能")

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClickLogin, Modifier.weight(1f)) {
                Icon(Icons.Rounded.HowToReg, null)
                Text("登录", Modifier.padding(start = 8.dp))
            }

            Button(onClickSearch, Modifier.weight(1f)) {
                Icon(Icons.Rounded.Search, null)
                Text("搜索", Modifier.padding(start = 8.dp))
            }
        }
    }
}
