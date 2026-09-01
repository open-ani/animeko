/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.bangumi.merge

import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.UnifiedCollectionType.DOING
import me.him188.ani.datasources.api.topic.UnifiedCollectionType.DONE
import me.him188.ani.datasources.api.topic.UnifiedCollectionType.DROPPED
import me.him188.ani.datasources.api.topic.UnifiedCollectionType.NOT_COLLECTED
import me.him188.ani.datasources.api.topic.UnifiedCollectionType.WISH
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BangumiMergeOpCompilerTest {
    private val computer = BangumiMergePlanComputer()
    private val compiler = BangumiMergeOpCompiler()

    private fun snapshot(
        type: UnifiedCollectionType = DOING,
        score: Int = 0,
        comment: String? = null,
        episodes: Map<Int, UnifiedCollectionType> = emptyMap(),
    ) = SubjectMergeSnapshot(
        collectionType = type,
        score = score,
        comment = comment,
        episodes = episodes,
        collectionModifiedAt = null,
    )

    private fun input(
        local: SubjectMergeSnapshot,
        remote: SubjectMergeSnapshot,
        base: SubjectMergeSnapshot?,
        subjectId: Int = 1,
        episodeSorts: Map<Int, EpisodeSort> = emptyMap(),
    ) = SubjectMergeInput(subjectId, "条目 $subjectId", local, remote, base, episodeSorts)

    private fun resolveAll(plan: BangumiMergePlan, side: BangumiMergeSide): BangumiMergeResolution =
        BangumiMergeResolution(
            plan.conflictGroups.flatMap { it.conflictKeys }.associateWith { side },
        )

    @Test
    fun `COMPILE-01 缺少冲突选择时抛出异常`() {
        val plan = computer.compute(
            listOf(input(snapshot(type = DOING), snapshot(type = DROPPED), base = null)),
        )
        assertFailsWith<IllegalArgumentException> {
            compiler.compile(plan, BangumiMergeResolution.Empty)
        }
    }

    @Test
    fun `COMPILE-02 无差异时无操作但仍输出基线`() {
        val s = snapshot(type = DOING, score = 8, episodes = mapOf(1 to DONE))
        val plan = computer.compute(listOf(input(s, s, s)))
        val result = compiler.compile(plan, BangumiMergeResolution.Empty)
        assertTrue(result.ops.isEmpty())
        val state = result.mergedStates.single()
        assertEquals(1, state.subjectId)
        assertEquals(DOING, state.snapshot.collectionType)
        assertEquals(mapOf(1 to DONE), state.snapshot.episodes)
    }

    @Test
    fun `COMPILE-03 冲突选择本地时产生推送远端的操作`() {
        val plan = computer.compute(
            listOf(input(snapshot(type = DOING), snapshot(type = DROPPED), base = snapshot(type = WISH))),
        )
        val result = compiler.compile(plan, resolveAll(plan, BangumiMergeSide.ANIMEKO))
        val op = result.ops.single()
        assertEquals(BangumiMergeApplyOp.SetSubjectCollection(1, DOING), op)
        assertEquals(DOING, result.mergedStates.single().snapshot.collectionType)
    }

    @Test
    fun `COMPILE-04 冲突选择远端时产生更新本地的操作`() {
        val plan = computer.compute(
            listOf(input(snapshot(type = DOING), snapshot(type = DROPPED), base = snapshot(type = WISH))),
        )
        val result = compiler.compile(plan, resolveAll(plan, BangumiMergeSide.BANGUMI))
        val op = result.ops.single()
        assertEquals(BangumiMergeApplyOp.SetSubjectCollection(1, DROPPED), op)
        assertEquals(DROPPED, result.mergedStates.single().snapshot.collectionType)
    }

    @Test
    fun `COMPILE-05 一致更改不产生操作但更新基线`() {
        val plan = computer.compute(
            listOf(input(snapshot(type = DOING), snapshot(type = DOING), base = snapshot(type = WISH))),
        )
        val result = compiler.compile(plan, BangumiMergeResolution.Empty)
        assertTrue(result.ops.isEmpty())
        assertEquals(DOING, result.mergedStates.single().snapshot.collectionType)
    }

    @Test
    fun `COMPILE-06 单侧修改的自动合并会产生收敛操作`() {
        // 仅本地修改: 需要推送远端.
        val localOnly = computer.compute(
            listOf(input(snapshot(type = DOING), snapshot(type = WISH), base = snapshot(type = WISH))),
        )
        val localResult = compiler.compile(localOnly, BangumiMergeResolution.Empty)
        assertEquals(
            listOf<BangumiMergeApplyOp>(BangumiMergeApplyOp.SetSubjectCollection(1, DOING)),
            localResult.ops,
        )

        // 仅远端修改: 需要更新本地.
        val remoteOnly = computer.compute(
            listOf(input(snapshot(type = WISH), snapshot(type = DOING), base = snapshot(type = WISH))),
        )
        val remoteResult = compiler.compile(remoteOnly, BangumiMergeResolution.Empty)
        assertEquals(
            listOf<BangumiMergeApplyOp>(BangumiMergeApplyOp.SetSubjectCollection(1, DOING)),
            remoteResult.ops,
        )
    }

    @Test
    fun `COMPILE-10 选择删除侧时只产生删除操作`() {
        val plan = computer.compute(
            listOf(
                input(
                    local = SubjectMergeSnapshot.NotCollected,
                    remote = snapshot(type = DOING, score = 8, episodes = mapOf(1 to DONE)),
                    base = snapshot(type = WISH, score = 7),
                ),
            ),
        )
        val result = compiler.compile(plan, resolveAll(plan, BangumiMergeSide.ANIMEKO))
        assertEquals(
            listOf<BangumiMergeApplyOp>(BangumiMergeApplyOp.SetSubjectCollection(1, NOT_COLLECTED)),
            result.ops,
        )
        assertEquals(SubjectMergeSnapshot.NotCollected, result.mergedStates.single().snapshot)
    }

    @Test
    fun `COMPILE-11 选择保留侧时恢复完整状态`() {
        val remote = snapshot(type = DOING, score = 8, comment = "好看", episodes = mapOf(1 to DONE, 2 to DONE))
        val plan = computer.compute(
            listOf(
                input(
                    local = SubjectMergeSnapshot.NotCollected,
                    remote = remote,
                    base = snapshot(type = WISH, score = 7),
                ),
            ),
        )
        val result = compiler.compile(plan, resolveAll(plan, BangumiMergeSide.BANGUMI))
        // 本地需要恢复: 收藏 + 评分 + 两集进度.
        assertEquals(
            listOf(
                BangumiMergeApplyOp.SetSubjectCollection(1, DOING),
                BangumiMergeApplyOp.UpdateRating(1, 8, "好看"),
                BangumiMergeApplyOp.SetEpisodeCollection(1, 1, DONE),
                BangumiMergeApplyOp.SetEpisodeCollection(1, 2, DONE),
            ),
            result.ops,
        )
        assertEquals(DOING, result.mergedStates.single().snapshot.collectionType)
        assertEquals(mapOf(1 to DONE, 2 to DONE), result.mergedStates.single().snapshot.episodes)
    }

    @Test
    fun `COMPILE-12 仅远端删除的冲突选择删除侧时产生删除操作`() {
        // 单侧删除不再自动合并 (破坏性变更必须用户确认), 用户选择删除侧后编译出删除操作.
        val plan = computer.compute(
            listOf(
                input(
                    local = snapshot(type = DOING, score = 8),
                    remote = SubjectMergeSnapshot.NotCollected,
                    base = snapshot(type = DOING, score = 8),
                ),
            ),
        )
        assertEquals(1, plan.totalConflictCount)
        val result = compiler.compile(plan, resolveAll(plan, BangumiMergeSide.BANGUMI))
        assertEquals(
            listOf<BangumiMergeApplyOp>(BangumiMergeApplyOp.SetSubjectCollection(1, NOT_COLLECTED)),
            result.ops,
        )
        assertEquals(SubjectMergeSnapshot.NotCollected, result.mergedStates.single().snapshot)
    }

    @Test
    fun `COMPILE-20 评分冲突并入短评时选择一侧同时应用两者`() {
        val plan = computer.compute(
            listOf(
                input(
                    local = snapshot(score = 8, comment = "本地"),
                    remote = snapshot(score = 6, comment = "远端"),
                    base = snapshot(score = 7, comment = "旧"),
                ),
            ),
        )
        val result = compiler.compile(plan, resolveAll(plan, BangumiMergeSide.BANGUMI))
        assertEquals(
            listOf<BangumiMergeApplyOp>(BangumiMergeApplyOp.UpdateRating(1, 6, "远端")),
            result.ops,
        )
        val merged = result.mergedStates.single().snapshot
        assertEquals(6, merged.score)
        assertEquals("远端", merged.comment)
    }

    @Test
    fun `COMPILE-21 评分冲突不并入短评时短评独立解决`() {
        // 评分两侧都改 (冲突), 短评仅远端改 (自动).
        val plan = computer.compute(
            listOf(
                input(
                    local = snapshot(score = 8, comment = "旧"),
                    remote = snapshot(score = 6, comment = "远端新"),
                    base = snapshot(score = 7, comment = "旧"),
                ),
            ),
        )
        // 评分选本地.
        val result = compiler.compile(plan, resolveAll(plan, BangumiMergeSide.ANIMEKO))
        assertEquals(
            listOf<BangumiMergeApplyOp>(BangumiMergeApplyOp.UpdateRating(1, 8, "远端新")),
            result.ops,
        )
        val merged = result.mergedStates.single().snapshot
        assertEquals(8, merged.score)
        assertEquals("远端新", merged.comment)
    }

    @Test
    fun `COMPILE-30 剧集冲突分侧选择`() {
        val base = snapshot(episodes = emptyMap())
        val local = snapshot(episodes = mapOf(1 to DONE, 2 to DONE))
        val remote = snapshot(episodes = mapOf(1 to DROPPED, 2 to DROPPED))
        val plan = computer.compute(
            listOf(input(local, remote, base, episodeSorts = mapOf(1 to EpisodeSort(1), 2 to EpisodeSort(2)))),
        )
        assertEquals(2, plan.totalConflictCount)
        // ep1 选本地, ep2 选远端.
        val resolution = BangumiMergeResolution(
            mapOf(
                BangumiMergeConflictKey(1, BangumiMergeFieldId.Episode(1)) to BangumiMergeSide.ANIMEKO,
                BangumiMergeConflictKey(1, BangumiMergeFieldId.Episode(2)) to BangumiMergeSide.BANGUMI,
            ),
        )
        val result = compiler.compile(plan, resolution)
        assertEquals(
            listOf(
                BangumiMergeApplyOp.SetEpisodeCollection(1, 1, DONE),
                BangumiMergeApplyOp.SetEpisodeCollection(1, 2, DROPPED),
            ),
            result.ops,
        )
        val merged = result.mergedStates.single().snapshot
        // DROPPED 不属于看过, 合并快照中只保留非 NOT_COLLECTED 的值.
        assertEquals(mapOf(1 to DONE, 2 to DROPPED), merged.episodes)
    }

    @Test
    fun `COMPILE-33 删除对照冲突选择保留侧时恢复本地进度`() {
        // 回归 MERGE-44 场景: 本地看了新剧集, 远端删除; 用户选择保留本地.
        val plan = computer.compute(
            listOf(
                input(
                    local = snapshot(type = DOING, episodes = mapOf(1 to DONE, 2 to DONE)),
                    remote = SubjectMergeSnapshot.NotCollected,
                    base = snapshot(type = DOING, episodes = mapOf(1 to DONE)),
                ),
            ),
        )
        assertEquals(1, plan.totalConflictCount)
        val result = compiler.compile(plan, resolveAll(plan, BangumiMergeSide.ANIMEKO))
        // 远端需要恢复: 收藏 + 两集进度.
        assertEquals(
            listOf(
                BangumiMergeApplyOp.SetSubjectCollection(1, DOING),
                BangumiMergeApplyOp.SetEpisodeCollection(1, 1, DONE),
                BangumiMergeApplyOp.SetEpisodeCollection(1, 2, DONE),
            ),
            result.ops,
        )
        val merged = result.mergedStates.single().snapshot
        assertEquals(DOING, merged.collectionType)
        assertEquals(mapOf(1 to DONE, 2 to DONE), merged.episodes)
    }

    @Test
    fun `COMPILE-34 未归一化的两侧未收藏条目无操作且基线记录未收藏`() {
        val dirty = SubjectMergeSnapshot(
            collectionType = NOT_COLLECTED,
            score = 8,
            comment = "残留",
            episodes = mapOf(5 to DONE),
            collectionModifiedAt = null,
        )
        val plan = computer.compute(
            listOf(input(local = dirty, remote = SubjectMergeSnapshot.NotCollected, base = null)),
        )
        assertTrue(plan.conflictGroups.isEmpty())
        val result = compiler.compile(plan, BangumiMergeResolution.Empty)
        assertTrue(result.ops.isEmpty())
        assertEquals(SubjectMergeSnapshot.NotCollected, result.mergedStates.single().snapshot)
    }

    @Test
    fun `COMPILE-31 多条目时所有条目都输出基线`() {
        val base = snapshot(type = WISH)
        val plan = computer.compute(
            listOf(
                input(snapshot(type = DOING), snapshot(type = DROPPED), base, subjectId = 10),
                input(snapshot(type = DONE), snapshot(type = DONE), snapshot(type = DONE), subjectId = 20),
            ),
        )
        val result = compiler.compile(plan, resolveAll(plan, BangumiMergeSide.ANIMEKO))
        assertEquals(listOf(10, 20), result.mergedStates.map { it.subjectId })
    }

    @Test
    fun `COMPILE-32 应用合并后重新计算无冲突`() {
        // 端到端: 合并结果写回基线后, 用最终状态作为两侧输入, 应无任何差异.
        val plan = computer.compute(
            listOf(
                input(
                    local = snapshot(type = DOING, score = 8, comment = "本地", episodes = mapOf(1 to DONE)),
                    remote = snapshot(type = DROPPED, score = 6, comment = "远端", episodes = mapOf(2 to DONE)),
                    base = snapshot(type = WISH, score = 7, comment = "旧"),
                ),
            ),
        )
        val result = compiler.compile(plan, resolveAll(plan, BangumiMergeSide.ANIMEKO))
        val merged = result.mergedStates.single().snapshot

        val nextPlan = computer.compute(
            listOf(input(local = merged, remote = merged, base = merged)),
        )
        assertTrue(nextPlan.conflictGroups.isEmpty())
        assertTrue(nextPlan.autoMerged.isEmpty())
    }
}
