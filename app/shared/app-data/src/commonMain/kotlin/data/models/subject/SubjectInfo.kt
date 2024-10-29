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
import kotlinx.serialization.Serializable

typealias SubjectInfo = me.him188.ani.app.data.models.SubjectInfo

//
///**
// * 详细信息.
// */
//@Immutable
//@Serializable
//data class SubjectInfo(
//    val id: Int = 0,
//    // 可搜索 "吹响！悠风号 第三季.json" 看示例
//    val name: String = "",
//    val nameCn: String = "",
//    val summary: String = "",
//    val nsfw: Boolean = false,
//    val locked: Boolean = false,
//    /* TV, Web, 欧美剧, PS4... */
////    val platform: String = "",
////    val images: Images,
//    /* 书籍条目的册数，由旧服务端从wiki中解析 */
////    val volumes: Int = 0,
//    /* 由旧服务端从wiki中解析，对于书籍条目为`话数` */
//    val eps: Int = 0,
//    /* 数据库中的章节数量 */
//    val totalEpisodes: Int = 0,
////    val rating: Rating,
//    val tags: List<Tag> = emptyList(), // must be sorted by count
//    /* air date in `YYYY-MM-DD` format */
//    val airDateString: String? = null,
//    val aliases: List<String> = emptyList(),
//    val imageLarge: String,
//    /**
//     * 该条目的全站收藏统计
//     */
//    val collection: SubjectCollectionStats = SubjectCollectionStats.Zero,
//    val ratingInfo: RatingInfo,
//
//    /**
//     * 放送结束. 当没有结束时间时为 [PackedDate.Invalid]
//     */
//    val completeDate: PackedDate = PackedDate.Invalid,
//) {
//    /**
//     * 放送开始
//     * @sample me.him188.ani.app.ui.subject.renderSubjectSeason
//     */
//    val airDate: PackedDate = if (airDateString == null) PackedDate.Invalid else PackedDate.parseFromDate(airDateString)
//
//    /**
//     * 主要显示名称
//     */
//    val displayName: String get() = nameCn.takeIf { it.isNotBlank() } ?: name
//
//    /**
//     * 主中文名, 主日文名, 以及所有别名
//     */
//    val allNames by lazy(LazyThreadSafetyMode.PUBLICATION) {
//        buildList {
//            // name2 千万不能改名叫 name, 否则 Kotlin 会错误地编译这份代码. `name` 他不会使用 local variable, 而是总是使用 [SubjectInfo.name]
//            fun addIfNotBlank(name2: String) {
//                if (name2.isNotBlank()) add(name2)
//            }
//            addIfNotBlank(nameCn)
//            addIfNotBlank(name)
//            aliases.forEach { addIfNotBlank(it) }
//        }
//    }
//
//    companion object {
//        @Stable
//        @JvmStatic
//        val Empty = SubjectInfo(
//            imageLarge = "",
//            ratingInfo = RatingInfo.Empty,
//        )
//
//        private val logger = logger<SubjectInfo>()
//    }
//}

@Stable
val SubjectInfo.nameCnOrName get() = nameCn.takeIf { it.isNotBlank() } ?: name


@Serializable
@Immutable
class InfoboxItem(
    val name: String,
    val values: List<String>,
) {
    val valueOrNull get() = values.firstOrNull()
}

