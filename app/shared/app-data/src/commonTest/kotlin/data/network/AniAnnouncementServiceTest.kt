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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import me.him188.ani.app.data.models.notification.Announcement
import me.him188.ani.app.data.models.notification.AnnouncementTag
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AniAnnouncementServiceTest {
    @Test
    fun `list maps tags and keeps the raw value`() = runTest {
        val server = NotificationTestServer {
            respondJson(
                """
                {"items":[
                  {"id":"a1","tag":"maintenance","title":"停机维护","body":"今晚维护","pinned":true,
                   "publishedAtMillis":20,"linkUrl":"https://example.com","read":true},
                  {"id":"a2","tag":"festival","title":"新活动","body":"活动内容","pinned":false,"publishedAtMillis":10}
                ],"nextCursor":"0:10:a2"}
                """.trimIndent(),
            )
        }

        val page = server.announcementService.listAnnouncements(after = null, limit = 30)

        assertEquals("0:10:a2", page.nextCursor)
        assertEquals(
            listOf(
                Announcement(
                    id = "a1",
                    tag = AnnouncementTag.MAINTENANCE,
                    rawTag = "maintenance",
                    title = "停机维护",
                    body = "今晚维护",
                    linkUrl = "https://example.com",
                    pinned = true,
                    publishedAtMillis = 20,
                    read = true,
                ),
                Announcement(
                    id = "a2",
                    tag = AnnouncementTag.UNKNOWN,
                    rawTag = "festival",
                    title = "新活动",
                    body = "活动内容",
                    pinned = false,
                    publishedAtMillis = 10,
                    read = null,
                ),
            ),
            page.items,
        )
        val request = server.requests.single()
        assertEquals("/v2/announcements", request.url.encodedPath)
        assertEquals("30", request.url.parameters["limit"])
        assertNull(request.url.parameters["after"])
    }

    @Test
    fun `get requests one announcement and returns null for missing ones`() = runTest {
        var status = HttpStatusCode.OK
        val server = NotificationTestServer {
            respondJson(
                """{"id":"a1","tag":"release","title":"新版本","body":"更新内容","pinned":false,"publishedAtMillis":1}""",
                status,
            )
        }

        assertEquals(AnnouncementTag.RELEASE, server.announcementService.getAnnouncement("a1")?.tag)
        assertEquals("/v2/announcements/a1", server.requests.single().url.encodedPath)

        // 不存在, 未发布或已删除
        status = HttpStatusCode.NotFound
        assertNull(server.announcementService.getAnnouncement("00000000-0000-0000-0000-000000000000"))

        // ID 不是 UUID
        status = HttpStatusCode.BadRequest
        assertNull(server.announcementService.getAnnouncement("draft"))

        status = HttpStatusCode.ServiceUnavailable
        assertFailsWith<RepositoryException> { server.announcementService.getAnnouncement("a1") }
    }

    @Test
    fun `mark read sends the ids and mark all read always sends an object body`() = runTest {
        val server = NotificationTestServer()

        server.announcementService.markAnnouncementsRead(listOf("a1"))
        server.announcementService.markAllAnnouncementsRead(beforeMillis = 42)
        server.announcementService.markAllAnnouncementsRead(beforeMillis = null)

        val (markRead, markAll, markAllWithoutTime) = server.requests
        assertEquals(HttpMethod.Post, markRead.method)
        assertEquals("/v2/announcements/read", markRead.url.encodedPath)
        assertEquals(JsonArray(listOf(JsonPrimitive("a1"))), markRead.bodyJson()["ids"])
        assertEquals(HttpMethod.Post, markAll.method)
        assertEquals("/v2/announcements/read-all", markAll.url.encodedPath)
        assertEquals(42, markAll.bodyJson().getValue("beforeMillis").jsonPrimitive.long)
        assertEquals("/v2/announcements/read-all", markAllWithoutTime.url.encodedPath)
        assertEquals(JsonObject(emptyMap()), markAllWithoutTime.bodyJson())
    }

    @Test
    fun `marking read as a guest is an authorization error`() = runTest {
        val server = NotificationTestServer { respondJson("{}", HttpStatusCode.Unauthorized) }

        assertFailsWith<RepositoryAuthorizationException> { server.announcementService.markAllAnnouncementsRead(null) }
    }
}
