/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.bangumi.merge

import me.him188.ani.app.domain.bangumi.merge.BangumiMergeConflictKey
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeFieldId
import me.him188.ani.app.domain.bangumi.merge.BangumiMergePlan
import me.him188.ani.app.domain.bangumi.merge.BangumiMergePlanComputer
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeSide
import me.him188.ani.app.domain.bangumi.merge.SubjectMergeInput
import me.him188.ani.app.domain.bangumi.merge.SubjectMergeSnapshot
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * 对照 Figma 设计稿 (同步冲突 · 双列对照) 的示例数据, 通过真实合并引擎计算.
 */
@TestOnly
fun createTestBangumiMergePlan(now: Instant): BangumiMergePlan {
    val computer = BangumiMergePlanComputer()

    fun snapshot(
        type: UnifiedCollectionType = UnifiedCollectionType.DOING,
        score: Int = 0,
        comment: String? = null,
        episodes: Map<Int, UnifiedCollectionType> = emptyMap(),
        modifiedAt: Instant? = null,
    ) = SubjectMergeSnapshot(
        collectionType = type,
        score = score,
        comment = comment,
        episodes = episodes,
        collectionModifiedAt = modifiedAt,
    )

    val inputs = listOf(
        // 状态冲突: 在看 (本地, 较新) vs 抛弃 (远端, 昨天).
        SubjectMergeInput(
            subjectId = 1,
            title = "孤独摇滚！",
            local = snapshot(type = UnifiedCollectionType.DOING, modifiedAt = now - 30.minutes),
            remote = snapshot(type = UnifiedCollectionType.DROPPED, modifiedAt = now - 16.hours),
            base = snapshot(type = UnifiedCollectionType.WISH),
        ),
        // 无基线 (首次合并): 进度与评分都冲突.
        SubjectMergeInput(
            subjectId = 2,
            title = "葬送的芙莉莲",
            local = snapshot(
                score = 8,
                comment = "旅途的意义就在旅途中",
                episodes = mapOf(7 to UnifiedCollectionType.DONE),
                modifiedAt = now - 2.hours,
            ),
            remote = snapshot(
                score = 7,
                comment = "节奏偏慢",
                modifiedAt = now - 4.days,
            ),
            base = null,
            episodeSorts = mapOf(7 to EpisodeSort(7)),
        ),
        // 仅短评冲突.
        SubjectMergeInput(
            subjectId = 3,
            title = "我的青春恋爱物语果然有问题。完",
            local = snapshot(
                type = UnifiedCollectionType.DONE,
                score = 9,
                comment = "世界线收束，神作",
                modifiedAt = now - 9.days,
            ),
            remote = snapshot(
                type = UnifiedCollectionType.DONE,
                score = 9,
                comment = "二周目细节更多",
                modifiedAt = now - 7.days,
            ),
            base = snapshot(type = UnifiedCollectionType.DONE, score = 9, comment = "旧短评"),
        ),
        // 本地删除收藏 vs 远端 想看 → 在看: 整体冲突.
        SubjectMergeInput(
            subjectId = 4,
            title = "上伊那牡丹，酒醉身姿似百合花般",
            local = SubjectMergeSnapshot.NotCollected.copy(collectionModifiedAt = now - 5.days),
            remote = snapshot(type = UnifiedCollectionType.DOING, modifiedAt = now - 4.days),
            base = snapshot(type = UnifiedCollectionType.WISH),
        ),
        // 剧集: 看过 vs 抛弃.
        SubjectMergeInput(
            subjectId = 5,
            title = "无职转生Ⅲ ~到了异世界就拿出真本事~",
            local = snapshot(
                episodes = mapOf(301 to UnifiedCollectionType.DONE),
                modifiedAt = now - 11.days,
            ),
            remote = snapshot(
                episodes = mapOf(301 to UnifiedCollectionType.DROPPED),
                modifiedAt = now - 3.days,
            ),
            base = snapshot(),
            episodeSorts = mapOf(301 to EpisodeSort(3)),
        ),
        // 以下为自动合并项 (单侧修改或一致更改).
        SubjectMergeInput(
            subjectId = 6,
            title = "夏日口袋",
            local = snapshot(type = UnifiedCollectionType.DONE, score = 10),
            remote = snapshot(type = UnifiedCollectionType.DOING, score = 10),
            base = snapshot(type = UnifiedCollectionType.DOING, score = 10),
        ),
        SubjectMergeInput(
            subjectId = 7,
            title = "轻音少女",
            local = snapshot(episodes = mapOf(1 to UnifiedCollectionType.DONE, 2 to UnifiedCollectionType.DONE)),
            remote = snapshot(episodes = mapOf(1 to UnifiedCollectionType.DONE)),
            base = snapshot(episodes = mapOf(1 to UnifiedCollectionType.DONE)),
            episodeSorts = mapOf(1 to EpisodeSort(1), 2 to EpisodeSort(2)),
        ),
        SubjectMergeInput(
            subjectId = 8,
            title = "白箱",
            local = snapshot(score = 9),
            remote = snapshot(score = 9, comment = "行业剧标杆"),
            base = snapshot(score = 9),
        ),
    )
    return computer.compute(inputs)
}

/**
 * 设计稿中的选择状态: 孤独摇滚与上伊那牡丹已解决 (选 Bangumi), 芙莉莲评分已选 Animeko.
 */
@TestOnly
fun createTestBangumiMergeChoices(): Map<BangumiMergeConflictKey, BangumiMergeSide> = mapOf(
    BangumiMergeConflictKey(1, BangumiMergeFieldId.Collection) to BangumiMergeSide.BANGUMI,
    BangumiMergeConflictKey(2, BangumiMergeFieldId.Rating) to BangumiMergeSide.ANIMEKO,
    BangumiMergeConflictKey(4, BangumiMergeFieldId.Collection) to BangumiMergeSide.BANGUMI,
)

@TestOnly
fun createTestBangumiMergeUiState(
    now: Instant,
    choices: Map<BangumiMergeConflictKey, BangumiMergeSide> = createTestBangumiMergeChoices(),
): BangumiMergeUiState = BangumiMergeUiState(
    isLoading = false,
    loadError = null,
    plan = createTestBangumiMergePlan(now),
    choices = choices,
    isApplying = false,
    applied = false,
)
