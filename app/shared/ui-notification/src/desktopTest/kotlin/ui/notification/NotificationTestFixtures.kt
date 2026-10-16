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

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.bottomCenter
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.topCenter
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.emptyPreferences
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import me.him188.ani.app.data.models.notification.Announcement
import me.him188.ani.app.data.models.notification.AppNotification
import me.him188.ani.app.data.models.notification.NotificationBadgeState
import me.him188.ani.app.data.models.notification.NotificationGroup
import me.him188.ani.app.data.models.notification.NotificationType
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.data.repository.notification.NotificationRepository
import me.him188.ani.app.data.repository.user.PreferencesRepositoryImpl
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.notification.NotificationBadgeManager
import me.him188.ani.app.domain.session.InvalidSessionReason
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.navigation.OpenBrowserResult
import me.him188.ani.app.platform.Context
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.input.ActiveInputSourceState
import me.him188.ani.app.ui.foundation.input.LocalActiveInputSource
import me.him188.ani.app.ui.foundation.input.trackActiveInputSource
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

/**
 * 一次通知列表的加载: 请求的分组和排除的类型.
 */
data class NotificationLoad(
    val group: NotificationGroup,
    val excludeTypes: Set<NotificationType>,
)

/**
 * 一次 "全部标为已读" 的请求.
 */
data class MarkAllCall(
    val group: NotificationGroup,
    val excludeTypes: Set<NotificationType>,
    val beforeMillis: Long?,
)

/**
 * 模拟服务端的通知和公告. 列表每次加载都读取当时的数据, 标记已读会修改数据中的已读状态, 与服务端一样.
 *
 * @param notifications 初始的通知, 按分类 ([AppNotification.rawCategory]) 出现在对应分组的列表中.
 * 之后可以修改 [notifications], 刷新列表后生效.
 * @param announcements 初始的公告, 按服务端的顺序排列 (置顶在前).
 * @param announcementPageSize 公告列表每页的条数.
 */
class FakeNotificationRepository(
    notifications: List<AppNotification> = TestNotifications,
    announcements: List<Announcement> = TestAnnouncements,
    private val announcementPageSize: Int = 30,
) : NotificationRepository {
    @Volatile
    var notifications: List<AppNotification> = notifications

    @Volatile
    var announcements: List<Announcement> = announcements

    val markedRead = CopyOnWriteArrayList<List<String>>()
    val markAllCalls = CopyOnWriteArrayList<MarkAllCall>()
    val markedAnnouncementsRead = CopyOnWriteArrayList<List<String>>()
    val markAllAnnouncementsBefore = CopyOnWriteArrayList<Long?>()

    /**
     * 接下来这么多次 [markAllAnnouncementsRead] 会以网络错误失败.
     */
    val markAllAnnouncementsFailures = AtomicInteger()

    /**
     * 每次加载通知列表时的请求. 每个分组的首次加载和每次刷新各一次.
     */
    val notificationLoadRequests = CopyOnWriteArrayList<NotificationLoad>()

    /**
     * 通知列表的加载次数, 包括所有分组.
     */
    val notificationLoads get() = notificationLoadRequests.size

    /**
     * [group] 的通知列表的加载次数.
     */
    fun notificationLoadsOf(group: NotificationGroup): Int = notificationLoadRequests.count { it.group == group }

    /**
     * 公告列表加载的页数. 首次加载、每次刷新和加载下一页各一次.
     */
    val announcementLoads = AtomicInteger()

    /**
     * 公告列表加载从某个位置开始的一页之前, 等待对应的 [CompletableDeferred] 完成. 用于控制下一页的加载时机.
     */
    val announcementPageGates = ConcurrentHashMap<Int, CompletableDeferred<Unit>>()

    @Volatile
    private var announcementsSource: PagingSource<Int, Announcement>? = null

    /**
     * 与真实的分页一样可以刷新: 每次加载都读取当时的 [notifications] 中属于 [group] 的通知, 全部放在一页中.
     */
    override fun notificationsPager(
        group: NotificationGroup,
        excludeTypes: Set<NotificationType>,
    ): Flow<PagingData<AppNotification>> =
        Pager(PagingConfig(pageSize = 30)) {
            object : PagingSource<Int, AppNotification>() {
                override fun getRefreshKey(state: PagingState<Int, AppNotification>): Int? = null

                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AppNotification> {
                    notificationLoadRequests += NotificationLoad(group, excludeTypes)
                    return LoadResult.Page(
                        notifications.filter { it.rawCategory == group.wireValue && it.type !in excludeTypes },
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

    override suspend fun markAllNotificationsRead(
        group: NotificationGroup,
        excludeTypes: Set<NotificationType>,
        beforeMillis: Long?,
    ) {
        markAllCalls += MarkAllCall(group, excludeTypes, beforeMillis)
    }

    /**
     * 按 [announcementPageSize] 分页, key 为起始位置.
     */
    override fun announcementsPager(): Flow<PagingData<Announcement>> =
        Pager(
            PagingConfig(
                pageSize = announcementPageSize,
                prefetchDistance = 1,
                initialLoadSize = announcementPageSize,
                enablePlaceholders = false,
            ),
        ) {
            object : PagingSource<Int, Announcement>() {
                override fun getRefreshKey(state: PagingState<Int, Announcement>): Int? = null

                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Announcement> {
                    val offset = params.key ?: 0
                    announcementPageGates[offset]?.await()
                    announcementLoads.incrementAndGet()
                    val all = announcements
                    val end = minOf(offset + announcementPageSize, all.size)
                    return LoadResult.Page(
                        all.subList(offset, end),
                        prevKey = null,
                        nextKey = end.takeIf { it < all.size },
                    )
                }
            }.also { announcementsSource = it }
        }.flow

    /**
     * 重新加载公告列表, 与下拉刷新相同.
     */
    fun refreshAnnouncements() {
        announcementsSource?.invalidate()
    }

    override suspend fun getAnnouncement(id: String): Announcement? =
        announcements.firstOrNull { it.id == id }

    override suspend fun markAnnouncementsRead(ids: List<String>) {
        markedAnnouncementsRead += ids
        announcements = announcements.map { if (it.id in ids && it.read != null) it.copy(read = true) else it }
    }

    /**
     * 先修改数据再记录到 [markAllAnnouncementsBefore], 所以测试看到记录时, 之后加载的列表已经是标记后的状态.
     */
    override suspend fun markAllAnnouncementsRead(beforeMillis: Long?) {
        if (markAllAnnouncementsFailures.getAndUpdate { maxOf(it - 1, 0) } > 0) {
            markAllAnnouncementsBefore += beforeMillis
            throw RepositoryNetworkException()
        }
        announcements = announcements.map {
            val covered = beforeMillis == null || it.publishedAtMillis <= beforeMillis
            if (it.read != null && covered) it.copy(read = true) else it
        }
        markAllAnnouncementsBefore += beforeMillis
    }
}

/**
 * 记录要在浏览器中打开的网页, 不真正打开.
 */
class RecordingBrowserNavigator : BrowserNavigator {
    val openedUrls = CopyOnWriteArrayList<String>()

    override fun openBrowser(context: Context, url: String): OpenBrowserResult {
        openedUrls += url
        return OpenBrowserResult.Success
    }

    override fun intentActionView(context: Context, url: String): OpenBrowserResult = openBrowser(context, url)
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
    settingsRepository: SettingsRepository = createTestSettingsRepository(),
): Koin = koinApplication {
    modules(
        module {
            single<NotificationRepository> { repository }
            single<NotificationBadgeManager> { badgeManager }
            single<SessionStateProvider> { sessionStateProvider }
            single<SettingsRepository> { settingsRepository }
        },
    )
}.koin

/**
 * 内存中的设置, 初始为默认值.
 */
fun createTestSettingsRepository(): SettingsRepository = PreferencesRepositoryImpl(MemoryDataStore(emptyPreferences()))

/**
 * 预览环境, 并把时间固定为 [TestNotificationTimeContext].
 *
 * 与 App 的根组件一样记录最近使用的指针设备, 每个测试独立. 只响应触摸的下拉刷新依据它判断输入方式.
 */
@Composable
fun NotificationTestHost(content: @Composable () -> Unit) {
    ProvideCompositionLocalsForPreview {
        val inputSource = remember { ActiveInputSourceState() }
        CompositionLocalProvider(
            LocalNotificationTimeContext provides TestNotificationTimeContext,
            LocalActiveInputSource provides inputSource,
        ) {
            Box(Modifier.trackActiveInputSource(inputSource), propagateMinConstraints = true) {
                content()
            }
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

/**
 * 文本节点排版时实际使用的字重.
 */
fun SemanticsNodeInteraction.textFontWeight(): FontWeight? {
    val results = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    return results.single().layoutInput.style.fontWeight
}

/**
 * [stateTag] 标记的 [NotificationEmptyState] 中有 64dp 的圆形图标.
 */
fun ComposeUiTest.assertEmptyStateIcon(stateTag: String) {
    val icon = onNode(
        hasTestTag(NotificationTestTags.EMPTY_STATE_ICON) and hasAnyAncestor(hasTestTag(stateTag)),
        useUnmergedTree = true,
    ).assertIsDisplayed().getBoundsInRoot()
    assertEquals(64f, (icon.right - icon.left).value, 0.5f, "icon width")
    assertEquals(64f, (icon.bottom - icon.top).value, 0.5f, "icon height")
}

/**
 * 用手指在 [tag] 节点上从顶部拖到底部, 触发下拉刷新.
 */
fun ComposeUiTest.pullToRefresh(tag: String) {
    onNodeWithTag(tag).performTouchInput {
        swipe(
            start = topCenter + Offset(0f, 20f),
            end = bottomCenter - Offset(0f, 20f),
            durationMillis = 1_000,
        )
    }
    waitForIdle()
}
