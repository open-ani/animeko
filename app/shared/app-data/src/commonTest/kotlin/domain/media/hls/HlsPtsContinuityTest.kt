/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

import me.him188.ani.app.domain.media.hls.HlsPtsContinuity.Group
import kotlin.test.Test
import kotlin.test.assertEquals

class HlsPtsContinuityTest {
    private fun detectAds(groups: List<Group>): Set<Int> = HlsPtsContinuity.classify(groups).ads

    @Test
    fun `detects ad whose pts does not continue the main timeline`() {
        val ads = detectAds(
            listOf(
                Group(0, durationMillis = 356_900, firstPtsMillis = 1_483),
                Group(1, durationMillis = 19_600, firstPtsMillis = 1_466),
                Group(2, durationMillis = 1_083_200, firstPtsMillis = 358_423),
            ),
        )

        assertEquals(setOf(1), ads)
    }

    /**
     * 取自一部真实影片: 广告插入点把正片镜头切出一个 2.4 秒的残片 (g39),
     * 紧接着是 15.8 秒的广告 (g40). 按时长猜会把两者判反, 按 PTS 则 g39 严丝合缝地接在 g38 之后.
     */
    @Test
    fun `keeps short main fragment that sits right before an ad`() {
        val ads = detectAds(
            listOf(
                Group(38, durationMillis = 42_000, firstPtsMillis = 1_454_780),
                Group(39, durationMillis = 2_400, firstPtsMillis = 1_496_820),
                Group(40, durationMillis = 15_800, firstPtsMillis = 1_466),
                Group(41, durationMillis = 36_800, firstPtsMillis = 1_499_230),
            ),
        )

        assertEquals(setOf(40), ads)
    }

    /**
     * 取自一个真实样本: 广告被额外的 `#EXT-X-DISCONTINUITY` 切成两组, 后一组从 15.6 秒起,
     * 不是从时间轴起点开始, 但接着前一组广告走, 两组都应判为广告.
     */
    @Test
    fun `detects ad split into two groups by an extra discontinuity`() {
        val ads = detectAds(
            listOf(
                Group(0, durationMillis = 417_500, firstPtsMillis = 1_466),
                Group(1, durationMillis = 14_134, firstPtsMillis = 1_466),
                Group(2, durationMillis = 4_900, firstPtsMillis = 15_600),
                Group(3, durationMillis = 600_000, firstPtsMillis = 1_466 + 417_500),
            ),
        )

        assertEquals(setOf(1, 2), ads)
    }

    /**
     * 首组的正片同样从时间轴起点开始, 仅凭时间戳分不出片头广告, 首组一律不判为广告.
     * 正片仍取覆盖时长最长的链, 不以首组为基准.
     */
    @Test
    fun `never judges the first group as an ad`() {
        val verdict = HlsPtsContinuity.classify(
            listOf(
                Group(0, durationMillis = 15_000, firstPtsMillis = 1_466),
                Group(1, durationMillis = 600_000, firstPtsMillis = 1_400),
                Group(2, durationMillis = 700_000, firstPtsMillis = 601_400),
            ),
        )

        assertEquals(emptySet(), verdict.ads)
        assertEquals(setOf(1, 2), verdict.main)
    }

    /**
     * 取自一个真实样本: 这条线路把正片从原时间轴上剪开重拼, 各段 PTS 有缺口也有重叠, 链只能串起最长的 g8.
     * 链外的正片段首个 PTS 落在原时间轴中间, 不是广告; 其中 g10 是下集预告, 夹在两段广告之间.
     * 广告都从 1.466 秒起.
     */
    @Test
    fun `keeps off-chain main content that starts mid-timeline`() {
        val ad = 17_600L
        val ads = detectAds(
            listOf(
                Group(0, durationMillis = 48_800, firstPtsMillis = 1_421),
                Group(1, durationMillis = ad, firstPtsMillis = 1_466),
                Group(2, durationMillis = 378_800, firstPtsMillis = 203_921),
                Group(3, durationMillis = ad, firstPtsMillis = 1_466),
                Group(4, durationMillis = 382_500, firstPtsMillis = 451_421),
                Group(5, durationMillis = ad, firstPtsMillis = 1_466),
                Group(6, durationMillis = 116_200, firstPtsMillis = 796_421),
                Group(7, durationMillis = ad, firstPtsMillis = 1_466),
                Group(8, durationMillis = 420_000, firstPtsMillis = 957_671),
                Group(9, durationMillis = ad, firstPtsMillis = 1_466),
                Group(10, durationMillis = 23_800, firstPtsMillis = 1_253_921),
                Group(11, durationMillis = ad, firstPtsMillis = 1_466),
            ),
        )

        assertEquals(setOf(1, 3, 5, 7, 9, 11), ads)
    }

    /**
     * 探测失败的组按其时长推进游标. 漏掉这一步的话它后面每一组都会偏出阈值, 整条链就此断掉.
     */
    @Test
    fun `treats unprobed group as main content and advances the cursor`() {
        val ads = detectAds(
            listOf(
                Group(0, durationMillis = 100_000, firstPtsMillis = 0),
                Group(1, durationMillis = 50_000, firstPtsMillis = null),
                Group(2, durationMillis = 100_000, firstPtsMillis = 150_000),
                Group(3, durationMillis = 19_000, firstPtsMillis = 1_466),
            ),
        )

        assertEquals(setOf(3), ads)
    }

    /**
     * 取自一个真实样本: 首组是 8 分钟的正片, 其首片在开头 8KB 内读不出 PES, 探测失败.
     * 链的锚点只能落在它后面, 若不向前延伸, 整个片头会被判成广告.
     */
    @Test
    fun `keeps leading group that failed probing and sits before the chain anchor`() {
        val ads = detectAds(
            listOf(
                Group(0, durationMillis = 496_400, firstPtsMillis = null),
                Group(1, durationMillis = 16_500, firstPtsMillis = 1_466),
                Group(2, durationMillis = 1_016_800, firstPtsMillis = 497_854),
                Group(3, durationMillis = 17_700, firstPtsMillis = 1_466),
            ),
        )

        assertEquals(setOf(1, 3), ads)
    }

    /**
     * 锚点之前的正片组要能沿时间轴向前接回来.
     */
    @Test
    fun `extends the chain backwards from the anchor`() {
        val ads = detectAds(
            listOf(
                Group(0, durationMillis = 100_000, firstPtsMillis = 0),
                Group(1, durationMillis = 19_000, firstPtsMillis = 1_466),
                Group(2, durationMillis = 400_000, firstPtsMillis = 100_000),
                Group(3, durationMillis = 500_000, firstPtsMillis = 500_000),
            ),
        )

        assertEquals(setOf(1), ads)
    }

    /**
     * 探测失败的组是广告时, 它不占正片时间轴. 若按其 `#EXTINF` 推进游标, 之后每一组都会偏出阈值,
     * 整条链断在这里——实测源站删掉一个广告分片 (返回 404) 就足以让其后几十组正片被判成广告.
     */
    @Test
    fun `survives an unprobed group that turns out to be an ad`() {
        val groups = buildList {
            var pts = 0L
            for (index in 0 until 10) {
                if (index == 4) {
                    add(Group(4, durationMillis = 25_700, firstPtsMillis = null)) // 广告, 探测失败
                    continue
                }
                add(Group(index, durationMillis = 20_000, firstPtsMillis = pts))
                pts += 20_000
            }
        }

        assertEquals(emptySet(), detectAds(groups))
    }

    /**
     * 反过来, 探测失败的组是正片时它占着时间轴, 同样不能断链.
     */
    @Test
    fun `survives an unprobed group that turns out to be main content`() {
        val groups = buildList {
            var pts = 0L
            for (index in 0 until 10) {
                if (index == 4) {
                    add(Group(4, durationMillis = 20_000, firstPtsMillis = null))
                    pts += 20_000
                    continue
                }
                add(Group(index, durationMillis = 20_000, firstPtsMillis = pts))
                pts += 20_000
            }
        }

        assertEquals(emptySet(), detectAds(groups))
    }

    /**
     * 取自一个真实样本: 正片 g0 / g2 / g4 / g6 / g8 的 PTS 首尾相接, 中间夹着四段广告.
     * g2、g3、g4 连续探测失败, 其中 g3 是广告而 g2、g4 是正片——游标的真实位置落在
     * "失败组一个都不占" 与 "全都占" 之间, 两个端点都对不上。此时若只比对端点, g0 这段
     * 359 秒的正片会被判成广告。
     */
    @Test
    fun `keeps the chain across unprobed groups that mix ads and main content`() {
        val ads = detectAds(
            listOf(
                Group(0, durationMillis = 359_200, firstPtsMillis = 1_483),
                Group(1, durationMillis = 19_600, firstPtsMillis = 1_467),
                Group(2, durationMillis = 1_320_500, firstPtsMillis = null),
                Group(3, durationMillis = 17_400, firstPtsMillis = null),
                Group(4, durationMillis = 1_916_900, firstPtsMillis = null),
                Group(5, durationMillis = 19_600, firstPtsMillis = 1_467),
                Group(6, durationMillis = 2_099_800, firstPtsMillis = 3_598_067),
                Group(7, durationMillis = 17_400, firstPtsMillis = 1_480),
                Group(8, durationMillis = 1_028_900, firstPtsMillis = 5_697_900),
            ),
        )

        assertEquals(setOf(1, 5, 7), ads)
    }

    @Test
    fun `gives no verdict when fewer than two groups were probed`() {
        val ads = detectAds(
            listOf(
                Group(0, durationMillis = 100_000, firstPtsMillis = 0),
                Group(1, durationMillis = 19_000, firstPtsMillis = null),
                Group(2, durationMillis = 100_000, firstPtsMillis = null),
            ),
        )

        assertEquals(emptySet(), ads)
    }
}
