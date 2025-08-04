/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.toNavPlaceholder
import me.him188.ani.app.data.repository.subject.CollectionsFilterQuery
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.ifNotNullThen
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
import me.him188.ani.app.ui.user.SelfInfoUiState
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.restartable
import me.him188.ani.utils.platform.hasScrollingBug
import me.him188.ani.utils.platform.isDesktop
import me.him188.ani.utils.platform.isMobile
import kotlin.math.abs


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
    val createEditableSubjectCollectionTypeState: (subjectCollection: SubjectCollectionInfo) -> EditableSubjectCollectionTypeState,
    defaultQuery: CollectionsFilterQuery = CollectionsFilterQuery(
        type = UnifiedCollectionType.DOING,
    ),
) {
    private var currentQuery by mutableStateOf(defaultQuery)

    val selectedTypeIndex by derivedStateOf { availableTypes.indexOf(currentQuery.type) }

    val collectionCounts: SubjectCollectionCounts? by collectionCountsState
    val tabRowScrollState = ScrollState(selectedTypeIndex)

    private val restarter = FlowRestarter()

    fun selectTypeIndex(index: Int) {
        currentQuery = currentQuery.copy(type = availableTypes[index])
    }

    fun getCollectionTypePagerFlow(typeIndex: Int): Flow<PagingData<SubjectCollectionInfo>> {
        return flowOf(CollectionsFilterQuery(availableTypes[typeIndex]))
            .restartable(restarter)
            .transformLatest {
                emitAll(startSearch(it))
            }
    }

    fun refresh() {
        restarter.restart()
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
    lazyGridState: LazyGridState = rememberLazyGridState(),
) {
    var currentPageItems by remember {
        mutableStateOf<LazyPagingItems<SubjectCollectionInfo>?>(null)
    }

    val isCurrentPageRefreshing by remember {
        derivedStateOf { currentPageItems?.isLoadingFirstPageOrRefreshing == true }
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
                    // 移动端使用手势切换，不需要动画
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
        isRefreshing = { isCurrentPageRefreshing },
        onRefresh = { currentPageItems?.refresh() },
        modifier,
        windowInsets,
    ) { nestedScrollConnection ->
        PullToRefreshBox(
            isCurrentPageRefreshing,
            onRefresh = { currentPageItems?.refresh() },
            state = rememberPullToRefreshState(),
            enabled = LocalPlatform.current.isMobile(),
        ) {
            CollectionPageColumnLayout(
                state,
                onCurrentPagingItemChange = { currentPageItems = it },
                modifier = Modifier.fillMaxSize(),
            ) { items ->
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
                    modifier = Modifier.fillMaxSize()
                        .ifNotNullThen(nestedScrollConnection) {
                            nestedScroll(
                                object : NestedScrollConnection by it {
                                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                        return super.onPreScroll(available, source)
                                    }
                                },
                            )
                        },
                    enableAnimation = enableAnimation,
                    gridState = lazyGridState,
                )
            }
        }
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
 * 在移动端使用自定义手势检测支持左右滑动切换标签.
 */
@Composable
private fun CollectionPageColumnLayout(
    state: UserCollectionsState,
    onCurrentPagingItemChange: (LazyPagingItems<SubjectCollectionInfo>) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (items: LazyPagingItems<SubjectCollectionInfo>) -> Unit,
) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    
    // 直接使用当前选中的类型的LazyPagingItems
    val currentItems = state.getCollectionTypePagerFlow(state.selectedTypeIndex)
        .collectAsLazyPagingItemsWithLifecycle()
    
    LaunchedEffect(currentItems) {
        onCurrentPagingItemChange(currentItems)
    }

    if (platform.isMobile()) {
        // 移动端使用手势检测
        Box(
            modifier = modifier.swipeGesture(
                onSwipeLeft = {
                    val nextIndex = (state.selectedTypeIndex + 1).coerceAtMost(COLLECTION_TABS_SORTED.size - 1)
                    if (nextIndex != state.selectedTypeIndex) {
                        state.selectTypeIndex(nextIndex)
                        scope.launch {
                            state.tabRowScrollState.animateScrollTo(
                                nextIndex * 100 // 滚动估算
                            )
                        }
                    }
                },
                onSwipeRight = {
                    val prevIndex = (state.selectedTypeIndex - 1).coerceAtLeast(0)
                    if (prevIndex != state.selectedTypeIndex) {
                        state.selectTypeIndex(prevIndex)
                        scope.launch {
                            state.tabRowScrollState.animateScrollTo(
                                prevIndex * 100
                            )
                        }
                    }
                }
            )
        ) {
            content(currentItems)
        }
    } else {
        // 桌面端直接显示
        Box(modifier) {
            content(currentItems)
        }
    }
}

@Stable
object CollectionPageFilters {
    @Composable
    fun CollectionTypeFilterButtons(
        selectedIndex: Int,
        onSelect: (Int) -> Unit,
        modifier: Modifier = Modifier,
        itemLabel: @Composable (UnifiedCollectionType) -> Unit = { type ->
            Text(type.displayText(), softWrap = false)
        },
    ) {
        SingleChoiceSegmentedButtonRow(modifier) {
            COLLECTION_TABS_SORTED.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) },
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

/**
 * 滑动手势检测
 */
private fun Modifier.swipeGesture(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    threshold: Float = 300f // 这是一个经验值
): Modifier = this.pointerInput(Unit) {
    var totalDragX = 0f
    var hasTriggered = false
    
    detectDragGestures(
        onDragStart = {
            totalDragX = 0f
            hasTriggered = false
        },
        onDragEnd = {
            // 拖拽结束时重置状态
            totalDragX = 0f
            hasTriggered = false
        }
    ) { _, dragAmount ->
        totalDragX += dragAmount.x
        
        // 只有在未触发过且累计滑动距离超过阈值时才触发一次
        if (!hasTriggered && abs(totalDragX) > threshold) {
            hasTriggered = true
            if (totalDragX > 0) {
                onSwipeRight()
            } else {
                onSwipeLeft()
            }
        }
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
