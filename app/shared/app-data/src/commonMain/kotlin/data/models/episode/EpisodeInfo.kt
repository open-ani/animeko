/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.episode

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate

/**
 * 与数据源无关的条目信息.
 */
@Immutable
@Serializable
data class EpisodeInfo(
    val episodeId: Int,
    /** `0` 本篇，`1` SP，`2` OP，`3` ED */
    val type: EpisodeType?,
    val name: String = "",
    val nameCn: String = "",
    /**
     * 条目所在地区的上映日期. 通常来说是日本时区 (UTC+9)
     */
    val airDate: PackedDate = PackedDate.Invalid,
    @Deprecated("No longer provided")
    val comment: Int = 0,
//    /** 维基人填写的原始时长 */
//    val duration: String = "",
    /** 简介 */
    val desc: String = "",
//    /** 音乐曲目的碟片数 */
//    val disc: Int = 0,
    /** 同类条目的排序和集数 */
    val sort: EpisodeSort = EpisodeSort(""),
    /** 条目内的集数, 从`1`开始。非本篇剧集的此字段无意义 */
    val ep: EpisodeSort? = null,
    /** TMDB 剧照 (宽 300px, 16:9) 的公开 CDN 直链. 没有剧照时为 `null`. */
    val imageMedium: String? = null,
    /** TMDB 原尺寸剧照 (通常 1920×1080) 的公开 CDN 直链. 没有剧照时为 `null`. */
    val imageLarge: String? = null,
//    /** 服务器解析的时长，无法解析时为 `0` */
//    val durationSeconds: Int? = null
) {
    override fun toString(): String {
        return "EpisodeInfo(episodeId=$episodeId, nameCn='$nameCn', sort=$sort)"
    }

    companion object {
        val Empty = EpisodeInfo(0, null)
    }
}

@Stable
val EpisodeInfo.displayName get() = nameCn.ifBlank { name }

@Stable
val EpisodeInfo.nameOrNameCn get() = name.ifBlank { nameCn }

/**
 * 根据用户偏好选择的显示名称, 与 [me.him188.ani.app.data.models.subject.preferredDisplayName] 同一约定.
 * @param useOriginalTitle 为 `true` 时优先显示原名 ([name]), 为 `false` 时行为与 [displayName] 一致.
 */
fun EpisodeInfo.preferredDisplayName(useOriginalTitle: Boolean): String =
    if (useOriginalTitle) nameOrNameCn else displayName

@Stable
fun EpisodeInfo.renderEpisodeEp() = sort.toString()