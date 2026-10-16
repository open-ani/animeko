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

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.notification.Announcement
import me.him188.ani.app.data.models.notification.AnnouncementTag
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.notification_announcement_not_found
import me.him188.ani.app.ui.lang.notification_announcement_tag_community
import me.him188.ani.app.ui.lang.notification_announcement_tag_general
import me.him188.ani.app.ui.lang.notification_announcement_tag_maintenance
import me.him188.ani.app.ui.lang.notification_announcement_tag_release
import me.him188.ani.app.ui.lang.notification_mark_all_read
import me.him188.ani.app.ui.lang.settings_mediasource_retry
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnouncementsScreenTest {
    private companion object {
        const val DAY_MILLIS = 24 * 3_600_000L
    }

    private fun announcement(id: String, daysAgo: Int, read: Boolean, pinned: Boolean = false): Announcement =
        createTestAnnouncement(
            id = id,
            title = id,
            pinned = pinned,
            publishedAtMillis = publishedAt(daysAgo),
            read = read,
        )

    private fun publishedAt(daysAgo: Int) = TestNotificationNowMillis - daysAgo * DAY_MILLIS

    private val taggedAnnouncements = listOf(
        createTestAnnouncement(id = "maintenance", tag = AnnouncementTag.MAINTENANCE, pinned = true),
        createTestAnnouncement(id = "release", tag = AnnouncementTag.RELEASE),
        createTestAnnouncement(id = "community", tag = AnnouncementTag.COMMUNITY),
        createTestAnnouncement(id = "general", tag = AnnouncementTag.GENERAL),
        createTestAnnouncement(id = "unknown", tag = AnnouncementTag.UNKNOWN, rawTag = "event"),
    )

    private fun ComposeUiTest.hasDot(id: String): Boolean = hasNodeIn(id, AnnouncementTestTags.UNREAD_DOT)

    private fun ComposeUiTest.showAnnouncements(vm: AnnouncementsViewModel, opened: MutableList<String>) {
        setContent {
            NotificationTestHost {
                AnnouncementsScreen(
                    vm = vm,
                    onNavigateToDetail = { opened += it },
                    onLogin = {},
                    navigator = rememberSinglePaneNavigator(),
                )
            }
        }
    }

    private fun ComposeUiTest.waitForItem(id: String) {
        waitUntil { onAllNodesWithTag(AnnouncementTestTags.item(id)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.nodeIn(id: String, tag: String): SemanticsNodeInteraction =
        onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag(AnnouncementTestTags.item(id))), useUnmergedTree = true)

    private fun ComposeUiTest.hasNodeIn(id: String, tag: String): Boolean =
        onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasTestTag(AnnouncementTestTags.item(id))), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.tagText(id: String): String =
        nodeIn(id, AnnouncementTestTags.TAG).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }

    /**
     * 下拉刷新的指示器正在转圈.
     */
    private fun ComposeUiTest.isRefreshing(): Boolean =
        onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.rowTexts(id: String): List<String> =
        onNodeWithTag(AnnouncementTestTags.item(id)).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    @Test
    fun `pinned announcements show a pin before the tag`() = runAniComposeUiTest {
        val vm = AnnouncementsViewModel(notificationTestKoin(FakeNotificationRepository(announcements = taggedAnnouncements)))
        showAnnouncements(vm, mutableListOf())
        waitForItem("maintenance")

        assertTrue(hasNodeIn("maintenance", AnnouncementTestTags.PIN))
        assertTrue(!hasNodeIn("release", AnnouncementTestTags.PIN))
        val pin = nodeIn("maintenance", AnnouncementTestTags.PIN).fetchSemanticsNode().boundsInRoot
        val tag = nodeIn("maintenance", AnnouncementTestTags.TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(pin.right <= tag.left, "pin $pin should be before tag $tag")
    }

    @Test
    fun `tags use localized labels and unknown tags are shown as general announcements`() = runAniComposeUiTest {
        val vm = AnnouncementsViewModel(notificationTestKoin(FakeNotificationRepository(announcements = taggedAnnouncements)))
        showAnnouncements(vm, mutableListOf())
        waitForItem("unknown")

        val expected = runBlocking {
            mapOf(
                "maintenance" to getString(Lang.notification_announcement_tag_maintenance),
                "release" to getString(Lang.notification_announcement_tag_release),
                "community" to getString(Lang.notification_announcement_tag_community),
                "general" to getString(Lang.notification_announcement_tag_general),
                "unknown" to getString(Lang.notification_announcement_tag_general),
            )
        }
        for ((id, label) in expected) {
            assertEquals(label, tagText(id), id)
        }
    }

    @Test
    fun `tag labels in simplified chinese`() = withSimplifiedChinese {
        runAniComposeUiTest {
            val vm = AnnouncementsViewModel(
                notificationTestKoin(FakeNotificationRepository(announcements = taggedAnnouncements)),
            )
            showAnnouncements(vm, mutableListOf())
            waitForItem("unknown")
            assertEquals("维护", tagText("maintenance"))
            assertEquals("版本更新", tagText("release"))
            assertEquals("社区", tagText("community"))
            assertEquals("公告", tagText("general"))
            assertEquals("公告", tagText("unknown"))
        }
    }

    @Test
    fun `rows show dates instead of relative times`() = withSimplifiedChinese {
        runAniComposeUiTest {
            val announcements = TestAnnouncements + listOf(
                createTestAnnouncement(id = "just-now", publishedAtMillis = TestNotificationNowMillis - 5 * 60_000),
                createTestAnnouncement(id = "last-year", publishedAtMillis = publishedAt(daysAgo = 368)),
            )
            val vm = AnnouncementsViewModel(notificationTestKoin(FakeNotificationRepository(announcements = announcements)))
            showAnnouncements(vm, mutableListOf())
            waitForItem("last-year")
            assertEquals(
                listOf("维护", "10 月 12 日", TestAnnouncements[0].title, TestAnnouncements[0].body),
                rowTexts("announcement-1"),
            )
            assertEquals("10 月 6 日", rowTexts("announcement-2")[1])
            assertEquals("9 月 28 日", rowTexts("announcement-3")[1])
            assertEquals("10 月 13 日", rowTexts("just-now")[1])
            assertEquals("2025/10/10", rowTexts("last-year")[1])
        }
    }

    @Test
    fun `row dates use regular weight and tags medium`() = withSimplifiedChinese {
        runAniComposeUiTest {
            val vm = AnnouncementsViewModel(notificationTestKoin())
            showAnnouncements(vm, mutableListOf())
            waitForItem("announcement-1")
            val date = onNode(
                hasText("10 月 12 日") and hasAnyAncestor(hasTestTag(AnnouncementTestTags.item("announcement-1"))),
                useUnmergedTree = true,
            )
            assertEquals(FontWeight.Normal, date.textFontWeight())
            assertEquals(FontWeight.Medium, nodeIn("announcement-1", AnnouncementTestTags.TAG).textFontWeight())
        }
    }

    @Test
    fun `entering the list marks loaded announcements read but keeps the unread marks of this visit`() =
        runAniComposeUiTest {
            val repository = FakeNotificationRepository()
            val badgeManager = FakeNotificationBadgeManager()
            val vm = AnnouncementsViewModel(notificationTestKoin(repository, badgeManager))
            showAnnouncements(vm, mutableListOf())
            waitForItem("announcement-3")

            waitUntil { repository.markAllAnnouncementsBefore.isNotEmpty() }
            assertEquals(listOf(TestAnnouncements.maxOf { it.publishedAtMillis }), repository.markAllAnnouncementsBefore.toList())
            waitUntil { badgeManager.refreshRequests.get() > 0 }
            assertTrue(repository.announcements.all { it.read == true })

            // 本次浏览仍显示进入前的未读标记
            waitForIdle()
            assertTrue(hasDot("announcement-1"))
            assertTrue(hasDot("announcement-2"))
            assertTrue(!hasDot("announcement-3"))
            assertEquals(1, repository.markAllAnnouncementsBefore.size)
        }

    @Test
    fun `there is no mark all action`() = runAniComposeUiTest {
        val markAllText = runBlocking { getString(Lang.notification_mark_all_read) }
        val vm = AnnouncementsViewModel(notificationTestKoin())
        showAnnouncements(vm, mutableListOf())
        waitForItem("announcement-1")
        assertTrue(onAllNodes(hasContentDescription(markAllText)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `later pages show the read state after entering`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository(
            announcements = listOf(
                announcement("a1", daysAgo = 1, read = false),
                announcement("a2", daysAgo = 2, read = false),
                announcement("a3", daysAgo = 3, read = false),
                announcement("a4", daysAgo = 4, read = true),
            ),
            announcementPageSize = 2,
        )
        val secondPage = CompletableDeferred<Unit>()
        repository.announcementPageGates[2] = secondPage
        val vm = AnnouncementsViewModel(notificationTestKoin(repository))
        showAnnouncements(vm, mutableListOf())
        waitForItem("a2")
        waitUntil { repository.markAllAnnouncementsBefore.isNotEmpty() }
        assertEquals(listOf(publishedAt(1)), repository.markAllAnnouncementsBefore.toList())

        secondPage.complete(Unit)
        waitForItem("a4")
        waitForIdle()

        assertTrue(hasDot("a1"))
        assertTrue(hasDot("a2"))
        // 进入时未读, 但发布时间不晚于已标记的时间, 加载时已是已读
        assertTrue(!hasDot("a3"))
        assertTrue(!hasDot("a4"))
        assertEquals(1, repository.markAllAnnouncementsBefore.size)
    }

    @Test
    fun `newer announcements on a later page stay unread and are marked when loaded`() = runAniComposeUiTest {
        // 置顶的公告排在前面, 第一页全是较早的置顶公告
        val repository = FakeNotificationRepository(
            announcements = listOf(
                announcement("p1", daysAgo = 20, read = false, pinned = true),
                announcement("p2", daysAgo = 30, read = false, pinned = true),
                announcement("a1", daysAgo = 1, read = false),
                announcement("a2", daysAgo = 25, read = false),
            ),
            announcementPageSize = 2,
        )
        val secondPage = CompletableDeferred<Unit>()
        repository.announcementPageGates[2] = secondPage
        val vm = AnnouncementsViewModel(notificationTestKoin(repository))
        showAnnouncements(vm, mutableListOf())
        waitForItem("p2")
        waitUntil { repository.markAllAnnouncementsBefore.isNotEmpty() }
        assertEquals(listOf(publishedAt(20)), repository.markAllAnnouncementsBefore.toList())

        secondPage.complete(Unit)
        waitForItem("a2")
        waitUntil { repository.markAllAnnouncementsBefore.size == 2 }
        assertEquals(listOf(publishedAt(20), publishedAt(1)), repository.markAllAnnouncementsBefore.toList())

        waitForIdle()
        assertTrue(hasDot("p1"))
        assertTrue(hasDot("p2"))
        assertTrue(hasDot("a1"))
        assertTrue(!hasDot("a2"))
        assertTrue(repository.announcements.all { it.read == true })
    }

    @Test
    fun `refreshing keeps the unread marks of this visit`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository()
        val vm = AnnouncementsViewModel(notificationTestKoin(repository))
        showAnnouncements(vm, mutableListOf())
        waitForItem("announcement-1")
        waitUntil { repository.markAllAnnouncementsBefore.isNotEmpty() }
        val loadsBefore = repository.announcementLoads.get()

        repository.refreshAnnouncements()

        waitUntil { repository.announcementLoads.get() > loadsBefore }
        waitForIdle()
        assertTrue(hasDot("announcement-1"))
        assertTrue(hasDot("announcement-2"))
        assertTrue(!hasDot("announcement-3"))
        assertEquals(1, repository.markAllAnnouncementsBefore.size)
    }

    @Test
    fun `a failed mark is retried when the list reloads`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository()
        repository.markAllAnnouncementsFailures.set(1)
        val vm = AnnouncementsViewModel(notificationTestKoin(repository))
        showAnnouncements(vm, mutableListOf())
        waitForItem("announcement-1")
        waitUntil { repository.markAllAnnouncementsBefore.isNotEmpty() }
        assertTrue(repository.announcements.any { it.read == false })

        // 与网络请求一样, 重新加载要等列表显示为正在刷新之后才完成. 否则列表可能看不到这次加载
        val reload = CompletableDeferred<Unit>()
        repository.announcementPageGates[0] = reload
        repository.refreshAnnouncements()
        waitUntil { isRefreshing() }
        reload.complete(Unit)

        waitUntil { repository.markAllAnnouncementsBefore.size == 2 }
        val newest = TestAnnouncements.maxOf { it.publishedAtMillis }
        assertEquals(listOf(newest, newest), repository.markAllAnnouncementsBefore.toList())
        waitUntil { repository.announcements.all { it.read == true } }
    }

    @Test
    fun `clicking an announcement marked on entry dims it and opens the detail`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository()
        val vm = AnnouncementsViewModel(notificationTestKoin(repository))
        val opened = mutableListOf<String>()
        showAnnouncements(vm, opened)
        waitForItem("announcement-1")
        waitUntil { repository.markAllAnnouncementsBefore.isNotEmpty() }
        assertTrue(hasDot("announcement-1"))

        onNodeWithTag(AnnouncementTestTags.item("announcement-1")).performClick()

        assertEquals(listOf("announcement-1"), opened)
        waitUntil { !hasDot("announcement-1") }
        // 进入列表时已经标为已读, 不再单独请求
        assertTrue(repository.markedAnnouncementsRead.isEmpty())
    }

    @Test
    fun `guests can browse but nothing is marked`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository(announcements = TestAnnouncements.map { it.copy(read = null) })
        val vm = AnnouncementsViewModel(notificationTestKoin(repository, sessionStateProvider = FakeSessionStateProvider.Guest))
        val opened = mutableListOf<String>()
        showAnnouncements(vm, opened)
        waitForItem("announcement-1")

        assertTrue(!hasDot("announcement-1"))

        onNodeWithTag(AnnouncementTestTags.item("announcement-1")).performClick()
        waitForIdle()
        assertEquals(listOf("announcement-1"), opened)
        assertTrue(repository.markedAnnouncementsRead.isEmpty())
        assertTrue(repository.markAllAnnouncementsBefore.isEmpty())
    }

    @Test
    fun `detail screen for a deleted announcement shows a hint without retry`() = runAniComposeUiTest {
        val notFoundText = runBlocking { getString(Lang.notification_announcement_not_found) }
        val retryText = runBlocking { getString(Lang.settings_mediasource_retry) }
        val vm = AnnouncementDetailViewModel("deleted", notificationTestKoin())
        setContent {
            NotificationTestHost {
                AnnouncementDetailScreen(vm)
            }
        }
        waitUntil { onAllNodesWithTag(AnnouncementTestTags.DETAIL_NOT_FOUND).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(notFoundText).assertIsDisplayed()
        assertTrue(onAllNodesWithText(retryText).fetchSemanticsNodes().isEmpty())
    }
}
