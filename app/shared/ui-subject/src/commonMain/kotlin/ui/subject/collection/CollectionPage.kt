/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection

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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.models.episode.findCacheStatus
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.toNavPlaceholder
import me.him188.ani.app.data.repository.subject.CollectionsFilterQuery
import me.him188.ani.app.domain.session.AuthState
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.session.SelfAvatar
import me.him188.ani.app.ui.foundation.session.SessionTipsArea
import me.him188.ani.app.ui.foundation.session.SessionTipsIcon
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.NsfwMask
import me.him188.ani.app.ui.foundation.widgets.PullToRefreshBox
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.collection.progress.EpisodeListStateFactory
import me.him188.ani.app.ui.subject.collection.progress.SubjectProgressButton
import me.him188.ani.app.ui.subject.collection.progress.SubjectProgressStateFactory
import me.him188.ani.app.ui.subject.collection.progress.rememberEpisodeListState
import me.him188.ani.app.ui.subject.collection.progress.rememberSubjectProgressState
import me.him188.ani.app.ui.subject.episode.list.EpisodeListDialog
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
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
    val authState: AuthState,
    selfInfoState: State<UserInfo?>,
    collectionCountsState: State<SubjectCollectionCounts?>,
    val episodeListStateFactory: EpisodeListStateFactory,
    val subjectProgressStateFactory: SubjectProgressStateFactory,
    val createEditableSubjectCollectionTypeState: (subjectCollection: SubjectCollectionInfo) -> EditableSubjectCollectionTypeState,
    defaultQuery: CollectionsFilterQuery = CollectionsFilterQuery(
        type = UnifiedCollectionType.DOING,
    ),
) {
    private var filterQueryPair by mutableStateOf(
        1 to defaultQuery,
    )

    private val filterQuery by derivedStateOf { filterQueryPair.second }

    private val availableTypes = COLLECTION_TABS_SORTED
    val selectedTypeIndex by derivedStateOf {
        availableTypes.indexOf(filterQuery.type)
    }

    fun selectTypeIndex(index: Int) {
        updateQuery { copy(type = availableTypes[index]) }
    }

    val currentPagerFlow: Flow<PagingData<SubjectCollectionInfo>> =
        snapshotFlow { filterQueryPair.second } // subscribe to both id and query, don't change to just `filterQuery`
            .transformLatest {
                emit(
                    PagingData.from(
                        emptyList(),
                        LoadStates(
                            refresh = LoadState.Loading,
                            append = LoadState.NotLoading(false),
                            prepend = LoadState.NotLoading(false),
                        ),
                    ),
                )
                emitAll(startSearch(it))
            }

    val selfInfo: UserInfo? by selfInfoState

    val collectionCounts: SubjectCollectionCounts? by collectionCountsState

    private fun updateQuery(query: CollectionsFilterQuery.() -> CollectionsFilterQuery) {
        val current = filterQueryPair
        filterQueryPair = current.copy(current.first, current.second.let(query))
    }

    fun refresh() {
        val current = filterQueryPair
        filterQueryPair = current.copy(current.first + 1)
    }
}

@Composable
fun CollectionPage(
    state: UserCollectionsState,
    items: LazyPagingItems<SubjectCollectionInfo>,
    onClickSearch: () -> Unit,
    onClickSettings: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    enableAnimation: Boolean = true,
    lazyGridState: LazyGridState = rememberLazyGridState(),
) {
    // 如果有缓存, 列表区域要展示缓存, 错误就用图标放在角落
    CollectionPageLayout(
        settingsIcon = {
            if (state.authState.isKnownGuest // #1269 游客模式下无法打开设置界面
                || currentWindowAdaptiveInfo1().windowSizeClass.windowWidthSizeClass.isAtLeastMedium
            ) {
                IconButton(onClick = onClickSettings) {
                    Icon(Icons.Rounded.Settings, "设置")
                }
            }
        },
        actions = {
            SessionTipsIcon(state.authState)
            actions()
        },
        avatar = { recommendedSize ->
            SelfAvatar(state.authState, state.selfInfo, size = recommendedSize)
        },
        filters = {
            CollectionTypeScrollableTabRow(
                selectedIndex = state.selectedTypeIndex,
                onSelect = { index -> state.selectTypeIndex(index) },
                Modifier.padding(horizontal = currentWindowAdaptiveInfo1().windowSizeClass.paneHorizontalPadding),
            ) { type ->
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
            }
        },
        isRefreshing = { items.loadState.refresh is LoadState.Loading },
        onRefresh = {
            items.refresh()
        },
        modifier,
        windowInsets,
    ) {
        when {
            // 假设没登录, 但是有缓存, 需要展示缓存
            state.authState.isKnownGuest && items.itemCount == 0 -> {
                SessionTipsArea(
                    state.authState,
                    guest = { GuestTips(state.authState, onClickSearch) },
                    Modifier.padding(top = 32.dp)
                        .padding(horizontal = 16.dp),
                )
            }

            else -> {
                PullToRefreshBox(
                    items.loadState.refresh is LoadState.Loading,
                    onRefresh = { items.refresh() },
                    state = rememberPullToRefreshState(),
                    enabled = LocalPlatform.current.isMobile(),
                ) {
                    SubjectCollectionsColumn(
                        items,
                        item = { collection ->
                            var nsfwModeState: NsfwMode by rememberSaveable { mutableStateOf(collection.nsfwMode) }
                            NsfwMask(
                                nsfwModeState,
                                onTemporarilyDisplay = { nsfwModeState = NsfwMode.DISPLAY },
                                shape = SubjectCollectionItemDefaults.shape,
                            ) {
                                SubjectCollectionItem(
                                    collection,
                                    state.episodeListStateFactory,
                                    state.subjectProgressStateFactory,
                                    state.createEditableSubjectCollectionTypeState(collection),
                                )
                            }
                        },
                        enableAnimation = enableAnimation,
                        gridState = lazyGridState,
                    )
                }
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
    content: @Composable () -> Unit,
) {
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
                )

                filters(CollectionPageFilters)
            }
        },
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
    ) { topBarPaddings ->
        Box(modifier = Modifier.padding(topBarPaddings).fillMaxSize()) {
            content()
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
    ) {
        val uiScope = rememberCoroutineScope()
        val widths = remember { mutableStateListOf(*COLLECTION_TABS_SORTED.map { 24.dp }.toTypedArray()) }
        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            indicator = @Composable { tabPositions ->
                TabRowDefaults.PrimaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    width = widths[selectedIndex],
                )
            },
            containerColor = Color.Unspecified,
            contentColor = MaterialTheme.colorScheme.onSurface,
            divider = {},
            modifier = modifier.fillMaxWidth(),
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
    episodeListStateFactory: EpisodeListStateFactory,
    subjectProgressStateFactory: SubjectProgressStateFactory,
    editableSubjectCollectionTypeState: EditableSubjectCollectionTypeState,
    type: UnifiedCollectionType = subjectCollection.collectionType,
    modifier: Modifier = Modifier,
) {
    var showEpisodeProgressDialog by rememberSaveable { mutableStateOf(false) }

    // 即使对话框不显示也加载, 避免打开对话框要等待一秒才能看到进度
    val episodeProgressState = episodeListStateFactory
        .rememberEpisodeListState(subjectCollection.subjectId)

    val navigator = LocalNavigator.current
    if (showEpisodeProgressDialog) {
        EpisodeListDialog(
            episodeProgressState,
            title = {
                Text(subjectCollection.subjectInfo.displayName)
            },
            onDismissRequest = { showEpisodeProgressDialog = false },
            actions = {
                OutlinedButton(
                    {
                        navigator.navigateSubjectDetails(
                            subjectCollection.subjectId,
                            placeholder = subjectCollection.subjectInfo.toNavPlaceholder(),
                        )
                    },
                ) {
                    Text("条目详情")
                }
            },
        )
    }

    val subjectProgressState = subjectProgressStateFactory
        .rememberSubjectProgressState(subjectCollection)

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

            if (type != UnifiedCollectionType.DONE) {
                if (subjectProgressState.isDone) {
                    FilledTonalButton(
                        {
                            editableSubjectCollectionTypeState.setSelfCollectionType(UnifiedCollectionType.DONE)
                        },
                        enabled = !editableSubjectCollectionTypePresentation.isSetSelfCollectionTypeWorking,
                    ) {
                        Text("移至\"看过\"", Modifier.requiredWidth(IntrinsicSize.Max), softWrap = false)
                    }
                } else {
                    val episodeProgressInfos by subjectProgressStateFactory.episodeProgressInfoList(subjectCollection.subjectId)
                        .collectAsStateWithLifecycle(null)
                    SubjectProgressButton(
                        subjectProgressState,
                        episodeCacheStatus = { episodeProgressInfos?.findCacheStatus(it) },
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
    authState: AuthState,
    onClickSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        val navigator = LocalNavigator.current
        Text("游客模式下请搜索后观看，或登录后使用收藏功能")

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton({ authState.launchAuthorize(navigator) }, Modifier.weight(1f)) {
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
