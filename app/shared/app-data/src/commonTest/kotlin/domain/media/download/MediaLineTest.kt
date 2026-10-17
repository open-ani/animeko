/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.utils.platform.annotations.TestOnly

/**
 * BT 资源按标题判断是否同一线路 ([lineSubjectNames], [isSameLineAs]).
 */
@OptIn(TestOnly::class)
class MediaLineTest {
    private fun btMedia(title: String, alliance: String = "ANi") = TestMediaList.first().copy(
        mediaId = title,
        originalTitle = title,
        properties = TestMediaList.first().properties.copy(subjectName = null, alliance = alliance),
    )

    private fun assertNames(title: String, alliance: String = "ANi", contains: Set<String>, excludes: Set<String> = emptySet()) {
        val names = btMedia(title, alliance).lineSubjectNames()
        assertTrue(contains.all { it in names }, "$title -> $names should contain $contains")
        assertTrue(excludes.none { it in names }, "$title -> $names should not contain $excludes")
    }

    @Test
    fun `bt line subject names come from the title without tags and episode numbers`() {
        // 开头的标签是字幕组, 数据源给的字幕组名写法不同也不算名字
        assertNames("[Sakurato] 日常 / Nichijou [02][1080p]", contains = setOf("日常", "Nichijou"), excludes = setOf("1080p", "Sakurato"))
        assertNames("[Nekomoe kissaten][BLEACH][01][1080p][JPSC].mp4", alliance = "喵萌奶茶屋", contains = setOf("BLEACH"), excludes = setOf("Nekomoe kissaten", "JPSC"))
        assertNames("[Sakurato] Nichijou - 01 [1080p]", contains = setOf("Nichijou"))
        assertNames("【喵萌奶茶屋】日常 第01话 [1080p]", contains = setOf("日常"))
        assertNames("[Sakurato] 日常 01-12 Fin [BDRip 1080p]", contains = setOf("日常"))
        assertNames("[Group] 日常 第01-12话 [1080p]", contains = setOf("日常"), excludes = setOf("日常  - 话"))
        assertNames("[Group] 日常 第01话 v2 [1080p]", contains = setOf("日常"))
        assertNames("[Sakurato] 坂本日常 / Sakamoto desu ga [03]", contains = setOf("坂本日常", "Sakamoto desu ga"), excludes = setOf("日常"))
        // 扩展名不是名字
        assertNames("[ANi] Nichijou - 01 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4", contains = setOf("Nichijou"), excludes = setOf(".mp4", "mp4"))
        assertNames("[Group] 葬送的芙莉莲 - 01 (WEB 1080p AVC AAC) [简繁内封].mkv", contains = setOf("葬送的芙莉莲"), excludes = setOf(".mkv"))
        // 整个标题都是标签: 取去掉字幕组名、月份新番、分辨率等之后的段
        assertNames("【幻樱字幕组】【4月新番】【日常 Nichijou】【01】【GB_MP4】【1280X720】", alliance = "幻樱字幕组", contains = setOf("日常 Nichijou"), excludes = setOf("4月新番", "GB_MP4"))
        assertNames("【悠哈璃羽字幕社】[日常][01][x264 1080p][CHS]", alliance = "悠哈璃羽字幕社", contains = setOf("日常"), excludes = setOf("x264 1080p"))
        assertNames("【幻樱字幕组】【4月新番】【Nichijou】【01】【GB_MP4】【1280X720】", alliance = "幻樱字幕组", contains = setOf("Nichijou"))
        assertNames("【動漫國字幕組】★04月新番[日常][01][1080P][簡繁外掛]", alliance = "動漫國字幕組", contains = setOf("日常"), excludes = setOf("★ 月新番", "★04月新番"))
        assertNames("【喵萌奶茶屋】★04月新番★[日常/Nichijou][01][1080p][简日双语][招募翻译]", alliance = "喵萌奶茶屋", contains = setOf("日常", "Nichijou"), excludes = setOf("★ 月新番★"))
        // 季号不是集数
        assertNames("[Group] Mushoku Tensei 2 [01-12] [1080p]", contains = setOf("Mushoku Tensei 2"), excludes = setOf("Mushoku Tensei"))
        assertNames("[Group] 文豪野犬 第3季 [01-12] [1080p]", contains = setOf("文豪野犬 第3季"), excludes = setOf("文豪野犬", "文豪野犬 季"))
        assertNames("[Group] 文豪ストレイドッグス 第4期 - 01 [1080p]", contains = setOf("文豪ストレイドッグス 第4期"))
        // 季号、剧场版这类区分续作的标记保留, 即使写成全大写的标签
        assertNames("[Nekomoe kissaten][Oshi no Ko][S2][01][1080p][JPSC]", alliance = "Nekomoe kissaten", contains = setOf("Oshi no Ko", "S2"), excludes = setOf("JPSC"))
        assertNames("[ANi] 【我推的孩子】第二季 - 05 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4", contains = setOf("我推的孩子", "第二季"))
        assertNames("[Group][药屋少女的呢喃 Kusuriya no Hitorigoto][第二季][01][1080P]", alliance = "Group", contains = setOf("药屋少女的呢喃 Kusuriya no Hitorigoto", "第二季"))
        assertNames("[Group][日常][剧场版][1080P]", alliance = "Group", contains = setOf("日常", "剧场版"))
        // 繁体标签与 "(完)" 不是名字
        assertNames("【動漫國字幕組】★10月新番[葬送的芙莉蓮][28(完)][1080P][繁體][MP4]", alliance = "動漫國字幕組", contains = setOf("葬送的芙莉蓮"), excludes = setOf("(完)", "完", "繁體"))
        assertNames("[Group][日常][12][先行版本][v2 修正字幕][1080P]", alliance = "Group", contains = setOf("日常"), excludes = setOf("先行版本", "v2 修正字幕"))
        // 纯数字的名字分不出来
        assertEquals(emptySet(), btMedia("[Group] 86 [01][1080p]", alliance = "Group").lineSubjectNames())
    }

    @Test
    fun `bt media of another subject from the same fansub is not on the same line`() {
        val subjectNames = listOf("日常", "Nichijou")
        val chosen = btMedia("[ANi] 日常 - 01 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4")
        val names = chosen.lineSubjectNames()
        assertTrue(btMedia("[ANi] 日常 - 02 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4").isSameLineAs(chosen, names, subjectNames))
        assertTrue(btMedia("[ANi] Nichijou - 02 [1080P].mp4").isSameLineAs(chosen, names, subjectNames))
        assertFalse(btMedia("[ANi] 坂本日常 - 02 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4").isSameLineAs(chosen, names, subjectNames))

        val bracketed = btMedia("【幻樱字幕组】【4月新番】【日常 Nichijou】【01】【GB_MP4】【1280X720】", alliance = "幻樱字幕组")
        val bracketedNames = bracketed.lineSubjectNames()
        assertTrue(btMedia("【幻樱字幕组】【4月新番】【日常 Nichijou】【02】【GB_MP4】【1280X720】", alliance = "幻樱字幕组").isSameLineAs(bracketed, bracketedNames, subjectNames))
        assertFalse(btMedia("【幻樱字幕组】【4月新番】【坂本日常 Sakamoto desu ga】【01】【GB_MP4】【1280X720】", alliance = "幻樱字幕组").isSameLineAs(bracketed, bracketedNames, subjectNames))

        // 两条标题共有的 "★04月新番★"、"简日双语" 不算同一条目的证据
        for ((a, b) in listOf(
            "【喵萌奶茶屋】★04月新番★[日常/Nichijou][01][1080p][简日双语][招募翻译]" to "【喵萌奶茶屋】★04月新番★[坂本日常/Sakamoto Days][01][1080p][简日双语][招募翻译]",
            "【動漫國字幕組】★04月新番[日常][01][1080P][簡繁外掛]" to "【動漫國字幕組】★04月新番[坂本日常][01][1080P][簡繁外掛]",
            "[织梦字幕组][日常 Nichijou][01][1080P][AVC][简日双语]" to "[织梦字幕组][坂本日常 Sakamoto Days][01][1080P][AVC][简日双语]",
            "[星空字幕组][日常 / Nichijou][01][简日双语][1080P][WEBrip][MP4]" to "[星空字幕组][坂本日常 / Sakamoto Days][01][简日双语][1080P][WEBrip][MP4]",
        )) {
            val chosenA = btMedia(a, alliance = "X")
            val namesA = chosenA.lineSubjectNames()
            assertFalse(btMedia(b, alliance = "X").isSameLineAs(chosenA, namesA, subjectNames), "$b should not be on the line of $a")
            assertTrue(btMedia(a.replace("[01]", "[02]"), alliance = "X").isSameLineAs(chosenA, namesA, subjectNames), "$a episode 2 should be on the line")
        }
        // 同字幕组把中日名写在一段里: 与所选资源那一段相同且含条目名
        val combined = btMedia("[织梦字幕组][日常 Nichijou][01][1080P][AVC][简日双语]", alliance = "织梦字幕组")
        assertTrue(btMedia("[织梦字幕组][日常 Nichijou][02][1080P][AVC][简日双语]", alliance = "织梦字幕组").isSameLineAs(combined, combined.lineSubjectNames(), subjectNames))

        // 同字幕组的前作 (条目名只是候选名字段的一部分) 不算同一条目
        val season2Names = listOf("【我推的孩子】第二季", "Oshi no Ko Season 2")
        val season2 = btMedia("[ANi] 【我推的孩子】第二季 - 01 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4")
        assertTrue(btMedia("[ANi] 【我推的孩子】第二季 - 05 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4").isSameLineAs(season2, season2.lineSubjectNames(), season2Names))
        assertFalse(btMedia("[ANi] 【我推的孩子】 - 05 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4").isSameLineAs(season2, season2.lineSubjectNames(), season2Names))

        // 同字幕组的下一季 (季号写成数字) 不算同一条目
        val season1Names = listOf("无职转生", "Mushoku Tensei")
        val season1 = btMedia("[Group] Mushoku Tensei - 01 [1080p]", alliance = "Group")
        assertFalse(btMedia("[Group] Mushoku Tensei 2 [01-12] [1080p]", alliance = "Group").isSameLineAs(season1, season1.lineSubjectNames(), season1Names))
        assertTrue(btMedia("[Group] Mushoku Tensei - 02 [1080p]", alliance = "Group").isSameLineAs(season1, season1.lineSubjectNames(), season1Names))

        // 季号写在标签里或名字用【】括起时, 续作也不算同一条目
        val s1Names = listOf("【我推的孩子】", "【推しの子】", "Oshi no Ko")
        val s1 = btMedia("[ANi] 【我推的孩子】 - 01 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4")
        assertTrue(btMedia("[ANi] 【我推的孩子】 - 05 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4").isSameLineAs(s1, s1.lineSubjectNames(), s1Names))
        assertFalse(btMedia("[ANi] 【我推的孩子】第二季 - 05 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4").isSameLineAs(s1, s1.lineSubjectNames(), s1Names))
        val kissaten = btMedia("[Nekomoe kissaten][Oshi no Ko][01][1080p][JPSC]", alliance = "Nekomoe kissaten")
        assertTrue(btMedia("[Nekomoe kissaten][Oshi no Ko][02][1080p][JPSC]", alliance = "Nekomoe kissaten").isSameLineAs(kissaten, kissaten.lineSubjectNames(), s1Names))
        assertFalse(btMedia("[Nekomoe kissaten][Oshi no Ko][S2][01][1080p][JPSC]", alliance = "Nekomoe kissaten").isSameLineAs(kissaten, kissaten.lineSubjectNames(), s1Names))
        val kusuriya = btMedia("[织梦字幕组][药屋少女的呢喃 Kusuriya no Hitorigoto][01][1080P][AVC][简日双语]", alliance = "织梦字幕组")
        val kusuriyaNames = listOf("药屋少女的呢喃", "Kusuriya no Hitorigoto")
        assertFalse(btMedia("[织梦字幕组][药屋少女的呢喃 Kusuriya no Hitorigoto][第二季][01][1080P][AVC][简日双语]", alliance = "织梦字幕组").isSameLineAs(kusuriya, kusuriya.lineSubjectNames(), kusuriyaNames))
        assertFalse(btMedia("[织梦字幕组][药屋少女的呢喃 Kusuriya no Hitorigoto][剧场版][1080P][AVC][简日双语]", alliance = "织梦字幕组").isSameLineAs(kusuriya, kusuriya.lineSubjectNames(), kusuriyaNames))

        // 繁体标题与 Bangumi 的简体名对不上: 名字段是子集关系也算同一条目
        val frieren = btMedia("【動漫國字幕組】★10月新番[葬送的芙莉蓮][01][1080P][繁體][MP4]", alliance = "動漫國字幕組")
        val frierenNames = listOf("葬送的芙莉莲", "Sousou no Frieren")
        assertTrue(btMedia("【動漫國字幕組】★10月新番[葬送的芙莉蓮][28(完)][1080P][繁體][MP4]", alliance = "動漫國字幕組").isSameLineAs(frieren, frieren.lineSubjectNames(), frierenNames))
        assertTrue(btMedia("【動漫國字幕組】★10月新番[葬送的芙莉蓮][02][1080P][繁體][字幕修正][MP4]", alliance = "動漫國字幕組").isSameLineAs(frieren, frieren.lineSubjectNames(), frierenNames))
        assertFalse(btMedia("【動漫國字幕組】★10月新番[葬送的芙莉蓮 第二季][01][1080P][繁體][MP4]", alliance = "動漫國字幕組").isSameLineAs(frieren, frieren.lineSubjectNames(), frierenNames))

        // 字幕组标签与数据源给的字幕组名写法不同时, 它不是两条标题共有的 "名字"; 全大写的条目名是名字
        val bleachNames = listOf("BLEACH", "死神")
        val bleach = btMedia("[Nekomoe kissaten][BLEACH][01][1080p][JPSC].mp4", alliance = "喵萌奶茶屋")
        assertTrue(btMedia("[Nekomoe kissaten][BLEACH][02][1080p][JPSC].mp4", alliance = "喵萌奶茶屋").isSameLineAs(bleach, bleach.lineSubjectNames(), bleachNames))
        assertFalse(btMedia("[Nekomoe kissaten][BLEACH Sennen Kessen-hen][01][1080p][JPSC].mp4", alliance = "喵萌奶茶屋").isSameLineAs(bleach, bleach.lineSubjectNames(), bleachNames))
        val kessenNames = listOf("BLEACH 千年血战篇", "BLEACH Sennen Kessen-hen")
        val kessen = btMedia("[Nekomoe kissaten][BLEACH Sennen Kessen-hen][01][1080p][JPSC].mp4", alliance = "喵萌奶茶屋")
        assertFalse(btMedia("[Nekomoe kissaten][BLEACH][05][1080p][JPSC].mp4", alliance = "喵萌奶茶屋").isSameLineAs(kessen, kessen.lineSubjectNames(), kessenNames))

        // 分不出名字的 (纯数字条目名) 看标题是否含条目名
        val numeric = btMedia("[Group] 86 [01][1080p]", alliance = "Group")
        assertTrue(btMedia("[Group] 86 [02][1080p]", alliance = "Group").isSameLineAs(numeric, numeric.lineSubjectNames(), listOf("86")))
        assertFalse(btMedia("[Group] 87 [02][1080p]", alliance = "Group").isSameLineAs(numeric, numeric.lineSubjectNames(), listOf("86")))
    }
}
