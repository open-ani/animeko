/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.notification

import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.models.notification.Announcement
import me.him188.ani.app.data.models.notification.AppNotification
import me.him188.ani.app.data.models.notification.NotificationBadgeState
import me.him188.ani.app.data.models.notification.NotificationGroup

import me.him188.ani.app.data.repository.notification.NotificationRepository
import me.him188.ani.app.domain.notification.NotificationBadgeManager
import me.him188.ani.app.domain.session.InvalidSessionReason
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * 与真实的 `Pager` 一样在加载完成后发出 [LoadState.NotLoading]. 不带状态的 `PagingData.from` 会让列表一直处于首次加载中.
 */
private val AllLoaded = LoadStates(
    refresh = LoadState.NotLoading(endOfPaginationReached = true),
    prepend = LoadState.NotLoading(endOfPaginationReached = true),
    append = LoadState.NotLoading(endOfPaginationReached = true),
)

/**
 * @param notifications 初始的通知. 之后可以修改 [notifications], 刷新列表后生效.
 */
class FakeNotificationRepository(
    notifications: List<AppNotification> = TestNotifications,
    private val announcements: List<Announcement> = TestAnnouncements,
) : NotificationRepository {
    @Volatile
    var notifications: List<AppNotification> = notifications

    val markedRead = CopyOnWriteArrayList<List<String>>()
    val markAllCalls = CopyOnWriteArrayList<Pair<NotificationGroup, Long?>>()
    val markedAnnouncementsRead = CopyOnWriteArrayList<List<String>>()
    val markAllAnnouncementsCalls = AtomicInteger()
    val markAllAnnouncementsBefore = CopyOnWriteArrayList<Long?>()

    /**
     * 通知列表的加载次数. 首次加载和每次刷新各一次.
     */
    val notificationLoads = AtomicInteger()

    /**
     * 与真实的分页一样可以刷新: 每次加载都读取当时的 [notifications], 全部放在一页中.
     */
    override fun notificationsPager(group: NotificationGroup): Flow<PagingData<AppNotification>> =
        Pager(PagingConfig(pageSize = 30)) {
            object : PagingSource<Int, AppNotification>() {
                override fun getRefreshKey(state: PagingState<Int, AppNotification>): Int? = null

                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AppNotification> {
                    notificationLoads.incrementAndGet()
                    return LoadResult.Page(
                        notifications.filter { it.type in group.types },
                        prevKey = null,
                        nextKey = null,
                    )
                }
            }
        }.flow

    override suspend fun getNotification(id: String): AppNotification? =
        notifications.firstOrNull { it.id == id }

    override suspend fun markNotificationsRead(ids: List<String>) {
        markedRead += ids
    }

    override suspend fun markAllNotificationsRead(group: NotificationGroup, beforeMillis: Long?) {
        markAllCalls += group to beforeMillis
    }

    override fun announcementsPager(): Flow<PagingData<Announcement>> =
        flowOf(PagingData.from(announcements, AllLoaded))

    override suspend fun getAnnouncement(id: String): Announcement? =
        announcements.firstOrNull { it.id == id }

    override suspend fun markAnnouncementsRead(ids: List<String>) {
        markedAnnouncementsRead += ids
    }

    override suspend fun markAllAnnouncementsRead(beforeMillis: Long?) {
        markAllAnnouncementsBefore += beforeMillis
        markAllAnnouncementsCalls.incrementAndGet()
    }
}

class FakeNotificationBadgeManager(
    initial: NotificationBadgeState = TestNotificationBadgeState,
) : NotificationBadgeManager {
    override val state: MutableStateFlow<NotificationBadgeState> = MutableStateFlow(initial)
    val refreshRequests = AtomicInteger()
    val foregroundChanges = CopyOnWriteArrayList<Boolean>()

    override fun requestRefresh() {
        refreshRequests.incrementAndGet()
    }

    override fun setAppForeground(foreground: Boolean) {
        foregroundChanges += foreground
    }
}

class FakeSessionStateProvider(
    initial: SessionState = SessionState.Valid(bangumiConnected = true),
) : SessionStateProvider {
    override val stateFlow: MutableStateFlow<SessionState> = MutableStateFlow(initial)
    override val eventFlow: Flow<SessionEvent> = emptyFlow()

    companion object {
        val Guest get() = FakeSessionStateProvider(SessionState.Invalid(InvalidSessionReason.NO_TOKEN))
    }
}

fun notificationTestKoin(
    repository: NotificationRepository = FakeNotificationRepository(),
    badgeManager: NotificationBadgeManager = FakeNotificationBadgeManager(),
    sessionStateProvider: SessionStateProvider = FakeSessionStateProvider(),
): Koin = koinApplication {
    modules(
        module {
            single<NotificationRepository> { repository }
            single<NotificationBadgeManager> { badgeManager }
            single<SessionStateProvider> { sessionStateProvider }
        },
    )
}.koin

/**
 * 预览环境, 并把时间固定为 [TestNotificationTimeContext].
 */
@Composable
fun NotificationTestHost(content: @Composable () -> Unit) {
    ProvideCompositionLocalsForPreview {
        CompositionLocalProvider(LocalNotificationTimeContext provides TestNotificationTimeContext) {
            content()
        }
    }
}

/**
 * 单栏 (手机) 布局, 与测试窗口大小无关.
 */
@Composable
fun rememberSinglePaneNavigator(): ThreePaneScaffoldNavigator<String> =
    rememberListDetailPaneScaffoldNavigator<String>(scaffoldDirective = PaneScaffoldDirective.Default)

/**
 * 双栏 (宽屏) 布局, 与测试窗口大小无关.
 */
@Composable
fun rememberTwoPaneNavigator(): ThreePaneScaffoldNavigator<String> =
    rememberListDetailPaneScaffoldNavigator<String>(
        scaffoldDirective = PaneScaffoldDirective.Default.copy(
            maxHorizontalPartitions = 2,
            horizontalPartitionSpacerSize = 24.dp,
        ),
    )

/**
 * 在 [locale] 下执行 [block], 之后恢复原来的 locale.
 */
inline fun <T> withLocale(locale: Locale, block: () -> T): T {
    val previous = Locale.getDefault()
    Locale.setDefault(locale)
    try {
        return block()
    } finally {
        Locale.setDefault(previous)
    }
}

/**
 * 在简体中文环境下执行 [block], 之后恢复原来的 locale.
 */
inline fun <T> withSimplifiedChinese(block: () -> T): T = withLocale(Locale.SIMPLIFIED_CHINESE, block)
