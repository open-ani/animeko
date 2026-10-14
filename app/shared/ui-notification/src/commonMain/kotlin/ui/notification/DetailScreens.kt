/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.him188.ani.app.data.models.notification.Announcement
import me.him188.ani.app.data.models.notification.AppNotification
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.notification_announcement_detail_title
import me.him188.ani.app.ui.lang.notification_announcement_not_found
import me.him188.ani.app.ui.lang.notification_not_found
import me.him188.ani.app.ui.lang.notification_title
import me.him188.ani.app.ui.search.LoadErrorCard
import org.jetbrains.compose.resources.stringResource

/**
 * 单独打开的通知详情页 (窄屏).
 */
@Composable
fun NotificationDetailScreen(
    vm: NotificationDetailViewModel,
    actions: NotificationActions,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    NotificationDetailScreen(
        state = state,
        actions = actions,
        onRetry = vm::reload,
        modifier = modifier,
        navigationIcon = navigationIcon,
        windowInsets = windowInsets,
    )
}

@Composable
fun NotificationDetailScreen(
    state: DetailLoadState<AppNotification>,
    actions: NotificationActions,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val title = when (state) {
        is DetailLoadState.Loaded -> notificationDetailTitle(state.value.content)
        else -> stringResource(Lang.notification_title)
    }
    DetailScaffold(
        title = title,
        state = state,
        notFoundText = stringResource(Lang.notification_not_found),
        notFoundTestTag = NotificationTestTags.DETAIL_NOT_FOUND,
        onRetry = onRetry,
        modifier = modifier,
        navigationIcon = navigationIcon,
        windowInsets = windowInsets,
    ) { notification, contentModifier ->
        NotificationDetailContent(notification, actions, contentModifier)
    }
}

/**
 * 单独打开的公告详情页 (窄屏).
 */
@Composable
fun AnnouncementDetailScreen(
    vm: AnnouncementDetailViewModel,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    AnnouncementDetailScreen(
        state = state,
        onRetry = vm::reload,
        modifier = modifier,
        navigationIcon = navigationIcon,
        windowInsets = windowInsets,
    )
}

@Composable
fun AnnouncementDetailScreen(
    state: DetailLoadState<Announcement>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    DetailScaffold(
        title = stringResource(Lang.notification_announcement_detail_title),
        state = state,
        notFoundText = stringResource(Lang.notification_announcement_not_found),
        notFoundTestTag = AnnouncementTestTags.DETAIL_NOT_FOUND,
        onRetry = onRetry,
        modifier = modifier,
        navigationIcon = navigationIcon,
        windowInsets = windowInsets,
    ) { announcement, contentModifier ->
        AnnouncementDetailContent(announcement, contentModifier)
    }
}

/**
 * @param notFoundText [DetailLoadState.NotFound] 时居中显示的提示. 重试也不会成功, 所以只有提示, 没有重试按钮.
 */
@Composable
private fun <T> DetailScaffold(
    title: String,
    state: DetailLoadState<T>,
    notFoundText: String,
    notFoundTestTag: String,
    onRetry: () -> Unit,
    modifier: Modifier,
    navigationIcon: @Composable () -> Unit,
    windowInsets: WindowInsets,
    content: @Composable (T, Modifier) -> Unit,
) {
    Scaffold(
        modifier,
        topBar = {
            AniTopAppBar(
                title = { AniTopAppBarDefaults.Title(title) },
                navigationIcon = navigationIcon,
                colors = AniThemeDefaults.topAppBarColors(),
                windowInsets = AniWindowInsets.forTopAppBarWithoutDesktopTitle(),
            )
        },
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { paddingValues ->
        val contentModifier = Modifier.padding(paddingValues).padding(horizontal = 16.dp)
        when (state) {
            DetailLoadState.Loading -> Box(Modifier.padding(paddingValues).fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            DetailLoadState.NotFound -> Box(Modifier.padding(paddingValues).fillMaxSize(), Alignment.Center) {
                NotificationEmptyState(
                    Icons.Outlined.SearchOff,
                    title = notFoundText,
                    Modifier.testTag(notFoundTestTag),
                )
            }

            is DetailLoadState.Failed -> LoadErrorCard(
                state.error,
                onRetry = onRetry,
                Modifier
                    .padding(paddingValues)
                    .padding(16.dp)
                    .detailContentWidth(),
            )

            is DetailLoadState.Loaded -> content(state.value, contentModifier)
        }
    }
}
