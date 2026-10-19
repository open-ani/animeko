/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import me.him188.ani.app.data.models.comment.CommentReportReason
import me.him188.ani.app.data.models.comment.CommentReportTargetType
import me.him188.ani.app.data.models.notification.AccountSecurityEvent
import me.him188.ani.app.data.models.notification.Announcement
import me.him188.ani.app.data.models.notification.AnnouncementTag
import me.him188.ani.app.data.models.notification.AppNotification
import me.him188.ani.app.data.models.notification.AppNotificationContent
import me.him188.ani.app.data.models.notification.NotificationDisplay
import me.him188.ani.app.data.models.notification.NotificationGroup
import me.him188.ani.app.data.models.notification.NotificationIcon
import me.him188.ani.app.data.models.notification.NotificationTarget
import me.him188.ani.app.data.models.notification.NotificationType
import me.him188.ani.app.data.models.notification.SystemNotificationEvent
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AniNotificationServiceTest {
    private val noExclusions = emptySet<NotificationType>()

    @Test
    fun `list maps every known type with its display content`() = runTest {
        val server = NotificationTestServer { respondJson(KNOWN_PAGE) }

        val page = server.list()

        assertEquals("998:n3", page.nextCursor)
        assertEquals(
            listOf(
                AppNotification(
                    id = "n1",
                    rawType = "report_result",
                    rawCategory = "notice",
                    createdAtMillis = 1000,
                    read = false,
                    display = NotificationDisplay(
                        title = "你举报的评论已被处理",
                        body = "葬送的芙莉莲 第 12 话",
                        icon = NotificationIcon.REPORT,
                        target = NotificationTarget(subjectId = 400602, episodeId = 1227087),
                    ),
                    content = AppNotificationContent.ReportResult(
                        targetType = CommentReportTargetType.EPISODE_COMMENT,
                        reason = CommentReportReason.SPOILER,
                        reportedAtMillis = 900,
                        handledAtMillis = 950,
                        contentRemoved = true,
                        contentSnapshot = "小明：剧透",
                        contentCreatedAtMillis = 880,
                        subjectId = 400602,
                        subjectName = "Sousou no Frieren",
                        subjectNameCn = "葬送的芙莉莲",
                        episodeId = 1227087,
                        episodeSort = "12",
                    ),
                ),
                AppNotification(
                    id = "n2",
                    rawType = "account_security",
                    rawCategory = "notice",
                    createdAtMillis = 999,
                    read = true,
                    display = NotificationDisplay(
                        title = "Bangumi 账号已解绑",
                        body = "该账号已登录另一个 Animeko 账号",
                        icon = NotificationIcon.SECURITY,
                    ),
                    content = AppNotificationContent.AccountSecurity(
                        event = AccountSecurityEvent.THIRD_PARTY_UNBOUND,
                        rawEvent = "third_party_unbound",
                        occurredAtMillis = 998,
                        provider = "bangumi",
                    ),
                ),
                AppNotification(
                    id = "n3",
                    rawType = "system",
                    rawCategory = "notice",
                    createdAtMillis = 998,
                    read = false,
                    display = NotificationDisplay(
                        title = "维护通知",
                        body = "第一行\n第二行",
                        icon = NotificationIcon.INFO,
                        target = NotificationTarget(url = "https://example.com"),
                    ),
                    content = AppNotificationContent.System(event = null),
                ),
                AppNotification(
                    id = "n4",
                    rawType = "report_result",
                    rawCategory = "notice",
                    createdAtMillis = 997,
                    read = false,
                    display = NotificationDisplay(
                        title = "你举报的评论已被处理",
                        body = "芙莉莲",
                        icon = NotificationIcon.REPORT,
                        target = NotificationTarget(characterId = 8),
                    ),
                    content = AppNotificationContent.ReportResult(
                        targetType = CommentReportTargetType.CHARACTER_COMMENT,
                        reason = CommentReportReason.HARASSMENT,
                        reportedAtMillis = 1,
                        handledAtMillis = 2,
                        contentRemoved = false,
                        characterId = 8,
                        targetName = "芙莉莲",
                    ),
                ),
            ),
            page.items,
        )
    }

    @Test
    fun `list requests one category and omits excludeTypes when nothing is excluded`() = runTest {
        val server = NotificationTestServer { respondJson("""{"items":[]}""") }

        val first = server.list()
        server.list(NotificationGroup.INTERACTION, after = "998:n3", limit = 100)

        assertNull(first.nextCursor)
        val (firstRequest, secondRequest) = server.requests
        assertEquals(HttpMethod.Get, firstRequest.method)
        assertEquals("/v2/notifications", firstRequest.url.encodedPath)
        assertEquals(listOf("notice"), firstRequest.url.parameters.getAll("category"))
        assertFalse("excludeTypes" in firstRequest.url.parameters)
        assertFalse("types" in firstRequest.url.parameters)
        assertEquals("30", firstRequest.url.parameters["limit"])
        assertNull(firstRequest.url.parameters["after"])

        assertEquals(listOf("interaction"), secondRequest.url.parameters.getAll("category"))
        assertEquals("998:n3", secondRequest.url.parameters["after"])
        assertEquals("100", secondRequest.url.parameters["limit"])
    }

    @Test
    fun `list sends the excluded types as one comma separated parameter`() = runTest {
        val server = NotificationTestServer { respondJson("""{"items":[]}""") }

        server.list(excludeTypes = setOf(NotificationType.REPORT_RESULT, NotificationType.ACCOUNT_SECURITY))

        val request = server.requests.single()
        assertEquals(listOf("notice"), request.url.parameters.getAll("category"))
        assertEquals(listOf("report_result,account_security"), request.url.parameters.getAll("excludeTypes"))
    }

    @Test
    fun `types without their own rendering use the display content`() = runTest {
        val server = NotificationTestServer { respondJson(INTERACTION_PAGE) }

        val items = server.list(NotificationGroup.INTERACTION).items

        assertEquals(
            AppNotification(
                id = "i1",
                rawType = "comment_reply",
                rawCategory = "interaction",
                createdAtMillis = 3,
                read = false,
                display = NotificationDisplay(
                    title = "Bob 回复了你的评论",
                    body = "同意\n作画很好",
                    overline = "葬送的芙莉莲 第 1 话",
                    icon = NotificationIcon.REPLY,
                    imageUrl = "https://example.com/bob.png",
                    target = NotificationTarget(subjectId = 400602, episodeId = 1227087),
                ),
                content = AppNotificationContent.Unknown,
            ),
            items[0],
        )
        assertNull(items[0].type)
        assertEquals(NotificationGroup.INTERACTION, items[0].group)

        // 未来的类型和图标, 以及目标中未来增加的字段
        val future = items[1]
        assertEquals("future_type", future.rawType)
        assertNull(future.type)
        assertEquals(AppNotificationContent.Unknown, future.content)
        assertEquals(
            NotificationDisplay(title = "新功能", target = NotificationTarget(personId = 5)),
            future.display,
        )
        assertEquals(NotificationGroup.INTERACTION, future.group)
    }

    @Test
    fun `known types without their content use the display content`() = runTest {
        val server = NotificationTestServer { respondJson(MISSING_CONTENT_PAGE) }

        val items = server.list().items

        assertEquals(listOf("no-payload", "wrong-payload", "ok"), items.map { it.id })
        assertEquals(
            listOf(AppNotificationContent.Unknown, AppNotificationContent.Unknown, AppNotificationContent.System()),
            items.map { it.content },
        )
        assertEquals(NotificationType.SYSTEM, items[0].type)
        assertEquals(NotificationType.REPORT_RESULT, items[1].type)
        assertEquals("报告", items[1].display.title)
    }

    @Test
    fun `unknown wire values fall back instead of failing the page`() = runTest {
        val server = NotificationTestServer { respondJson(UNKNOWN_VALUES_PAGE) }

        val items = server.list().items

        val report = assertIs<AppNotificationContent.ReportResult>(items[0].content)
        assertNull(report.targetType)
        assertEquals(CommentReportReason.OTHER, report.reason)

        val unknownEvent = assertIs<AppNotificationContent.AccountSecurity>(items[1].content)
        assertEquals(AccountSecurityEvent.UNKNOWN, unknownEvent.event)
        assertEquals("password_changed", unknownEvent.rawEvent)
        assertNull(items[1].display.icon)

        // 不认识的系统消息事件用通用展示内容
        assertEquals(AppNotificationContent.System(event = null), items[2].content)
        assertEquals(NotificationDisplay(title = "标题", body = "正文"), items[2].display)

        val unbound = assertIs<AppNotificationContent.AccountSecurity>(items[3].content)
        assertEquals(AccountSecurityEvent.THIRD_PARTY_UNBOUND, unbound.event)
        assertEquals("github", unbound.provider)
    }

    @Test
    fun `system notification maps known events`() = runTest {
        val server = NotificationTestServer {
            respondJson(
                """
                {"items":[
                  {"id":"s1","type":"system","category":"notice","createdAtMillis":2,"read":false,
                   "title":"Bangumi 授权已过期","body":"正文","icon":"info",
                   "system":{"event":"bangumi_authorization_expired"}},
                  {"id":"s2","type":"system","category":"notice","createdAtMillis":1,"read":false,
                   "title":"标题","system":{}}
                ]}
                """.trimIndent(),
            )
        }

        val items = server.list().items

        assertEquals(
            listOf(SystemNotificationEvent.BANGUMI_AUTHORIZATION_EXPIRED, null),
            items.map { assertIs<AppNotificationContent.System>(it.content).event },
        )
        assertEquals("Bangumi 授权已过期", items[0].display.title)
        assertEquals(NotificationDisplay(title = "标题"), items[1].display)
    }

    @Test
    fun `get maps one notification and returns null for missing ones`() = runTest {
        var status = HttpStatusCode.OK
        var body = """{"id":"n3","type":"system","category":"notice","createdAtMillis":998,"read":true,"title":"标题",
            "body":"正文","system":{}}"""
        val server = NotificationTestServer { respondJson(body, status) }

        val notification = server.notificationService.getNotification("n3")
        assertEquals(AppNotificationContent.System(), notification?.content)
        assertEquals(NotificationDisplay(title = "标题", body = "正文"), notification?.display)
        assertEquals("/v2/notifications/n3", server.requests.single().url.encodedPath)

        body = """{"id":"n5","type":"system","category":"notice","createdAtMillis":998,"read":true,"title":"标题"}"""
        assertEquals(AppNotificationContent.Unknown, server.notificationService.getNotification("n5")?.content)

        body = """{"id":"n6","type":"mention","category":"interaction","createdAtMillis":998,"read":true,
            "title":"提及"}"""
        val mention = server.notificationService.getNotification("n6")
        assertEquals(AppNotificationContent.Unknown, mention?.content)
        assertEquals("mention", mention?.rawType)

        // 不存在
        status = HttpStatusCode.NotFound
        body = """{"message":"Notification not found"}"""
        assertNull(server.notificationService.getNotification("00000000-0000-0000-0000-000000000000"))

        // ID 不是 UUID
        status = HttpStatusCode.BadRequest
        body = """{"message":"Invalid notification id"}"""
        assertNull(server.notificationService.getNotification("missing"))

        status = HttpStatusCode.Unauthorized
        assertFailsWith<RepositoryAuthorizationException> { server.notificationService.getNotification("n3") }
    }

    @Test
    fun `unread summary maps counts per category and the latest announcement`() = runTest {
        val server = NotificationTestServer {
            respondJson(
                """
                {"counts":[{"category":"interaction","count":2},{"category":"notice","count":3}],
                "unreadAnnouncementCount":1,
                "latestAnnouncement":{"id":"a1","tag":"festival","title":"标题","body":"正文","pinned":true,
                "publishedAtMillis":5,"read":false}}
                """.trimIndent(),
            )
        }

        val summary = server.notificationService.getUnreadSummary(NotificationGroup.TABS, noExclusions)

        assertEquals(mapOf(NotificationGroup.INTERACTION to 2, NotificationGroup.NOTICE to 3), summary.unreadCounts)
        assertEquals(1, summary.unreadAnnouncementCount)
        assertEquals(
            Announcement(
                id = "a1",
                tag = AnnouncementTag.UNKNOWN,
                rawTag = "festival",
                title = "标题",
                body = "正文",
                pinned = true,
                publishedAtMillis = 5,
                read = false,
            ),
            summary.latestAnnouncement,
        )
        val request = server.requests.single()
        assertEquals("/v2/notifications/unread-summary", request.url.encodedPath)
        assertEquals(listOf("interaction,notice"), request.url.parameters.getAll("categories"))
        assertFalse("excludeTypes" in request.url.parameters)
        assertFalse("types" in request.url.parameters)
    }

    @Test
    fun `unread summary sends the excluded types and keeps only the requested categories`() = runTest {
        val server = NotificationTestServer {
            respondJson(
                """
                {"counts":[{"category":"notice","count":4},{"category":"direct_message","count":5},
                {"category":"interaction","count":6},{"category":"future_category","count":7}],
                "unreadAnnouncementCount":0}
                """.trimIndent(),
            )
        }

        val summary = server.notificationService.getUnreadSummary(
            listOf(NotificationGroup.NOTICE),
            setOf(NotificationType.ACCOUNT_SECURITY),
        )

        assertEquals(mapOf(NotificationGroup.NOTICE to 4), summary.unreadCounts)
        val request = server.requests.single()
        assertEquals(listOf("notice"), request.url.parameters.getAll("categories"))
        assertEquals(listOf("account_security"), request.url.parameters.getAll("excludeTypes"))
    }

    @Test
    fun `unread summary without categories requests no notification counts`() = runTest {
        val server = NotificationTestServer { respondJson("""{"counts":[],"unreadAnnouncementCount":2}""") }

        val summary = server.notificationService.getUnreadSummary(emptyList(), noExclusions)

        assertEquals(emptyMap(), summary.unreadCounts)
        assertEquals(2, summary.unreadAnnouncementCount)
        assertFalse("categories" in server.requests.single().url.parameters)
    }

    @Test
    fun `guest unread summary has no counts`() = runTest {
        val server = NotificationTestServer { respondJson("""{"counts":[],"unreadAnnouncementCount":0}""") }

        val summary = server.notificationService.getUnreadSummary(NotificationGroup.TABS, noExclusions)

        assertEquals(emptyMap(), summary.unreadCounts)
        assertEquals(0, summary.unreadAnnouncementCount)
        assertNull(summary.latestAnnouncement)
    }

    @Test
    fun `mark read sends the ids`() = runTest {
        val server = NotificationTestServer()

        server.notificationService.markNotificationsRead(listOf("n1", "n2"))

        val request = server.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/v2/notifications/read", request.url.encodedPath)
        assertEquals(
            JsonArray(listOf(JsonPrimitive("n1"), JsonPrimitive("n2"))),
            request.bodyJson()["ids"],
        )
    }

    @Test
    fun `mark all read sends the category excluded types and time`() = runTest {
        val server = NotificationTestServer()

        server.notificationService.markAllNotificationsRead(
            NotificationGroup.NOTICE,
            setOf(NotificationType.REPORT_RESULT),
            beforeMillis = 1000,
        )
        server.notificationService.markAllNotificationsRead(
            NotificationGroup.INTERACTION,
            noExclusions,
            beforeMillis = null,
        )

        val (withTime, withoutTime) = server.requests
        assertEquals(HttpMethod.Post, withTime.method)
        assertEquals("/v2/notifications/read-all", withTime.url.encodedPath)
        val withTimeBody = withTime.bodyJson()
        assertEquals("notice", withTimeBody.getValue("category").jsonPrimitive.content)
        assertEquals(
            listOf("report_result"),
            withTimeBody.getValue("excludeTypes").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(1000, withTimeBody.getValue("beforeMillis").jsonPrimitive.long)
        assertFalse("types" in withTimeBody)

        val withoutTimeBody = withoutTime.bodyJson()
        assertEquals("interaction", withoutTimeBody.getValue("category").jsonPrimitive.content)
        assertEquals(JsonArray(emptyList()), withoutTimeBody["excludeTypes"])
        assertTrue(withoutTimeBody["beforeMillis"].let { it == null || it is JsonNull })
    }

    @Test
    fun `server errors become repository exceptions`() = runTest {
        val server = NotificationTestServer { respondJson("""{"message":"bad cursor"}""", HttpStatusCode.BadRequest) }

        assertFailsWith<RepositoryException> {
            server.list(after = "bad")
        }
        assertFailsWith<RepositoryException> { server.notificationService.markNotificationsRead(listOf("x")) }
        assertFailsWith<RepositoryException> {
            server.notificationService.markAllNotificationsRead(NotificationGroup.NOTICE, noExclusions, null)
        }
    }

    private suspend fun NotificationTestServer.list(
        group: NotificationGroup = NotificationGroup.NOTICE,
        excludeTypes: Set<NotificationType> = emptySet(),
        after: String? = null,
        limit: Int = 30,
    ) = notificationService.listNotifications(group, excludeTypes, after, limit)

    private companion object {
        val KNOWN_PAGE = """
            {"items":[
              {"id":"n1","type":"report_result","category":"notice","createdAtMillis":1000,"read":false,
               "title":"你举报的评论已被处理","body":"葬送的芙莉莲 第 12 话","icon":"report",
               "target":{"subjectId":400602,"episodeId":1227087},
               "reportResult":{"targetType":"episode_comment","reason":"spoiler","reportedAtMillis":900,
               "handledAtMillis":950,"contentRemoved":true,"contentSnapshot":"小明：剧透","contentCreatedAtMillis":880,
               "subjectId":400602,"subjectName":"Sousou no Frieren","subjectNameCn":"葬送的芙莉莲","episodeId":1227087,
               "episodeSort":"12"}},
              {"id":"n2","type":"account_security","category":"notice","createdAtMillis":999,"read":true,
               "title":"Bangumi 账号已解绑","body":"该账号已登录另一个 Animeko 账号","icon":"security",
               "accountSecurity":{"event":"third_party_unbound","occurredAtMillis":998,"provider":"bangumi"}},
              {"id":"n3","type":"system","category":"notice","createdAtMillis":998,"read":false,
               "title":"维护通知","body":"第一行\n第二行","icon":"info","target":{"url":"https://example.com"},"system":{}},
              {"id":"n4","type":"report_result","category":"notice","createdAtMillis":997,"read":false,
               "title":"你举报的评论已被处理","body":"芙莉莲","icon":"report","target":{"characterId":8},
               "reportResult":{"targetType":"character_comment","reason":"harassment","reportedAtMillis":1,
               "handledAtMillis":2,"contentRemoved":false,"characterId":8,"targetName":"芙莉莲"}}
            ],"nextCursor":"998:n3"}
        """.trimIndent()

        val INTERACTION_PAGE = """
            {"items":[
              {"id":"i1","type":"comment_reply","category":"interaction","createdAtMillis":3,"read":false,
               "title":"Bob 回复了你的评论","body":"同意\n作画很好","overline":"葬送的芙莉莲 第 1 话","icon":"reply",
               "imageUrl":"https://example.com/bob.png","target":{"subjectId":400602,"episodeId":1227087}},
              {"id":"i2","type":"future_type","category":"interaction","createdAtMillis":2,"read":true,
               "title":"新功能","icon":"sparkles","target":{"personId":5,"commentId":9},"futurePayload":{"x":1}}
            ]}
        """.trimIndent()

        val UNKNOWN_VALUES_PAGE = """
            {"items":[
              {"id":"u1","type":"report_result","category":"notice","createdAtMillis":4,"read":false,"title":"t",
               "reportResult":{"targetType":"danmaku","reason":"impersonation","reportedAtMillis":1,
               "handledAtMillis":2,"contentRemoved":false}},
              {"id":"u2","type":"account_security","category":"notice","createdAtMillis":3,"read":false,"title":"t",
               "icon":"lock","accountSecurity":{"event":"password_changed","occurredAtMillis":3,"futureField":"x"}},
              {"id":"u3","type":"system","category":"notice","createdAtMillis":2,"read":false,"title":"标题","body":"正文",
               "system":{"event":"future_event"}},
              {"id":"u4","type":"account_security","category":"notice","createdAtMillis":1,"read":false,"title":"t",
               "accountSecurity":{"event":"third_party_unbound","occurredAtMillis":1,"provider":"github",
               "futureField":1}}
            ]}
        """.trimIndent()

        val MISSING_CONTENT_PAGE = """
            {"items":[
              {"id":"no-payload","type":"system","category":"notice","createdAtMillis":3,"read":false,"title":"系统"},
              {"id":"wrong-payload","type":"report_result","category":"notice","createdAtMillis":2,"read":false,
               "title":"报告","system":{}},
              {"id":"ok","type":"system","category":"notice","createdAtMillis":1,"read":false,"title":"t","system":{}}
            ],"nextCursor":"1:ok"}
        """.trimIndent()
    }
}
