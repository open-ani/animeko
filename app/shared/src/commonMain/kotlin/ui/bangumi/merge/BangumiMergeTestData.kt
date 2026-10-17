/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.bangumi.merge

import me.him188.ani.app.data.models.bangumi.BangumiAutoMergedChange
import me.him188.ani.app.data.models.bangumi.BangumiConflictField
import me.him188.ani.app.data.models.bangumi.BangumiConflictFieldType
import me.him188.ani.app.data.models.bangumi.BangumiConflictKey
import me.him188.ani.app.data.models.bangumi.BangumiMergeSide
import me.him188.ani.app.data.models.bangumi.BangumiMergeState
import me.him188.ani.app.data.models.bangumi.BangumiSubjectConflict
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * 对照 Figma 设计稿 (同步冲突 · 双列对照) 的示例服务端状态: 5 个条目共 6 项冲突, 12 项自动合并 (明细 5 条).
 *
 * - 1 孤独摇滚！: 收藏状态 在看 (Animeko, 较新) vs 抛弃 (Bangumi, 昨天);
 * - 2 葬送的芙莉莲: 收藏状态 在看 vs 看过 (Bangumi 较新) + 评分 8 分 “旅途的意义就在旅途中” vs 7 分 “节奏偏慢”;
 * - 3 我的青春恋爱物语果然有问题。完: 仅短评不同;
 * - 4 上伊那牡丹，酒醉身姿似百合花般: Animeko 在看 vs Bangumi 已删除收藏 (破坏性, 无 Bangumi 时间);
 * - 5 无职转生Ⅲ: 评分相同, 仅标签不同.
 */
@TestOnly
fun createTestBangumiMergeState(now: Instant): BangumiMergeState {
    fun rating(
        score: Int,
        comment: String? = null,
        tags: List<String> = emptyList(),
        isPrivate: Boolean = false,
    ) = SelfRatingInfo(score, comment, tags, isPrivate)

    return BangumiMergeState(
        conflicts = listOf(
            BangumiSubjectConflict(
                subjectId = 1,
                title = "孤独摇滚！",
                animekoUpdatedAt = now - 30.minutes,
                bangumiUpdatedAt = now - 16.hours,
                detectedAt = now - 10.minutes,
                fields = listOf(
                    BangumiConflictField.Collection(UnifiedCollectionType.DOING, UnifiedCollectionType.DROPPED),
                ),
            ),
            BangumiSubjectConflict(
                subjectId = 2,
                title = "葬送的芙莉莲",
                animekoUpdatedAt = now - 2.hours,
                bangumiUpdatedAt = now - 1.hours,
                detectedAt = now - 10.minutes,
                fields = listOf(
                    BangumiConflictField.Collection(UnifiedCollectionType.DOING, UnifiedCollectionType.DONE),
                    BangumiConflictField.Rating(
                        rating(8, "旅途的意义就在旅途中"),
                        rating(7, "节奏偏慢"),
                    ),
                ),
            ),
            BangumiSubjectConflict(
                subjectId = 3,
                title = "我的青春恋爱物语果然有问题。完",
                animekoUpdatedAt = now - 9.days,
                bangumiUpdatedAt = now - 7.days,
                detectedAt = now - 10.minutes,
                fields = listOf(
                    BangumiConflictField.Rating(
                        rating(9, "世界线收束，神作"),
                        rating(9, "二周目细节更多"),
                    ),
                ),
            ),
            BangumiSubjectConflict(
                subjectId = 4,
                title = "上伊那牡丹，酒醉身姿似百合花般",
                animekoUpdatedAt = now - 5.days,
                bangumiUpdatedAt = null,
                detectedAt = now - 10.minutes,
                fields = listOf(
                    BangumiConflictField.Collection(UnifiedCollectionType.DOING, UnifiedCollectionType.NOT_COLLECTED),
                ),
            ),
            BangumiSubjectConflict(
                subjectId = 5,
                title = "无职转生Ⅲ ~到了异世界就拿出真本事~",
                animekoUpdatedAt = now - 11.days,
                bangumiUpdatedAt = now - 3.days,
                detectedAt = now - 10.minutes,
                fields = listOf(
                    BangumiConflictField.Rating(
                        rating(8, tags = listOf("异世界", "转生")),
                        rating(8, tags = listOf("异世界")),
                    ),
                ),
            ),
        ),
        autoMerged = createTestBangumiAutoMerged(),
        autoMergedTotal = 12,
        lastSyncedAt = now - 10.minutes,
        syncInProgress = false,
    )
}

@TestOnly
fun createTestBangumiAutoMerged(): List<BangumiAutoMergedChange> = listOf(
    BangumiAutoMergedChange(
        subjectId = 6,
        title = "小市民系列",
        fieldType = BangumiConflictFieldType.EPISODE,
        side = BangumiMergeSide.BANGUMI,
        isNew = false,
        collectionType = null,
        rating = null,
        watchedEpisodeSorts = listOf("5"),
        unwatchedEpisodeSorts = emptyList(),
    ),
    BangumiAutoMergedChange(
        subjectId = 7,
        title = "夏日口袋",
        fieldType = BangumiConflictFieldType.COLLECTION,
        side = BangumiMergeSide.ANIMEKO,
        isNew = true,
        collectionType = UnifiedCollectionType.WISH,
        rating = null,
        watchedEpisodeSorts = emptyList(),
        unwatchedEpisodeSorts = emptyList(),
    ),
    BangumiAutoMergedChange(
        subjectId = 8,
        title = "Re：从零开始的异世界生活 第三季",
        fieldType = BangumiConflictFieldType.RATING,
        side = BangumiMergeSide.BANGUMI,
        isNew = false,
        collectionType = null,
        rating = SelfRatingInfo(9, null, emptyList(), false),
        watchedEpisodeSorts = emptyList(),
        unwatchedEpisodeSorts = emptyList(),
    ),
    BangumiAutoMergedChange(
        subjectId = 9,
        title = "轻音少女",
        fieldType = BangumiConflictFieldType.EPISODE,
        side = BangumiMergeSide.ANIMEKO,
        isNew = false,
        collectionType = null,
        rating = null,
        watchedEpisodeSorts = emptyList(),
        unwatchedEpisodeSorts = listOf("3"),
    ),
    BangumiAutoMergedChange(
        subjectId = 10,
        title = "白箱",
        fieldType = BangumiConflictFieldType.RATING,
        side = BangumiMergeSide.BANGUMI,
        isNew = false,
        collectionType = null,
        rating = SelfRatingInfo(0, "行业剧标杆", emptyList(), false),
        watchedEpisodeSorts = emptyList(),
        unwatchedEpisodeSorts = emptyList(),
    ),
)

/**
 * 设计稿中的选择状态: 孤独摇滚与上伊那牡丹已解决 (选 Bangumi), 芙莉莲评分已选 Animeko.
 */
@TestOnly
fun createTestBangumiMergeChoices(): Map<BangumiConflictKey, BangumiMergeSide> = mapOf(
    BangumiConflictKey(1, BangumiConflictFieldType.COLLECTION) to BangumiMergeSide.BANGUMI,
    BangumiConflictKey(2, BangumiConflictFieldType.RATING) to BangumiMergeSide.ANIMEKO,
    BangumiConflictKey(4, BangumiConflictFieldType.COLLECTION) to BangumiMergeSide.BANGUMI,
)

@TestOnly
fun createTestBangumiMergeUiState(
    now: Instant,
    choices: Map<BangumiConflictKey, BangumiMergeSide> = createTestBangumiMergeChoices(),
    mergeState: BangumiMergeState = createTestBangumiMergeState(now),
): BangumiMergeUiState = BangumiMergeUiState(
    isLoading = false,
    loadError = null,
    mergeState = mergeState,
    groups = mergeState.toConflictGroups(),
    choices = choices,
    isApplying = false,
)

/**
 * 没有冲突, 只有自动合并明细的 "已同步" 状态.
 */
@TestOnly
fun createTestBangumiMergeSyncedState(now: Instant): BangumiMergeState = BangumiMergeState(
    conflicts = emptyList(),
    autoMerged = createTestBangumiAutoMerged(),
    autoMergedTotal = 12,
    lastSyncedAt = now - 10.minutes,
    syncInProgress = false,
)
