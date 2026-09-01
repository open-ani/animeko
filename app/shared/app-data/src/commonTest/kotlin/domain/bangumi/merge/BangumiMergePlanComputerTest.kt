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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class BangumiMergePlanComputerTest {
    private val computer = BangumiMergePlanComputer()

    private fun snapshot(
        type: UnifiedCollectionType = DOING,
        score: Int = 0,
        comment: String? = null,
        watchedEpisodes: Set<Int> = emptySet(),
        modifiedAt: Long? = null,
        episodeModifiedAt: Map<Int, Long?> = emptyMap(),
    ) = SubjectMergeSnapshot(
        collectionType = type,
        score = score,
        comment = comment,
        episodes = watchedEpisodes.associateWith { DONE },
        collectionModifiedAt = modifiedAt?.let(Instant::fromEpochMilliseconds),
        episodeModifiedAt = episodeModifiedAt.mapValues { (_, v) -> v?.let(Instant::fromEpochMilliseconds) },
    )

    private fun input(
        local: SubjectMergeSnapshot,
        remote: SubjectMergeSnapshot,
        base: SubjectMergeSnapshot?,
        subjectId: Int = 1,
        title: String = "孤独摇滚！",
        episodeSorts: Map<Int, EpisodeSort> = emptyMap(),
    ) = SubjectMergeInput(subjectId, title, local, remote, base, episodeSorts)

    private fun compute(vararg inputs: SubjectMergeInput) = computer.compute(inputs.toList())

    // region 基础规则

    @Test
    fun `MERGE-01 三方完全一致时无冲突无自动合并`() {
        val s = snapshot(type = DOING, score = 8, comment = "好看", watchedEpisodes = setOf(1, 2))
        val plan = compute(input(s, s, s))
        assertTrue(plan.conflictGroups.isEmpty())
        assertTrue(plan.autoMerged.isEmpty())
        assertEquals(0, plan.totalConflictCount)
    }

    @Test
    fun `MERGE-02 无基线且两侧一致时无差异`() {
        val s = snapshot(type = DOING, score = 8)
        val plan = compute(input(s, s, base = null))
        assertTrue(plan.conflictGroups.isEmpty())
        assertTrue(plan.autoMerged.isEmpty())
    }

    @Test
    fun `MERGE-03 无基线且两侧不同时记为冲突`() {
        val plan = compute(input(snapshot(type = DOING), snapshot(type = DROPPED), base = null))
        val group = plan.conflictGroups.single()
        val conflict = assertIs<BangumiMergeConflict.Collection>(group.conflicts.single())
        assertEquals(DOING, conflict.local.value)
        assertEquals(DROPPED, conflict.remote.value)
    }

    @Test
    fun `MERGE-04 仅本地修改时自动采用本地`() {
        val base = snapshot(type = WISH)
        val plan = compute(input(local = snapshot(type = DOING), remote = snapshot(type = WISH), base = base))
        assertTrue(plan.conflictGroups.isEmpty())
        val change = plan.autoMerged.single()
        assertEquals(AutoMergeReason.LOCAL_ONLY, change.reason)
        assertEquals(BangumiMergeFieldId.Collection, change.fieldId)
        assertEquals(DOING, change.mergedValue)
    }

    @Test
    fun `MERGE-05 仅远端修改时自动采用远端`() {
        val base = snapshot(type = WISH)
        val plan = compute(input(local = snapshot(type = WISH), remote = snapshot(type = DOING), base = base))
        assertTrue(plan.conflictGroups.isEmpty())
        val change = plan.autoMerged.single()
        assertEquals(AutoMergeReason.REMOTE_ONLY, change.reason)
        assertEquals(DOING, change.mergedValue)
    }

    @Test
    fun `MERGE-06 两侧一致更改时记为一致自动合并`() {
        val base = snapshot(type = WISH)
        val plan = compute(input(local = snapshot(type = DOING), remote = snapshot(type = DOING), base = base))
        assertTrue(plan.conflictGroups.isEmpty())
        val change = plan.autoMerged.single()
        assertEquals(AutoMergeReason.CONSISTENT, change.reason)
        assertEquals(DOING, change.mergedValue)
    }

    @Test
    fun `MERGE-07 两侧不同修改时记为冲突`() {
        val base = snapshot(type = WISH)
        val plan = compute(input(local = snapshot(type = DOING), remote = snapshot(type = DROPPED), base = base))
        val conflict = assertIs<BangumiMergeConflict.Collection>(plan.conflictGroups.single().conflicts.single())
        assertEquals(DOING, conflict.local.value)
        assertEquals(DROPPED, conflict.remote.value)
        assertTrue(plan.autoMerged.isEmpty())
    }

    // endregion

    // region 较新一侧标记

    @Test
    fun `MERGE-10 本地较新时 newerSide 为 ANIMEKO`() {
        val base = snapshot(type = WISH)
        val plan = compute(
            input(
                local = snapshot(type = DOING, modifiedAt = 2000),
                remote = snapshot(type = DROPPED, modifiedAt = 1000),
                base = base,
            ),
        )
        assertEquals(
            BangumiMergeSide.ANIMEKO,
            plan.conflictGroups.single().conflicts.single().newerSide,
        )
    }

    @Test
    fun `MERGE-11 远端较新时 newerSide 为 BANGUMI`() {
        val base = snapshot(type = WISH)
        val plan = compute(
            input(
                local = snapshot(type = DOING, modifiedAt = 1000),
                remote = snapshot(type = DROPPED, modifiedAt = 2000),
                base = base,
            ),
        )
        assertEquals(
            BangumiMergeSide.BANGUMI,
            plan.conflictGroups.single().conflicts.single().newerSide,
        )
    }

    @Test
    fun `MERGE-12 任一侧时间未知时 newerSide 为 null`() {
        val base = snapshot(type = WISH)
        val plan = compute(
            input(
                local = snapshot(type = DOING, modifiedAt = null),
                remote = snapshot(type = DROPPED, modifiedAt = 2000),
                base = base,
            ),
        )
        assertNull(plan.conflictGroups.single().conflicts.single().newerSide)
    }

    @Test
    fun `MERGE-13 两侧时间相同时 newerSide 为 null`() {
        val base = snapshot(type = WISH)
        val plan = compute(
            input(
                local = snapshot(type = DOING, modifiedAt = 2000),
                remote = snapshot(type = DROPPED, modifiedAt = 2000),
                base = base,
            ),
        )
        assertNull(plan.conflictGroups.single().conflicts.single().newerSide)
    }

    // endregion

    // region 剧集

    @Test
    fun `MERGE-20 剧集看过状态两侧不同修改时逐集冲突`() {
        val base = snapshot(watchedEpisodes = setOf(1))
        val plan = compute(
            input(
                // 本地: 看完 ep2; 远端: 撤销 ep1
                local = snapshot(watchedEpisodes = setOf(1, 2)),
                remote = snapshot(watchedEpisodes = emptySet()),
                base = base,
                episodeSorts = mapOf(1 to EpisodeSort(1), 2 to EpisodeSort(2)),
            ),
        )
        // ep1: 本地未动, 远端撤销 → 自动采用远端; ep2: 本地看过, 远端未动 → 自动采用本地. 无冲突.
        assertTrue(plan.conflictGroups.isEmpty())
        assertEquals(2, plan.autoMerged.size)
        val byEpisode = plan.autoMerged.associateBy { it.fieldId }
        assertEquals(AutoMergeReason.REMOTE_ONLY, byEpisode[BangumiMergeFieldId.Episode(1)]?.reason)
        assertEquals(NOT_COLLECTED, byEpisode[BangumiMergeFieldId.Episode(1)]?.mergedValue)
        assertEquals(AutoMergeReason.LOCAL_ONLY, byEpisode[BangumiMergeFieldId.Episode(2)]?.reason)
        assertEquals(DONE, byEpisode[BangumiMergeFieldId.Episode(2)]?.mergedValue)
    }

    @Test
    fun `MERGE-21 同一剧集两侧相反修改时冲突`() {
        val base = snapshot(watchedEpisodes = emptySet())
        val plan = compute(
            input(
                // 本地看过 ep7, 远端也动过 ep7 (看过又撤销? 无基线粒度) —— 用不同值构造:
                // 基线未看, 本地 DONE, 远端保持未看 → 只有本地改 → 自动.
                // 要制造冲突需要三值不同: 本地 DONE, 远端 DROPPED, 基线未看.
                local = snapshot(watchedEpisodes = setOf(7)),
                remote = SubjectMergeSnapshot(
                    collectionType = DOING,
                    score = 0,
                    comment = null,
                    episodes = mapOf(7 to DROPPED),
                    collectionModifiedAt = null,
                ),
                base = base,
                episodeSorts = mapOf(7 to EpisodeSort(7)),
            ),
        )
        val conflict = assertIs<BangumiMergeConflict.Episode>(plan.conflictGroups.single().conflicts.single())
        assertEquals(7, conflict.episodeId)
        assertEquals(EpisodeSort(7), conflict.sort)
        assertEquals(DONE, conflict.local.value)
        assertEquals(DROPPED, conflict.remote.value)
    }

    @Test
    fun `MERGE-22 剧集时间戳缺失时回退到条目时间戳`() {
        val base = snapshot(watchedEpisodes = emptySet())
        val plan = compute(
            input(
                local = snapshot(watchedEpisodes = setOf(7), modifiedAt = 3000),
                remote = SubjectMergeSnapshot(
                    collectionType = DOING,
                    score = 0,
                    comment = null,
                    episodes = mapOf(7 to DROPPED),
                    collectionModifiedAt = Instant.fromEpochMilliseconds(1000),
                ),
                base = base,
            ),
        )
        val conflict = assertIs<BangumiMergeConflict.Episode>(plan.conflictGroups.single().conflicts.single())
        assertEquals(BangumiMergeSide.ANIMEKO, conflict.newerSide)
    }

    @Test
    fun `MERGE-23 剧集专属时间戳优先于条目时间戳`() {
        val base = snapshot(watchedEpisodes = emptySet())
        val plan = compute(
            input(
                local = snapshot(
                    watchedEpisodes = setOf(7),
                    modifiedAt = 3000,
                    episodeModifiedAt = mapOf(7 to 500L),
                ),
                remote = SubjectMergeSnapshot(
                    collectionType = DOING,
                    score = 0,
                    comment = null,
                    episodes = mapOf(7 to DROPPED),
                    collectionModifiedAt = Instant.fromEpochMilliseconds(1000),
                ),
                base = base,
            ),
        )
        val conflict = assertIs<BangumiMergeConflict.Episode>(plan.conflictGroups.single().conflicts.single())
        assertEquals(BangumiMergeSide.BANGUMI, conflict.newerSide)
    }

    @Test
    fun `MERGE-24 剧集冲突按序号排序`() {
        val base = snapshot(watchedEpisodes = emptySet())
        val local = SubjectMergeSnapshot(
            collectionType = DOING, score = 0, comment = null,
            episodes = mapOf(30 to DONE, 10 to DONE), collectionModifiedAt = null,
        )
        val remote = SubjectMergeSnapshot(
            collectionType = DOING, score = 0, comment = null,
            episodes = mapOf(30 to DROPPED, 10 to DROPPED), collectionModifiedAt = null,
        )
        val plan = compute(
            input(
                local, remote, base,
                // 序号与 id 相反, 验证按 sort 排序.
                episodeSorts = mapOf(30 to EpisodeSort(1), 10 to EpisodeSort(2)),
            ),
        )
        val conflicts = plan.conflictGroups.single().conflicts.map { assertIs<BangumiMergeConflict.Episode>(it) }
        assertEquals(listOf(30, 10), conflicts.map { it.episodeId })
    }

    // endregion

    // region 评分与短评

    @Test
    fun `MERGE-30 仅评分冲突时短评独立自动合并`() {
        val base = snapshot(score = 7, comment = "旧短评")
        val plan = compute(
            input(
                local = snapshot(score = 8, comment = "旧短评"),
                remote = snapshot(score = 6, comment = "新短评"),
                base = base,
            ),
        )
        val conflict = assertIs<BangumiMergeConflict.Rating>(plan.conflictGroups.single().conflicts.single())
        assertEquals(false, conflict.includesComment)
        assertEquals(8, conflict.local.value.score)
        assertEquals(6, conflict.remote.value.score)
        assertNull(conflict.local.value.comment)
        // 短评仅远端修改 → 自动采用远端.
        val commentChange = plan.autoMerged.single { it.fieldId == BangumiMergeFieldId.Comment }
        assertEquals(AutoMergeReason.REMOTE_ONLY, commentChange.reason)
        assertEquals("新短评", commentChange.mergedValue)
    }

    @Test
    fun `MERGE-31 评分与短评同时冲突时并入一行`() {
        val base = snapshot(score = 7, comment = "旧短评")
        val plan = compute(
            input(
                local = snapshot(score = 8, comment = "本地短评"),
                remote = snapshot(score = 6, comment = "远端短评"),
                base = base,
            ),
        )
        val conflict = assertIs<BangumiMergeConflict.Rating>(plan.conflictGroups.single().conflicts.single())
        assertEquals(true, conflict.includesComment)
        assertEquals("本地短评", conflict.local.value.comment)
        assertEquals("远端短评", conflict.remote.value.comment)
        // 不应再有单独的短评冲突.
        assertTrue(plan.conflictGroups.single().conflicts.none { it is BangumiMergeConflict.Comment })
    }

    @Test
    fun `MERGE-32 仅短评冲突时单独一行`() {
        val base = snapshot(score = 8, comment = "旧短评")
        val plan = compute(
            input(
                local = snapshot(score = 8, comment = "本地短评"),
                remote = snapshot(score = 8, comment = "远端短评"),
                base = base,
            ),
        )
        val conflict = assertIs<BangumiMergeConflict.Comment>(plan.conflictGroups.single().conflicts.single())
        assertEquals("本地短评", conflict.local.value)
        assertEquals("远端短评", conflict.remote.value)
    }

    @Test
    fun `MERGE-33 评分一致更改自动合并`() {
        val base = snapshot(score = 0)
        val plan = compute(
            input(local = snapshot(score = 9), remote = snapshot(score = 9), base = base),
        )
        val change = plan.autoMerged.single()
        assertEquals(AutoMergeReason.CONSISTENT, change.reason)
        assertEquals(MergeRatingValue(9, null), change.mergedValue)
    }

    // endregion

    // region 删除收藏 (整体处理)

    @Test
    fun `MERGE-40 本地删除远端修改时只产生一条收藏冲突`() {
        val base = snapshot(type = WISH, score = 7, watchedEpisodes = setOf(1))
        val plan = compute(
            input(
                local = SubjectMergeSnapshot.NotCollected,
                remote = snapshot(type = DOING, score = 8, watchedEpisodes = setOf(1, 2)),
                base = base,
            ),
        )
        val group = plan.conflictGroups.single()
        val conflict = assertIs<BangumiMergeConflict.Collection>(group.conflicts.single())
        assertEquals(NOT_COLLECTED, conflict.local.value)
        assertEquals(DOING, conflict.remote.value)
        // 整体处理: 不再产生评分/剧集冲突或自动合并.
        assertTrue(plan.autoMerged.isEmpty())
        // 选择删除侧是破坏性的.
        assertTrue(conflict.isDestructive(BangumiMergeSide.ANIMEKO))
        assertEquals(false, conflict.isDestructive(BangumiMergeSide.BANGUMI))
    }

    @Test
    fun `MERGE-41 仅远端删除时也不自动合并而是冲突`() {
        // 防御性规则: 删除收藏是破坏性变更, 即使只有一侧修改也必须由用户确认.
        val base = snapshot(type = DOING, score = 8)
        val plan = compute(
            input(
                local = snapshot(type = DOING, score = 8),
                remote = SubjectMergeSnapshot.NotCollected,
                base = base,
            ),
        )
        assertTrue(plan.autoMerged.isEmpty())
        val conflict = assertIs<BangumiMergeConflict.Collection>(plan.conflictGroups.single().conflicts.single())
        assertEquals(DOING, conflict.local.value)
        assertEquals(NOT_COLLECTED, conflict.remote.value)
        assertTrue(conflict.isDestructive(BangumiMergeSide.BANGUMI))
        assertEquals(false, conflict.isDestructive(BangumiMergeSide.ANIMEKO))
    }

    @Test
    fun `MERGE-47 仅本地删除时也不自动合并而是冲突`() {
        val base = snapshot(type = DOING, score = 8)
        val plan = compute(
            input(
                local = SubjectMergeSnapshot.NotCollected,
                remote = snapshot(type = DOING, score = 8),
                base = base,
            ),
        )
        assertTrue(plan.autoMerged.isEmpty())
        val conflict = assertIs<BangumiMergeConflict.Collection>(plan.conflictGroups.single().conflicts.single())
        assertEquals(NOT_COLLECTED, conflict.local.value)
        assertTrue(conflict.isDestructive(BangumiMergeSide.ANIMEKO))
    }

    @Test
    fun `MERGE-48 两侧都已删除时一致自动合并且无写操作需求`() {
        // 两侧都已是未收藏, 状态已收敛, 记为一致更改自动合并 (仅更新基线).
        val base = snapshot(type = DOING, score = 8)
        val plan = compute(
            input(
                local = SubjectMergeSnapshot.NotCollected,
                remote = SubjectMergeSnapshot.NotCollected,
                base = base,
            ),
        )
        assertTrue(plan.conflictGroups.isEmpty())
        val change = plan.autoMerged.single()
        assertEquals(AutoMergeReason.CONSISTENT, change.reason)
        assertEquals(NOT_COLLECTED, change.mergedValue)
        assertTrue(change.isDestructive)
    }

    @Test
    fun `MERGE-42 基线未收藏而远端新增时整体采用远端`() {
        val base = SubjectMergeSnapshot.NotCollected
        val plan = compute(
            input(
                local = SubjectMergeSnapshot.NotCollected,
                remote = snapshot(type = WISH, score = 0),
                base = base,
            ),
        )
        assertTrue(plan.conflictGroups.isEmpty())
        val change = plan.autoMerged.single()
        assertEquals(AutoMergeReason.REMOTE_ONLY, change.reason)
        assertEquals(WISH, change.mergedValue)
        assertEquals(false, change.isDestructive)
    }

    @Test
    fun `MERGE-43 无基线且一侧未收藏另一侧收藏时冲突`() {
        val plan = compute(
            input(
                local = snapshot(type = DOING, score = 8, watchedEpisodes = setOf(1)),
                remote = SubjectMergeSnapshot.NotCollected,
                base = null,
            ),
        )
        val conflict = assertIs<BangumiMergeConflict.Collection>(plan.conflictGroups.single().conflicts.single())
        assertEquals(DOING, conflict.local.value)
        assertEquals(NOT_COLLECTED, conflict.remote.value)
        // 整体处理, 无其他冲突.
        assertEquals(1, plan.totalConflictCount)
    }

    @Test
    fun `MERGE-44 本地仅修改进度而远端删除时必须冲突而非自动删除`() {
        // 回归: 整体处理必须以完整快照判断一侧是否有修改, 不能只看收藏状态.
        val base = snapshot(type = DOING, watchedEpisodes = setOf(1))
        val plan = compute(
            input(
                local = snapshot(type = DOING, watchedEpisodes = setOf(1, 2, 3)), // 本地看了 ep2-3
                remote = SubjectMergeSnapshot.NotCollected, // 远端删除了收藏
                base = base,
            ),
        )
        assertTrue(plan.autoMerged.isEmpty(), "不能自动采用删除")
        val conflict = assertIs<BangumiMergeConflict.Collection>(plan.conflictGroups.single().conflicts.single())
        assertEquals(DOING, conflict.local.value)
        assertEquals(NOT_COLLECTED, conflict.remote.value)
    }

    @Test
    fun `MERGE-45 远端仅修改评分而本地删除时必须冲突`() {
        val base = snapshot(type = DOING, score = 7)
        val plan = compute(
            input(
                local = SubjectMergeSnapshot.NotCollected,
                remote = snapshot(type = DOING, score = 9), // 远端改了评分
                base = base,
            ),
        )
        assertTrue(plan.autoMerged.isEmpty())
        val conflict = assertIs<BangumiMergeConflict.Collection>(plan.conflictGroups.single().conflicts.single())
        assertEquals(NOT_COLLECTED, conflict.local.value)
        assertEquals(DOING, conflict.remote.value)
    }

    @Test
    fun `MERGE-46 未归一化的未收藏快照不产生幻影冲突`() {
        // 回归: 未收藏一侧带有残留评分/剧集时, 引擎入口归一化, 不产生任何子字段冲突.
        val dirtyNotCollected = SubjectMergeSnapshot(
            collectionType = NOT_COLLECTED,
            score = 8,
            comment = "残留短评",
            episodes = mapOf(5 to DONE),
            collectionModifiedAt = null,
        )
        val plan = compute(
            input(
                local = dirtyNotCollected,
                remote = SubjectMergeSnapshot.NotCollected,
                base = null,
            ),
        )
        assertTrue(plan.conflictGroups.isEmpty())
        assertTrue(plan.autoMerged.isEmpty())
        // 存入计划的输入已归一化.
        assertEquals(SubjectMergeSnapshot.NotCollected, plan.inputs.single().local)
    }

    // endregion

    // region 排序与统计

    @Test
    fun `MERGE-50 冲突按 收藏-剧集-评分-短评 排序`() {
        val base = snapshot(type = WISH, score = 7, comment = "旧", watchedEpisodes = emptySet())
        val local = SubjectMergeSnapshot(
            collectionType = DOING, score = 8, comment = "本地",
            episodes = mapOf(1 to DONE), collectionModifiedAt = null,
        )
        val remote = SubjectMergeSnapshot(
            collectionType = DROPPED, score = 6, comment = "远端",
            episodes = mapOf(1 to DROPPED), collectionModifiedAt = null,
        )
        val plan = compute(input(local, remote, base, episodeSorts = mapOf(1 to EpisodeSort(1))))
        val conflicts = plan.conflictGroups.single().conflicts
        assertEquals(3, conflicts.size)
        assertIs<BangumiMergeConflict.Collection>(conflicts[0])
        assertIs<BangumiMergeConflict.Episode>(conflicts[1])
        val rating = assertIs<BangumiMergeConflict.Rating>(conflicts[2])
        assertEquals(true, rating.includesComment)
    }

    @Test
    fun `MERGE-51 多条目时保持输入顺序并统计冲突总数`() {
        val base = snapshot(type = WISH)
        val conflicting1 = input(
            local = snapshot(type = DOING),
            remote = snapshot(type = DROPPED),
            base = base,
            subjectId = 10,
            title = "A",
        )
        val clean = input(snapshot(type = DOING), snapshot(type = DOING), snapshot(type = DOING), subjectId = 20, title = "B")
        val conflicting2 = input(
            local = snapshot(score = 8),
            remote = snapshot(score = 6),
            base = snapshot(score = 7),
            subjectId = 30,
            title = "C",
        )
        val plan = compute(conflicting1, clean, conflicting2)
        assertEquals(listOf(10, 30), plan.conflictGroups.map { it.subjectId })
        assertEquals(listOf("A", "C"), plan.conflictGroups.map { it.title })
        assertEquals(2, plan.totalConflictCount)
        assertEquals(3, plan.inputs.size)
    }

    // endregion
}
