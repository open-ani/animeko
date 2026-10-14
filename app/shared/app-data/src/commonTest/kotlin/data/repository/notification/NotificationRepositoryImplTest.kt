/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.notification

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import me.him188.ani.app.data.models.notification.Announcement
import me.him188.ani.app.data.models.notification.AppNotification
import me.him188.ani.app.data.models.notification.AppNotificationContent
import me.him188.ani.app.data.models.notification.NotificationGroup
import me.him188.ani.app.data.models.notification.NotificationType
import me.him188.ani.app.data.network.NotificationTestServer
import me.him188.ani.app.data.network.bodyJson
import me.him188.ani.app.data.network.respondJson
import me.him188.ani.app.data.repository.RepositoryServiceUnavailableException
import me.him188.ani.app.data.repository.repositoryException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class NotificationRepositoryImplTest {
    private fun NotificationTestServer.repository() = NotificationRepositoryImpl(notificationService, announcementService)

    @Test
    fun `notifications page by cursor until the server has no more`() = runTest {
        val server = NotificationTestServer { request ->
            when (request.url.parameters["after"]) {
                null -> respondJson(notificationPage(listOf("n1", "n2"), nextCursor = "2:n2"))
                "2:n2" -> respondJson(notificationPage(listOf("n3"), nextCursor = null))
                else -> error("Unexpected cursor")
            }
        }
        val source = server.repository().notificationsPagingSource(
            NotificationGroup.NOTICE,
            setOf(NotificationType.ACCOUNT_SECURITY),
        )

        val first = assertIs<PagingSource.LoadResult.Page<String, AppNotification>>(source.load(refresh(90)))
        assertEquals(listOf("n1", "n2"), first.data.map { it.id })
        assertNull(first.prevKey)
        assertEquals("2:n2", first.nextKey)

        val last = assertIs<PagingSource.LoadResult.Page<String, AppNotification>>(source.load(append("2:n2", 30)))
        assertEquals(listOf("n3"), last.data.map { it.id })
        assertNull(last.nextKey)

        assertEquals(listOf(null, "2:n2"), server.requests.map { it.url.parameters["after"] })
        assertEquals(listOf("90", "30"), server.requests.map { it.url.parameters["limit"] })
        assertEquals(listOf("notice"), server.requests.map { it.url.parameters["category"] }.distinct())
        assertEquals(listOf("account_security"), server.requests.map { it.url.parameters["excludeTypes"] }.distinct())
    }

    @Test
    fun `page size never exceeds the server limit`() = runTest {
        val server = NotificationTestServer { respondJson("""{"items":[]}""") }
        val repository = server.repository()

        repository.notificationsPagingSource(NotificationGroup.NOTICE, emptySet()).load(refresh(300))
        repository.announcementsPagingSource().load(refresh(300))

        assertEquals(listOf("100", "100"), server.requests.map { it.url.parameters["limit"] })
    }

    @Test
    fun `refresh always starts from the first page`() = runTest {
        val server = NotificationTestServer { respondJson("""{"items":[]}""") }
        val source = server.repository().notificationsPagingSource(NotificationGroup.NOTICE, emptySet())

        assertNull(source.getRefreshKey(PagingState(emptyList(), null, PagingConfig(30), 0)))
    }

    @Test
    fun `load errors carry a repository exception`() = runTest {
        val server = NotificationTestServer { respondJson("{}", HttpStatusCode.ServiceUnavailable) }
        val repository = server.repository()

        val notifications = assertIs<PagingSource.LoadResult.Error<String, AppNotification>>(
            repository.notificationsPagingSource(NotificationGroup.NOTICE, emptySet()).load(refresh(30)),
        )
        assertIs<RepositoryServiceUnavailableException>(notifications.repositoryException)
        val announcements = assertIs<PagingSource.LoadResult.Error<String, Announcement>>(
            repository.announcementsPagingSource().load(refresh(30)),
        )
        assertIs<RepositoryServiceUnavailableException>(announcements.repositoryException)
    }

    @Test
    fun `announcements page by cursor`() = runTest {
        val server = NotificationTestServer { request ->
            when (request.url.parameters["after"]) {
                null -> respondJson(
                    """{"items":[{"id":"a1","tag":"general","title":"t","body":"b","pinned":true,"publishedAtMillis":2}],
                    "nextCursor":"1:2:a1"}""",
                )

                else -> respondJson(
                    """{"items":[{"id":"a2","tag":"general","title":"t","body":"b","pinned":false,"publishedAtMillis":1}]}""",
                )
            }
        }
        val source = server.repository().announcementsPagingSource()

        val first = assertIs<PagingSource.LoadResult.Page<String, Announcement>>(source.load(refresh(30)))
        val last = assertIs<PagingSource.LoadResult.Page<String, Announcement>>(source.load(append("1:2:a1", 30)))

        assertEquals(listOf("a1", "a2"), (first.data + last.data).map { it.id })
        assertEquals("1:2:a1", first.nextKey)
        assertNull(last.nextKey)
        assertEquals("/v2/announcements", server.requests.first().url.encodedPath)
        assertEquals("1:2:a1", server.requests.last().url.parameters["after"])
    }

    @Test
    fun `an announcement moved past the cursor is not loaded twice`() = runTest {
        // a1 在第一页时是置顶的; 取消置顶后它排到了游标之后, 下一页又返回了它
        val server = NotificationTestServer { request ->
            when (request.url.parameters["after"]) {
                null -> respondJson(
                    """{"items":[{"id":"a1","tag":"general","title":"t","body":"b","pinned":true,"publishedAtMillis":1},
                    {"id":"a2","tag":"general","title":"t","body":"b","pinned":false,"publishedAtMillis":3}],
                    "nextCursor":"0:3:a2"}""",
                )

                else -> respondJson(
                    """{"items":[{"id":"a1","tag":"general","title":"t","body":"b","pinned":false,"publishedAtMillis":1},
                    {"id":"a3","tag":"general","title":"t","body":"b","pinned":false,"publishedAtMillis":0}]}""",
                )
            }
        }
        val source = server.repository().announcementsPagingSource()

        val first = assertIs<PagingSource.LoadResult.Page<String, Announcement>>(source.load(refresh(30)))
        val last = assertIs<PagingSource.LoadResult.Page<String, Announcement>>(source.load(append("0:3:a2", 30)))

        assertEquals(listOf("a1", "a2", "a3"), (first.data + last.data).map { it.id })
        assertNull(last.nextKey)

        // 刷新会创建新的 PagingSource, 重新开始
        val refreshed = server.repository().announcementsPagingSource()
        val again = assertIs<PagingSource.LoadResult.Page<String, Announcement>>(refreshed.load(refresh(30)))
        assertEquals(listOf("a1", "a2"), again.data.map { it.id })
    }

    @Test
    fun `mark read splits ids into requests of at most 100`() = runTest {
        val server = NotificationTestServer()
        val repository = server.repository()
        val ids = (1..250).map { "n$it" }

        repository.markNotificationsRead(ids + ids.take(10))
        repository.markAnnouncementsRead((1..101).map { "a$it" })
        repository.markNotificationsRead(emptyList())

        val notificationRequests = server.requests.filter { it.url.encodedPath == "/v2/notifications/read" }
        val sent = notificationRequests.map { request -> request.bodyJson().getValue("ids").jsonArray.map { it.jsonPrimitive.content } }
        assertEquals(listOf(100, 100, 50), sent.map { it.size })
        assertEquals(ids, sent.flatten())

        val announcementRequests = server.requests.filter { it.url.encodedPath == "/v2/announcements/read" }
        assertEquals(
            listOf(100, 1),
            announcementRequests.map { it.bodyJson().getValue("ids").jsonArray.size },
        )
    }

    @Test
    fun `interaction notifications page like the other categories`() = runTest {
        val server = NotificationTestServer {
            respondJson(
                """{"items":[{"id":"i1","type":"comment_reply","category":"interaction","createdAtMillis":1,
                "read":false,"title":"Bob 回复了你的评论"}]}""",
            )
        }
        val source = server.repository().notificationsPagingSource(NotificationGroup.INTERACTION, emptySet())

        val page = assertIs<PagingSource.LoadResult.Page<String, AppNotification>>(source.load(refresh(30)))

        assertEquals(listOf("i1"), page.data.map { it.id })
        assertEquals(AppNotificationContent.Unknown, page.data.single().content)
        assertEquals("interaction", server.requests.single().url.parameters["category"])
        assertNull(server.requests.single().url.parameters["excludeTypes"])
    }

    @Test
    fun `mark all read sends the group excluded types and time`() = runTest {
        val server = NotificationTestServer()
        val repository = server.repository()

        repository.markAllNotificationsRead(
            NotificationGroup.NOTICE,
            setOf(NotificationType.REPORT_RESULT),
            beforeMillis = 42,
        )
        repository.markAllAnnouncementsRead(beforeMillis = 7)

        val (notifications, announcements) = server.requests
        assertEquals("/v2/notifications/read-all", notifications.url.encodedPath)
        val body = notifications.bodyJson()
        assertEquals("notice", body.getValue("category").jsonPrimitive.content)
        assertEquals(
            listOf("report_result"),
            body.getValue("excludeTypes").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(42, body.getValue("beforeMillis").jsonPrimitive.long)
        assertEquals("/v2/announcements/read-all", announcements.url.encodedPath)
        assertEquals(7, announcements.bodyJson().getValue("beforeMillis").jsonPrimitive.long)
    }

    private fun notificationPage(ids: List<String>, nextCursor: String?): String {
        val items = ids.joinToString(",") { id ->
            """{"id":"$id","type":"system","category":"notice","createdAtMillis":1,"read":false,"title":"t",
            "system":{}}"""
        }
        val cursor = nextCursor?.let { ""","nextCursor":"$it"""" } ?: ""
        return """{"items":[$items]$cursor}"""
    }

    private fun refresh(loadSize: Int) = PagingSource.LoadParams.Refresh<String>(
        key = null,
        loadSize = loadSize,
        placeholdersEnabled = false,
    )

    private fun append(key: String, loadSize: Int) = PagingSource.LoadParams.Append(
        key = key,
        loadSize = loadSize,
        placeholdersEnabled = false,
    )
}
