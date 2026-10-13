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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import me.him188.ani.app.data.models.notification.SystemNotificationEvent
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_report_reason_other
import me.him188.ani.app.ui.lang.comment_report_reason_spoiler
import me.him188.ani.app.ui.lang.notification_not_found
import me.him188.ani.app.ui.lang.notification_report_go_to_character
import me.him188.ani.app.ui.lang.notification_report_go_to_episode
import me.him188.ani.app.ui.lang.notification_report_go_to_person
import me.him188.ani.app.ui.lang.notification_report_go_to_subject
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
            reportTargetText(notification.content as AppNotificationContent.ReportResult, false)
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
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertTextEquals(string(Lang.notification_report_go_to_episode))
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
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertTextEquals(string(Lang.notification_report_go_to_subject))
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
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertTextEquals(string(Lang.notification_report_go_to_person))
            .performClick()

        runOnIdle {
            notification = createTestReportResult(
                targetType = CommentReportTargetType.CHARACTER_COMMENT,
                subjectId = null, episodeId = null, episodeSort = null,
                characterId = 6, targetName = "芙莉莲",
            )
        }
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertTextEquals(string(Lang.notification_report_go_to_character))
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
            .assertContentDescriptionEquals(string(Lang.notification_report_go_to_episode))
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
        onNodeWithTag(NotificationTestTags.DETAIL_ACTION).assertTextEquals(string(Lang.notification_report_go_to_episode))
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

    @Test
    fun `report with unknown target type shows a generic target and no navigation button`() = runAniComposeUiTest {
        showDetail(
            createTestReportResult(
                targetType = null,
                reason = CommentReportReason.OTHER,
                personId = 5,
                characterId = 6,
                targetName = "芙莉莲",
            ),
            NotificationActions.Noop,
        )
        assertFieldValue(NotificationTestTags.REPORT_TARGET, string(Lang.notification_report_target_comment))
        assertFieldValue(NotificationTestTags.REPORT_REASON, string(Lang.comment_report_reason_other))
        assertTrue(onAllNodesWithText("芙莉莲").fetchSemanticsNodes().isEmpty())
        assertNoAction()
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
}
