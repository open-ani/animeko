/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.bangumi.merge

import me.him188.ani.app.data.models.bangumi.BangumiConflictField
import me.him188.ani.app.data.models.bangumi.BangumiConflictFieldType
import me.him188.ani.app.data.models.bangumi.BangumiConflictKey
import me.him188.ani.app.data.models.bangumi.BangumiMergeSide
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * 服务端冲突模型 → UI 行的映射规则.
 */
@OptIn(TestOnly::class)
class BangumiMergeUiModelsTest {
    private val now = Instant.fromEpochMilliseconds(1_753_000_000_000)
    private val earlier = now - 5.hours

    private fun rating(
        score: Int,
        comment: String? = null,
        tags: List<String> = emptyList(),
        isPrivate: Boolean = false,
    ) = SelfRatingInfo(score, comment, tags, isPrivate)

    // region 收藏状态行

    @Test
    fun `MODEL-01 收藏状态行 Animeko 较新`() {
        val row = BangumiConflictField.Collection(UnifiedCollectionType.DOING, UnifiedCollectionType.DROPPED)
            .toUiConflict(subjectId = 1, animekoUpdatedAt = now, bangumiUpdatedAt = earlier)
        assertIs<BangumiMergeConflict.Collection>(row)
        assertEquals(BangumiConflictKey(1, BangumiConflictFieldType.COLLECTION), row.key)
        assertEquals(UnifiedCollectionType.DOING, row.animeko.value)
        assertEquals(UnifiedCollectionType.DROPPED, row.bangumi.value)
        assertEquals(now, row.animeko.modifiedAt)
        assertEquals(earlier, row.bangumi.modifiedAt)
        assertEquals(BangumiMergeSide.ANIMEKO, row.newerSide)
        assertFalse(row.isDestructive(BangumiMergeSide.ANIMEKO))
        assertFalse(row.isDestructive(BangumiMergeSide.BANGUMI))
    }

    @Test
    fun `MODEL-02 收藏状态行 Bangumi 较新`() {
        val row = BangumiConflictField.Collection(UnifiedCollectionType.DOING, UnifiedCollectionType.DONE)
            .toUiConflict(subjectId = 1, animekoUpdatedAt = earlier, bangumiUpdatedAt = now)
        assertEquals(BangumiMergeSide.BANGUMI, row.newerSide)
    }

    @Test
    fun `MODEL-03 两侧时间相同或缺失时没有较新一侧`() {
        assertNull(
            BangumiConflictField.Collection(UnifiedCollectionType.DOING, UnifiedCollectionType.DONE)
                .toUiConflict(1, now, now).newerSide,
        )
        assertNull(
            BangumiConflictField.Collection(UnifiedCollectionType.DOING, UnifiedCollectionType.DONE)
                .toUiConflict(1, null, now).newerSide,
        )
        assertNull(
            BangumiConflictField.Collection(UnifiedCollectionType.DOING, UnifiedCollectionType.DONE)
                .toUiConflict(1, now, null).newerSide,
        )
    }

    @Test
    fun `MODEL-04 Bangumi 侧已删除收藏 没有 Bangumi 时间且为破坏性`() {
        // 服务端对已删除的一侧不给 bangumiUpdatedAt; 即使给了也不应显示 (删除不是一次有时间的修改).
        val row = BangumiConflictField.Collection(UnifiedCollectionType.DOING, UnifiedCollectionType.NOT_COLLECTED)
            .toUiConflict(subjectId = 4, animekoUpdatedAt = earlier, bangumiUpdatedAt = now)
        assertIs<BangumiMergeConflict.Collection>(row)
        assertEquals(earlier, row.animeko.modifiedAt)
        assertNull(row.bangumi.modifiedAt)
        assertNull(row.newerSide)
        assertTrue(row.isDestructive(BangumiMergeSide.BANGUMI))
        assertFalse(row.isDestructive(BangumiMergeSide.ANIMEKO))
    }

    // endregion

    // region 评分单元行

    @Test
    fun `MODEL-05 评分不同 → 评分行 含不同的短评`() {
        val row = BangumiConflictField.Rating(rating(8, "好看"), rating(7, "一般"))
            .toUiConflict(subjectId = 2, animekoUpdatedAt = now, bangumiUpdatedAt = now)
        assertIs<BangumiMergeConflict.Rating>(row)
        assertEquals(BangumiConflictKey(2, BangumiConflictFieldType.RATING), row.key)
        assertEquals(8, row.animeko.value.score)
        assertEquals(7, row.bangumi.value.score)
        assertEquals("好看", row.animeko.value.comment)
        assertEquals("一般", row.bangumi.value.comment)
        assertTrue(row.includesComment)
        assertFalse(row.includesTags)
        assertFalse(row.includesPrivate)
    }

    @Test
    fun `MODEL-06 评分不同 短评相同 → 评分行不含短评`() {
        val row = BangumiConflictField.Rating(rating(8, "好看"), rating(7, "好看"))
            .toUiConflict(2, now, now)
        assertIs<BangumiMergeConflict.Rating>(row)
        assertFalse(row.includesComment)
    }

    @Test
    fun `MODEL-07 仅短评不同 → 短评行`() {
        val row = BangumiConflictField.Rating(rating(9, "世界线收束"), rating(9, "二周目"))
            .toUiConflict(3, now, earlier)
        assertIs<BangumiMergeConflict.Comment>(row)
        assertEquals(BangumiConflictFieldType.RATING, row.fieldType)
        assertEquals("世界线收束", row.animeko.value)
        assertEquals("二周目", row.bangumi.value)
    }

    @Test
    fun `MODEL-08 仅标签不同 → 评分行 标记标签`() {
        val row = BangumiConflictField.Rating(rating(8, tags = listOf("a", "b")), rating(8, tags = listOf("a")))
            .toUiConflict(5, now, now)
        assertIs<BangumiMergeConflict.Rating>(row)
        assertFalse(row.includesComment)
        assertTrue(row.includesTags)
        assertFalse(row.includesPrivate)
        assertEquals(listOf("a", "b"), row.animeko.value.tags)
    }

    @Test
    fun `MODEL-09 仅私密不同 → 评分行 标记私密`() {
        val row = BangumiConflictField.Rating(rating(8, isPrivate = true), rating(8, isPrivate = false))
            .toUiConflict(5, now, now)
        assertIs<BangumiMergeConflict.Rating>(row)
        assertFalse(row.includesComment)
        assertFalse(row.includesTags)
        assertTrue(row.includesPrivate)
    }

    @Test
    fun `MODEL-10 短评不同且标签不同 → 评分行 同时标记`() {
        val row = BangumiConflictField.Rating(rating(8, "a", tags = listOf("x")), rating(8, "b", tags = listOf("y")))
            .toUiConflict(5, now, now)
        assertIs<BangumiMergeConflict.Rating>(row)
        assertTrue(row.includesComment)
        assertTrue(row.includesTags)
    }

    @Test
    fun `MODEL-11 归一化 空白短评视为无 标签按集合比较`() {
        val row = BangumiConflictField.Rating(
            rating(8, "  ", tags = listOf("a", "b")),
            rating(7, null, tags = listOf("b", "a")),
        ).toUiConflict(5, now, now)
        assertIs<BangumiMergeConflict.Rating>(row)
        assertNull(row.animeko.value.comment)
        assertFalse(row.includesComment)
        assertFalse(row.includesTags)
    }

    @Test
    fun `MODEL-12 评分行两侧都不显示时间 不参与采用较新的`() {
        val row = BangumiConflictField.Rating(rating(8), rating(7))
            .toUiConflict(2, animekoUpdatedAt = now, bangumiUpdatedAt = earlier)
        assertNull(row.sideModifiedAt(BangumiMergeSide.ANIMEKO))
        assertNull(row.sideModifiedAt(BangumiMergeSide.BANGUMI))
        assertNull(row.newerSide)

        val comment = BangumiConflictField.Rating(rating(8, "a"), rating(8, "b"))
            .toUiConflict(2, animekoUpdatedAt = now, bangumiUpdatedAt = earlier)
        assertNull(comment.sideModifiedAt(BangumiMergeSide.ANIMEKO))
        assertNull(comment.newerSide)
    }

    // endregion

    // region 分组

    @Test
    fun `MODEL-13 测试数据映射为 5 组 6 行 且 key 与服务端字段一一对应`() {
        val state = createTestBangumiMergeState(now)
        val groups = state.toConflictGroups()
        assertEquals(5, groups.size)
        assertEquals(6, groups.sumOf { it.conflicts.size })
        assertEquals(state.conflictCount, groups.sumOf { it.conflicts.size })
        assertEquals(
            state.conflicts.flatMap { it.conflictKeys },
            groups.flatMap { it.conflictKeys },
        )
        assertEquals(listOf(1, 2, 3, 4, 5), groups.map { it.subjectId })
        assertEquals("孤独摇滚！", groups[0].title)
    }

    @Test
    fun `MODEL-14 测试数据中只有两行可判定较新一侧`() {
        val groups = createTestBangumiMergeState(now).toConflictGroups()
        val newer = groups.flatMap { g -> g.conflicts.mapNotNull { c -> c.newerSide?.let { c.key to it } } }.toMap()
        assertEquals(
            mapOf(
                BangumiConflictKey(1, BangumiConflictFieldType.COLLECTION) to BangumiMergeSide.ANIMEKO,
                BangumiConflictKey(2, BangumiConflictFieldType.COLLECTION) to BangumiMergeSide.BANGUMI,
            ),
            newer,
        )
        // 行类型: 1 状态, 2 状态+评分(含短评), 3 短评, 4 已删除, 5 评分(标签)
        assertIs<BangumiMergeConflict.Collection>(groups[0].conflicts.single())
        assertIs<BangumiMergeConflict.Collection>(groups[1].conflicts[0])
        assertIs<BangumiMergeConflict.Rating>(groups[1].conflicts[1]).let { assertTrue(it.includesComment) }
        assertIs<BangumiMergeConflict.Comment>(groups[2].conflicts.single())
        assertIs<BangumiMergeConflict.Collection>(groups[3].conflicts.single()).let {
            assertTrue(it.isDestructive(BangumiMergeSide.BANGUMI))
        }
        assertIs<BangumiMergeConflict.Rating>(groups[4].conflicts.single()).let { assertTrue(it.includesTags) }
    }

    // endregion
}
