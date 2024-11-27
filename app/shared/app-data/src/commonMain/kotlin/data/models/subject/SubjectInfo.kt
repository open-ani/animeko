/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.datasources.api.PackedDate

/**
 * 条目本身的信息
 */
@Immutable
data class SubjectInfo(
    /**
     * Bangumi 条目 ID
     */
    val subjectId: Int,
    val subjectType: SubjectType,
    /**
     * 条目原名
     * @see displayName
     * @see aliases
     */
    val name: String,
    /**
     * 简体中文名称
     * @see displayName
     * @see aliases
     */
    val nameCn: String,
    /**
     * 简介, 可能为空. 当条目解析出错时, 此字段可能包含错误原因 (堆栈).
     */
    val summary: String,
    val nsfw: Boolean,
    val imageLarge: String,
    /**
     * 总集数, 0 表示未知.
     */
    @Deprecated("This includes all MainStory/OVA/SP while the app only supports MainStory") 
    val totalEpisodes: Int,
    /**
     * 放送开始日期. 时区为条目所在地区的时区, 即一般为 UTC+9.
     * @sample me.him188.ani.app.ui.subject.renderSubjectSeason
     */
    val airDate: PackedDate,

    /**
     * 标签列表, 可能为空. 标签可能是由维基人指定的 [公共标签][Tag.isCanonical], 也可能是用户自定义的标签. See [Tag].
     */
    val tags: List<Tag>,
    /**
     * 别名列表, 可能为空. 每个元素不可能为空.
     * @see allNames
     */
    val aliases: List<String>,
    /**
     * 评分信息, 包含评分人数, 评分分数, 评分人数等
     */
    val ratingInfo: RatingInfo,
    /**
     * 全站收藏统计
     */
    val collectionStats: SubjectCollectionStats,

    // 以下为来自 infoxbox 的信息
    /**
     * 完成日期. 时区为条目所在地区的时区, 即一般为 UTC+9.
     */
    val completeDate: PackedDate,
) {
    override fun toString(): String {
        return "SubjectInfo(subjectId=$subjectId, nameCn='$nameCn')"
    }

    /**
     * 主要显示名称
     */
    val displayName: String get() = nameCn.takeIf { it.isNotBlank() } ?: name

    /**
     * 主中文名, 主日文名, 以及所有别名
     */
    val allNames by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildList {
            // name2 千万不能改名叫 name, 否则 Kotlin 会错误地编译这份代码. `name` 他不会使用 local variable, 而是总是使用 [SubjectInfo.name]
            fun addIfNotBlank(name2: String) {
                if (name2.isNotBlank()) add(name2)
            }
            addIfNotBlank(nameCn)
            addIfNotBlank(name)
            aliases.forEach { addIfNotBlank(it) }
        }
    }

    companion object {
        @Stable
        val Empty = SubjectInfo(
            subjectId = 0,
            subjectType = SubjectType.ANIME,
            name = "",
            nameCn = "",
            summary = "",
            nsfw = false,
            imageLarge = "",
            totalEpisodes = 0,
            airDate = PackedDate.Invalid,
            tags = emptyList(),
            aliases = emptyList(),
            ratingInfo = RatingInfo.Empty,
            collectionStats = SubjectCollectionStats.Zero,
            completeDate = PackedDate.Invalid,
        )

        /**
         * 创建一个最小可以显示 subject 大概信息的 subject.
         * 仅包含[封面图][SubjectInfo.imageLarge]和[名称][SubjectInfo.name].
         */
        fun createPlaceholder(subjectId: Int, name: String, image: String, nameCn: String = ""): SubjectInfo {
            return SubjectInfo(
                subjectId = subjectId,
                subjectType = SubjectType.ANIME,
                name = name,
                nameCn = nameCn,
                summary = "",
                nsfw = false,
                imageLarge = image,
                totalEpisodes = 0,
                airDate = PackedDate.Invalid,
                tags = emptyList(),
                aliases = emptyList(),
                ratingInfo = RatingInfo.Empty,
                collectionStats = SubjectCollectionStats.Zero,
                completeDate = PackedDate.Invalid,
            )
        }
    }
}

@Stable
val SubjectInfo.nameCnOrName get() = nameCn.takeIf { it.isNotBlank() } ?: name
