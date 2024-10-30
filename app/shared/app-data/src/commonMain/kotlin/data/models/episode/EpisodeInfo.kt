/*
 * Copyright (C) 2024 OpenAni and contributors.
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
    val type: EpisodeType? = EpisodeType.MainStory,
    val name: String = "",
    val nameCn: String = "",
    /**
     * 上映日期
     */
    val airDate: PackedDate = PackedDate.Invalid, // TODO: 考虑剧集上映时间 
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
//    /** 服务器解析的时长，无法解析时为 `0` */
//    val durationSeconds: Int? = null
) {
    override fun toString(): String {
        return "EpisodeInfo(episodeId=$episodeId, nameCn='$nameCn', sort=$sort)"
    }
}

@Stable
val EpisodeInfo.displayName get() = nameCn.ifBlank { name }

/**
 * 是否一定已经播出了
 */
@Stable
val EpisodeInfo.isKnownCompleted: Boolean
    get() = airDate.isValid && airDate <= PackedDate.now() // TODO: consider time 

/**
 * 是否一定还未播出
 */
@Stable
val EpisodeInfo.isKnownOnAir
    get() = airDate.isValid && airDate > PackedDate.now() // TODO: consider time 

@Stable
fun EpisodeInfo.renderEpisodeEp() = sort.toString()