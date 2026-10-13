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
import me.him188.ani.app.data.models.notification.NotificationGroup
import me.him188.ani.app.data.models.notification.NotificationType
import me.him188.ani.app.data.models.notification.SystemNotificationEvent
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryUnknownException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AniNotificationServiceTest {
    private val noticeTypes = NotificationGroup.NOTICE.types

    @Test
    fun `list maps every supported type`() = runTest {
        val server = NotificationTestServer { respondJson(KNOWN_PAGE) }

        val page = server.notificationService.listNotifications(noticeTypes, after = null, limit = 30)

        assertEquals("998:n3", page.nextCursor)
        assertEquals(
            listOf(
                AppNotification(
                    id = "n1",
                    createdAtMillis = 1000,
                    read = false,
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
                    createdAtMillis = 999,
                    read = true,
                    content = AppNotificationContent.AccountSecurity(
                        event = AccountSecurityEvent.THIRD_PARTY_UNBOUND,
                        rawEvent = "third_party_unbound",
                        occurredAtMillis = 998,
                        provider = "bangumi",
                    ),
                ),
                AppNotification(
                    id = "n3",
                    createdAtMillis = 998,
                    read = false,
                    content = AppNotificationContent.System(
                        title = "维护通知",
                        body = "第一行\n第二行",
                        linkUrl = "https://example.com",
                    ),
                ),
                AppNotification(
                    id = "n4",
                    createdAtMillis = 997,
                    read = false,
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
    fun `list sends the requested types as one comma separated parameter`() = runTest {
        val server = NotificationTestServer { respondJson("""{"items":[]}""") }

        val first = server.notificationService.listNotifications(noticeTypes, after = null, limit = 30)
        server.notificationService.listNotifications(noticeTypes, after = "998:n3", limit = 100)

        assertNull(first.nextCursor)
        val (firstRequest, secondRequest) = server.requests
        assertEquals(HttpMethod.Get, firstRequest.method)
        assertEquals("/v2/notifications", firstRequest.url.encodedPath)
        assertEquals(listOf("system,report_result,account_security"), firstRequest.url.parameters.getAll("types"))
        assertEquals("30", firstRequest.url.parameters["limit"])
        assertNull(firstRequest.url.parameters["after"])
        assertEquals("998:n3", secondRequest.url.parameters["after"])
        assertEquals("100", secondRequest.url.parameters["limit"])
    }

    @Test
    fun `unknown wire values fall back instead of failing the page`() = runTest {
        val server = NotificationTestServer { respondJson(UNKNOWN_VALUES_PAGE) }

        val items = server.notificationService.listNotifications(noticeTypes, after = null, limit = 30).items

        val report = assertIs<AppNotificationContent.ReportResult>(items[0].content)
        assertNull(report.targetType)
        assertEquals(CommentReportReason.OTHER, report.reason)

        val unknownEvent = assertIs<AppNotificationContent.AccountSecurity>(items[1].content)
        assertEquals(AccountSecurityEvent.UNKNOWN, unknownEvent.event)
        assertEquals("password_changed", unknownEvent.rawEvent)

        // 不认识的系统消息事件展示服务端的标题和正文
        assertEquals(
            AppNotificationContent.System(title = "标题", body = "正文", event = null),
            items[2].content,
        )

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
                  {"id":"s1","type":"system","createdAtMillis":2,"read":false,
                   "system":{"title":"Bangumi 授权已过期","body":"正文","event":"bangumi_authorization_expired"}},
                  {"id":"s2","type":"system","createdAtMillis":1,"read":false,"system":{"title":"标题","body":"正文"}}
                ]}
                """.trimIndent(),
            )
        }

        val items = server.notificationService.listNotifications(noticeTypes, after = null, limit = 30).items

        assertEquals(
            listOf(SystemNotificationEvent.BANGUMI_AUTHORIZATION_EXPIRED, null),
            items.map { assertIs<AppNotificationContent.System>(it.content).event },
        )
        assertEquals("Bangumi 授权已过期", assertIs<AppNotificationContent.System>(items[0].content).title)
    }

    @Test
    fun `empty types never request all types`() = runTest {
        val server = NotificationTestServer { respondJson("""{"items":[]}""") }

        val page = server.notificationService.listNotifications(emptySet(), after = null, limit = 30)
        server.notificationService.markAllNotificationsRead(emptySet(), beforeMillis = 1000)

        assertEquals(emptyList(), page.items)
        assertNull(page.nextCursor)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `unread summary only keeps the requested types`() = runTest {
        val server = NotificationTestServer {
            respondJson(
                """
                {"counts":[{"type":"system","count":2},{"type":"report_result","count":3},
                {"type":"account_security","count":4}],"unreadAnnouncementCount":0}
                """.trimIndent(),
            )
        }

        val summary = server.notificationService.getUnreadSummary(
            setOf(NotificationType.SYSTEM, NotificationType.ACCOUNT_SECURITY),
        )

        assertEquals(
            mapOf(NotificationType.SYSTEM to 2, NotificationType.ACCOUNT_SECURITY to 4),
            summary.unreadCounts,
        )
        assertEquals(
            listOf("system,account_security"),
            server.requests.single().url.parameters.getAll("types"),
        )
    }

    @Test
    fun `items without content for their type are skipped`() = runTest {
        val server = NotificationTestServer { respondJson(MISSING_CONTENT_PAGE) }

        val page = server.notificationService.listNotifications(noticeTypes, after = null, limit = 30)

        assertEquals(listOf("ok"), page.items.map { it.id })
        assertEquals("1:ok", page.nextCursor)
    }

    @Test
    fun `get maps one notification and returns null for missing ones`() = runTest {
        var status = HttpStatusCode.OK
        var body = """{"id":"n3","type":"system","createdAtMillis":998,"read":true,"system":{"title":"标题","body":"正文"}}"""
        val server = NotificationTestServer { respondJson(body, status) }

        val notification = server.notificationService.getNotification("n3")
        assertEquals(AppNotificationContent.System(title = "标题", body = "正文"), notification?.content)
        assertEquals("/v2/notifications/n3", server.requests.single().url.encodedPath)

        body = """{"id":"n5","type":"system","createdAtMillis":998,"read":true}"""
        assertFailsWith<RepositoryUnknownException> { server.notificationService.getNotification("n5") }

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
    fun `unread summary maps counts and the latest announcement`() = runTest {
        val server = NotificationTestServer {
            respondJson(
                """
                {"counts":[{"type":"system","count":2},{"type":"report_result","count":3}],
                "unreadAnnouncementCount":1,
                "latestAnnouncement":{"id":"a1","tag":"festival","title":"标题","body":"正文","pinned":true,
                "publishedAtMillis":5,"read":false}}
                """.trimIndent(),
            )
        }

        val summary = server.notificationService.getUnreadSummary(NotificationType.SUPPORTED)

        assertEquals(mapOf(NotificationType.SYSTEM to 2, NotificationType.REPORT_RESULT to 3), summary.unreadCounts)
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
        assertEquals(listOf("system,report_result,account_security"), request.url.parameters.getAll("types"))
    }

    @Test
    fun `guest unread summary has no counts`() = runTest {
        val server = NotificationTestServer { respondJson("""{"counts":[],"unreadAnnouncementCount":0}""") }

        val summary = server.notificationService.getUnreadSummary(NotificationType.SUPPORTED)

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
    fun `mark all read always sends a JSON object`() = runTest {
        val server = NotificationTestServer()

        server.notificationService.markAllNotificationsRead(noticeTypes, beforeMillis = 1000)
        server.notificationService.markAllNotificationsRead(noticeTypes, beforeMillis = null)

        val (withTime, withoutTime) = server.requests
        assertEquals(HttpMethod.Post, withTime.method)
        assertEquals("/v2/notifications/read-all", withTime.url.encodedPath)
        val withTimeBody = withTime.bodyJson()
        assertEquals(
            listOf("system", "report_result", "account_security"),
            withTimeBody.getValue("types").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(1000, withTimeBody.getValue("beforeMillis").jsonPrimitive.long)

        val withoutTimeBody = withoutTime.bodyJson()
        assertTrue("types" in withoutTimeBody)
        assertTrue(withoutTimeBody["beforeMillis"].let { it == null || it is JsonNull })
    }

    @Test
    fun `server errors become repository exceptions`() = runTest {
        val server = NotificationTestServer { respondJson("""{"message":"bad cursor"}""", HttpStatusCode.BadRequest) }

        assertFailsWith<RepositoryException> {
            server.notificationService.listNotifications(noticeTypes, after = "bad", limit = 30)
        }
        assertFailsWith<RepositoryException> { server.notificationService.markNotificationsRead(listOf("x")) }
    }

    private companion object {
        val KNOWN_PAGE = """
            {"items":[
              {"id":"n1","type":"report_result","createdAtMillis":1000,"read":false,
               "reportResult":{"targetType":"episode_comment","reason":"spoiler","reportedAtMillis":900,
               "handledAtMillis":950,"contentRemoved":true,"contentSnapshot":"小明：剧透","contentCreatedAtMillis":880,
               "subjectId":400602,"subjectName":"Sousou no Frieren","subjectNameCn":"葬送的芙莉莲","episodeId":1227087,
               "episodeSort":"12"}},
              {"id":"n2","type":"account_security","createdAtMillis":999,"read":true,
               "accountSecurity":{"event":"third_party_unbound","occurredAtMillis":998,"provider":"bangumi"}},
              {"id":"n3","type":"system","createdAtMillis":998,"read":false,
               "system":{"title":"维护通知","body":"第一行\n第二行","linkUrl":"https://example.com"}},
              {"id":"n4","type":"report_result","createdAtMillis":997,"read":false,
               "reportResult":{"targetType":"character_comment","reason":"harassment","reportedAtMillis":1,
               "handledAtMillis":2,"contentRemoved":false,"characterId":8,"targetName":"芙莉莲"}}
            ],"nextCursor":"998:n3"}
        """.trimIndent()

        val UNKNOWN_VALUES_PAGE = """
            {"items":[
              {"id":"u1","type":"report_result","createdAtMillis":4,"read":false,
               "reportResult":{"targetType":"danmaku","reason":"impersonation","reportedAtMillis":1,
               "handledAtMillis":2,"contentRemoved":false}},
              {"id":"u2","type":"account_security","createdAtMillis":3,"read":false,
               "accountSecurity":{"event":"password_changed","occurredAtMillis":3,"futureField":"x"}},
              {"id":"u3","type":"system","createdAtMillis":2,"read":false,
               "system":{"title":"标题","body":"正文","event":"future_event"}},
              {"id":"u4","type":"account_security","createdAtMillis":1,"read":false,
               "accountSecurity":{"event":"third_party_unbound","occurredAtMillis":1,"provider":"github","futureField":1}}
            ]}
        """.trimIndent()

        val MISSING_CONTENT_PAGE = """
            {"items":[
              {"id":"no-payload","type":"system","createdAtMillis":3,"read":false},
              {"id":"wrong-payload","type":"report_result","createdAtMillis":2,"read":false,
               "system":{"title":"t","body":"b"}},
              {"id":"reserved","type":"comment_reply","createdAtMillis":2,"read":false},
              {"id":"ok","type":"system","createdAtMillis":1,"read":false,"system":{"title":"t","body":"b"}}
            ],"nextCursor":"1:ok"}
        """.trimIndent()
    }
}
