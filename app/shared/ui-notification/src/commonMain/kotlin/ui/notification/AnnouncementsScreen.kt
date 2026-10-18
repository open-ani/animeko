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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import androidx.paging.compose.itemKey
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.notification.Announcement
import me.him188.ani.app.ui.adaptive.AniListDetailPaneScaffold
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.adaptive.ListDetailLayoutParameters
import me.him188.ani.app.ui.adaptive.PaneScope
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.PullToRefreshBox
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.notification_announcement_detail_placeholder
import me.him188.ani.app.ui.lang.notification_announcement_detail_title
import me.him188.ani.app.ui.lang.notification_announcement_pinned
import me.him188.ani.app.ui.lang.notification_announcements_empty
import me.him188.ani.app.ui.lang.notification_announcements_empty_supporting
import me.him188.ani.app.ui.lang.notification_announcements_title
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.search.isLoadingFirstPage
import me.him188.ani.app.ui.search.rememberLoadErrorState
import org.jetbrains.compose.resources.stringResource

/**
 * 公告列表. 窄屏点击公告时调用 [onNavigateToDetail]; 宽屏为双栏, 详情显示在右侧.
 *
 * 登录用户进入列表即已读, 见 [AnnouncementsViewModel].
 */
@Composable
fun AnnouncementsScreen(
    vm: AnnouncementsViewModel,
    onNavigateToDetail: (announcementId: String) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    navigator: ThreePaneScaffoldNavigator<String> = rememberListDetailPaneScaffoldNavigator<String>(),
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val items = vm.announcementsPager.collectAsLazyPagingItemsWithLifecycle()
    val access by vm.access.collectAsStateWithLifecycle()
    val readOverlay by vm.readOverlay.collectAsStateWithLifecycle()

    val loadState = items.loadState
    LaunchedEffect(loadState) {
        if (loadState.refresh is LoadState.NotLoading) {
            vm.markLoadedAnnouncementsRead()
        }
    }

    AnnouncementsScreen(
        items = items,
        access = access,
        readOverlay = readOverlay,
        onOpenAnnouncement = vm::markRead,
        onRefresh = {
            items.refresh()
            vm.refreshBadge()
        },
        onNavigateToDetail = onNavigateToDetail,
        onLogin = onLogin,
        modifier = modifier,
        navigator = navigator,
        navigationIcon = navigationIcon,
        windowInsets = windowInsets,
    )
}

/**
 * @param onOpenAnnouncement 打开一条公告时调用 (用于标为已读), 之后再导航到详情.
 */
@Composable
fun AnnouncementsScreen(
    items: LazyPagingItems<Announcement>,
    access: NotificationAccess,
    readOverlay: ReadOverlay,
    onOpenAnnouncement: (Announcement) -> Unit,
    onRefresh: () -> Unit,
    onNavigateToDetail: (announcementId: String) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    navigator: ThreePaneScaffoldNavigator<String> = rememberListDetailPaneScaffoldNavigator<String>(),
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val coroutineScope = rememberCoroutineScope()
    val layoutParameters = ListDetailLayoutParameters.calculate(navigator.scaffoldDirective)
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    fun openAnnouncement(announcement: Announcement) {
        onOpenAnnouncement(announcement)
        if (layoutParameters.preferSinglePane) {
            onNavigateToDetail(announcement.id)
        } else {
            selectedId = announcement.id
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, announcement.id)
            }
        }
    }

    val loadedItems = items.itemSnapshotList.items

    Surface(modifier, color = AniThemeDefaults.pageContentBackgroundColor) {
        AniListDetailPaneScaffold(
            navigator = navigator,
            listPaneTopAppBar = {
                AniTopAppBar(
                    title = { AniTopAppBarDefaults.Title(stringResource(Lang.notification_announcements_title)) },
                    navigationIcon = navigationIcon,
                    windowInsets = paneContentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
            },
            listPaneContent = {
                AnnouncementList(
                    items = items,
                    access = access,
                    readOverlay = readOverlay,
                    selectedId = if (listDetailLayoutParameters.highlightSelectedItem) selectedId else null,
                    onClickAnnouncement = ::openAnnouncement,
                    onRefresh = onRefresh,
                    onLogin = onLogin,
                )
            },
            detailPane = {
                val announcement = selectedId?.let { id -> loadedItems.firstOrNull { it.id == id } }
                NotificationDetailPaneLayout(
                    title = stringResource(Lang.notification_announcement_detail_title),
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
                    if (announcement == null) {
                        // 列表为空时没有可选的公告, 详情栏留空
                        if (loadedItems.isNotEmpty()) NotificationDetailPanePlaceholder(
                            Icons.Outlined.Campaign,
                            stringResource(Lang.notification_announcement_detail_placeholder),
                            Modifier.testTag(AnnouncementTestTags.DETAIL_PLACEHOLDER),
                        )
                    } else {
                        AnnouncementDetailContent(
                            announcement,
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
private fun PaneScope.AnnouncementList(
    items: LazyPagingItems<Announcement>,
    access: NotificationAccess,
    readOverlay: ReadOverlay,
    selectedId: String?,
    onClickAnnouncement: (Announcement) -> Unit,
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
) {
    val isRefreshing = items.loadState.refresh is LoadState.Loading && items.itemCount > 0
    val rowInset = if (isSinglePane) (-16).dp else 0.dp
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        Modifier.fillMaxSize(),
        enabled = access != NotificationAccess.LOADING,
        touchOnly = true,
    ) {
        LazyColumn(
            Modifier
                .paneContentPadding(extraStart = rowInset, extraEnd = rowInset)
                .paneWindowInsetsPadding()
                .fillMaxSize()
                .testTag(AnnouncementTestTags.LIST),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            announcementItems(items, access, readOverlay, isSinglePane, selectedId, onClickAnnouncement, onLogin)
        }
    }
}

private fun LazyListScope.announcementItems(
    items: LazyPagingItems<Announcement>,
    access: NotificationAccess,
    readOverlay: ReadOverlay,
    singlePane: Boolean,
    selectedId: String?,
    onClickAnnouncement: (Announcement) -> Unit,
    onLogin: () -> Unit,
) {
    if (items.itemCount == 0) {
        when {
            access == NotificationAccess.LOADING -> placeholderItems()

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
                icon = Icons.Outlined.Campaign,
                title = Lang.notification_announcements_empty,
                supportingText = Lang.notification_announcements_empty_supporting,
                testTag = AnnouncementTestTags.EMPTY,
            )
        }
        return
    }

    items(
        items.itemCount,
        key = items.itemKey { it.id },
        contentType = { "announcement" },
    ) { index ->
        val announcement = items[index]
        if (announcement == null) {
            NotificationListItemPlaceholder()
        } else {
            AnnouncementListItem(
                announcement,
                read = readOverlay.isRead(announcement),
                onClick = { onClickAnnouncement(announcement) },
                modifier = Modifier.testTag(AnnouncementTestTags.item(announcement.id)),
                selected = announcement.id == selectedId,
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

/**
 * 一条公告的详情内容 (不含顶栏). 自带纵向滚动.
 */
@Composable
fun AnnouncementDetailContent(
    announcement: Announcement,
    modifier: Modifier = Modifier,
) {
    DetailContentColumn(AnnouncementTestTags.DETAIL, modifier) {
        LinkedTextDetail(
            title = announcement.title,
            timeMillis = announcement.publishedAtMillis,
            body = announcement.body,
            overline = { AnnouncementTagLabel(announcement) },
            action = announcement.linkUrl?.let { url -> { DetailLinkButton(url, AnnouncementTestTags.DETAIL_LINK) } },
        )
    }
}

/**
 * 公告分类, 置顶时前面有图钉图标.
 */
@Composable
internal fun AnnouncementTagLabel(
    announcement: Announcement,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (announcement.pinned) {
            Icon(
                Icons.Rounded.PushPin,
                contentDescription = stringResource(Lang.notification_announcement_pinned),
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            stringResource(announcement.tag.labelRes),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
