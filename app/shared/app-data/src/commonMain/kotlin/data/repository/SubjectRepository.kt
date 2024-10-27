/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.datasources.api.PackedDate

/**
 * 条目本身的信息
 */
@Immutable
data class SubjectInfo(
    val subjectId: Int,
    val subjectType: SubjectType,
    val name: String,
    val nameCn: String,
    val summary: String,
    val nsfw: Boolean,
    val imageLarge: String,
    val totalEpisodes: Int,
    /**
     * 放送开始
     * @sample me.him188.ani.app.ui.subject.renderSubjectSeason
     */
    val airDate: PackedDate,

    val tags: List<Tag>,
    val aliases: List<String>,
    val ratingInfo: RatingInfo,
    val collectionStats: SubjectCollectionStats,

    // 以下为来自 infoxbox 的信息
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
    }
}
