/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.app.data.models.bangumi.BangumiConflictField
import me.him188.ani.app.data.models.bangumi.BangumiConflictFieldType
import me.him188.ani.app.data.models.bangumi.BangumiConflictResolution
import me.him188.ani.app.data.models.bangumi.BangumiMergeSide
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.client.apis.BangumiAniApi
import me.him188.ani.client.models.AniBangumiAutoMerged
import me.him188.ani.client.models.AniBangumiConflictFieldType
import me.him188.ani.client.models.AniBangumiMergeSide
import me.him188.ani.client.models.AniBangumiMergeState
import me.him188.ani.client.models.AniBangumiMergeSummary
import me.him188.ani.client.models.AniBangumiSyncConflict
import me.him188.ani.client.models.AniBangumiSyncConflictField
import me.him188.ani.client.models.AniCollectionType
import me.him188.ani.client.models.AniSelfRatingInfo
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * [DefaultBangumiMergeRepository]: 服务端实体 → 客户端模型的映射, 错误映射, 解决冲突后的缓存失效.
 *
 * 用 MockEngine 驱动真实的 [BangumiAniApi], 覆盖 JSON 反序列化到模型的完整路径.
 */
class DefaultBangumiMergeRepositoryTest {

    // ─── 测试基础设施 ───────────────────────────────────────────────────────

    private class RecordingSubjectCollectionRepository : SubjectCollectionRepository() {
        val invalidatedSubjectIds = mutableListOf<List<Int>>()
        var invalidateAllCalls = 0
        var failInvalidate = false

        override suspend fun invalidateCache(subjectIds: List<Int>) {
            if (failInvalidate) throw IllegalStateException("db closed")
            invalidatedSubjectIds += subjectIds
        }

        override suspend fun invalidateAllCaches() {
            invalidateAllCalls++
        }

        override fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts?> = unsupported()
        override fun subjectCollectionFlow(subjectId: Int): Flow<SubjectCollectionInfo> = unsupported()
        override fun subjectCollectionsPager(
            query: CollectionsFilterQuery,
            pagingConfig: PagingConfig,
        ): Flow<PagingData<SubjectCollectionInfo>> = unsupported()

        override fun cachedValidSubjectIds(): Flow<List<Int>> = unsupported()
        override suspend fun updateRecentlyUpdatedSubjectCollections(
            limit: Int,
            type: UnifiedCollectionType?,
            offset: Int,
        ) = unsupported()

        override fun mostRecentlyUpdatedSubjectCollectionsFlow(
            limit: Int,
            types: List<UnifiedCollectionType>?,
        ): Flow<List<SubjectCollectionInfo>> = unsupported()

        override suspend fun updateRating(
            subjectId: Int,
            score: Int?,
            comment: String?,
            tags: List<String>?,
            isPrivate: Boolean?,
        ) = unsupported()

        override suspend fun setSubjectCollectionTypeOrDelete(subjectId: Int, type: UnifiedCollectionType?) = unsupported()
        override fun getSubjectCollectionTypeOffline(subjectId: Int): Flow<UnifiedCollectionType?> = unsupported()
        override fun getSubjectDisplayInfoOffline(subjectId: Int): Flow<OfflineSubjectDisplayInfo?> = unsupported()
        override suspend fun getSubjectIdsByCollectionType(types: List<UnifiedCollectionType>): Flow<List<Int>> =
            unsupported()

        override suspend fun getSubjectNamesCnByCollectionType(types: List<UnifiedCollectionType>): Flow<List<String>> =
            unsupported()

        override suspend fun performBangumiFullSync() = unsupported()
        override suspend fun getBangumiFullSyncState(): BangumiSyncState? = unsupported()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("not used by DefaultBangumiMergeRepository")

        @Suppress("unused")
        private val unusedEpisodeType: EpisodeCollectionInfo? = null
    }

    private class Server(
        private val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) {
        val requests = mutableListOf<HttpRequestData>()

        val api: ApiInvoker<BangumiAniApi> = object : ApiInvoker<BangumiAniApi> {
            private val engine = MockEngine { request ->
                requests += request
                handler(request)
            }
            // 与生产的 HttpClient 一致: 4xx/5xx 抛出 ClientRequestException / ServerResponseException
            private val client = BangumiAniApi(
                baseUrl = "http://test",
                httpClientEngine = engine,
                httpClientConfig = { it.expectSuccess = true },
            )
            override suspend fun <R> invoke(action: suspend BangumiAniApi.() -> R): R = action(client)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
        respond(body, status, headersOf("Content-Type", "application/json"))

    private fun summaryJson(
        conflictCount: Int = 6,
        autoMergedTotal: Int = 12,
        lastSyncedAt: String? = "2025-07-20T09:16:00Z",
        syncInProgress: Boolean = false,
    ): String = json.encodeToString(
        AniBangumiMergeSummary.serializer(),
        AniBangumiMergeSummary(
            conflictCount = conflictCount,
            autoMergedTotal = autoMergedTotal,
            syncInProgress = syncInProgress,
            lastSyncedAt = lastSyncedAt,
        ),
    )

    private fun rating(score: Int, comment: String? = null, tags: List<String> = emptyList(), isPrivate: Boolean = false) =
        AniSelfRatingInfo(score = score, tags = tags, isPrivate = isPrivate, comment = comment)

    private fun conflictEntity(
        subjectId: Long,
        name: String? = "Name $subjectId",
        nameCn: String? = "中文 $subjectId",
        animekoUpdatedAt: String? = "2025-07-24T05:26:00Z",
        bangumiUpdatedAt: String? = "2025-07-25T01:26:00Z",
        detectedAt: String = "2025-07-26T00:00:00Z",
        fields: List<AniBangumiSyncConflictField>,
    ) = AniBangumiSyncConflict(
        subjectId = subjectId,
        detectedAt = detectedAt,
        fields = fields,
        subjectName = name,
        subjectNameCn = nameCn,
        animekoUpdatedAt = animekoUpdatedAt,
        bangumiUpdatedAt = bangumiUpdatedAt,
    )

    private fun collectionField(animeko: AniCollectionType?, bangumi: AniCollectionType?) = AniBangumiSyncConflictField(
        fieldType = AniBangumiConflictFieldType.COLLECTION,
        animekoCollectionType = animeko,
        bangumiCollectionType = bangumi,
    )

    private fun ratingField(animeko: AniSelfRatingInfo?, bangumi: AniSelfRatingInfo?) = AniBangumiSyncConflictField(
        fieldType = AniBangumiConflictFieldType.RATING,
        animekoRating = animeko,
        bangumiRating = bangumi,
    )

    private fun stateJson(
        conflicts: List<AniBangumiSyncConflict> = emptyList(),
        autoMerged: List<AniBangumiAutoMerged> = emptyList(),
        autoMergedTotal: Int = autoMerged.size,
        lastSyncedAt: String? = "2025-07-20T09:16:00Z",
        syncInProgress: Boolean = false,
    ): String = json.encodeToString(
        AniBangumiMergeState.serializer(),
        AniBangumiMergeState(
            conflicts = conflicts,
            autoMerged = autoMerged,
            autoMergedTotal = autoMergedTotal,
            syncInProgress = syncInProgress,
            lastSyncedAt = lastSyncedAt,
        ),
    )

    private fun repository(server: Server, cache: RecordingSubjectCollectionRepository = RecordingSubjectCollectionRepository()) =
        DefaultBangumiMergeRepository(server.api, cache)

    // ─── summary ────────────────────────────────────────────────────────────

    @Test
    fun `REPO-01 getSummary 映射数量, 时间与同步状态`() = runTest {
        val server = Server { json(summaryJson(conflictCount = 3, autoMergedTotal = 7, syncInProgress = true)) }
        val summary = repository(server).getSummary()

        assertEquals(3, summary.conflictCount)
        assertEquals(7, summary.autoMergedTotal)
        assertTrue(summary.syncInProgress)
        assertEquals(Instant.parse("2025-07-20T09:16:00Z"), summary.lastSyncedAt)
        assertEquals("/v2/bangumi/sync/merge/summary", server.requests.single().url.encodedPath)
        assertEquals(HttpMethod.Get, server.requests.single().method)
    }

    @Test
    fun `REPO-02 getSummary 从未同步时 lastSyncedAt 为 null`() = runTest {
        val server = Server { json(summaryJson(conflictCount = 0, autoMergedTotal = 0, lastSyncedAt = null)) }
        val summary = repository(server).getSummary()

        assertEquals(0, summary.conflictCount)
        assertNull(summary.lastSyncedAt)
        assertFalse(summary.syncInProgress)
    }

    @Test
    fun `REPO-03 getSummary 网络错误包装为 RepositoryException`() = runTest {
        val server = Server { throw IOException("connection reset") }
        assertFailsWith<RepositoryNetworkException> { repository(server).getSummary() }
    }

    @Test
    fun `REPO-04 getSummary 服务端 500 包装为 RepositoryException`() = runTest {
        val server = Server { json("""{"message":"boom"}""", HttpStatusCode.InternalServerError) }
        assertFailsWith<RepositoryException> { repository(server).getSummary() }
    }

    // ─── getMergeState 映射 ─────────────────────────────────────────────────

    @Test
    fun `REPO-05 getMergeState 映射收藏状态冲突与时间`() = runTest {
        val server = Server {
            json(
                stateJson(
                    conflicts = listOf(
                        conflictEntity(
                            subjectId = 100,
                            fields = listOf(collectionField(AniCollectionType.DOING, AniCollectionType.DROPPED)),
                        ),
                    ),
                ),
            )
        }
        val state = repository(server).getMergeState()

        val conflict = state.conflicts.single()
        assertEquals(100, conflict.subjectId)
        assertEquals("中文 100", conflict.title)
        assertEquals(Instant.parse("2025-07-24T05:26:00Z"), conflict.animekoUpdatedAt)
        assertEquals(Instant.parse("2025-07-25T01:26:00Z"), conflict.bangumiUpdatedAt)
        assertEquals(Instant.parse("2025-07-26T00:00:00Z"), conflict.detectedAt)
        val field = assertIs<BangumiConflictField.Collection>(conflict.fields.single())
        assertEquals(UnifiedCollectionType.DOING, field.animeko)
        assertEquals(UnifiedCollectionType.DROPPED, field.bangumi)
        assertEquals(1, state.conflictCount)
        assertTrue(state.hasConflicts)
        assertEquals("/v2/bangumi/sync/merge", server.requests.single().url.encodedPath)
    }

    @Test
    fun `REPO-06 Bangumi 侧已删除映射为 NOT_COLLECTED 且没有 Bangumi 时间`() = runTest {
        val server = Server {
            json(
                stateJson(
                    conflicts = listOf(
                        conflictEntity(
                            subjectId = 101,
                            bangumiUpdatedAt = null,
                            fields = listOf(collectionField(AniCollectionType.DOING, null)),
                        ),
                    ),
                ),
            )
        }
        val conflict = repository(server).getMergeState().conflicts.single()

        assertNull(conflict.bangumiUpdatedAt)
        val field = assertIs<BangumiConflictField.Collection>(conflict.fields.single())
        assertEquals(UnifiedCollectionType.DOING, field.animeko)
        assertEquals(UnifiedCollectionType.NOT_COLLECTED, field.bangumi)
    }

    @Test
    fun `REPO-07 评分冲突映射完整评分单元`() = runTest {
        val server = Server {
            json(
                stateJson(
                    conflicts = listOf(
                        conflictEntity(
                            subjectId = 102,
                            fields = listOf(
                                ratingField(
                                    rating(8, comment = "旅途的意义", tags = listOf("异世界", "转生"), isPrivate = true),
                                    rating(7, comment = null, tags = listOf("异世界")),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
        val conflict = repository(server).getMergeState().conflicts.single()

        val field = assertIs<BangumiConflictField.Rating>(conflict.fields.single())
        assertEquals(8, field.animeko.score)
        assertEquals("旅途的意义", field.animeko.comment)
        assertEquals(listOf("异世界", "转生"), field.animeko.tags)
        assertTrue(field.animeko.isPrivate)
        assertEquals(7, field.bangumi.score)
        assertNull(field.bangumi.comment)
        assertEquals(listOf("异世界"), field.bangumi.tags)
        assertFalse(field.bangumi.isPrivate)
    }

    @Test
    fun `REPO-08 同一条目多个字段保持顺序, 键与服务端字段一致`() = runTest {
        val server = Server {
            json(
                stateJson(
                    conflicts = listOf(
                        conflictEntity(
                            subjectId = 103,
                            fields = listOf(
                                collectionField(AniCollectionType.DOING, AniCollectionType.DONE),
                                ratingField(rating(8), rating(7)),
                            ),
                        ),
                    ),
                ),
            )
        }
        val conflict = repository(server).getMergeState().conflicts.single()

        assertEquals(
            listOf(BangumiConflictFieldType.COLLECTION, BangumiConflictFieldType.RATING),
            conflict.conflictKeys.map { it.fieldType },
        )
        assertEquals(listOf(103, 103), conflict.conflictKeys.map { it.subjectId })
    }

    @Test
    fun `REPO-09 标题回退 - 中文名优先, 其次原名, 都没有用 #id`() = runTest {
        val server = Server {
            json(
                stateJson(
                    conflicts = listOf(
                        conflictEntity(104, name = "Name", nameCn = "", fields = listOf(collectionField(AniCollectionType.DOING, AniCollectionType.DONE))),
                        conflictEntity(105, name = null, nameCn = null, fields = listOf(collectionField(AniCollectionType.DOING, AniCollectionType.DONE))),
                        conflictEntity(106, name = "  ", nameCn = "中文", fields = listOf(collectionField(AniCollectionType.DOING, AniCollectionType.DONE))),
                    ),
                ),
            )
        }
        val titles = repository(server).getMergeState().conflicts.map { it.subjectId to it.title }

        assertEquals(listOf(104 to "Name", 105 to "#105", 106 to "中文"), titles)
    }

    @Test
    fun `REPO-10 不能展示的字段被丢弃, 只剩不能展示字段的条目整条丢弃`() = runTest {
        val server = Server {
            json(
                stateJson(
                    conflicts = listOf(
                        // EPISODE 冲突 (服务端不会产生) + 缺少一侧评分的 RATING
                        conflictEntity(
                            107,
                            fields = listOf(
                                AniBangumiSyncConflictField(fieldType = AniBangumiConflictFieldType.EPISODE),
                                ratingField(rating(8), null),
                            ),
                        ),
                        conflictEntity(
                            108,
                            fields = listOf(
                                AniBangumiSyncConflictField(fieldType = AniBangumiConflictFieldType.EPISODE),
                                collectionField(AniCollectionType.WISH, AniCollectionType.DOING),
                            ),
                        ),
                    ),
                ),
            )
        }
        val state = repository(server).getMergeState()

        val conflict = state.conflicts.single()
        assertEquals(108, conflict.subjectId)
        assertEquals(1, conflict.fields.size)
        assertEquals(1, state.conflictCount)
    }

    @Test
    fun `REPO-11 无法解析的时间视为未知`() = runTest {
        val server = Server {
            json(
                stateJson(
                    conflicts = listOf(
                        conflictEntity(
                            109,
                            animekoUpdatedAt = "not-a-time",
                            bangumiUpdatedAt = "",
                            fields = listOf(collectionField(AniCollectionType.DOING, AniCollectionType.DONE)),
                        ),
                    ),
                ),
            )
        }
        val conflict = repository(server).getMergeState().conflicts.single()

        assertNull(conflict.animekoUpdatedAt)
        assertNull(conflict.bangumiUpdatedAt)
    }

    @Test
    fun `REPO-12 自动合并明细映射 (新增收藏, 评分, 剧集进度)`() = runTest {
        val server = Server {
            json(
                stateJson(
                    autoMerged = listOf(
                        AniBangumiAutoMerged(
                            subjectId = 200,
                            fieldType = AniBangumiConflictFieldType.COLLECTION,
                            side = AniBangumiMergeSide.BANGUMI,
                            isNew = true,
                            subjectName = "Summer Pockets",
                            subjectNameCn = "夏日口袋",
                            collectionType = AniCollectionType.WISH,
                        ),
                        AniBangumiAutoMerged(
                            subjectId = 201,
                            fieldType = AniBangumiConflictFieldType.RATING,
                            side = AniBangumiMergeSide.ANIMEKO,
                            isNew = false,
                            subjectName = null,
                            subjectNameCn = null,
                            rating = rating(9, comment = "神作"),
                        ),
                        AniBangumiAutoMerged(
                            subjectId = 202,
                            fieldType = AniBangumiConflictFieldType.EPISODE,
                            side = AniBangumiMergeSide.BANGUMI,
                            isNew = false,
                            subjectNameCn = "小市民系列",
                            watchedEpisodeSorts = listOf("5"),
                            unwatchedEpisodeSorts = listOf("#999"),
                        ),
                    ),
                    autoMergedTotal = 120,
                    syncInProgress = true,
                ),
            )
        }
        val state = repository(server).getMergeState()

        assertEquals(3, state.autoMerged.size)
        assertEquals(120, state.autoMergedTotal)
        assertTrue(state.syncInProgress)
        assertFalse(state.hasConflicts)

        val added = state.autoMerged[0]
        assertEquals("夏日口袋", added.title)
        assertEquals(BangumiConflictFieldType.COLLECTION, added.fieldType)
        assertEquals(BangumiMergeSide.BANGUMI, added.side)
        assertTrue(added.isNew)
        assertEquals(UnifiedCollectionType.WISH, added.collectionType)
        assertNull(added.rating)

        val rated = state.autoMerged[1]
        assertEquals("#201", rated.title)
        assertEquals(BangumiMergeSide.ANIMEKO, rated.side)
        assertEquals(9, assertNotNull(rated.rating).score)
        assertEquals("神作", rated.rating?.comment)

        val progress = state.autoMerged[2]
        assertEquals(BangumiConflictFieldType.EPISODE, progress.fieldType)
        assertEquals(listOf("5"), progress.watchedEpisodeSorts)
        assertEquals(listOf("#999"), progress.unwatchedEpisodeSorts)
    }

    @Test
    fun `REPO-13 空状态`() = runTest {
        val server = Server { json(stateJson(lastSyncedAt = null)) }
        val state = repository(server).getMergeState()

        assertTrue(state.conflicts.isEmpty())
        assertTrue(state.autoMerged.isEmpty())
        assertEquals(0, state.conflictCount)
        assertNull(state.lastSyncedAt)
    }

    // ─── resolve ────────────────────────────────────────────────────────────

    @Test
    fun `REPO-14 resolve 发送全部选择并返回剩余状态`() = runTest {
        val server = Server { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/v2/bangumi/sync/merge/resolve", request.url.encodedPath)
            json(
                stateJson(
                    conflicts = listOf(
                        conflictEntity(300, fields = listOf(collectionField(AniCollectionType.DOING, AniCollectionType.DONE))),
                    ),
                ),
            )
        }
        val cache = RecordingSubjectCollectionRepository()
        val remaining = repository(server, cache).resolve(
            listOf(
                BangumiConflictResolution(100, BangumiConflictFieldType.COLLECTION, BangumiMergeSide.ANIMEKO),
                BangumiConflictResolution(100, BangumiConflictFieldType.RATING, BangumiMergeSide.BANGUMI),
                BangumiConflictResolution(101, BangumiConflictFieldType.COLLECTION, BangumiMergeSide.BANGUMI),
            ),
        )

        val body = server.requests.single().body
        assertIs<TextContent>(body)
        val resolutions = json.parseToJsonElement(body.text).jsonObject["resolutions"]!!.jsonArray
        assertEquals(3, resolutions.size)
        assertEquals("100", resolutions[0].jsonObject["subjectId"]!!.jsonPrimitive.content)
        assertEquals("COLLECTION", resolutions[0].jsonObject["fieldType"]!!.jsonPrimitive.content)
        assertEquals("ANIMEKO", resolutions[0].jsonObject["side"]!!.jsonPrimitive.content)
        assertEquals("RATING", resolutions[1].jsonObject["fieldType"]!!.jsonPrimitive.content)
        assertEquals("BANGUMI", resolutions[1].jsonObject["side"]!!.jsonPrimitive.content)
        assertEquals("101", resolutions[2].jsonObject["subjectId"]!!.jsonPrimitive.content)

        assertEquals(listOf(300), remaining.conflicts.map { it.subjectId })
        // 涉及的条目 (去重) 缓存失效
        assertEquals(listOf(listOf(100, 101)), cache.invalidatedSubjectIds)
        assertEquals(0, cache.invalidateAllCalls)
    }

    @Test
    fun `REPO-15 resolve 空列表不失效缓存`() = runTest {
        val server = Server { json(stateJson()) }
        val cache = RecordingSubjectCollectionRepository()
        repository(server, cache).resolve(emptyList())

        assertTrue(cache.invalidatedSubjectIds.isEmpty())
    }

    @Test
    fun `REPO-16 resolve 409 抛出 BangumiMergeSyncInProgressException 且不失效缓存`() = runTest {
        val server = Server { json("""{"message":"sync in progress"}""", HttpStatusCode.Conflict) }
        val cache = RecordingSubjectCollectionRepository()

        assertFailsWith<BangumiMergeSyncInProgressException> {
            repository(server, cache).resolve(
                listOf(BangumiConflictResolution(1, BangumiConflictFieldType.COLLECTION, BangumiMergeSide.ANIMEKO)),
            )
        }
        assertTrue(cache.invalidatedSubjectIds.isEmpty())
    }

    @Test
    fun `REPO-17 resolve 其他失败包装为 RepositoryException 且不失效缓存`() = runTest {
        val server = Server { json("""{"message":"unauthorized"}""", HttpStatusCode.Unauthorized) }
        val cache = RecordingSubjectCollectionRepository()

        assertFailsWith<RepositoryException> {
            repository(server, cache).resolve(
                listOf(BangumiConflictResolution(1, BangumiConflictFieldType.COLLECTION, BangumiMergeSide.ANIMEKO)),
            )
        }
        assertTrue(cache.invalidatedSubjectIds.isEmpty())
    }

    @Test
    fun `REPO-18 缓存失效失败不影响 resolve 结果`() = runTest {
        val server = Server { json(stateJson()) }
        val cache = RecordingSubjectCollectionRepository().apply { failInvalidate = true }

        val remaining = repository(server, cache).resolve(
            listOf(BangumiConflictResolution(1, BangumiConflictFieldType.RATING, BangumiMergeSide.BANGUMI)),
        )

        assertFalse(remaining.hasConflicts)
    }
}
