/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.notification

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.notification.NotificationBadgeState
import me.him188.ani.app.data.models.notification.NotificationGroup
import me.him188.ani.app.data.models.notification.NotificationType
import me.him188.ani.app.data.models.notification.NotificationUnreadSummary
import me.him188.ani.app.data.models.preference.NotificationSettings
import me.him188.ani.app.data.network.AniNotificationService
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryUnknownException
import me.him188.ani.app.domain.session.InvalidSessionReason
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.thisLogger
import me.him188.ani.utils.logging.warn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * 从未读概况接口获取 [NotificationBadgeState].
 *
 * 自动刷新只在前台进行:
 * - 进入前台时刷新. 登录用户之后每 [pollInterval] 刷新一次, 直到进入后台; 游客只刷新这一次.
 * - 登录状态变化时刷新. 退出登录时立即清空未读数.
 * - [SessionEvent.NewLogin] 之后刷新. 刚登录时 HTTP 客户端可能还在用旧的 token, 所以登录后的第一次请求遇到 401 会重试.
 * - 本机关闭的通知类型 ([NotificationSettings.excludedTypes]) 变化时刷新. 未读数不统计这些类型.
 *
 * 未读数按通知中心的标签页 ([NotificationGroup.TABS]) 分别统计.
 *
 * [requestRefresh] 不论前后台都会刷新. 同一时刻最多一个请求, 请求期间收到的刷新请求合并为结束后的一次.
 *
 * 初始视为后台, 前台状态由 UI 根组件 `NotificationForegroundObserver` 报告: Lifecycle 至少为 STARTED, 且窗口没有隐藏到系统托盘.
 * Android 上 Lifecycle 的 owner 是 Activity; 桌面端是窗口, 窗口显示且未最小化时至少为 STARTED. 桌面端把窗口隐藏到托盘时
 * Lifecycle 仍为 STARTED, 所以需要单独判断窗口是否可见.
 * 该组件挂载时就读取当前状态, 所以无论何时挂载, 启动后都会报告一次前台. 这次报告就是启动时的刷新.
 * TV 不挂载该组件, 因此不会自动请求.
 */
class NotificationBadgeManagerImpl(
    private val notificationService: AniNotificationService,
    private val sessionStateProvider: SessionStateProvider,
    private val scope: CoroutineScope,
    private val notificationSettings: Flow<NotificationSettings> = flowOf(NotificationSettings.Default),
    private val pollInterval: Duration = 5.minutes,
) : NotificationBadgeManager {
    private val logger = thisLogger()

    private val _state = MutableStateFlow(NotificationBadgeState.Empty)
    override val state: StateFlow<NotificationBadgeState> = _state.asStateFlow()

    private val foreground = MutableStateFlow(false)
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    /**
     * 下一次以登录用户身份请求时遇到 401 是否重试. [SessionEvent.NewLogin] 可能早于登录状态变为 [SessionState.Valid],
     * 所以标记保留到真正以登录用户身份请求的那一次.
     */
    private val retryAuthorizationOnNextFetch = MutableStateFlow(false)

    fun start() {
        scope.launch(CoroutineName("NotificationBadgeManager")) {
            sessionStateProvider.stateFlow
                .map { Access.of(it) }
                .distinctUntilChanged()
                .collectLatest { access -> serve(access) }
        }
        scope.launch(CoroutineName("NotificationBadgeManager.excludedTypes")) {
            excludedTypes()
                .distinctUntilChanged()
                .drop(1)
                .collect { requestRefresh() }
        }
        scope.launch(CoroutineName("NotificationBadgeManager.newLogin")) {
            sessionStateProvider.eventFlow.collect { event ->
                if (event is SessionEvent.NewLogin) {
                    retryAuthorizationOnNextFetch.value = true
                    if (foreground.value) requestRefresh()
                }
            }
        }
    }

    override fun requestRefresh() {
        refreshRequests.trySend(Unit)
    }

    override fun setAppForeground(foreground: Boolean) {
        this.foreground.value = foreground
    }

    /**
     * 在登录状态为 [access] 期间处理刷新. 登录状态变化时被取消, 进行中的请求也随之取消, 因此不会写回上一个用户的结果.
     */
    private suspend fun serve(access: Access): Unit = coroutineScope {
        if (access == Access.GUEST) {
            _state.update { NotificationBadgeState.Empty.copy(latestAnnouncement = it.latestAnnouncement?.copy(read = null)) }
        }
        // 不经调度立即开始, 使前台时的首次刷新请求在下面开始处理之前发出, 与此前积压的请求合并为一次
        launch(start = CoroutineStart.UNDISPATCHED) {
            var poller: Job? = null
            foreground.collect { isForeground ->
                poller?.cancel()
                poller = if (isForeground) {
                    launch(start = CoroutineStart.UNDISPATCHED) { refreshWhileForeground(access) }
                } else {
                    null
                }
            }
        }
        while (true) {
            refreshRequests.receive()
            refresh(access)
        }
    }

    private suspend fun refreshWhileForeground(access: Access) {
        when (access) {
            Access.USER -> while (true) {
                requestRefresh()
                delay(pollInterval)
            }

            Access.GUEST -> requestRefresh()
            Access.UNAVAILABLE -> {}
        }
    }

    private suspend fun refresh(access: Access) {
        if (access == Access.UNAVAILABLE) return
        val retryAuthorization = access == Access.USER && retryAuthorizationOnNextFetch.getAndUpdate { false }
        val excludedTypes = excludedTypes().first()
        val summary = try {
            getUnreadSummary(excludedTypes, retryAuthorization)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is RepositoryException && e !is RepositoryUnknownException) {
                logger.info { "Failed to refresh notification badge: $e" }
            } else {
                logger.warn(e) { "Failed to refresh notification badge" }
            }
            return
        }
        _state.value = if (access == Access.USER) {
            NotificationBadgeState(
                unreadCounts = summary.unreadCounts.filterKeys { it in NotificationGroup.TABS },
                hasUnreadAnnouncements = summary.unreadAnnouncementCount > 0,
                unreadAnnouncementCount = summary.unreadAnnouncementCount,
                latestAnnouncement = summary.latestAnnouncement,
            )
        } else {
            NotificationBadgeState.Empty.copy(latestAnnouncement = summary.latestAnnouncement?.copy(read = null))
        }
    }

    /**
     * 本机关闭的通知类型.
     */
    private fun excludedTypes(): Flow<Set<NotificationType>> =
        notificationSettings.map { it.excludedTypes }

    private suspend fun getUnreadSummary(
        excludedTypes: Set<NotificationType>,
        retryAuthorization: Boolean,
    ): NotificationUnreadSummary {
        var retries = 0
        while (true) {
            try {
                return notificationService.getUnreadSummary(NotificationGroup.TABS, excludedTypes)
            } catch (e: RepositoryAuthorizationException) {
                if (!retryAuthorization || retries >= MAX_AUTHORIZATION_RETRIES) throw e
                retries++
                delay(AUTHORIZATION_RETRY_DELAY)
            }
        }
    }

    private enum class Access {
        /**
         * 已登录: 获取通知未读数和公告未读状态.
         */
        USER,

        /**
         * 游客, 或登录已失效: 只获取最新公告, 没有未读数.
         */
        GUEST,

        /**
         * token 已过期, 且因网络错误未能刷新. 请求一定会失败, 所以保留现有状态, 等登录状态恢复.
         */
        UNAVAILABLE,
        ;

        companion object {
            fun of(state: SessionState): Access = when (state) {
                is SessionState.Valid -> USER
                is SessionState.Invalid -> when (state.reason) {
                    InvalidSessionReason.NETWORK_ERROR -> UNAVAILABLE
                    InvalidSessionReason.NO_TOKEN,
                    InvalidSessionReason.UNKNOWN,
                        -> GUEST
                }
            }
        }
    }

    private companion object {
        const val MAX_AUTHORIZATION_RETRIES = 3
        val AUTHORIZATION_RETRY_DELAY = 125.milliseconds
    }
}
