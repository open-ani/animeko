/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.notification

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.notification.Announcement
import me.him188.ani.app.data.models.notification.AnnouncementTag
import me.him188.ani.app.data.models.notification.NotificationBadgeState
import me.him188.ani.app.data.models.notification.NotificationType
import me.him188.ani.app.data.models.notification.NotificationUnreadSummary
import me.him188.ani.app.data.models.preference.NotificationSettings
import me.him188.ani.app.data.network.AniNotificationService
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.domain.session.InvalidSessionReason
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.client.apis.NotificationsAniApi
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class NotificationBadgeManagerImplTest {
    private val announcement = Announcement(
        id = "a1",
        tag = AnnouncementTag.MAINTENANCE,
        rawTag = "maintenance",
        title = "停机维护",
        body = "今晚维护",
        pinned = true,
        publishedAtMillis = 1,
        read = false,
    )

    private val userSummary = NotificationUnreadSummary(
        unreadCounts = mapOf(
            NotificationType.SYSTEM to 2,
            NotificationType.REPORT_RESULT to 3,
            NotificationType.COMMENT_REPLY to 7,
        ),
        unreadAnnouncementCount = 2,
        latestAnnouncement = announcement,
    )

    private val guestSummary = NotificationUnreadSummary(
        unreadCounts = emptyMap(),
        unreadAnnouncementCount = 0,
        latestAnnouncement = announcement.copy(read = null),
    )

    private val userState = NotificationBadgeState(
        unreadCount = 5,
        hasUnreadAnnouncements = true,
        unreadAnnouncementCount = 2,
        latestAnnouncement = announcement,
    )

    private val guestState = NotificationBadgeState.Empty.copy(latestAnnouncement = announcement.copy(read = null))

    private val loggedIn = SessionState.Valid(bangumiConnected = false)
    private val guest = SessionState.Invalid(InvalidSessionReason.NO_TOKEN)

    private class FakeSessionStateProvider : SessionStateProvider {
        override val stateFlow = MutableSharedFlow<SessionState>(replay = 1)
        override val eventFlow = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 8)

        fun set(state: SessionState) {
            check(stateFlow.tryEmit(state))
        }
    }

    private object UnusedNotificationsApi : ApiInvoker<NotificationsAniApi> {
        override suspend fun <R> invoke(action: suspend NotificationsAniApi.() -> R): R =
            error("ApiInvoker not expected in tests")
    }

    private class FakeNotificationService(
        var handler: suspend (call: Int) -> NotificationUnreadSummary,
    ) : AniNotificationService(UnusedNotificationsApi, EmptyCoroutineContext) {
        val requestedTypes = mutableListOf<Set<NotificationType>>()
        val calls get() = requestedTypes.size

        override suspend fun getUnreadSummary(types: Set<NotificationType>): NotificationUnreadSummary {
            requestedTypes += types
            return handler(requestedTypes.size)
        }
    }

    private val session = FakeSessionStateProvider()
    private val service = FakeNotificationService { userSummary }
    private val settings = MutableStateFlow(NotificationSettings.Default)

    private fun TestScope.startManager(): NotificationBadgeManagerImpl =
        NotificationBadgeManagerImpl(service, session, backgroundScope, notificationSettings = settings).also {
            it.start()
            runCurrent()
        }

    @Test
    fun `does not request before the app reports foreground`() = runTest {
        session.set(loggedIn)
        startManager()

        advanceTimeBy(1.minutes * 30)
        runCurrent()
        session.eventFlow.emit(SessionEvent.NewLogin)
        runCurrent()

        assertEquals(0, service.calls)
    }

    @Test
    fun `polls every 5 minutes only while in foreground`() = runTest {
        session.set(loggedIn)
        val manager = startManager()

        manager.setAppForeground(true)
        runCurrent()
        assertEquals(1, service.calls)
        assertEquals(userState, manager.state.value)

        advanceTimeBy(5.minutes - 1.milliseconds)
        runCurrent()
        assertEquals(1, service.calls)
        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertEquals(2, service.calls)

        manager.setAppForeground(false)
        runCurrent()
        advanceTimeBy(30.minutes)
        runCurrent()
        assertEquals(2, service.calls)

        manager.setAppForeground(true)
        runCurrent()
        assertEquals(3, service.calls)
        assertEquals(listOf(NotificationType.SUPPORTED), service.requestedTypes.distinct())
    }

    @Test
    fun `waits for the session state before requesting`() = runTest {
        val manager = startManager()

        manager.setAppForeground(true)
        manager.requestRefresh()
        runCurrent()
        assertEquals(0, service.calls)

        session.set(loggedIn)
        runCurrent()
        assertEquals(1, service.calls)
        assertEquals(userState, manager.state.value)
    }

    @Test
    fun `guest gets the latest announcement only and does not poll`() = runTest {
        session.set(guest)
        service.handler = { guestSummary }
        val manager = startManager()

        manager.setAppForeground(true)
        runCurrent()
        assertEquals(guestState, manager.state.value)

        advanceTimeBy(30.minutes)
        runCurrent()
        assertEquals(1, service.calls)

        manager.requestRefresh()
        runCurrent()
        assertEquals(2, service.calls)
    }

    @Test
    fun `guest has no unread state even if the server reports some`() = runTest {
        session.set(guest)
        val manager = startManager()

        manager.setAppForeground(true)
        runCurrent()

        assertEquals(guestState, manager.state.value)
    }

    @Test
    fun `logout clears counts immediately`() = runTest {
        session.set(loggedIn)
        val manager = startManager()
        manager.setAppForeground(true)
        runCurrent()
        assertEquals(userState, manager.state.value)

        service.handler = { throw RepositoryNetworkException() }
        session.set(guest)
        runCurrent()

        assertEquals(guestState, manager.state.value)
    }

    @Test
    fun `response for the previous session is discarded`() = runTest {
        session.set(loggedIn)
        val manager = startManager()
        val slowResponse = CompletableDeferred<NotificationUnreadSummary>()
        service.handler = { call -> if (call == 1) slowResponse.await() else guestSummary }

        manager.setAppForeground(true)
        runCurrent()
        session.set(guest)
        runCurrent()
        slowResponse.complete(userSummary)
        runCurrent()

        assertEquals(guestState, manager.state.value)
        assertEquals(2, service.calls)
    }

    @Test
    fun `new login retries authorization failures`() = runTest {
        session.set(guest)
        val manager = startManager()
        manager.setAppForeground(true)
        runCurrent()
        assertEquals(1, service.calls)

        var authorizationFailures = 2
        service.handler = {
            if (authorizationFailures > 0) {
                authorizationFailures--
                throw RepositoryAuthorizationException()
            }
            userSummary
        }
        session.eventFlow.emit(SessionEvent.NewLogin)
        session.set(loggedIn)
        runCurrent()
        advanceTimeBy(1.seconds)
        runCurrent()

        assertEquals(0, authorizationFailures)
        assertEquals(userState, manager.state.value)
    }

    @Test
    fun `new login gives up after three retries`() = runTest {
        session.set(loggedIn)
        val manager = startManager()
        service.handler = { throw RepositoryAuthorizationException() }

        session.eventFlow.emit(SessionEvent.NewLogin)
        runCurrent()
        assertEquals(0, service.calls)
        manager.setAppForeground(true)
        runCurrent()
        advanceTimeBy(1.seconds)
        runCurrent()

        assertEquals(4, service.calls)
        assertEquals(NotificationBadgeState.Empty, manager.state.value)
    }

    @Test
    fun `authorization failures are not retried without a new login`() = runTest {
        session.set(loggedIn)
        val manager = startManager()
        service.handler = { throw RepositoryAuthorizationException() }

        manager.setAppForeground(true)
        runCurrent()
        advanceTimeBy(1.seconds)
        runCurrent()

        assertEquals(1, service.calls)
    }

    @Test
    fun `requestRefresh works in background and is conflated while a request is running`() = runTest {
        session.set(loggedIn)
        val manager = startManager()
        val firstResponse = CompletableDeferred<NotificationUnreadSummary>()
        service.handler = { call -> if (call == 1) firstResponse.await() else userSummary }

        manager.requestRefresh()
        runCurrent()
        assertEquals(1, service.calls)
        repeat(5) { manager.requestRefresh() }
        runCurrent()
        assertEquals(1, service.calls)

        firstResponse.complete(userSummary.copy(unreadCounts = emptyMap()))
        runCurrent()

        assertEquals(2, service.calls)
        assertEquals(userState, manager.state.value)
    }

    @Test
    fun `errors keep the previous state`() = runTest {
        session.set(loggedIn)
        val manager = startManager()
        manager.setAppForeground(true)
        runCurrent()
        assertEquals(userState, manager.state.value)

        service.handler = { throw RepositoryNetworkException() }
        manager.requestRefresh()
        runCurrent()
        assertEquals(userState, manager.state.value)

        service.handler = { throw IllegalStateException("unexpected") }
        manager.requestRefresh()
        runCurrent()
        assertEquals(userState, manager.state.value)

        service.handler = { userSummary.copy(unreadCounts = mapOf(NotificationType.SYSTEM to 1)) }
        manager.requestRefresh()
        runCurrent()
        assertEquals(1, manager.state.value.unreadCount)
        assertEquals(4, service.calls)
    }

    @Test
    fun `does not request while the session cannot be refreshed because of network errors`() = runTest {
        session.set(SessionState.Invalid(InvalidSessionReason.NETWORK_ERROR))
        val manager = startManager()

        manager.setAppForeground(true)
        manager.requestRefresh()
        runCurrent()
        advanceTimeBy(30.minutes)
        runCurrent()

        assertEquals(0, service.calls)
        assertEquals(NotificationBadgeState.Empty, manager.state.value)
    }

    @Test
    fun `switched off types are neither requested nor counted`() = runTest {
        session.set(loggedIn)
        settings.value = NotificationSettings(showReportResults = false)
        val manager = startManager()

        manager.setAppForeground(true)
        runCurrent()

        assertEquals(
            listOf(setOf(NotificationType.SYSTEM, NotificationType.ACCOUNT_SECURITY)),
            service.requestedTypes,
        )
        // 服务端即使返回了关闭的类型, 也不计入
        assertEquals(userState.copy(unreadCount = 2), manager.state.value)
    }

    @Test
    fun `changing the shown types refreshes once`() = runTest {
        session.set(loggedIn)
        val manager = startManager()
        manager.setAppForeground(true)
        runCurrent()
        assertEquals(1, service.calls)
        assertEquals(userState, manager.state.value)

        settings.value = NotificationSettings(showReportResults = false)
        runCurrent()
        assertEquals(2, service.calls)
        assertEquals(setOf(NotificationType.SYSTEM, NotificationType.ACCOUNT_SECURITY), service.requestedTypes.last())
        assertEquals(2, manager.state.value.unreadCount)

        // 公告红点的设置不影响请求的类型
        settings.value = settings.value.copy(showAnnouncementDot = false)
        runCurrent()
        assertEquals(2, service.calls)

        settings.value = NotificationSettings.Default
        runCurrent()
        assertEquals(3, service.calls)
        assertEquals(NotificationType.SUPPORTED, service.requestedTypes.last())
        assertEquals(userState, manager.state.value)
    }
}
