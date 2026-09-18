/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.source

import kotlinx.serialization.Serializable
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.PackedDate

/**
 * 一个数据源查询请求, 以条目为单位. 该请求包含尽可能多的信息以便 [MediaSource] 可以查到尽可能多的结果.
 *
 * 请求同时携带发起查询时正在观看 (或要下载) 的剧集 ([episodeId], [episodeSort], [episodeName], [episodeEp]).
 * 这些字段仅作提示, 例如用于日志或选择更合适的搜索关键字; 数据源不得据此裁剪结果, 按集裁剪由 `MediaSelector` 完成.
 *
 * @see MediaSource.fetch
 */
@Serializable
data class MediaFetchRequest(
    // 提示, 查看 [MediaFetcher]
    /**
     * 条目服务 (Bangumi) 提供的条目 ID. 若数据源支持, 可以用此信息做精确匹配.
     * 可能为空字符串, 表示未知.
     */
    val subjectId: String,
    /**
     * 条目服务 (Bangumi) 提供的当前剧集 ID. 仅作提示.
     * 可能为空字符串, 表示未知.
     */
    val episodeId: String,
    /**
     * 条目的主简体中文名称.
     * 建议使用 [subjectNames] 用所有已知名称去匹配.
     */
    // Note: this is soft-deprecated. Don't use it. Use the subjectNames.first() instead.
    // UI 内不支持编辑此项
    val subjectNameCN: String? = null,
    /**
     * 已知的该条目的所有名称. 包含季度信息.
     *
     * 所有名称包括简体中文译名, 各种别名, 简称, 以及日文原名.
     *
     * E.g. "关于我转生变成史莱姆这档事 第三季"
     */
    val subjectNames: List<String>,
    /**
     * 当前剧集在系列中的集数, 例如第二季的第一集为 26. 仅作提示.
     *
     * E.g. "49", "01".
     *
     * @see EpisodeSort
     */
    val episodeSort: EpisodeSort,
    /**
     * 条目服务 (Bangumi) 提供的当前剧集名称, 例如 "恶魔与阴谋", 不会包含 "第 x 集". 仅作提示.
     * 不一定为简体中文, 可能为日文. 也可能为空字符串.
     */
    val episodeName: String,
    /**
     * 当前剧集在当前季度中的集数, 例如第二季的第一集为 01. 仅作提示.
     *
     * E.g. "49", "01".
     *
     * @see EpisodeSort
     */
    val episodeEp: EpisodeSort? = episodeSort,
    /**
     * 条目的全部剧集, 按剧集顺序.
     *
     * 数据源可以用它把源上的剧集序号映射到条目的剧集 (例如按剧集 ID 精确对应), 以及判断缓存的条目页面是否已经陈旧.
     * 为空表示未知.
     */
    val episodes: List<Episode> = emptyList(),
) {
    /**
     * 条目的一集.
     */
    @Serializable
    data class Episode(
        /**
         * @see MediaFetchRequest.episodeId
         */
        val episodeId: String,
        /**
         * @see MediaFetchRequest.episodeSort
         */
        val sort: EpisodeSort,
        /**
         * @see MediaFetchRequest.episodeEp
         */
        val ep: EpisodeSort? = sort,
        /**
         * @see MediaFetchRequest.episodeName
         */
        val name: String = "",
        /**
         * 上映日期, 未知时为 [PackedDate.Invalid].
         */
        val airDate: PackedDate = PackedDate.Invalid,
    )

    companion object
}

fun MediaFetchRequest.toStringMultiline() = buildString {
    append("subjectId").append(": ").append(subjectId).appendLine()
    append("episodeId").append(": ").append(episodeId).appendLine()
    append("subjectNameCn").append(": ").append(subjectNameCN).appendLine()
    append("subjectNames:").appendLine()
    subjectNames.forEach { append("- ").appendLine(it) }
    append("episodeSort").append(": ").append(episodeSort).appendLine()
    append("episodeName").append(": ").append(episodeName).appendLine()
    append("episodeEp").append(": ").append(episodeEp).appendLine()
    append("episodes").append(": ").append(episodes.size).appendLine()
}

/**
 * 两个请求是否查询同一个条目: 条目 ID, 名称与剧集列表相同, 忽略仅作提示的当前剧集字段.
 * 条目级查询会话可以在满足此条件的请求之间共用.
 */
fun MediaFetchRequest.isSameSubjectQuery(other: MediaFetchRequest): Boolean {
    return subjectId == other.subjectId &&
            subjectNameCN == other.subjectNameCN &&
            subjectNames == other.subjectNames &&
            episodes == other.episodes
}

/**
 * 判断缓存记录 [cache] 是否属于本请求的条目.
 *
 * @return 按条目 ID 对应为 [MatchKind.EXACT]; 无法按 ID 判断时按条目名称对应为 [MatchKind.FUZZY]; `null` 表示不属于.
 */
infix fun MediaFetchRequest.matchesSubject(cache: MediaCacheMetadata): MatchKind? {
    if (subjectId.isNotEmpty() && cache.subjectId.isNotEmpty()) {
        // Both query and cache have subjectId, perform exact match.
        // Don't go for fuzzy match otherwise we'll always get false positives.
        return if (cache.subjectId == subjectId) MatchKind.EXACT else null
    }

    if (subjectNames.any { cache.subjectNames.contains(it) }) return MatchKind.FUZZY
    return null
}
