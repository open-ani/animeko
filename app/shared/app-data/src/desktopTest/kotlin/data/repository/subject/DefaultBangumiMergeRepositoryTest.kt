/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.network.BatchSubjectRelations
import me.him188.ani.app.data.network.SubjectService
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.persistent.database.createTestAniDatabase
import me.him188.ani.app.data.persistent.database.dao.BangumiMergeBaselineEntity
import me.him188.ani.app.data.persistent.database.dao.EpisodeCollectionEntity
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionEntity
import me.him188.ani.app.domain.bangumi.merge.AutoMergeReason
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeConflict
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeConflictKey
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeFieldId
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeResolution
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeSide
import me.him188.ani.client.models.AniCollectionType
import me.him188.ani.client.models.AniEpisodeCollection
import me.him188.ani.client.models.AniEpisodeCollectionType
import me.him188.ani.client.models.AniEpisodeType
import me.him188.ani.client.models.AniFavourite
import me.him188.ani.client.models.AniSelfRatingInfo
import me.him188.ani.client.models.AniSubjectCollection
import me.him188.ani.client.models.AniSubjectRecommendation
import me.him188.ani.client.models.AniSubjectRelations
import me.him188.ani.client.models.AniSubjectType
import me.him188.ani.client.models.AniUpdateSubjectCollectionRequest
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.models.BangumiSubjectCollectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 覆盖 [DefaultBangumiMergeRepository] 的快照构建 (真实 Room DAO) 与合并应用.
 */
class DefaultBangumiMergeRepositoryTest {

    private class FakeSubjectService(
        var collections: List<AniSubjectCollection> = emptyList(),
        var pageSize: Int = 100,
    ) : SubjectService {
        override suspend fun getSubjectCollections(
            type: BangumiSubjectCollectionType?,
            offset: Int,
            limit: Int,
        ): List<AniSubjectCollection> {
            val effectiveLimit = minOf(limit, pageSize)
            return collections.drop(offset).take(effectiveLimit)
        }

        override suspend fun getSubjectCollection(subjectId: Int): AniSubjectCollection? =
            collections.find { it.id.toInt() == subjectId }

        override suspend fun getSubjectRelations(
            subjectId: Int,
            withCharacterActors: Boolean,
        ): BatchSubjectRelations = throw UnsupportedOperationException()

        override fun subjectCollectionById(subjectId: Int): Flow<AniSubjectCollection?> =
            throw UnsupportedOperationException()

        override suspend fun patchSubjectCollection(subjectId: Int, payload: AniUpdateSubjectCollectionRequest) =
            throw UnsupportedOperationException()

        override suspend fun deleteSubjectCollection(subjectId: Int) = throw UnsupportedOperationException()

        override suspend fun getSubjectRecommendations(subjectId: Int, limit: Int): List<AniSubjectRecommendation> =
            throw UnsupportedOperationException()

        override fun subjectCollectionCountsFlow() = throw UnsupportedOperationException()

        override suspend fun performBangumiFullSync() = throw UnsupportedOperationException()

        override suspend fun getBangumiFullSyncState(): BangumiSyncState? = throw UnsupportedOperationException()
    }

    private class RecordingWriteGateway : BangumiMergeWriteGateway {
        val calls = mutableListOf<String>()

        override suspend fun setSubjectCollection(subjectId: Int, type: UnifiedCollectionType?) {
            calls.add("collection($subjectId, $type)")
        }

        override suspend fun updateRating(subjectId: Int, score: Int, comment: String?) {
            calls.add("rating($subjectId, $score, $comment)")
        }

        override suspend fun setEpisodeCollection(subjectId: Int, episodeId: Int, type: UnifiedCollectionType) {
            calls.add("episode($subjectId, $episodeId, $type)")
        }
    }

    private fun localSubject(
        subjectId: Int,
        type: UnifiedCollectionType = UnifiedCollectionType.DOING,
        score: Int = 0,
        comment: String? = null,
        lastUpdated: Long = 0,
        nameCn: String = "本地条目 $subjectId",
    ) = SubjectCollectionEntity(
        subjectId = subjectId, name = "subject-$subjectId", nameCn = nameCn, summary = "", nsfw = false,
        imageLarge = "", totalEpisodes = 12, airDate = PackedDate.Invalid,
        aliases = emptyList(), tags = emptyList(),
        collectionStats = SubjectCollectionStats.Zero, ratingInfo = RatingInfo.Empty,
        completeDate = PackedDate.Invalid,
        selfRatingInfo = SelfRatingInfo(score = score, comment = comment, tags = emptyList(), isPrivate = false),
        collectionType = type, recurrence = null,
        lastUpdated = lastUpdated, lastFetched = 0, cachedStaffUpdated = 0, cachedCharactersUpdated = 0,
    )

    private fun localEpisode(
        subjectId: Int,
        episodeId: Int,
        sort: Int,
        type: UnifiedCollectionType,
    ) = EpisodeCollectionEntity(
        subjectId = subjectId, episodeId = episodeId, episodeType = null,
        name = "ep", nameCn = "第 $sort 集", airDate = PackedDate.Invalid,
        comment = 0, desc = "", sort = EpisodeSort(sort), sortNumber = sort.toFloat(),
        selfCollectionType = type, lastFetched = 0,
    )

    private fun remoteSubject(
        subjectId: Int,
        type: AniCollectionType? = AniCollectionType.DOING,
        score: Int = 0,
        comment: String? = null,
        updatedAt: String? = null,
        watchedEpisodes: Map<Int, Int> = emptyMap(), // episodeId to sort
        nameCn: String = "远端条目 $subjectId",
    ) = AniSubjectCollection(
        id = subjectId.toLong(),
        type = AniSubjectType.ANIME,
        name = "subject-$subjectId",
        nameCn = nameCn,
        summary = "",
        nsfw = false,
        airDate = "",
        aliases = emptyList(),
        favorite = AniFavourite(wish = 0, done = 0, doing = 0, onHold = 0, dropped = 0),
        tags = emptyList(),
        metaTags = emptyList(),
        scoreDetails = emptyMap(),
        selfRating = AniSelfRatingInfo(score = score, tags = emptyList(), isPrivate = false, comment = comment),
        episodes = watchedEpisodes.map { (episodeId, sort) ->
            AniEpisodeCollection(
                episodeId = episodeId.toLong(),
                subjectId = subjectId.toLong(),
                sort = sort.toString(),
                type = AniEpisodeType.MAIN,
                name = "ep",
                nameCn = "第 $sort 集",
                description = "",
                collectionType = AniEpisodeCollectionType.DONE,
            )
        },
        relations = AniSubjectRelations(
            subjectId = subjectId.toLong(),
            seriesMainSubjectIds = emptyList(),
            seriesMainSubjectNames = emptyList(),
            sequelSubjects = emptyList(),
            sequelSubjectNames = emptyList(),
        ),
        collectionType = type,
        updatedAt = updatedAt,
    )

    private fun runRepositoryTest(
        block: suspend (
            database: AniDatabase,
            service: FakeSubjectService,
            gateway: RecordingWriteGateway,
            repository: DefaultBangumiMergeRepository,
        ) -> Unit,
    ) = runBlocking {
        val database = createTestAniDatabase()
        try {
            val service = FakeSubjectService()
            val gateway = RecordingWriteGateway()
            val repository = DefaultBangumiMergeRepository(
                subjectService = service,
                subjectCollectionDao = database.subjectCollection(),
                episodeCollectionDao = database.episodeCollection(),
                baselineDao = database.bangumiMergeBaselineDao(),
                writeGateway = gateway,
                getCurrentTimeMillis = { 42_000L },
            )
            block(database, service, gateway, repository)
        } finally {
            database.close()
        }
    }

    @Test
    fun `REPO-01 两侧一致时无冲突`() = runRepositoryTest { database, service, _, repository ->
        database.subjectCollection().upsert(localSubject(1, UnifiedCollectionType.DOING, score = 8))
        service.collections = listOf(remoteSubject(1, AniCollectionType.DOING, score = 8))

        val plan = repository.computeMergePlan()
        assertTrue(plan.conflictGroups.isEmpty())
        assertTrue(plan.autoMerged.isEmpty())
        assertEquals(1, plan.inputs.size)
    }

    @Test
    fun `REPO-02 无基线时两侧差异成为冲突 标题优先本地中文`() = runRepositoryTest { database, service, _, repository ->
        database.subjectCollection().upsert(localSubject(1, UnifiedCollectionType.DOING, nameCn = "孤独摇滚！"))
        service.collections = listOf(remoteSubject(1, AniCollectionType.DROPPED))

        val plan = repository.computeMergePlan()
        val group = plan.conflictGroups.single()
        assertEquals("孤独摇滚！", group.title)
        val conflict = assertIs<BangumiMergeConflict.Collection>(group.conflicts.single())
        assertEquals(UnifiedCollectionType.DOING, conflict.local.value)
        assertEquals(UnifiedCollectionType.DROPPED, conflict.remote.value)
    }

    @Test
    fun `REPO-03 时间戳来自本地 lastUpdated 与远端 updatedAt`() = runRepositoryTest { database, service, _, repository ->
        database.subjectCollection().upsert(
            localSubject(1, UnifiedCollectionType.DOING, lastUpdated = 2_000_000),
        )
        service.collections = listOf(
            remoteSubject(1, AniCollectionType.DROPPED, updatedAt = "1970-01-01T00:00:01Z"),
        )

        val plan = repository.computeMergePlan()
        val conflict = assertIs<BangumiMergeConflict.Collection>(plan.conflictGroups.single().conflicts.single())
        // 本地 2_000_000ms > 远端 1_000ms.
        assertEquals(BangumiMergeSide.ANIMEKO, conflict.newerSide)
    }

    @Test
    fun `REPO-04 本地非 DONE 剧集视为未看`() = runRepositoryTest { database, service, _, repository ->
        database.subjectCollection().upsert(localSubject(1))
        database.episodeCollection().upsert(
            listOf(
                localEpisode(1, 101, 1, UnifiedCollectionType.DONE),
                localEpisode(1, 102, 2, UnifiedCollectionType.WISH), // 归一化为未看
            ),
        )
        service.collections = listOf(
            remoteSubject(1, watchedEpisodes = mapOf(101 to 1)),
        )

        val plan = repository.computeMergePlan()
        // ep101 两侧都看过; ep102 本地 WISH 归一化为未看, 与远端一致 → 无差异.
        assertTrue(plan.conflictGroups.isEmpty())
        assertTrue(plan.autoMerged.isEmpty())
    }

    @Test
    fun `REPO-05 本地行缺失且无基线的远端条目视为一致 不产生删除冲突`() = runRepositoryTest { _, service, _, repository ->
        // 本地 Room 表是分页缓存, 行缺失只说明未加载: 无基线时本地视为与远端一致.
        service.collections = listOf(remoteSubject(9, AniCollectionType.WISH, nameCn = "远端新条目"))

        val plan = repository.computeMergePlan()
        assertTrue(plan.conflictGroups.isEmpty())
        assertTrue(plan.autoMerged.isEmpty())
        val input = plan.inputs.single()
        assertEquals(9, input.subjectId)
        assertEquals(UnifiedCollectionType.WISH, input.local.collectionType)
    }

    @Test
    fun `REPO-06 远端分页全部拉取`() = runRepositoryTest { _, service, _, repository ->
        service.pageSize = 2
        service.collections = (1..5).map { remoteSubject(it, AniCollectionType.WISH) }

        val plan = repository.computeMergePlan()
        assertEquals(5, plan.inputs.size)
        assertEquals((1..5).toList(), plan.inputs.map { it.subjectId })
    }

    @Test
    fun `REPO-07 应用合并执行写操作并保存基线`() = runRepositoryTest { database, service, gateway, repository ->
        database.subjectCollection().upsert(localSubject(1, UnifiedCollectionType.DOING, score = 8))
        database.episodeCollection().upsert(listOf(localEpisode(1, 101, 1, UnifiedCollectionType.DONE)))
        service.collections = listOf(remoteSubject(1, AniCollectionType.DROPPED, score = 6))

        val plan = repository.computeMergePlan()
        // 无基线: 收藏状态 + 评分 + ep101 都冲突.
        assertEquals(3, plan.totalConflictCount)

        val resolution = BangumiMergeResolution(
            mapOf(
                BangumiMergeConflictKey(1, BangumiMergeFieldId.Collection) to BangumiMergeSide.ANIMEKO,
                BangumiMergeConflictKey(1, BangumiMergeFieldId.Rating) to BangumiMergeSide.ANIMEKO,
                BangumiMergeConflictKey(1, BangumiMergeFieldId.Episode(101)) to BangumiMergeSide.ANIMEKO,
            ),
        )
        repository.applyMerge(plan, resolution)

        assertEquals(
            listOf(
                "collection(1, DOING)",
                "rating(1, 8, null)",
                "episode(1, 101, DONE)",
            ),
            gateway.calls,
        )

        val baseline = database.bangumiMergeBaselineDao().getAll().single()
        assertEquals(1, baseline.subjectId)
        assertEquals(UnifiedCollectionType.DOING, baseline.collectionType)
        assertEquals(8, baseline.score)
        assertEquals(listOf(101), baseline.watchedEpisodeIds)
        assertEquals(42_000L, baseline.updatedAtMillis)
    }

    @Test
    fun `REPO-08 有基线后第二次合并单侧修改自动合并`() = runRepositoryTest { database, service, gateway, repository ->
        // 第一次: 两侧一致, 建立基线.
        database.subjectCollection().upsert(localSubject(1, UnifiedCollectionType.DOING, score = 8))
        service.collections = listOf(remoteSubject(1, AniCollectionType.DOING, score = 8))
        repository.applyMerge(repository.computeMergePlan(), BangumiMergeResolution.Empty)
        assertTrue(gateway.calls.isEmpty())

        // 第二次: 仅远端修改评分 → 自动合并, 无需确认.
        service.collections = listOf(remoteSubject(1, AniCollectionType.DOING, score = 10))
        val plan = repository.computeMergePlan()
        assertTrue(plan.conflictGroups.isEmpty())
        assertEquals(1, plan.autoMerged.size)

        repository.applyMerge(plan, BangumiMergeResolution.Empty)
        assertEquals(listOf("rating(1, 10, null)"), gateway.calls)

        val baseline = database.bangumiMergeBaselineDao().getBySubjectId(1)!!
        assertEquals(10, baseline.score)
    }

    @Test
    fun `REPO-09 选择删除侧时基线记录未收藏`() = runRepositoryTest { database, service, gateway, repository ->
        // 第一次: 两侧一致, 建立基线.
        database.subjectCollection().upsert(localSubject(1, UnifiedCollectionType.DOING, score = 6))
        service.collections = listOf(remoteSubject(1, AniCollectionType.DOING, score = 6))
        repository.applyMerge(repository.computeMergePlan(), BangumiMergeResolution.Empty)
        assertTrue(gateway.calls.isEmpty())

        // 第二次: 远端删除了收藏 (破坏性变更不自动合并) → 冲突, 用户选择删除侧.
        service.collections = emptyList()
        val plan = repository.computeMergePlan()
        val conflict = assertIs<BangumiMergeConflict.Collection>(plan.conflictGroups.single().conflicts.single())
        assertEquals(UnifiedCollectionType.NOT_COLLECTED, conflict.remote.value)

        val resolution = BangumiMergeResolution(
            mapOf(BangumiMergeConflictKey(1, BangumiMergeFieldId.Collection) to BangumiMergeSide.BANGUMI),
        )
        repository.applyMerge(plan, resolution)

        assertEquals(listOf("collection(1, null)"), gateway.calls)
        val baseline = database.bangumiMergeBaselineDao().getBySubjectId(1)!!
        assertEquals(UnifiedCollectionType.NOT_COLLECTED, baseline.collectionType)
    }

    @Test
    fun `REPO-10 空短评归一化为无短评`() = runRepositoryTest { database, service, _, repository ->
        database.subjectCollection().upsert(localSubject(1, comment = ""))
        service.collections = listOf(remoteSubject(1, comment = null))

        val plan = repository.computeMergePlan()
        assertTrue(plan.conflictGroups.isEmpty())
        assertTrue(plan.autoMerged.isEmpty())
    }

    private fun baseline(
        subjectId: Int,
        type: UnifiedCollectionType = UnifiedCollectionType.DOING,
        score: Int = 0,
        watchedEpisodeIds: List<Int> = emptyList(),
    ) = BangumiMergeBaselineEntity(
        subjectId = subjectId, collectionType = type, score = score, comment = null,
        watchedEpisodeIds = watchedEpisodeIds, updatedAtMillis = 0,
    )

    @Test
    fun `REPO-11 本地条目行缺失时视为与基线一致而非删除`() = runRepositoryTest { database, service, _, repository ->
        // 回归: 本地行被分页缓存清掉 (REFRESH 时 deleteAll 后只回填已加载页),
        // 不能推断为用户删除了收藏, 否则远端的修改会被"删除 vs 修改"冲突甚至静默删除吞掉.
        database.bangumiMergeBaselineDao().upsertAll(
            listOf(baseline(1, UnifiedCollectionType.DOING, watchedEpisodeIds = listOf(101))),
        )
        service.collections = listOf(
            remoteSubject(1, AniCollectionType.DOING, score = 10, watchedEpisodes = mapOf(101 to 1)),
        )

        val plan = repository.computeMergePlan()
        assertTrue(plan.conflictGroups.isEmpty())
        // 只有"远端修改评分"这一处自动合并.
        val change = plan.autoMerged.single()
        assertEquals(AutoMergeReason.REMOTE_ONLY, change.reason)
    }

    @Test
    fun `REPO-12 本地剧集行缺失时回退基线不视为取消看过`() = runRepositoryTest { database, service, _, repository ->
        database.subjectCollection().upsert(localSubject(1, UnifiedCollectionType.DOING))
        // 本地没有任何剧集行 (缓存未加载), 基线与远端都记录 ep101 已看 → 无差异.
        database.bangumiMergeBaselineDao().upsertAll(
            listOf(baseline(1, UnifiedCollectionType.DOING, watchedEpisodeIds = listOf(101))),
        )
        service.collections = listOf(
            remoteSubject(1, AniCollectionType.DOING, watchedEpisodes = mapOf(101 to 1)),
        )

        val plan = repository.computeMergePlan()
        assertTrue(plan.conflictGroups.isEmpty())
        assertTrue(plan.autoMerged.isEmpty())
    }

    @Test
    fun `REPO-13 本地剧集行显式非 DONE 时才视为本地取消看过`() = runRepositoryTest { database, service, _, repository ->
        database.subjectCollection().upsert(localSubject(1, UnifiedCollectionType.DOING))
        // 与 REPO-12 相对: 行存在且明确非 DONE, 才是本地的真实修改.
        database.episodeCollection().upsert(listOf(localEpisode(1, 101, 1, UnifiedCollectionType.WISH)))
        database.bangumiMergeBaselineDao().upsertAll(
            listOf(baseline(1, UnifiedCollectionType.DOING, watchedEpisodeIds = listOf(101))),
        )
        service.collections = listOf(
            remoteSubject(1, AniCollectionType.DOING, watchedEpisodes = mapOf(101 to 1)),
        )

        val plan = repository.computeMergePlan()
        assertTrue(plan.conflictGroups.isEmpty())
        val change = plan.autoMerged.single()
        assertEquals(AutoMergeReason.LOCAL_ONLY, change.reason)
    }
}
