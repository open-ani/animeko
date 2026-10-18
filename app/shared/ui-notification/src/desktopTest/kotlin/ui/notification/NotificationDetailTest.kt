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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.toInstant
import me.him188.ani.app.data.models.comment.CommentReportReason
import me.him188.ani.app.data.models.comment.CommentReportTargetType
import me.him188.ani.app.data.models.notification.AppNotification
import me.him188.ani.app.data.models.notification.AppNotificationContent
import me.him188.ani.app.data.models.notification.NotificationGroup
import me.him188.ani.app.data.models.notification.NotificationTarget
import me.him188.ani.app.data.models.notification.NotificationType
import me.him188.ani.app.data.models.notification.SystemNotificationEvent
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.navigation.SettingsTab
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.platform.navigation.LocalBrowserNavigator
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_report_reason_other
import me.him188.ani.app.ui.lang.comment_report_reason_spoiler
import me.him188.ani.app.ui.lang.notification_go_to_character
import me.him188.ani.app.ui.lang.notification_go_to_episode
import me.him188.ani.app.ui.lang.notification_go_to_person
import me.him188.ani.app.ui.lang.notification_go_to_subject
import me.him188.ani.app.ui.lang.notification_not_found
import me.him188.ani.app.ui.lang.notification_report_outcome_handled
import me.him188.ani.app.ui.lang.notification_report_outcome_removed
import me.him188.ani.app.ui.lang.notification_report_privacy_footnote
import me.him188.ani.app.ui.lang.notification_report_reason
import me.him188.ani.app.ui.lang.notification_report_result_description_handled
import me.him188.ani.app.ui.lang.notification_report_result_description_removed
import me.him188.ani.app.ui.lang.notification_report_snapshot_footnote
import me.him188.ani.app.ui.lang.notification_report_snapshot_title
import me.him188.ani.app.ui.lang.notification_report_target
import me.him188.ani.app.ui.lang.notification_report_target_comment
import me.him188.ani.app.ui.lang.notification_system_bangumi_expired
import me.him188.ani.app.ui.lang.notification_system_bangumi_expired_supporting
import me.him188.ani.app.ui.lang.notification_system_bangumi_reauthorize
import me.him188.ani.app.ui.lang.notification_title
import me.him188.ani.app.ui.lang.notification_view_details
import me.him188.ani.app.ui.lang.settings_mediasource_copy
import me.him188.ani.app.ui.lang.settings_mediasource_retry
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationDetailTest {
    private class RecordedActions {
        val calls = mutableListOf<String>()
        val actions = NotificationActions(
            onOpenEpisode = { subjectId, episodeId -> calls += "episode:$subjectId:$episodeId" },
            onOpenSubject = { subjectId, placeholder: SubjectDetailPlaceholder? ->
                calls += "subject:$subjectId:${placeholder?.nameCN}"
            },
            onOpenPerson = { calls += "person:$it" },
            onOpenCharacter = { calls += "character:$it" },
            onReauthorizeBangumi = { calls += "reauthorize" },
            onOpenAccountSettings = { calls += "account-settings" },
            onCheckUpdate = { calls += "check-update" },
        )
    }

    private fun ComposeUiTest.showDetail(notification: AppNotification, actions: NotificationActions) {
        setContent {
            NotificationTestHost {
                NotificationDetailContent(notification, actions)
            }
        }
    }

    private fun string(resource: StringResource) = runBlocking { getString(resource) }

    /**
     * [TestNotificationTimeContext] 时区 (UTC+8) 中的 [dateTime].
     */
    private fun localMillis(dateTime: String): Long =
        LocalDateTime.parse(dateTime).toInstant(UtcOffset(hours = 8).asTimeZone()).toEpochMilliseconds()

    private fun ComposeUiTest.assertNoTopBarAction() {
        assertTrue(onAllNodesWithTag(NotificationTestTags.DETAIL_TOP_BAR_ACTION).fetchSemanticsNodes().isEmpty())
    }

    private fun ComposeUiTest.assertFieldValue(fieldTag: String, value: String) {
        onNode(hasText(value) and hasAnyAncestor(hasTestTag(fieldTag)), useUnmergedTree = true).assertExists()
    }

    private fun ComposeUiTest.assertNoAction() {
        assertTrue(onAllNodesWithTag(NotificationTestTags.DETAIL_ACTION).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `report result shows reason target outcome and snapshot`() = runAniComposeUiTest {
        val notification = createTestReportResult()
        val target = runBlocking {
            reportTargetText(notification.content as AppNotificationContent.ReportResult, notification.display, false)
                .resolveString()
        }
        showDetail(notification, NotificationActions.Noop)

        onNodeWithTag(NotificationTestTags.REPORT_REASON).assertIsDisplayed()
        onNodeWithText(string(Lang.notification_report_reason)).assertIsDisplayed()
        onNodeWithText(string(Lang.comment_report_reason_spoiler)).assertIsDisplayed()
        onNodeWithText(string(Lang.notification_report_target)).assertIsDisplayed()
        onNodeWithText(target).assertIsDisplayed()
        onNodeWithText(string(Lang.notification_report_outcome_removed)).assertIsDisplayed()
        onNodeWithTag(NotificationTestTags.REPORT_SNAPSHOT_AUTHOR, useUnmergedTree = true).assertTextEquals("星野")
        onNodeWithTag(NotificationTestTags.REPORT_SNAPSHOT_TEXT, useUnmergedTree = true)
            .assertTextEquals("最后一集芙莉莲会和费伦分开")
    }

    @Test
    fun `report result with snapshot explains the snapshot and the privacy rule`() = runAniComposeUiTest {
        val privacy = string(Lang.notification_report_privacy_footnote)
        val footnote = runBlocking { getString(Lang.notification_report_snapshot_footnote, privacy) }
        showDetail(createTestReportResult(), NotificationActions.Noop)
        onNodeWithText(footnote).assertExists()
    }

    @Test
    fun `report result without snapshot only states the privacy rule`() = runAniComposeUiTest {
        val privacy = string(Lang.notification_report_privacy_footnote)
        val footnote = runBlocking { getString(Lang.notification_report_snapshot_footnote, privacy) }
        showDetail(createTestReportResult(contentSnapshot = null), NotificationActions.Noop)

        onNodeWithText(privacy).assertExists()
        assertTrue(onAllNodesWithText(footnote).fetchSemanticsNodes().isEmpty())
        assertTrue(onAllNodesWithText(string(Lang.notification_report_snapshot_title)).fetchSemanticsNodes().isEmpty())
        assertTrue(
            onAllNodesWithTag(NotificationTestTags.REPORT_SNAPSHOT_TEXT, useUnmergedTree = true).fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun `report result shows handled when the comment was kept`() = runAniComposeUiTest {
        showDetail(createTestReportResult(contentRemoved = false), NotificationActions.Noop)
        onNodeWithText(string(Lang.notification_report_outcome_handled)).assertIsDisplayed()
    }

    @Test
    fun `report result description depends on whether the comment was removed`() = runAniComposeUiTest {
        val removed = string(Lang.notification_report_result_description_removed)
        val handled = string(Lang.notification_report_result_description_handled)
        var notification by mutableStateOf(createTestReportResult(contentRemoved = true))
        setContent {
            NotificationTestHost {
                NotificationDetailContent(notification, NotificationActions.Noop)
            }
        }
        onNodeWithText(removed).assertIsDisplayed()
        assertTrue(onAllNodesWithText(handled).fetchSemanticsNodes().isEmpty())

        runOnIdle { notification = createTestReportResult(contentRemoved = false) }
        onNodeWithText(handled).assertIsDisplayed()
        assertTrue(onAllNodesWithText(removed).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `simplified chinese report result copy`() = withSimplifiedChinese {
        runAniComposeUiTest {
            showDetail(createTestReportResult(), NotificationActions.Noop)
            onNodeWithText("经审核，该评论违反社区规则，已被删除。感谢你的举报。").assertIsDisplayed()
            onNodeWithText("评论可能已被作者编辑或删除，审核以你举报时的快照为准。举报人身份不会告知对方。").assertExists()
        }
    }

    @Test
    fun `report result times use the detail format`() = withSimplifiedChinese {
        runAniComposeUiTest {
            showDetail(createTestReportResult(), NotificationActions.Noop)
            // 举报时间和处理时间
            onNodeWithText("10 月 12 日 18:00").assertIsDisplayed()
            onNodeWithText("10 月 13 日 18:00").assertIsDisplayed()
        }
    }

    @Test
    fun `report result times of another year include the year`() = withSimplifiedChinese {
        runAniComposeUiTest {
            showDetail(
                createTestReportResult(createdAtMillis = localMillis("2025-12-31T09:05")),
                NotificationActions.Noop,
            )
            onNodeWithText("2025 年 12 月 30 日 09:05").assertIsDisplayed()
            onNodeWithText("2025 年 12 月 31 日 09:05").assertIsDisplayed()
            onNodeWithTag(NotificationTestTags.REPORT_SNAPSHOT_TIME, useUnmergedTree = true)
                .assertTextEquals("2025 年 12 月 30 日 08:43")
        }
    }

    @Test
    fun `snapshot shows the comment time after the nickname`() = withSimplifiedChinese {
        runAniComposeUiTest {
            showDetail(createTestReportResult(), NotificationActions.Noop)
            val author = onNodeWithTag(NotificationTestTags.REPORT_SNAPSHOT_AUTHOR, useUnmergedTree = true)
            val time = onNodeWithTag(NotificationTestTags.REPORT_SNAPSHOT_TIME, useUnmergedTree = true)
            author.assertTextEquals("星野")
            time.assertTextEquals("10 月 12 日 17:38")

            val authorBounds = author.fetchSemanticsNode().boundsInRoot
            val timeBounds = time.fetchSemanticsNode().boundsInRoot
            assertTrue(timeBounds.left >= authorBounds.right, "time should follow the nickname")
            assertTrue(timeBounds.top < authorBounds.bottom && authorBounds.top < timeBounds.bottom, "same line")
            assertEquals(FontWeight.Medium, author.textFontWeight())
            assertEquals(FontWeight.Normal, time.textFontWeight())
        }
    }

    @Test
    fun `snapshot without comment time shows only the nickname`() = runAniComposeUiTest {
        showDetail(createTestReportResult(contentCreatedAtMillis = null), NotificationActions.Noop)
        onNodeWithTag(NotificationTestTags.REPORT_SNAPSHOT_AUTHOR, useUnmergedTree = true).assertTextEquals("星野")
        assertTrue(
            onAllNodesWithTag(NotificationTestTags.REPORT_SNAPSHOT_TIME, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `system and announcement details use the detail time format`() = withSimplifiedChinese {
        runAniComposeUiTest {
            setContent {
                NotificationTestHost {
                    Column {
                        NotificationDetailContent(createTestSystemNotification(), NotificationActions.Noop)
                        AnnouncementDetailContent(
                            createTestAnnouncement(publishedAtMillis = localMillis("2025-10-11T19:02")),
                        )
                    }
                }
            }
            // 系统消息: 40 天前, 当地时间 20:00
            onNodeWithText("9 月 3 日 20:00").assertExists()
            onNodeWithText("2025 年 10 月 11 日 19:02").assertExists()
        }
    }

    @Test
    fun `episode comment report opens the episode`() = runAniComposeUiTest {
        val recorded = RecordedActions()
        showDetail(createTestReportResult(), recorded.actions)
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertTextEquals(string(Lang.notification_go_to_episode))
            .performClick()
        assertEquals(listOf("episode:400602:1227087"), recorded.calls)
    }

    @Test
    fun `subject review report opens the subject`() = runAniComposeUiTest {
        val recorded = RecordedActions()
        showDetail(
            createTestReportResult(targetType = CommentReportTargetType.SUBJECT_REVIEW, episodeId = null, episodeSort = null),
            recorded.actions,
        )
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertTextEquals(string(Lang.notification_go_to_subject))
            .performClick()
        assertEquals(listOf("subject:400602:葬送的芙莉莲"), recorded.calls)
    }

    @Test
    fun `person and character reports open the person or character`() = runAniComposeUiTest {
        val recorded = RecordedActions()
        var notification by mutableStateOf(
            createTestReportResult(
                targetType = CommentReportTargetType.PERSON_COMMENT,
                subjectId = null, episodeId = null, episodeSort = null,
                personId = 5, targetName = "种崎敦美",
            ),
        )
        setContent {
            NotificationTestHost {
                NotificationDetailContent(notification, recorded.actions)
            }
        }
        onNodeWithText("种崎敦美").assertIsDisplayed()
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertTextEquals(string(Lang.notification_go_to_person))
            .performClick()

        runOnIdle {
            notification = createTestReportResult(
                targetType = CommentReportTargetType.CHARACTER_COMMENT,
                subjectId = null, episodeId = null, episodeSort = null,
                characterId = 6, targetName = "芙莉莲",
            )
        }
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertTextEquals(string(Lang.notification_go_to_character))
            .performClick()
        assertEquals(listOf("person:5", "character:6"), recorded.calls)
    }

    @Test
    fun `report action can be shown in the top bar instead of the bottom`() = runAniComposeUiTest {
        val recorded = RecordedActions()
        val notification = createTestReportResult()
        setContent {
            NotificationTestHost {
                Column {
                    Row {
                        NotificationDetailTopBarActions(notification, recorded.actions)
                    }
                    NotificationDetailContent(notification, recorded.actions, reportActionInTopBar = true)
                }
            }
        }
        assertNoAction()
        onNodeWithTag(NotificationTestTags.DETAIL_TOP_BAR_ACTION)
            .assertContentDescriptionEquals(string(Lang.notification_go_to_episode))
            .performClick()
        assertEquals(listOf("episode:400602:1227087"), recorded.calls)
    }

    @Test
    fun `top bar has no action for notifications without a report target`() = runAniComposeUiTest {
        setContent {
            NotificationTestHost {
                Row {
                    NotificationDetailTopBarActions(createTestSystemNotification(), NotificationActions.Noop)
                    NotificationDetailTopBarActions(createTestBangumiExpired(), NotificationActions.Noop)
                    NotificationDetailTopBarActions(
                        createTestReportResult(subjectId = null, episodeId = null),
                        NotificationActions.Noop,
                    )
                    NotificationDetailTopBarActions(unknownTargetTypeReport(target = null), NotificationActions.Noop)
                }
            }
        }
        assertNoTopBarAction()
    }

    @Test
    fun `detail screen keeps the report action at the bottom`() = runAniComposeUiTest {
        val recorded = RecordedActions()
        setContent {
            NotificationTestHost {
                NotificationDetailScreen(
                    state = DetailLoadState.Loaded(createTestReportResult()),
                    actions = recorded.actions,
                    onRetry = {},
                )
            }
        }
        assertNoTopBarAction()
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertTextEquals(string(Lang.notification_go_to_episode))
            .performClick()
        assertEquals(listOf("episode:400602:1227087"), recorded.calls)
    }

    @Test
    fun `report without target ids has no navigation button`() = runAniComposeUiTest {
        showDetail(
            createTestReportResult(
                targetType = CommentReportTargetType.CHARACTER_COMMENT,
                subjectId = null, episodeId = null, episodeSort = null, characterId = null,
            ),
            NotificationActions.Noop,
        )
        onNodeWithTag(NotificationTestTags.REPORT_TARGET).assertIsDisplayed()
        assertNoAction()
    }

    /**
     * 不认识的评论类型. 被举报的对象和跳转的位置来自通用展示内容.
     */
    private fun unknownTargetTypeReport(
        body: String? = "芙莉莲的角色评论",
        target: NotificationTarget? = NotificationTarget(characterId = 6),
    ): AppNotification = createTestReportResult(
        targetType = null,
        reason = CommentReportReason.OTHER,
        subjectId = null, subjectName = null, subjectNameCn = null, episodeId = null, episodeSort = null,
        personId = 5,
        characterId = 6,
        targetName = "芙莉莲",
    ).let { it.copy(display = it.display.copy(body = body, target = target)) }

    @Test
    fun `report with unknown target type shows the display target and destination`() = runAniComposeUiTest {
        val recorded = RecordedActions()
        showDetail(unknownTargetTypeReport(), recorded.actions)
        assertFieldValue(NotificationTestTags.REPORT_TARGET, "芙莉莲的角色评论")
        assertFieldValue(NotificationTestTags.REPORT_REASON, string(Lang.comment_report_reason_other))
        assertTrue(onAllNodesWithText("芙莉莲").fetchSemanticsNodes().isEmpty())
        // 类型是认识的, 不提示更新
        assertTrue(onAllNodesWithTag(NotificationTestTags.DETAIL_UPDATE_HINT).fetchSemanticsNodes().isEmpty())
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertTextEquals(string(Lang.notification_go_to_character))
            .performClick()
        assertEquals(listOf("character:6"), recorded.calls)
    }

    @Test
    fun `report with unknown target type opens a display url in the browser`() = runAniComposeUiTest {
        val browser = RecordingBrowserNavigator()
        setContent {
            CompositionLocalProvider(LocalBrowserNavigator provides browser) {
                NotificationTestHost {
                    NotificationDetailContent(
                        unknownTargetTypeReport(target = NotificationTarget(url = "https://myani.org/post/1")),
                        NotificationActions.Noop,
                    )
                }
            }
        }
        assertNoAction()
        onNodeWithTag(NotificationTestTags.DETAIL_LINK).assertTextEquals(string(Lang.notification_view_details))
            .performClick()
        waitUntil { browser.openedUrls.isNotEmpty() }
        assertEquals(listOf("https://myani.org/post/1"), browser.openedUrls.toList())
    }

    @Test
    fun `report with unknown target type and no display content shows a generic target`() = runAniComposeUiTest {
        showDetail(unknownTargetTypeReport(body = null, target = null), NotificationActions.Noop)
        assertFieldValue(NotificationTestTags.REPORT_TARGET, string(Lang.notification_report_target_comment))
        assertNoAction()
        assertTrue(onAllNodesWithTag(NotificationTestTags.DETAIL_LINK).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `report with unknown target type shows the display destination in the top bar`() = runAniComposeUiTest {
        val recorded = RecordedActions()
        val notification = unknownTargetTypeReport(target = NotificationTarget(personId = 5))
        setContent {
            NotificationTestHost {
                Column {
                    Row {
                        NotificationDetailTopBarActions(notification, recorded.actions)
                    }
                    NotificationDetailContent(notification, recorded.actions, reportActionInTopBar = true)
                }
            }
        }
        assertNoAction()
        onNodeWithTag(NotificationTestTags.DETAIL_TOP_BAR_ACTION)
            .assertContentDescriptionEquals(string(Lang.notification_go_to_person))
            .performClick()
        assertEquals(listOf("person:5"), recorded.calls)
    }

    @Test
    fun `bangumi authorization expired uses client text and offers reauthorization`() = runAniComposeUiTest {
        val recorded = RecordedActions()
        showDetail(
            createTestSystemNotification(
                title = "服务端标题",
                body = "服务端正文",
                linkUrl = "https://myani.org",
                event = SystemNotificationEvent.BANGUMI_AUTHORIZATION_EXPIRED,
            ),
            recorded.actions,
        )
        onNodeWithText(string(Lang.notification_system_bangumi_expired)).assertIsDisplayed()
        onNodeWithText(string(Lang.notification_system_bangumi_expired_supporting)).assertExists()
        assertTrue(onAllNodesWithText("服务端", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(onAllNodesWithTag(NotificationTestTags.DETAIL_LINK).fetchSemanticsNodes().isEmpty())
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION)
            .assertTextEquals(string(Lang.notification_system_bangumi_reauthorize))
            .performClick()
        assertEquals(listOf("reauthorize"), recorded.calls)
    }

    @Test
    fun `system notification shows linked body and details link`() = runAniComposeUiTest {
        showDetail(createTestSystemNotification(), NotificationActions.Noop)
        onNodeWithText("欢迎加入 Animeko").assertIsDisplayed()
        onNodeWithText("https://myani.org/help", substring = true).assertExists()
        onNodeWithTag(NotificationTestTags.DETAIL_LINK).assertExists()
    }

    @Test
    fun `system notification without link has no details button`() = runAniComposeUiTest {
        showDetail(createTestSystemNotification(linkUrl = null), NotificationActions.Noop)
        assertTrue(onAllNodesWithTag(NotificationTestTags.DETAIL_LINK).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `detail screen loads the notification by id`() = runAniComposeUiTest {
        val vm = NotificationDetailViewModel("bangumi-expired", notificationTestKoin())
        setContent {
            NotificationTestHost {
                NotificationDetailScreen(vm, NotificationActions.Noop)
            }
        }
        waitUntil { onAllNodes(hasTestTag(NotificationTestTags.DETAIL)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION)
            .assertTextEquals(string(Lang.notification_system_bangumi_reauthorize))
    }

    @Test
    fun `detail screen for a missing notification shows a hint without retry`() = runAniComposeUiTest {
        val vm = NotificationDetailViewModel("missing", notificationTestKoin())
        setContent {
            NotificationTestHost {
                NotificationDetailScreen(vm, NotificationActions.Noop)
            }
        }
        waitUntil { onAllNodesWithTag(NotificationTestTags.DETAIL_NOT_FOUND).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(string(Lang.notification_not_found)).assertIsDisplayed()
        assertTrue(onAllNodesWithText(string(Lang.settings_mediasource_retry)).fetchSemanticsNodes().isEmpty())
        assertTrue(onAllNodesWithText(string(Lang.settings_mediasource_copy)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `not found state renders without a retry button`() = runAniComposeUiTest {
        var retries = 0
        setContent {
            NotificationTestHost {
                NotificationDetailScreen(
                    state = DetailLoadState.NotFound,
                    actions = NotificationActions.Noop,
                    onRetry = { retries++ },
                )
            }
        }
        onNodeWithTag(NotificationTestTags.DETAIL_NOT_FOUND).assertIsDisplayed()
        assertTrue(onAllNodesWithText(string(Lang.settings_mediasource_retry)).fetchSemanticsNodes().isEmpty())
        assertEquals(0, retries)
    }

    // region types without their own rendering

    @Test
    fun `generic detail shows the generic content in order`() = withSimplifiedChinese {
        runAniComposeUiTest {
            showDetail(createTestGenericNotification(), NotificationActions.Noop)

            val overline = onNodeWithTag(NotificationTestTags.DETAIL_OVERLINE, useUnmergedTree = true)
                .assertTextEquals("葬送的芙莉莲 第 1 话")
            val title = onNodeWithText("Bob 回复了你的评论").assertIsDisplayed()
            // 30 分钟前, 当地时间 19:30
            val time = onNodeWithText("10 月 13 日 19:30").assertIsDisplayed()
            val body = onNodeWithText("同意，这一集的作画太好了").assertIsDisplayed()
            val action = onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertIsDisplayed()
            val hint = onNodeWithTag(NotificationTestTags.DETAIL_UPDATE_HINT).assertIsDisplayed()

            val tops = listOf(overline, title, time, body, action, hint).map { it.fetchSemanticsNode().boundsInRoot.top }
            assertEquals(tops.sorted(), tops, "overline, title, time, body, action and hint should be in order: $tops")
            // 没有图片时只有图标
            assertTrue(onAllNodesWithTag(NotificationTestTags.LEADING_IMAGE, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        }
    }

    @Test
    fun `generic detail shows the image and omits missing parts`() = runAniComposeUiTest {
        showDetail(
            createTestGenericNotification(
                overline = null,
                body = null,
                imageUrl = "https://example.com/avatar.png",
                target = null,
            ),
            NotificationActions.Noop,
        )
        onNodeWithText("Bob 回复了你的评论").assertIsDisplayed()
        onNodeWithTag(NotificationTestTags.LEADING_IMAGE, useUnmergedTree = true).assertExists()
        assertTrue(onAllNodesWithTag(NotificationTestTags.DETAIL_OVERLINE, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertNoAction()
        assertTrue(onAllNodesWithTag(NotificationTestTags.DETAIL_LINK).fetchSemanticsNodes().isEmpty())
        // 仍然提示更新
        onNodeWithTag(NotificationTestTags.DETAIL_UPDATE_HINT).assertIsDisplayed()
    }

    @Test
    fun `generic detail body is selectable and links its urls`() = runAniComposeUiTest {
        showDetail(
            createTestGenericNotification(body = "第一行\n详情见 https://myani.org/help"),
            NotificationActions.Noop,
        )
        val body = onNodeWithText("https://myani.org/help", substring = true).fetchSemanticsNode()
        val text = body.config[SemanticsProperties.Text].single()
        assertEquals("第一行\n详情见 https://myani.org/help", text.text)
        assertEquals(1, text.getLinkAnnotations(0, text.length).size)
    }

    @Test
    fun `unknown type detail asks to update and check update opens the update settings`() = withSimplifiedChinese {
        runAniComposeUiTest {
            val recorded = RecordedActions()
            showDetail(createTestGenericNotification(rawType = "future_type"), recorded.actions)

            onNodeWithText("当前版本可能无法完整显示这条通知，更新 Animeko 后可查看完整内容。").assertIsDisplayed()
            onNodeWithTag(NotificationTestTags.DETAIL_CHECK_UPDATE).assertTextEquals("检查更新").performClick()
            assertEquals(listOf("check-update"), recorded.calls)
        }
    }

    @Test
    fun `check update navigates to the software update settings`() = runAniComposeUiTest {
        lateinit var navigator: AniNavigator
        setContent {
            NotificationTestHost {
                navigator = LocalNavigator.current
                NotificationDetailContent(createTestGenericNotification(), rememberNotificationActions(navigator))
            }
        }
        onNodeWithTag(NotificationTestTags.DETAIL_CHECK_UPDATE).performClick()
        runOnIdle {
            assertEquals(NavRoutes.Settings(SettingsTab.UPDATE), navigator.backStack.last())
        }
    }

    @Test
    fun `known type shown from the generic content does not ask to update`() = runAniComposeUiTest {
        // 认识的类型缺少自己的内容
        showDetail(
            createTestGenericNotification(
                rawType = NotificationType.REPORT_RESULT.wireValue,
                rawCategory = NotificationGroup.NOTICE.wireValue,
                title = "你举报的评论已被处理",
            ),
            NotificationActions.Noop,
        )
        onNodeWithText("你举报的评论已被处理").assertIsDisplayed()
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertIsDisplayed()
        assertTrue(onAllNodesWithTag(NotificationTestTags.DETAIL_UPDATE_HINT).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `generic detail goes to the most specific destination`() = runAniComposeUiTest {
        val recorded = RecordedActions()
        val browser = RecordingBrowserNavigator()
        var notification by mutableStateOf(
            createTestGenericNotification(target = NotificationTarget(subjectId = 1, episodeId = 2, personId = 9, url = "https://a.example")),
        )
        setContent {
            CompositionLocalProvider(LocalBrowserNavigator provides browser) {
                NotificationTestHost {
                    NotificationDetailContent(notification, recorded.actions)
                }
            }
        }

        fun goTo(label: StringResource) {
            assertTrue(onAllNodesWithTag(NotificationTestTags.DETAIL_LINK).fetchSemanticsNodes().isEmpty())
            onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertTextEquals(string(label)).performClick()
        }

        goTo(Lang.notification_go_to_episode)
        runOnIdle { notification = createTestGenericNotification(target = NotificationTarget(subjectId = 3, personId = 9)) }
        goTo(Lang.notification_go_to_subject)
        runOnIdle { notification = createTestGenericNotification(target = NotificationTarget(personId = 4, characterId = 9)) }
        goTo(Lang.notification_go_to_person)
        runOnIdle { notification = createTestGenericNotification(target = NotificationTarget(characterId = 5, url = "https://a.example")) }
        goTo(Lang.notification_go_to_character)
        assertEquals(listOf("episode:1:2", "subject:3:null", "person:4", "character:5"), recorded.calls)

        // 网页链接在浏览器中打开
        runOnIdle { notification = createTestGenericNotification(target = NotificationTarget(url = " https://myani.org/post/1 ")) }
        assertNoAction()
        onNodeWithTag(NotificationTestTags.DETAIL_LINK).assertTextEquals(string(Lang.notification_view_details)).performClick()
        waitUntil { browser.openedUrls.isNotEmpty() }
        assertEquals(listOf("https://myani.org/post/1"), browser.openedUrls.toList())

        // 只接受 http(s) 链接; 没有位置时没有按钮
        for (target in listOf(NotificationTarget(url = "ftp://myani.org"), NotificationTarget(), null)) {
            runOnIdle { notification = createTestGenericNotification(target = target) }
            waitForIdle()
            assertNoAction()
            assertTrue(onAllNodesWithTag(NotificationTestTags.DETAIL_LINK).fetchSemanticsNodes().isEmpty(), "$target")
        }
        assertEquals(4, recorded.calls.size)
        assertEquals(1, browser.openedUrls.size)
    }

    @Test
    fun `system message goes to its destination`() = runAniComposeUiTest {
        val recorded = RecordedActions()
        showDetail(
            createTestSystemNotification(linkUrl = null).let {
                it.copy(display = it.display.copy(target = NotificationTarget(subjectId = 7)))
            },
            recorded.actions,
        )
        assertTrue(onAllNodesWithTag(NotificationTestTags.DETAIL_LINK).fetchSemanticsNodes().isEmpty())
        assertTrue(onAllNodesWithTag(NotificationTestTags.DETAIL_UPDATE_HINT).fetchSemanticsNodes().isEmpty())
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertTextEquals(string(Lang.notification_go_to_subject))
            .performClick()
        assertEquals(listOf("subject:7:null"), recorded.calls)
    }

    @Test
    fun `detail screen shows an unknown type loaded by id`() = runAniComposeUiTest {
        val repository = FakeNotificationRepository(notifications = TestNotifications + TestInteractionNotifications)
        val vm = NotificationDetailViewModel("reply-1", notificationTestKoin(repository))
        setContent {
            NotificationTestHost {
                NotificationDetailScreen(vm, NotificationActions.Noop)
            }
        }
        waitUntil { onAllNodes(hasTestTag(NotificationTestTags.DETAIL)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Bob 回复了你的评论").assertIsDisplayed()
        onNodeWithTag(NotificationTestTags.DETAIL_UPDATE_HINT).assertIsDisplayed()
        onNodeWithText(string(Lang.notification_title)).assertIsDisplayed()
    }

    // endregion
}
