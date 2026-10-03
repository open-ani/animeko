/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormat
import me.him188.ani.datasources.api.EpisodeSort
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 把真实站点条目页解析出的集号冻起来, 改集号相关判据时用它确认没有波及到别的作品.
 * 与 `datasource/api/test-codegen` 对 BT 标题做的事情是一回事, 只是对象换成 web 源的条目页.
 *
 * 数据于 2026-09-03 从「樱花动漫」(www.yinghua2.com) 与「稀饭动漫」(dm1.xfdm.pro) 用与 app
 * 相同的选择器实地抓取, 覆盖四类作品各三部: 当季在播、老番、剧场版、OVA/特别篇, 共 29 个线路、
 * 143 个条目.
 *
 * 两列都要对:
 * - [Case.parsed] 是 [SelectorChannelFormat.convertSpecialEpisodes] 的结果, 即站点上写的集号;
 * - [Case.matching] 是 [matchingEpisodeSort] 的结果, 即真正参与匹配的集号.
 *
 * 两列只在 6 个条目上不同, 全是只标画质与语言的 `HD…`; 其余 137 个条目原样透传. 这个测试挂了先看
 * 是哪一列变了: `parsed` 变了说明动到了解析, `matching` 变了说明动到了匹配判据.
 */
class SelectorEpisodeSortCharacterizationTest {
    private data class Case(
        val kind: String,
        val source: String,
        val channel: String,
        val page: String,
        val entries: List<String>,
        val parsed: List<String>,
        val matching: List<String>,
    )

    /** 与 [SelectorChannelFormat] 内部一致: 正则取不到 group 就用整个标题 */
    private fun parseSort(name: String) =
        SelectorChannelFormat.convertSpecialEpisodes(name, SORT_REGEX.find(name)?.let { it.groups["ep"]?.value ?: name })

    private fun Case.infos() = entries.map { name ->
        WebSearchEpisodeInfo(
            channel = channel.ifEmpty { null },
            name = name,
            episodeSortOrEp = parseSort(name),
            playUrl = "https://example.com/$page/$name",
        )
    }

    @Test
    fun `站点集号的解析结果不变`() {
        val diffs = CASES.mapNotNull { case ->
            val actual = case.entries.map { parseSort(it).toString() }
            if (actual == case.parsed) null
            else "${case.source} ${case.page} ${case.channel}: 期望 ${case.parsed}, 实际 $actual"
        }
        assertEquals(emptyList(), diffs)
    }

    @Test
    fun `参与匹配的集号不变`() {
        val diffs = CASES.mapNotNull { case ->
            val actual = case.infos().map { it.matchingEpisodeSort(EpisodeSort(1), null).toString() }
            if (actual == case.matching) null
            else "${case.source} ${case.page} ${case.channel}: 期望 ${case.matching}, 实际 $actual"
        }
        assertEquals(emptyList(), diffs)
    }

    @Test
    fun `只有六个条目被判成整部作品`() {
        // 现算, 不能比对 Case 里记录的两列 —— 那样判据变了也发现不了
        val differing = CASES.flatMap { case ->
            case.infos().mapIndexedNotNull { i, info ->
                val matching = info.matchingEpisodeSort(EpisodeSort(1), null).toString()
                if (matching == parseSort(case.entries[i]).toString()) null
                else "${case.source} ${case.page} ${case.entries[i]} -> $matching"
            }
        }
        assertEquals(6, differing.size, "被判为整部作品的条目变了: $differing")
    }

    private companion object {
        private val SORT_REGEX = Regex(SelectorChannelFormat.DEFAULT_MATCH_EPISODE_SORT_FROM_NAME)

        private val CASES = listOf(
            Case(
                kind = "当季", source = "樱花动漫", channel = "线路1",
                page = "转学后班上的清纯可爱美少女，竟是小时候玩在一起的哥儿们",
                entries = listOf("第01集", "第02集", "第03集", "第04集", "第05集", "第06集", "第07集", "第08集"),
                parsed = listOf("01", "02", "03", "04", "05", "06", "07", "08"),
                matching = listOf("01", "02", "03", "04", "05", "06", "07", "08"),
            ),
            Case(
                kind = "当季", source = "樱花动漫", channel = "线路4",
                page = "转学后班上的清纯可爱美少女，竟是小时候玩在一起的哥儿们",
                entries = listOf("第01集", "第02集", "第03集", "第04集", "第05集", "第06集", "第07集", "第08集"),
                parsed = listOf("01", "02", "03", "04", "05", "06", "07", "08"),
                matching = listOf("01", "02", "03", "04", "05", "06", "07", "08"),
            ),
            Case(
                kind = "当季", source = "樱花动漫", channel = "线路2",
                page = "碧蓝之海 第三季",
                entries = listOf("第01集", "第02集", "第03集", "第04集", "第05集", "第06集", "第07集", "第08集"),
                parsed = listOf("01", "02", "03", "04", "05", "06", "07", "08"),
                matching = listOf("01", "02", "03", "04", "05", "06", "07", "08"),
            ),
            Case(
                kind = "当季", source = "樱花动漫", channel = "线路3",
                page = "碧蓝之海 第三季",
                entries = listOf("第1集"),
                parsed = listOf("01"),
                matching = listOf("01"),
            ),
            Case(
                kind = "当季", source = "樱花动漫", channel = "线路2",
                page = "幼女战记 第二季",
                entries = listOf("第01集", "第02集", "第03集", "第04集", "第05集", "第06集", "第07集", "第08集"),
                parsed = listOf("01", "02", "03", "04", "05", "06", "07", "08"),
                matching = listOf("01", "02", "03", "04", "05", "06", "07", "08"),
            ),
            Case(
                kind = "老番", source = "樱花动漫", channel = "线路2",
                page = "凉宫春日的忧郁",
                entries = listOf("第01集", "第02集", "第03集", "第04集", "第05集", "第06集", "第07集", "第08集", "第09集", "第10集", "第11集", "第12集", "第13集", "第14集"),
                parsed = listOf("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14"),
                matching = listOf("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14"),
            ),
            Case(
                kind = "老番", source = "樱花动漫", channel = "线路3",
                page = "凉宫春日的忧郁",
                entries = listOf("第1集", "第2集", "第3集", "第4集", "第5集", "第6集", "第7集", "第8集", "第9集", "第10集", "第11集", "第12集", "第13集", "第14集完结"),
                parsed = listOf("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14"),
                matching = listOf("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14"),
            ),
            Case(
                kind = "老番", source = "樱花动漫", channel = "线路1",
                page = "灌篮高手",
                entries = listOf("正片"),
                parsed = listOf("01"),
                matching = listOf("01"),
            ),
            Case(
                kind = "老番", source = "樱花动漫", channel = "线路2",
                page = "灌篮高手",
                entries = listOf("正片"),
                parsed = listOf("01"),
                matching = listOf("01"),
            ),
            Case(
                kind = "剧场版", source = "樱花动漫", channel = "线路1",
                page = "你的名字",
                entries = listOf("正片"),
                parsed = listOf("01"),
                matching = listOf("01"),
            ),
            Case(
                kind = "剧场版", source = "樱花动漫", channel = "线路4",
                page = "你的名字",
                entries = listOf("HD中字", "HD国语"),
                parsed = listOf("HD中字", "HD国语"),
                matching = listOf("01", "01"),
            ),
            Case(
                kind = "剧场版", source = "樱花动漫", channel = "线路1",
                page = "铃芽之旅",
                entries = listOf("HD高清国语版", "HD高清原声版"),
                parsed = listOf("HD高清国语版", "HD高清原声版"),
                matching = listOf("01", "01"),
            ),
            Case(
                kind = "剧场版", source = "樱花动漫", channel = "线路2",
                page = "铃芽之旅",
                entries = listOf("正片"),
                parsed = listOf("01"),
                matching = listOf("01"),
            ),
            Case(
                kind = "剧场版", source = "樱花动漫", channel = "线路1",
                page = "紫罗兰永恒花园外传：永远与自动手记人偶",
                entries = listOf("HD原声版", "HD中文版"),
                parsed = listOf("HD原声版", "HD中文版"),
                matching = listOf("01", "01"),
            ),
            Case(
                kind = "剧场版", source = "樱花动漫", channel = "线路2",
                page = "紫罗兰永恒花园外传：永远与自动手记人偶",
                entries = listOf("正片"),
                parsed = listOf("01"),
                matching = listOf("01"),
            ),
            Case(
                kind = "OVA", source = "樱花动漫", channel = "线路1",
                page = "某科学的超电磁炮OVA：御坂学姐现在是焦点人物",
                entries = listOf("第1集"),
                parsed = listOf("01"),
                matching = listOf("01"),
            ),
            Case(
                kind = "OVA", source = "樱花动漫", channel = "线路3",
                page = "某科学的超电磁炮OVA：御坂学姐现在是焦点人物",
                entries = listOf("正片"),
                parsed = listOf("01"),
                matching = listOf("01"),
            ),
            Case(
                kind = "OVA", source = "樱花动漫", channel = "线路4",
                page = "空之境界剧场版",
                entries = listOf("剧场版01", "剧场版02", "剧场版03", "剧场版04", "剧场版05", "剧场版06", "剧场版07", "剧场版08", "剧场版09", "剧场版10番外"),
                parsed = listOf("剧场版01", "剧场版02", "剧场版03", "剧场版04", "剧场版05", "剧场版06", "剧场版07", "剧场版08", "剧场版09", "剧场版10番外"),
                matching = listOf("剧场版01", "剧场版02", "剧场版03", "剧场版04", "剧场版05", "剧场版06", "剧场版07", "剧场版08", "剧场版09", "剧场版10番外"),
            ),
            Case(
                kind = "OVA", source = "樱花动漫", channel = "线路9",
                page = "空之境界剧场版",
                entries = listOf("第01集", "第02集", "第03集", "第04集", "第05集", "第06集", "第07集", "第08集", "第09集", "第10集"),
                parsed = listOf("01", "02", "03", "04", "05", "06", "07", "08", "09", "10"),
                matching = listOf("01", "02", "03", "04", "05", "06", "07", "08", "09", "10"),
            ),
            Case(
                kind = "OVA", source = "樱花动漫", channel = "线路3",
                page = "再造人卡辛（OVA）",
                entries = listOf("第1集", "第2集", "第3集", "第4集已完结"),
                parsed = listOf("01", "02", "03", "04"),
                matching = listOf("01", "02", "03", "04"),
            ),
            Case(
                kind = "OVA", source = "樱花动漫", channel = "线路12",
                page = "再造人卡辛（OVA）",
                entries = listOf("第1集", "第2集", "第3集", "第4集已完结"),
                parsed = listOf("01", "02", "03", "04"),
                matching = listOf("01", "02", "03", "04"),
            ),
            Case(
                kind = "当季", source = "稀饭动漫", channel = "新番主线①9",
                page = "碧蓝之海 第三季",
                entries = listOf("第01集", "第02集", "第03集", "第04集", "第05集", "第06集", "第07集", "第08集", "第09集"),
                parsed = listOf("01", "02", "03", "04", "05", "06", "07", "08", "09"),
                matching = listOf("01", "02", "03", "04", "05", "06", "07", "08", "09"),
            ),
            Case(
                kind = "当季", source = "稀饭动漫", channel = "新番主线②9",
                page = "碧蓝之海 第三季",
                entries = listOf("第01集", "第02集", "第03集", "第04集", "第05集", "第06集", "第07集", "第08集", "第09集"),
                parsed = listOf("01", "02", "03", "04", "05", "06", "07", "08", "09"),
                matching = listOf("01", "02", "03", "04", "05", "06", "07", "08", "09"),
            ),
            Case(
                kind = "当季", source = "稀饭动漫", channel = "新番主线①9",
                page = "幼女战记 第二季",
                entries = listOf("第01集", "第02集", "第03集", "第04集", "第05集", "第06集", "第07集", "第08集", "第09集"),
                parsed = listOf("01", "02", "03", "04", "05", "06", "07", "08", "09"),
                matching = listOf("01", "02", "03", "04", "05", "06", "07", "08", "09"),
            ),
            Case(
                kind = "当季", source = "稀饭动漫", channel = "新番主线②9",
                page = "幼女战记 第二季",
                entries = listOf("第01集", "第02集", "第03集", "第04集", "第05集", "第06集", "第07集", "第08集", "第09集"),
                parsed = listOf("01", "02", "03", "04", "05", "06", "07", "08", "09"),
                matching = listOf("01", "02", "03", "04", "05", "06", "07", "08", "09"),
            ),
            Case(
                kind = "老番", source = "稀饭动漫", channel = "旧番主线①",
                page = "小凉宫春日的忧郁",
                entries = listOf("剧场版"),
                parsed = listOf("剧场版"),
                matching = listOf("剧场版"),
            ),
            Case(
                kind = "剧场版", source = "稀饭动漫", channel = "旧番主线①",
                page = "铃芽之旅",
                entries = listOf("剧场版"),
                parsed = listOf("剧场版"),
                matching = listOf("剧场版"),
            ),
            Case(
                kind = "剧场版", source = "稀饭动漫", channel = "旧番主线①",
                page = "你的名字。",
                entries = listOf("第01集"),
                parsed = listOf("01"),
                matching = listOf("01"),
            ),
            Case(
                kind = "OVA", source = "稀饭动漫", channel = "旧番主线①2",
                page = "剧场版 空之境界 未来福音 extra chorus",
                entries = listOf("剧场版01", "剧场版02"),
                parsed = listOf("剧场版01", "剧场版02"),
                matching = listOf("剧场版01", "剧场版02"),
            ),
        )
    }
}
