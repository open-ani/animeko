/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

import kotlin.math.abs

/**
 * 按时间戳连续性区分正片与插播广告.
 *
 * 采集站把广告拼进播放列表时不会重新编码正片, 正片各段的 PTS 因此仍然首尾相接, 而广告是独立转码的,
 * 其 PTS 自成一条时间轴 (实测多为 1.47 秒起). 于是"这一组的起始 PTS 是否接得上前一段正片的结尾"
 * 就足以把两者分开, 不必依赖组的大小.
 *
 * 按时长和分片数猜测的前提是"广告比正片短", 在广告插入点把正片镜头切出零星残片时会完全判反:
 * 实测一部 116 分钟的影片里, 4 段 15.8-19.1 秒的广告全部漏过, 反而删掉了两段 2.4 秒和 4.2 秒的正片.
 */
object HlsPtsContinuity {
    /**
     * 认为"接得上"的最大偏差. 实测正片组之间的偏差在 0.1 秒以内, 广告组则相差数百至数千秒,
     * 两者之间有数量级的间隔, 阈值取在哪里都一样.
     */
    private const val TOLERANCE_MILLIS = 2_000L

    /**
     * 独立转码的片段, 时间轴从这个范围内起算. 实测广告的首个 PTS 都在 1.466-1.480 秒.
     * 从正片里切出来的片段, 首个 PTS 落在原时间轴中间, 实测最小的也有 200 秒.
     */
    private const val FRESH_TIMELINE_MILLIS = 10_000L

    /**
     * @param index 组在播放列表中的序号.
     * @param durationMillis 组内各分片 `#EXTINF` 之和.
     * @param firstPtsMillis 组内首个分片的首个 PTS. 探测失败时为 `null`.
     */
    data class Group(
        val index: Int,
        val durationMillis: Long,
        val firstPtsMillis: Long?,
    )

    /**
     * 两个集合都只含探测成功的组. 探测失败的组两边都不进: 既没有证据说它是广告, 也没有证据说它是正片.
     * 不在正片链上、又不像独立转码的组 (见 [classify]) 也两边都不进.
     *
     * @property ads 判为广告的组.
     * @property main 在正片时间轴上的组.
     */
    data class Verdict(
        val ads: Set<Int>,
        val main: Set<Int>,
    ) {
        companion object {
            val Inconclusive = Verdict(emptySet(), emptySet())
        }
    }

    fun classify(groups: List<Group>): Verdict {
        if (groups.size < 2) return Verdict.Inconclusive
        // 至少要有两组探测成功才谈得上连续性
        if (groups.count { it.firstPtsMillis != null } < 2) return Verdict.Inconclusive

        // 不能假定首组就是正片: 有的源站在片头贴广告, 以它为基准会把整部正片判成广告.
        // 逐个起点建链, 取覆盖时长最大的那条作为正片.
        var best: Chain? = null
        for (start in groups.indices) {
            if (groups[start].firstPtsMillis == null) continue
            val chain = buildChain(groups, start)
            if (best == null || chain.coveredMillis > best.coveredMillis) {
                best = chain
            }
        }
        val mainChain = best ?: return Verdict.Inconclusive
        val main = mutableSetOf<Int>()
        val adPositions = mutableSetOf<Int>()
        for (position in groups.indices) {
            if (groups[position].firstPtsMillis == null) continue
            when {
                position in mainChain.members -> main += groups[position].index
                // 首组的正片同样从时间轴起点开始, 仅凭时间戳分不出片头广告. 样本里没有片头广告.
                position == 0 -> Unit
                isIndependentEncode(groups, position, adPositions) -> adPositions += position
            }
        }
        return Verdict(
            ads = adPositions.mapTo(mutableSetOf()) { groups[it].index },
            main = main,
        )
    }

    /**
     * 链外的组只有像独立转码的短片时才算广告: 时间轴从头起算, 或紧接前一段广告
     * (一段插播被多出来的 `#EXT-X-DISCONTINUITY` 切成两组时, 后一组接着前一组走).
     *
     * 不在链上不等于是广告. 有的线路把正片从原时间轴上剪开重拼, 各段之间有缺口也有重叠, 链串不起来.
     * 这些正片段的首个 PTS 落在原时间轴中间; 实测曾把一段夹在两段广告之间、首个 PTS 为 1253 秒的
     * 下集预告判成了广告.
     */
    private fun isIndependentEncode(groups: List<Group>, position: Int, adPositions: Set<Int>): Boolean {
        val pts = groups[position].firstPtsMillis ?: return false
        if (pts <= FRESH_TIMELINE_MILLIS) return true
        val previous = groups[position - 1]
        val previousPts = previous.firstPtsMillis ?: return false
        return position - 1 in adPositions &&
                abs(pts - (previousPts + previous.durationMillis)) <= TOLERANCE_MILLIS
    }

    private class Chain(val members: Set<Int>, val coveredMillis: Long)

    /**
     * 以 [start] 组为锚点向两侧贪心建链. 游标每次按"本组实测 PTS + 本组时长"重设而不是累加 `#EXTINF`,
     * 否则 `#EXTINF` 的舍入误差会沿着上百个组累积到阈值以上.
     *
     * 必须向前也延伸: 只向后建链的话, 锚点之前的组一律进不了链, 会被判成广告. 实测一个开头探测失败的
     * 样本正是这样把整段片头判反的.
     */
    private fun buildChain(groups: List<Group>, start: Int): Chain {
        val members = mutableSetOf(start)
        var covered = groups[start].durationMillis

        var cursor = groups[start].firstPtsMillis!! + groups[start].durationMillis
        var unprobed = 0L
        for (index in start + 1 until groups.size) {
            val group = groups[index]
            val pts = group.firstPtsMillis
            if (pts == null) {
                // 探测失败的组没有证据, 一律留下, 但它到底占不占正片时间轴是未知的:
                // 是正片就该推进游标, 是广告就不该. 两种都试 (见下), 在这里只把时长记着.
                members += index
                covered += group.durationMillis
                unprobed += group.durationMillis
                continue
            }
            // 探测失败的那几组里哪些占正片时间轴是未知的, 游标的真实位置因此落在一个区间里:
            // 一个都不占是下界, 全都占是上界. 只比对这两个端点的话, 失败组里正片与广告混在一起时
            // 两端都对不上, 链就断在这里——实测一个 17 秒的广告夹在两段探测失败的正片之间,
            // 就让链前面 359 秒的正片被判成了广告.
            if (pts >= cursor - TOLERANCE_MILLIS && pts <= cursor + unprobed + TOLERANCE_MILLIS) {
                members += index
                covered += group.durationMillis
                cursor = pts + group.durationMillis
                unprobed = 0
            }
        }

        var backCursor = groups[start].firstPtsMillis!!
        var backUnprobed = 0L
        for (index in start - 1 downTo 0) {
            val group = groups[index]
            val pts = group.firstPtsMillis
            if (pts == null) {
                members += index
                covered += group.durationMillis
                backUnprobed += group.durationMillis
                continue
            }
            val end = pts + group.durationMillis
            if (end >= backCursor - backUnprobed - TOLERANCE_MILLIS && end <= backCursor + TOLERANCE_MILLIS) {
                members += index
                covered += group.durationMillis
                backCursor = pts
                backUnprobed = 0
            }
        }
        return Chain(members, covered)
    }
}
