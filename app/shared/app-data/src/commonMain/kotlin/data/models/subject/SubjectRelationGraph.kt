/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

import androidx.compose.runtime.Immutable
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

/**
 * 一个条目所在系列的关系图. 以主线故事为骨架: 主线上只有正片和剧场版, 其他相关条目列在对应的主线条目下.
 */
@Immutable
data class SubjectRelationGraph(
    /**
     * 用户查看的条目. 可能在 [mainline] 中, 也可能是某个主线条目的分支.
     */
    val subjectId: Int,
    /**
     * 按故事顺序排列的主线条目. 如果系列有正片, 第一个一定是正片.
     */
    val mainline: List<SubjectRelationGraphMainNode>,
    /**
     * 系列过大, 有条目未包含在图中
     */
    val truncated: Boolean,
) {
    val mainCount: Int get() = mainline.size
    val branchCount: Int get() = mainline.sumOf { it.branches.size }
}

@Immutable
data class SubjectRelationGraphMainNode(
    val subject: SubjectRelationGraphSubject,
    /**
     * 主线上的剧场版. 不计入 "第几部".
     */
    val isMovie: Boolean,
    /**
     * 列在此条目下的相关条目, 依次为:
     * - 主线上的特别篇, 总集篇, 短篇等次要条目 ([SubjectRelation.PREQUEL], [SubjectRelation.SEQUEL]
     *   或 [SubjectRelation.COMPILATION]), 按故事顺序;
     * - 此条目的原作 ([SubjectRelation.MAIN_STORY]);
     * - 总集篇, 番外和衍生, 按放送日期.
     */
    val branches: List<SubjectRelationGraphBranch>,
)

@Immutable
data class SubjectRelationGraphBranch(
    val subject: SubjectRelationGraphSubject,
    /**
     * 相对于所属主线条目的关系. `null` 表示其他类型.
     */
    val relation: SubjectRelation?,
)

@Immutable
data class SubjectRelationGraphSubject(
    val subjectId: Int,
    val name: String,
    val nameCn: String,
    val image: String,
    val airDate: PackedDate,
    val platform: SubjectRelationGraphPlatform?,
    /**
     * 正片集数, 未知时为 0
     */
    val episodeCount: Int,
    /**
     * 用户的收藏状态. 只来自本地缓存, 未收藏或未缓存时为 [UnifiedCollectionType.NOT_COLLECTED].
     */
    val collectionType: UnifiedCollectionType,
) {
    val displayName: String get() = nameCn.ifBlank { name }
}

enum class SubjectRelationGraphPlatform {
    TV, OVA, MOVIE, WEB,
}
