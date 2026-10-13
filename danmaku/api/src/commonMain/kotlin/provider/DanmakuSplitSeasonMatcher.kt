/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.api.provider

/**
 * 正在观看 Bangumi 拆成几个条目的一季 (如 Re:0 第四季的丧失篇与夺还篇) 的后半时, 把弹幕源番剧上的集对到当前集.
 *
 * 弹幕源常把这几个条目合成一部番剧从 1 连续编号, 并且只关联到第一段的条目. 后半各集在合并番剧上的集号是季内序号,
 * 既不是 [DanmakuFetchRequest.episodeEp] 也不是 [DanmakuFetchRequest.episodeSort], 按它们匹配会选到前半的同号集.
 *
 * 弹幕源的一部番剧相当于资源选择器里的一个站点页面, 由 app 按资源选择器的拆分季页面规则实现.
 */
interface DanmakuSplitSeasonMatcher {
    /**
     * 当前条目前面各段的 Bangumi 条目 id, 按播出顺序. 当前条目在弹幕源没有关联时, 合并的番剧可能关联在它们上.
     */
    val previousPartSubjectIds: List<Int>

    /**
     * 弹幕源的番剧 [subjectName] 是否属于系列里的其他季. 其他季的番剧不含当前集, 标题相同也不是.
     */
    fun isOtherSeason(subjectName: String): Boolean

    /**
     * 番剧 [subjectName] 上集号为 [number] 的一集是不是当前集. [numbers] 是这部番剧所有正片的整数集号.
     *
     * @param mappedToSelf 弹幕源把这部番剧关联到了当前条目. 认不出番剧名时把它当作当前条目自己的番剧.
     */
    fun isCurrentEpisode(subjectName: String, numbers: Set<Int>, number: Int, mappedToSelf: Boolean): Boolean
}
