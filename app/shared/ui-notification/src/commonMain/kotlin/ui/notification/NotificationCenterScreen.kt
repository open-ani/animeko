/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabIndicatorScope
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import androidx.paging.compose.itemKey
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.notification.AppNotification
import me.him188.ani.app.data.models.notification.NotificationBadgeState
import me.him188.ani.app.data.models.notification.NotificationGroup
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.adaptive.AniListDetailPaneScaffold
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.adaptive.ListDetailLayoutParameters
import me.him188.ani.app.ui.adaptive.PaneScope
import me.him188.ani.app.ui.foundation.LocalSubjectAppearanceSettings
import me.him188.ani.app.ui.foundation.input.touchHorizontalScrollOnly
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.rememberAsyncHandler
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.PullToRefreshBox
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.notification_detail_placeholder
import me.him188.ani.app.ui.lang.notification_empty
import me.him188.ani.app.ui.lang.notification_empty_supporting
import me.him188.ani.app.ui.lang.notification_group_interaction
import me.him188.ani.app.ui.lang.notification_group_notice
import me.him188.ani.app.ui.lang.notification_interaction_empty
import me.him188.ani.app.ui.lang.notification_interaction_empty_supporting
import me.him188.ani.app.ui.lang.notification_mark_all_read
import me.him188.ani.app.ui.lang.notification_settings
import me.him188.ani.app.ui.lang.notification_title
import me.him188.ani.app.ui.lang.notification_unread_count
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.search.isLoadingFirstPage
import me.him188.ani.app.ui.search.rememberLoadErrorState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 通知中心.
 *
 * 每个分组 ([NotificationCenterViewModel.groups]) 是一个标签页, 有自己的列表、滚动位置和本地已读状态.
 * 点击通知时按 [notificationOpenTarget] 打开: 详情在窄屏调用 [onNavigateToDetail] 打开单独的页面, 宽屏为双栏, 显示在右侧;
 * 账号设置在窄屏和宽屏都调用 [NotificationActions.onOpenAccountSettings]. 打开一条通知或执行它的行内操作都会立即把它标为已读.
 * 标签页已加载的通知中有客户端不认识的类型 ([isUnknownType]) 时, 列表顶部提示更新 ([NotificationActions.onCheckUpdate]).
 *
 * 列表不会自动加载新到的通知, 而未读数由后台定时刷新. 所以 "全部标为已读" 之后, 以及某个分组的未读数增加到超过它的列表中的未读通知时,
 * 重新加载对应的列表, 让新通知显示出来: "全部标为已读" 只标记已加载的通知.
 *
 * @param onNavigateToDetail 窄屏打开通知详情.
 * @param onLogin 游客点击登录.
 * @param actions 宽屏详情栏、列表行的操作和账号安全通知的跳转.
 */
@Composable
fun NotificationCenterScreen(
    vm: NotificationCenterViewModel,
    onNavigateToDetail: (notificationId: String) -> Unit,
    onNavigateToAnnouncements: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogin: () -> Unit,
    actions: NotificationActions,
    modifier: Modifier = Modifier,
    navigator: ThreePaneScaffoldNavigator<String> = rememberListDetailPaneScaffoldNavigator<String>(),
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val access by vm.access.collectAsStateWithLifecycle()
    val readOverlays by vm.readOverlays.collectAsStateWithLifecycle()
    val badgeState by vm.badgeState.collectAsStateWithLifecycle()
    val selectedGroup by vm.selectedGroup.collectAsStateWithLifecycle()
    val asyncHandler = rememberAsyncHandler()

    val pages = vm.groups.map { group ->
        key(group) {
            NotificationCenterPage(
                group = group,
                items = vm.notificationsPager(group).collectAsLazyPagingItemsWithLifecycle(),
                readOverlay = readOverlays[group] ?: ReadOverlay.Empty,
            )
        }
    }

    // 某个标签页的未读数增加到超过列表中的未读通知时, 重新加载这个标签页, 让新通知显示出来
    var lastUnreadCounts by remember { mutableStateOf(badgeState.unreadCounts) }
    LaunchedEffect(badgeState.unreadCounts) {
        val previous = lastUnreadCounts
        lastUnreadCounts = badgeState.unreadCounts
        if (access != NotificationAccess.AUTHORIZED) return@LaunchedEffect
        for (page in pages) {
            val unreadCount = badgeState.unreadCountOf(page.group)
            if (unreadCount > (previous[page.group] ?: 0) &&
                page.items.loadState.refresh is LoadState.NotLoading &&
                unreadCount > page.loadedUnreadCount()
            ) {
                page.items.refresh()
            }
        }
    }

    fun pageOf(group: NotificationGroup) = pages.first { it.group == group }

    NotificationCenterScreen(
        pages = pages,
        selectedGroup = selectedGroup,
        onSelectGroup = vm::selectGroup,
        access = access,
        badgeState = badgeState,
        onOpenNotification = vm::markRead,
        onMarkAllRead = { group, newestCreatedAtMillis ->
            asyncHandler.launch {
                vm.markAllRead(group, newestCreatedAtMillis)
                pageOf(group).items.refresh()
            }
        },
        onRefresh = { group ->
            pageOf(group).items.refresh()
            vm.refreshBadge()
        },
        onNavigateToDetail = onNavigateToDetail,
        onNavigateToAnnouncements = onNavigateToAnnouncements,
        onNavigateToSettings = onNavigateToSettings,
        onLogin = onLogin,
        actions = actions,
        modifier = modifier,
        navigator = navigator,
        navigationIcon = navigationIcon,
        windowInsets = windowInsets,
    )
}

/**
 * 通知中心的一个标签页.
 *
 * @property items 这个分组的通知.
 * @property readOverlay 这个分组本地叠加的已读状态.
 */
@Stable
data class NotificationCenterPage(
    val group: NotificationGroup,
    val items: LazyPagingItems<AppNotification>,
    val readOverlay: ReadOverlay,
) {
    val loadedItems: List<AppNotification> get() = items.itemSnapshotList.items

    fun loadedUnreadCount(): Int = loadedItems.count { !readOverlay.isRead(it) }
}

/**
 * @param pages 每个标签页一个, 按显示顺序. 只有一个时不显示标签页.
 * @param onSelectGroup 点击标签或滑动切换到另一个标签页时调用.
 * @param onOpenNotification 打开一条通知或执行它的行内操作时调用 (用于标为已读), 之后再导航.
 * @param onMarkAllRead 参数为选中的分组和它已加载的最新一条通知的创建时间.
 * @param onRefresh 下拉刷新某个分组的列表.
 */
@Composable
fun NotificationCenterScreen(
    pages: List<NotificationCenterPage>,
    selectedGroup: NotificationGroup,
    onSelectGroup: (NotificationGroup) -> Unit,
    access: NotificationAccess,
    badgeState: NotificationBadgeState,
    onOpenNotification: (AppNotification) -> Unit,
    onMarkAllRead: (group: NotificationGroup, newestCreatedAtMillis: Long) -> Unit,
    onRefresh: (NotificationGroup) -> Unit,
    onNavigateToDetail: (notificationId: String) -> Unit,
    onNavigateToAnnouncements: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogin: () -> Unit,
    actions: NotificationActions,
    modifier: Modifier = Modifier,
    navigator: ThreePaneScaffoldNavigator<String> = rememberListDetailPaneScaffoldNavigator<String>(),
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val coroutineScope = rememberCoroutineScope()
    val layoutParameters = ListDetailLayoutParameters.calculate(navigator.scaffoldDirective)
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    fun openNotification(notification: AppNotification) {
        onOpenNotification(notification)
        when (notificationOpenTarget(notification.content)) {
            NotificationOpenTarget.ACCOUNT_SETTINGS -> actions.onOpenAccountSettings()

            NotificationOpenTarget.DETAIL -> if (layoutParameters.preferSinglePane) {
                onNavigateToDetail(notification.id)
            } else {
                selectedId = notification.id
                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, notification.id)
                }
            }
        }
    }

    fun performInlineAction(notification: AppNotification, action: NotificationInlineAction) {
        onOpenNotification(notification)
        when (action) {
            NotificationInlineAction.REAUTHORIZE_BANGUMI -> actions.onReauthorizeBangumi()
        }
    }

    // "全部标为已读" 作用于选中的标签页
    val selectedPage = pages.firstOrNull { it.group == selectedGroup }
    val newestCreatedAtMillis = selectedPage?.loadedItems?.maxOfOrNull { it.createdAtMillis }
    val canMarkAllRead = access == NotificationAccess.AUTHORIZED && selectedPage != null &&
            newestCreatedAtMillis != null &&
            (badgeState.unreadCountOf(selectedPage.group) > 0 || selectedPage.loadedUnreadCount() > 0)

    Surface(modifier, color = AniThemeDefaults.pageContentBackgroundColor) {
        AniListDetailPaneScaffold(
            navigator = navigator,
            listPaneTopAppBar = {
                AniTopAppBar(
                    title = { AniTopAppBarDefaults.Title(stringResource(Lang.notification_title)) },
                    navigationIcon = navigationIcon,
                    actions = {
                        IconButton(
                            onClick = {
                                if (selectedPage != null && newestCreatedAtMillis != null) {
                                    onMarkAllRead(selectedPage.group, newestCreatedAtMillis)
                                }
                            },
                            Modifier.testTag(NotificationTestTags.MARK_ALL_READ),
                            enabled = canMarkAllRead,
                        ) {
                            Icon(Icons.Rounded.DoneAll, stringResource(Lang.notification_mark_all_read))
                        }
                        IconButton(onNavigateToSettings, Modifier.testTag(NotificationTestTags.SETTINGS)) {
                            Icon(Icons.Rounded.Tune, stringResource(Lang.notification_settings))
                        }
                    },
                    windowInsets = paneContentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
            },
            listPaneContent = {
                // 只有列表行显示相对时间
                ProvideNotificationNow {
                    NotificationCenterListPane(
                        pages = pages,
                        selectedGroup = selectedGroup,
                        onSelectGroup = onSelectGroup,
                        access = access,
                        badgeState = badgeState,
                        selectedId = if (listDetailLayoutParameters.highlightSelectedItem) selectedId else null,
                        onClickNotification = ::openNotification,
                        onInlineAction = ::performInlineAction,
                        onClickAnnouncements = onNavigateToAnnouncements,
                        onRefresh = onRefresh,
                        onCheckUpdate = actions.onCheckUpdate,
                        onLogin = onLogin,
                    )
                }
            },
            detailPane = {
                val notification = selectedId?.let { id ->
                    pages.firstNotNullOfOrNull { page -> page.loadedItems.firstOrNull { it.id == id } }
                }
                // 与列表并排时, 举报结果的跳转按钮在顶栏; 只显示详情栏时与单独的详情页一样在底部
                val reportActionInTopBar = !isSinglePane
                NotificationDetailPaneLayout(
                    title = notification?.let { notificationDetailTitle(it.content) }.orEmpty(),
                    onNavigateBack = if (isSinglePane && navigator.canNavigateBack()) {
                        {
                            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                navigator.navigateBack()
                            }
                        }
                    } else {
                        null
                    },
                    actions = {
                        if (notification != null && reportActionInTopBar) {
                            NotificationDetailTopBarActions(notification, actions)
                        }
                    },
                ) {
                    if (notification == null) {
                        // 列表为空时没有可选的通知, 详情栏留空
                        if (!selectedPage?.loadedItems.isNullOrEmpty()) NotificationDetailPanePlaceholder(
                            Icons.Outlined.Notifications,
                            stringResource(Lang.notification_detail_placeholder),
                            Modifier.testTag(NotificationTestTags.DETAIL_PLACEHOLDER),
                        )
                    } else {
                        NotificationDetailContent(
                            notification,
                            actions,
                            Modifier.paneContentPadding().paneWindowInsetsPadding(),
                            reportActionInTopBar = reportActionInTopBar,
                        )
                    }
                }
            },
            contentWindowInsets = windowInsets,
        )
    }
}

/**
 * 列表栏: 公告入口和标签页固定在顶部, 下面是左右滑动切换的各个标签页的列表. 只有列表滚动.
 */
@Composable
private fun PaneScope.NotificationCenterListPane(
    pages: List<NotificationCenterPage>,
    selectedGroup: NotificationGroup,
    onSelectGroup: (NotificationGroup) -> Unit,
    access: NotificationAccess,
    badgeState: NotificationBadgeState,
    selectedId: String?,
    onClickNotification: (AppNotification) -> Unit,
    onInlineAction: (AppNotification, NotificationInlineAction) -> Unit,
    onClickAnnouncements: () -> Unit,
    onRefresh: (NotificationGroup) -> Unit,
    onCheckUpdate: () -> Unit,
    onLogin: () -> Unit,
) {
    val showTabs = pages.size > 1
    val selectedIndex = pages.indexOfFirst { it.group == selectedGroup }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { pages.size }
    val uiScope = rememberCoroutineScope()

    // 滑动切换标签页
    val currentPages by rememberUpdatedState(pages)
    val currentOnSelectGroup by rememberUpdatedState(onSelectGroup)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            currentPages.getOrNull(page)?.let { currentOnSelectGroup(it.group) }
        }
    }
    // 滚动停下时以实际显示的页为准: 点击标签的动画被拖动打断后会回弹到原来的页, 此时 currentPage 没有变化.
    // 滚动中发出 null, 这样停在同一页时也会再次发出.
    LaunchedEffect(pagerState) {
        snapshotFlow { if (pagerState.isScrollInProgress) null else pagerState.currentPage }
            .filterNotNull()
            .collect { page -> currentPages.getOrNull(page)?.let { currentOnSelectGroup(it.group) } }
    }

    val listStates = pages.map { page -> key(page.group) { rememberLazyListState() } }

    // 窄屏时列表项贴边, 列表项自带 16dp 的内边距; 宽屏时列表项需要与边缘留出空间来显示选中背景.
    val rowInset = if (isSinglePane) (-16).dp else 0.dp
    Column(
        Modifier
            .paneContentPadding(extraStart = rowInset, extraEnd = rowInset)
            .paneWindowInsetsPadding()
            .fillMaxSize(),
    ) {
        badgeState.latestAnnouncement?.let { announcement ->
            AnnouncementEntryItem(
                announcement,
                hasUnread = badgeState.unreadAnnouncementCount > 0 || badgeState.hasUnreadAnnouncements,
                onClick = onClickAnnouncements,
            )
            // 显示标签页时, 由标签页底部的分隔线与公告入口分开
            if (!showTabs) {
                NotificationListDivider()
                if (!isSinglePane) {
                    // 双栏的列表项有选中背景, 与分隔线之间留出空隙
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        if (showTabs) {
            NotificationGroupTabRow(
                groups = pages.map { it.group },
                selectedIndex = selectedIndex,
                pagerState = pagerState,
                unreadCountOf = { badgeState.unreadCountOf(it) },
                onClickTab = { index ->
                    onSelectGroup(pages[index].group)
                    uiScope.launch { pagerState.animateScrollToPage(index) }
                },
            )
        }

        HorizontalPager(
            pagerState,
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .touchHorizontalScrollOnly(),
            verticalAlignment = Alignment.Top,
            key = { pages[it].group },
        ) { index ->
            val page = pages[index]
            NotificationCenterPageList(
                page = page,
                listState = listStates[index],
                access = access,
                // 双栏的列表项有选中背景, 与标签页的分隔线之间留出空隙
                topPadding = if (showTabs && !isSinglePane) 8.dp else 0.dp,
                selectedId = selectedId,
                onClickNotification = onClickNotification,
                onInlineAction = onInlineAction,
                onRefresh = { onRefresh(page.group) },
                onCheckUpdate = onCheckUpdate,
                onLogin = onLogin,
            )
        }
    }
}

@Composable
private fun PaneScope.NotificationCenterPageList(
    page: NotificationCenterPage,
    listState: LazyListState,
    access: NotificationAccess,
    topPadding: Dp,
    selectedId: String?,
    onClickNotification: (AppNotification) -> Unit,
    onInlineAction: (AppNotification, NotificationInlineAction) -> Unit,
    onRefresh: () -> Unit,
    onCheckUpdate: () -> Unit,
    onLogin: () -> Unit,
) {
    val items = page.items
    val isRefreshing = items.loadState.refresh is LoadState.Loading && items.itemCount > 0
    val hasUnknownTypes by remember(items) {
        derivedStateOf { items.itemSnapshotList.items.any { it.isUnknownType } }
    }
    KeepAtTopWhenFirstItemChanges(
        listState,
        firstItemKey = when {
            access != NotificationAccess.AUTHORIZED || items.itemCount == 0 -> null
            hasUnknownTypes -> UpdateBannerKey
            else -> items.peek(0)?.id
        },
    )
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        Modifier.fillMaxSize(),
        enabled = access == NotificationAccess.AUTHORIZED,
        touchOnly = true,
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .testTag(NotificationTestTags.centerList(page.group)),
            state = listState,
            contentPadding = PaddingValues(top = topPadding, bottom = 16.dp),
        ) {
            notificationItems(
                group = page.group,
                items = items,
                access = access,
                readOverlay = page.readOverlay,
                singlePane = isSinglePane,
                selectedId = selectedId,
                showUpdateBanner = hasUnknownTypes,
                onClickNotification = onClickNotification,
                onInlineAction = onInlineAction,
                onCheckUpdate = onCheckUpdate,
                onLogin = onLogin,
            )
        }
    }
}

private const val UpdateBannerKey = "update-banner"

/**
 * LazyColumn 在最前面插入内容时让原来的第一项保持在原处, 插入的内容在视口上方. 重新加载后出现的新通知和更新提示都在最前面,
 * 所以列表停在顶部时, 第一项变化后仍然停在顶部, 让它们显示出来.
 *
 * @param firstItemKey 列表第一项的 key.
 */
@Composable
private fun KeepAtTopWhenFirstItemChanges(listState: LazyListState, firstItemKey: Any?) {
    val lastFirstItemKey = remember(listState) { arrayOfNulls<Any>(1) }
    SideEffect {
        if (lastFirstItemKey[0] == firstItemKey) return@SideEffect
        lastFirstItemKey[0] = firstItemKey
        // 此时列表还没有按新的内容测量, 读到的仍是变化前的位置
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            listState.requestScrollToItem(0)
        }
    }
}

/**
 * 标签页. 选中的标签和指示器使用主色, 指示器与标签内容等宽, 并跟随列表的左右滑动移动.
 * 有未读通知的标签在文字后显示未读数.
 */
@Composable
private fun NotificationGroupTabRow(
    groups: List<NotificationGroup>,
    selectedIndex: Int,
    pagerState: PagerState,
    unreadCountOf: (NotificationGroup) -> Int,
    onClickTab: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    PrimaryTabRow(
        selectedTabIndex = selectedIndex,
        modifier
            .fillMaxWidth()
            .testTag(NotificationTestTags.TAB_ROW),
        containerColor = Color.Transparent,
        contentColor = colorScheme.onSurfaceVariant,
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                pagerTabIndicatorModifier(pagerState),
                width = Dp.Unspecified,
                color = colorScheme.primary,
            )
        },
        divider = { HorizontalDivider(color = colorScheme.outlineVariant) },
    ) {
        groups.forEachIndexed { index, group ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onClickTab(index) },
                Modifier.testTag(NotificationTestTags.tab(group)),
                text = { NotificationGroupTabLabel(stringResource(group.labelRes), unreadCountOf(group)) },
                selectedContentColor = colorScheme.primary,
                unselectedContentColor = colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 标签文字和未读数. 读屏时未读数读作 "N 条未读", 而不是徽标上的数字.
 */
@Composable
private fun NotificationGroupTabLabel(
    label: String,
    unreadCount: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, softWrap = false)
        if (unreadCount > 0) {
            val description = stringResource(Lang.notification_unread_count, unreadCount)
            Spacer(Modifier.width(6.dp))
            Badge(
                Modifier
                    .testTag(NotificationTestTags.TAB_BADGE)
                    .clearAndSetSemantics { contentDescription = description },
            ) {
                Text(notificationBadgeText(unreadCount))
            }
        }
    }
}

/**
 * 徽标上的未读数. 超过 99 时显示 "99+".
 */
internal fun notificationBadgeText(count: Int): String = if (count > 99) "99+" else count.toString()

/**
 * 与 [TabIndicatorScope.tabIndicatorOffset] 一样与标签内容等宽, 但位置和宽度跟随 [pagerState] 的滑动进度.
 */
private fun TabIndicatorScope.pagerTabIndicatorModifier(pagerState: PagerState): Modifier =
    Modifier.tabIndicatorLayout { measurable, constraints, tabPositions ->
        if (tabPositions.isEmpty()) return@tabIndicatorLayout layout(0, 0) {}
        val position = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
            .coerceIn(0f, tabPositions.lastIndex.toFloat())
        val from = tabPositions[floor(position).toInt()]
        val to = tabPositions[ceil(position).toInt()]
        val fraction = position - floor(position)
        val width = lerp(from.contentWidth, to.contentWidth, fraction).roundToPx()
        val offset = lerp(from.left, to.left, fraction).roundToPx()
        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        // 比标签窄, 所以在标签宽度内居中
        layout(placeable.width, placeable.height) { placeable.placeRelative(offset, 0) }
    }

/**
 * @param showUpdateBanner 在列表顶部显示 [NotificationUpdateBanner].
 */
private fun LazyListScope.notificationItems(
    group: NotificationGroup,
    items: LazyPagingItems<AppNotification>,
    access: NotificationAccess,
    readOverlay: ReadOverlay,
    singlePane: Boolean,
    selectedId: String?,
    showUpdateBanner: Boolean,
    onClickNotification: (AppNotification) -> Unit,
    onInlineAction: (AppNotification, NotificationInlineAction) -> Unit,
    onCheckUpdate: () -> Unit,
    onLogin: () -> Unit,
) {
    when (access) {
        NotificationAccess.GUEST -> {
            item(key = "login", contentType = "error") {
                LoadErrorCard(
                    LoadError.RequiresLogin,
                    onRetry = {},
                    Modifier.padding(16.dp),
                    onLogin = onLogin,
                )
            }
            return
        }

        NotificationAccess.LOADING -> {
            placeholderItems()
            return
        }

        NotificationAccess.AUTHORIZED -> {}
    }

    if (items.itemCount == 0) {
        when {
            items.loadState.refresh is LoadState.Error -> item(key = "error", contentType = "error") {
                val error by items.rememberLoadErrorState()
                LoadErrorCard(
                    error,
                    onRetry = { items.retry() },
                    Modifier.padding(16.dp),
                    onLogin = onLogin,
                )
            }

            items.isLoadingFirstPage || items.loadState.refresh is LoadState.Loading -> placeholderItems()

            else -> notificationEmptyStateItem(
                icon = group.emptyIcon,
                title = group.emptyTextRes,
                supportingText = group.emptySupportingTextRes,
                testTag = NotificationTestTags.EMPTY,
            )
        }
        return
    }

    if (showUpdateBanner) {
        item(key = UpdateBannerKey, contentType = "update-banner") {
            NotificationUpdateBanner(
                onUpdate = onCheckUpdate,
                // 单栏时列表项贴边, 提示与列表项的文字对齐; 双栏时与列表项的选中背景对齐
                Modifier
                    .padding(horizontal = if (singlePane) NotificationListItemDefaults.HorizontalPadding else 0.dp)
                    .padding(top = if (singlePane) 8.dp else 0.dp, bottom = 8.dp),
            )
        }
    }

    items(
        items.itemCount,
        key = items.itemKey { it.id },
        contentType = { "notification" },
    ) { index ->
        val notification = items[index]
        if (notification == null) {
            NotificationListItemPlaceholder()
        } else {
            NotificationRow(
                notification,
                read = readOverlay.isRead(notification),
                selected = notification.id == selectedId,
                onClick = { onClickNotification(notification) },
                onInlineAction = { onInlineAction(notification, it) },
            )
        }
        if (index < items.itemCount - 1) {
            NotificationListItemSeparator(singlePane)
        }
    }

    when (items.loadState.append) {
        is LoadState.Error -> item(key = "append-error", contentType = "error") {
            val error by items.rememberLoadErrorState()
            LoadErrorCard(
                error,
                onRetry = { items.retry() },
                Modifier.padding(16.dp),
                onLogin = onLogin,
            )
        }

        is LoadState.Loading -> item(key = "append-loading", contentType = "placeholder") {
            NotificationListItemPlaceholder()
        }

        is LoadState.NotLoading -> {}
    }
}

private fun LazyListScope.placeholderItems() {
    items(6, key = { "placeholder-$it" }, contentType = { "placeholder" }) {
        NotificationListItemPlaceholder()
    }
}

@Composable
private fun NotificationRow(
    notification: AppNotification,
    read: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onInlineAction: (NotificationInlineAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val useOriginalTitle = LocalSubjectAppearanceSettings.current.useOriginalTitle
    val rowText = remember(notification, useOriginalTitle) {
        notificationRowText(notification, useOriginalTitle)
    }
    val inlineActions = remember(notification.content) { notificationInlineActions(notification.content) }
    NotificationListItem(
        headline = rowText.headline.resolve(),
        supporting = rowText.supporting?.resolve(),
        time = formatNotificationRowTime(notification.createdAtMillis),
        icon = NotificationListItemDefaults.iconFor(notification),
        read = read,
        onClick = onClick,
        modifier = modifier.testTag(NotificationTestTags.item(notification.id)),
        selected = selected,
        overline = rowText.overline?.resolve(),
        imageUrl = NotificationListItemDefaults.imageUrlFor(notification),
        actions = if (inlineActions.isEmpty()) {
            null
        } else {
            {
                for (action in inlineActions) {
                    NotificationInlineActionButton(
                        stringResource(action.labelRes),
                        onClick = { onInlineAction(action) },
                        Modifier.testTag(NotificationTestTags.INLINE_ACTION),
                    )
                }
            }
        },
    )
}

val NotificationGroup.labelRes: StringResource
    get() = when (this) {
        NotificationGroup.INTERACTION -> Lang.notification_group_interaction
        NotificationGroup.NOTICE -> Lang.notification_group_notice
    }

private val NotificationGroup.emptyIcon: ImageVector
    get() = when (this) {
        NotificationGroup.INTERACTION -> Icons.Outlined.Forum
        NotificationGroup.NOTICE -> Icons.Outlined.Notifications
    }

private val NotificationGroup.emptyTextRes: StringResource
    get() = when (this) {
        NotificationGroup.INTERACTION -> Lang.notification_interaction_empty
        NotificationGroup.NOTICE -> Lang.notification_empty
    }

private val NotificationGroup.emptySupportingTextRes: StringResource
    get() = when (this) {
        NotificationGroup.INTERACTION -> Lang.notification_interaction_empty_supporting
        NotificationGroup.NOTICE -> Lang.notification_empty_supporting
    }
