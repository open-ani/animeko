/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import androidx.paging.compose.itemKey
import kotlinx.coroutines.CoroutineStart
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
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.rememberAsyncHandler
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.PullToRefreshBox
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.notification_detail_placeholder
import me.him188.ani.app.ui.lang.notification_empty
import me.him188.ani.app.ui.lang.notification_group_notice
import me.him188.ani.app.ui.lang.notification_mark_all_read
import me.him188.ani.app.ui.lang.notification_settings
import me.him188.ani.app.ui.lang.notification_title
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.search.isLoadingFirstPage
import me.him188.ani.app.ui.search.rememberLoadErrorState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * 通知中心.
 *
 * 窄屏只显示列表, 点击通知时调用 [onNavigateToDetail] 打开单独的详情页; 宽屏为双栏, 详情显示在右侧.
 * 打开一条通知会立即把它标为已读.
 *
 * 列表不会自动加载新到的通知, 而未读数由后台定时刷新. 所以 "全部标为已读" 之后, 以及未读数增加到超过列表中的未读通知时,
 * 重新加载列表, 让新通知显示出来: "全部标为已读" 只标记已加载的通知.
 *
 * @param onNavigateToDetail 窄屏打开通知详情.
 * @param onLogin 游客点击登录.
 * @param detailActions 宽屏详情栏中的跳转.
 */
@Composable
fun NotificationCenterScreen(
    vm: NotificationCenterViewModel,
    onNavigateToDetail: (notificationId: String) -> Unit,
    onNavigateToAnnouncements: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogin: () -> Unit,
    detailActions: NotificationDetailActions,
    modifier: Modifier = Modifier,
    navigator: ThreePaneScaffoldNavigator<String> = rememberListDetailPaneScaffoldNavigator<String>(),
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val items = vm.notificationsPager.collectAsLazyPagingItemsWithLifecycle()
    val access by vm.access.collectAsStateWithLifecycle()
    val readOverlay by vm.readOverlay.collectAsStateWithLifecycle()
    val badgeState by vm.badgeState.collectAsStateWithLifecycle()
    val selectedGroup by vm.selectedGroup.collectAsStateWithLifecycle()
    val asyncHandler = rememberAsyncHandler()

    var lastUnreadCount by remember { mutableIntStateOf(badgeState.unreadCount) }
    LaunchedEffect(badgeState.unreadCount) {
        val unreadCount = badgeState.unreadCount
        val increased = unreadCount > lastUnreadCount
        lastUnreadCount = unreadCount
        if (increased &&
            access == NotificationAccess.AUTHORIZED &&
            items.loadState.refresh is LoadState.NotLoading &&
            unreadCount > items.itemSnapshotList.items.count { !readOverlay.isRead(it) }
        ) {
            items.refresh()
        }
    }

    NotificationCenterScreen(
        items = items,
        access = access,
        readOverlay = readOverlay,
        badgeState = badgeState,
        groups = vm.groups,
        selectedGroup = selectedGroup,
        onSelectGroup = vm::selectGroup,
        onOpenNotification = vm::markRead,
        onMarkAllRead = { newestCreatedAtMillis ->
            asyncHandler.launch {
                vm.markAllRead(newestCreatedAtMillis)
                items.refresh()
            }
        },
        onRefresh = {
            items.refresh()
            vm.refreshBadge()
        },
        onNavigateToDetail = onNavigateToDetail,
        onNavigateToAnnouncements = onNavigateToAnnouncements,
        onNavigateToSettings = onNavigateToSettings,
        onLogin = onLogin,
        detailActions = detailActions,
        modifier = modifier,
        navigator = navigator,
        navigationIcon = navigationIcon,
        windowInsets = windowInsets,
    )
}

/**
 * @param onOpenNotification 打开一条通知时调用 (用于标为已读), 之后再导航到详情.
 * @param onMarkAllRead 参数为已加载的最新一条通知的创建时间.
 */
@Composable
fun NotificationCenterScreen(
    items: LazyPagingItems<AppNotification>,
    access: NotificationAccess,
    readOverlay: ReadOverlay,
    badgeState: NotificationBadgeState,
    groups: List<NotificationGroup>,
    selectedGroup: NotificationGroup,
    onSelectGroup: (NotificationGroup) -> Unit,
    onOpenNotification: (AppNotification) -> Unit,
    onMarkAllRead: (newestCreatedAtMillis: Long) -> Unit,
    onRefresh: () -> Unit,
    onNavigateToDetail: (notificationId: String) -> Unit,
    onNavigateToAnnouncements: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogin: () -> Unit,
    detailActions: NotificationDetailActions,
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
        if (layoutParameters.preferSinglePane) {
            onNavigateToDetail(notification.id)
        } else {
            selectedId = notification.id
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, notification.id)
            }
        }
    }

    val loadedItems = items.itemSnapshotList.items
    val newestCreatedAtMillis = loadedItems.maxOfOrNull { it.createdAtMillis }
    val canMarkAllRead = access == NotificationAccess.AUTHORIZED && newestCreatedAtMillis != null &&
            (badgeState.unreadCount > 0 || loadedItems.any { !readOverlay.isRead(it) })

    Surface(modifier, color = AniThemeDefaults.pageContentBackgroundColor) {
        AniListDetailPaneScaffold(
            navigator = navigator,
            listPaneTopAppBar = {
                AniTopAppBar(
                    title = { AniTopAppBarDefaults.Title(stringResource(Lang.notification_title)) },
                    navigationIcon = navigationIcon,
                    actions = {
                        IconButton(
                            onClick = { newestCreatedAtMillis?.let(onMarkAllRead) },
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
                    NotificationCenterList(
                        items = items,
                        access = access,
                        readOverlay = readOverlay,
                        badgeState = badgeState,
                        groups = groups,
                        selectedGroup = selectedGroup,
                        onSelectGroup = onSelectGroup,
                        selectedId = if (listDetailLayoutParameters.highlightSelectedItem) selectedId else null,
                        onClickNotification = ::openNotification,
                        onClickAnnouncements = onNavigateToAnnouncements,
                        onRefresh = onRefresh,
                        onLogin = onLogin,
                    )
                }
            },
            detailPane = {
                val notification = selectedId?.let { id -> loadedItems.firstOrNull { it.id == id } }
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
                ) {
                    if (notification == null) {
                        NotificationHintText(
                            stringResource(Lang.notification_detail_placeholder),
                            Modifier.testTag(NotificationTestTags.DETAIL_PLACEHOLDER),
                        )
                    } else {
                        NotificationDetailContent(
                            notification,
                            detailActions,
                            Modifier.paneContentPadding().paneWindowInsetsPadding(),
                        )
                    }
                }
            },
            contentWindowInsets = windowInsets,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaneScope.NotificationCenterList(
    items: LazyPagingItems<AppNotification>,
    access: NotificationAccess,
    readOverlay: ReadOverlay,
    badgeState: NotificationBadgeState,
    groups: List<NotificationGroup>,
    selectedGroup: NotificationGroup,
    onSelectGroup: (NotificationGroup) -> Unit,
    selectedId: String?,
    onClickNotification: (AppNotification) -> Unit,
    onClickAnnouncements: () -> Unit,
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
) {
    val isRefreshing = items.loadState.refresh is LoadState.Loading && items.itemCount > 0
    // 窄屏时列表项贴边, 列表项自带 16dp 的内边距; 宽屏时列表项需要与边缘留出空间来显示选中背景.
    val rowInset = if (isSinglePane) (-16).dp else 0.dp
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        Modifier.fillMaxSize(),
        enabled = access == NotificationAccess.AUTHORIZED,
        touchOnly = true,
    ) {
        LazyColumn(
            Modifier
                .paneContentPadding(extraStart = rowInset, extraEnd = rowInset)
                .paneWindowInsetsPadding()
                .fillMaxSize()
                .testTag(NotificationTestTags.CENTER_LIST),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            badgeState.latestAnnouncement?.let { announcement ->
                item(key = "announcement-entry", contentType = "announcement-entry") {
                    AnnouncementEntryItem(
                        announcement,
                        hasUnread = badgeState.unreadAnnouncementCount > 0 || badgeState.hasUnreadAnnouncements,
                        onClick = onClickAnnouncements,
                    )
                    NotificationListDivider()
                    if (!isSinglePane) {
                        // 双栏的列表项有选中背景, 与分隔线之间留出空隙
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            if (groups.size > 1) {
                item(key = "groups", contentType = "groups") {
                    SecondaryTabRow(
                        selectedTabIndex = groups.indexOf(selectedGroup).coerceAtLeast(0),
                        Modifier.fillMaxWidth(),
                    ) {
                        for (group in groups) {
                            Tab(
                                selected = group == selectedGroup,
                                onClick = { onSelectGroup(group) },
                                text = { Text(stringResource(group.labelRes)) },
                            )
                        }
                    }
                }
            }

            notificationItems(
                items = items,
                access = access,
                readOverlay = readOverlay,
                singlePane = isSinglePane,
                selectedId = selectedId,
                onClickNotification = onClickNotification,
                onLogin = onLogin,
            )
        }
    }
}

private fun LazyListScope.notificationItems(
    items: LazyPagingItems<AppNotification>,
    access: NotificationAccess,
    readOverlay: ReadOverlay,
    singlePane: Boolean,
    selectedId: String?,
    onClickNotification: (AppNotification) -> Unit,
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

            else -> item(key = "empty", contentType = "empty") {
                NotificationHintText(
                    stringResource(Lang.notification_empty),
                    Modifier.testTag(NotificationTestTags.EMPTY),
                )
            }
        }
        return
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
    modifier: Modifier = Modifier,
) {
    val useOriginalTitle = LocalSubjectAppearanceSettings.current.useOriginalTitle
    val rowText = remember(notification.content, useOriginalTitle) {
        notificationRowText(notification.content, useOriginalTitle)
    }
    NotificationListItem(
        headline = rowText.headline.resolve(),
        supporting = rowText.supporting?.resolve(),
        time = formatNotificationRowTime(notification.createdAtMillis),
        icon = NotificationListItemDefaults.iconFor(notification.type),
        read = read,
        onClick = onClick,
        modifier = modifier.testTag(NotificationTestTags.item(notification.id)),
        selected = selected,
    )
}

val NotificationGroup.labelRes: StringResource
    get() = when (this) {
        NotificationGroup.NOTICE -> Lang.notification_group_notice
    }
