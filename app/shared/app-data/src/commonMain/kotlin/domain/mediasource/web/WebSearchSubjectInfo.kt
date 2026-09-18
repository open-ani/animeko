/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.contains
import me.him188.ani.utils.xml.Element

data class WebSearchSubjectInfo(
    val internalId: String,
    val name: String,
    val fullUrl: String,
    val partialUrl: String,
    val origin: Element?,
)

class WebSearchChannelInfo(
    val name: String,
    val content: Element,
)

data class WebSearchEpisodeInfo(
    /**
     * 播放线路, 与 [name] 一起组成 ID. 如要修改, 考虑 [SelectorMediaSourceEngine.selectMedia]
     */
    val channel: String?,
    /**
     * "第x集" 等原名.
     */
    val name: String,
    /**
     * 解析成功的 [EpisodeSort], 未解析成功则为 `null`.
     * 可能表示 sort, 也可能是 ep.
     */
    val episodeSortOrEp: EpisodeSort?,
    /**
     * 播放地址
     */
    val playUrl: String
)

/**
 * 从剧集列表中找到正在播放的剧集.
 *
 * 匹配优先级 (与按当前集过滤 media 的语义一致, 见 `MediaListFilters.ContainsAnyEpisodeInfo`):
 * 1. [matchingEpisodeSort] 匹配系列内集数 [episodeSort];
 * 2. 特殊剧集 (非 [EpisodeSort.Normal]) 按剧集名称匹配 [episodeName];
 * 3. [matchingEpisodeSort] 匹配季度内集数 [episodeEp].
 */
fun List<WebSearchEpisodeInfo>.findMatchingEpisodeOrNull(
    episodeSort: EpisodeSort,
    episodeEp: EpisodeSort?,
    episodeName: String?,
): WebSearchEpisodeInfo? {
    firstOrNull { info ->
        info.matchingEpisodeSort(episodeSort, episodeEp)
            ?.let { EpisodeRange.single(it).contains(episodeSort) } == true
    }?.let { return it }
    if (episodeSort !is EpisodeSort.Normal && !episodeName.isNullOrBlank()) {
        firstOrNull { MediaListFilters.specialContains(it.name, episodeName) }?.let { return it }
    }
    if (episodeEp != null) {
        firstOrNull { info ->
            info.matchingEpisodeSort(episodeSort, episodeEp)
                ?.let { EpisodeRange.single(it).contains(episodeEp) } == true
        }?.let { return it }
    }
    return null
}

/**
 * 参与匹配的集号. 通常就是站点上解析出的 [WebSearchEpisodeInfo.episodeSortOrEp].
 *
 * 站点没能给出集号时 ([WebSearchEpisodeInfo.episodeSortOrEp] 为 `null` 或 [EpisodeSort.Unknown]),
 * 若这一条只标了画质与语言 (见 [isWholeWorkLabel]), 它就是整部作品, 按第 1 集参与匹配.
 *
 * 解析结果本身不动 —— 那是页面上的事实, 还要写进搜索缓存.
 */
internal fun WebSearchEpisodeInfo.matchingEpisodeSort(
    episodeSort: EpisodeSort,
    episodeEp: EpisodeSort?,
): EpisodeSort? {
    val parsed = episodeSortOrEp
    if (parsed != null && parsed !is EpisodeSort.Unknown) return parsed // 站点给了集号, 以它为准
    // 解析结果虽然是 Unknown, 但已经与请求相等 (站点把集号写成了 Bangumi 侧同样的怪字符串).
    // 这种本来就能匹配上, 不要替换, 否则反而把它弄丢.
    if (parsed != null && (parsed == episodeSort || parsed == episodeEp)) return parsed
    return if (isWholeWorkLabel(name)) EpisodeSort(1) else parsed
}

/**
 * 这一条是不是"整部作品", 即名称只有画质与语言、不含任何集号信息.
 *
 * 单集作品的条目页常见这种命名: 一部剧场版给出 "HD高清国语版" 与 "HD高清原声版" 两条, 它们是同一部
 * 作品的不同配音, 都是第 1 集. [SelectorChannelFormat.isPossiblyMovie] 只认「正片」「高清版」与
 * 含 1080P 字样的, 覆盖不到这些.
 *
 * 判据是"去掉画质与语言词之后什么都不剩", 所以带作品名的 "铃芽之旅（普通话版）"、带集号的
 * "剧场版01" (那要靠站点自己的集号正则)、表示整季的 "全集" 都不算.
 */
internal fun isWholeWorkLabel(name: String): Boolean {
    if (name.isBlank()) return false
    return name.replace(WHOLE_WORK_LABEL_TOKENS, "")
        .none { it !in WHOLE_WORK_LABEL_SEPARATORS }
}

private val WHOLE_WORK_LABEL_TOKENS = Regex(
    "HD|BD|TC|HC|4K|2K|2160P|1440P|1080P|720P|" +
        "超清|高清|清晰|流畅|标准|抢先|正片|全片|" +
        "原声|原版|国语|日语|粤语|普通话|中字|中文|双语|字幕|版",
    RegexOption.IGNORE_CASE,
)

/** 分隔符与装饰字符, 判定时忽略 */
private const val WHOLE_WORK_LABEL_SEPARATORS = " \t·・-—_/|＋+（）()【】[]"
