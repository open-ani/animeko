/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

// @formatter:off
@file:Suppress(
  "FunctionName",
  "ClassName",
  "RedundantVisibilityModifier",
  "PackageDirectoryMismatch",
  "NonAsciiCharacters",
  "SpellCheckingInspection",
)

import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.title.PatternBasedTitleParserTestSuite
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 原名: `Recent202503`
 * 数据源: `dmhy`
 *
 * 由 `test-codegen` 的 `GenerateTests.kt` 生成, 不要手动修改!
 * 如果你优化了解析器, 这些 test 可能会失败, 请检查是否它是因为以前解析错误而现在解析正确了. 
 * 如果是, 请更新测试数据: 执行 `GenerateTests.kt`.
 */
public class PatternTitleParserTestRecent202503 : PatternBasedTitleParserTestSuite() {
  @Test
  public fun `690641-690640-690639-690638-690637-690636-690635-690634-690633-690632`() {
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社&西農YUI漢化組】[暗芝居 第十四季_Yami Shibai 14][09][x264 1080p][CHT]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社&西农YUI汉化组】[暗芝居 第十四季_Yami Shibai 14][09][x264 1080p][CHS]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 精灵来日 / 歡迎來到日本，妖精小姐。 / 日本へようこそエルフさん。 / Nihon e Youkoso Elf-san. - 08 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]中年男的异世界网购生活 / Arafou Otoko no Isekai Tsuuhan Seikatsu [08] [webRip] [AVC-8bit 1080P AAC] [繁日内嵌]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]魔术发明家 ~异世界魔法的制作方法~ / Magic Maker: Isekai Mahou no Tsukurikata [08] [webRip] [AVC-8bit 1080P AAC] [繁日内嵌]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]虽然我是注定没落的贵族，闲来无事只好来深究魔法 / Botsuraku Yotei no Kizoku dakedo, Hima Datta kara Mahou wo Kiwametemita [07] [webRip] [AVC-8bit 1080P AAC] [繁日内嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語]隊長小翼第二季 青少年篇 / 世少篇 / Captain Tsubasa Season 2 - Junior Youth Hen - 22 (WEB 1920x1080 AVC AACx2 SRT ViuTV CHT)")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 我和班上最讨厌的女生结婚了。 / Class no Daikirai na Joshi to Kekkon Suru Koto ni Natta. [09][1080p][简繁内封]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕組] 我和班上最討厭的女生結婚了。 / Class no Daikirai na Joshi to Kekkon Suru Koto ni Natta. [09][1080p][繁體內嵌]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]虽然我是注定没落的贵族，闲来无事只好来深究魔法 / Botsuraku Yotei no Kizoku dakedo, Hima Datta kara Mahou wo Kiwametemita [07] [webRip] [AVC-8bit 1080P AAC] [简日内嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `690631-690630-690629-690628-690627-690626-690625-690624-690623-690622`() {
    kotlin.run {
    val r =
        parse("[黑白字幕组]魔术发明家 ~异世界魔法的制作方法~ / Magic Maker: Isekai Mahou no Tsukurikata [08] [webRip] [AVC-8bit 1080P AAC] [简日内嵌]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 我和班上最讨厌的女生结婚了。 / Class no Daikirai na Joshi to Kekkon Suru Koto ni Natta. [09][1080p][简体内嵌]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]中年男的异世界网购生活 / Arafou Otoko no Isekai Tsuuhan Seikatsu [08] [webRip] [AVC-8bit 1080P AAC] [简日内嵌]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户压制部] 新石纪 第四季 科学与未来 / Dr.STONE：Science Future [04] [1080p] [繁日内嵌] [2025年1月番]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户压制部] 石纪元 第四季 科学与未来 / Dr.STONE：Science Future [04] [1080p] [简日内嵌] [2025年1月番]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] Promise of Wizard / 魔法使的約定 - 09 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 平凡上班族到異世界當上了四天王的故事 - 10 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[夜莺家族&YYQ字幕组]New Doraemon 哆啦A梦新番[852][2025.03.01][AVC][1080P][GB_JP]")
    assertEquals("852..852", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("你的颜色[简繁字幕].The.Colors.Within.2024.1080p.BluRay.x264.DTS-CTRLHD")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] I Have a Crush at Work / 這公司有我喜歡的人 - 09 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690620-690619-690618-690617-690616-690614-690612-690611-690610-690608`() {
    kotlin.run {
    val r = parse("[织梦字幕组] 与猫共度的夜晚 Yoru wa Neko to Issho 3 [73] [HEVC-10bit 1080P] [简日双语] [特效字幕]")
    assertEquals("73..73", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 坂本日常 / SAKAMOTO DAYS [08][WebRip][HEVC_AAC][简繁日內封]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 坂本日常 / SAKAMOTO DAYS [08][WebRip][HEVC_AAC][繁日內嵌]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 坂本日常 / SAKAMOTO DAYS [08][WebRip][HEVC_AAC][简日內嵌]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[吞噬星空][第161集][国语中字][WEB-MKV][2160P]")
    assertEquals("161..161", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[整理搬运] 日常 (Nichijou／My Ordinary Life)：TV动画+OVA+漫画+音乐+其他；日语音轨; 外挂简中字幕 (整理时间：2023.05.13)")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[SweetSub][刹那之花][Momentary Lily][09][WebRip][1080P][AVC 8bit][简日内嵌]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[SweetSub&LoliHouse] 刹那之花 / Momentary Lily - 09v2 [WebRip 1080p HEVC-10bit AAC][简日内封字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[蓝白汉化] 战队红战士在异世界当冒险者 / Sentai Red Isekai de Boukensha ni Naru [08][1080p][WebRip][x264_AAC][CHS]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[SweetSub][刹那之花][Momentary Lily][09][WebRip][1080P][AV1 10bit][简日内封]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `690607-690606-690605-690604-690602-690601-690600-690599-690598-690596`() {
    kotlin.run {
    val r =
        parse("[整理搬运] 青出于蓝／青蓝姻缘／未婚关系 (藍より青し) (Ai Yori Aoshi)：TV动画 (2002年版、2003年版)+OVA+漫画+CD+其他；日语音轨; 外挂繁中字幕 (整理时间：2024.06.30)")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[整理搬运] 轻音少女 (けいおん !) (K-ON !)：TV动画 (2009年版、2010年版)+剧场版+漫画+演唱会+其他；日语音轨; 外挂简中字幕 (整理时间：2023.05.09)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[丸子家族][櫻桃小丸子第二期(Chibi Maruko-chan II)][1469]櫻家的女兒節玩偶騷動&小玉想要午飯添菜[2025.03.02][BIG5][1080P][MP4]")
    assertEquals("1469..1469", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[丸子家族][樱桃小丸子第二期(Chibi Maruko-chan II)][1469]樱家的女儿节玩偶骚动&小玉想要午饭添菜[2025.03.02][GB][1080P][MP4]")
    assertEquals("1469..1469", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Neko ni Tensei Shita Ojisan / 轉生成貓咪的大叔 - 21 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 灰色：幻影扳机 / Grisaia：Phantom Trigger [09][1080p][简繁内封]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕組] 灰色：幻影扳機 / Grisaia：Phantom Trigger [09][1080p][繁體內嵌]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 灰色：幻影扳机 / Grisaia：Phantom Trigger [09][1080p][简体内嵌]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[整理搬运] 热带雨林的爆笑生活／哈雷小子 (ジャングルはいつもハレのちグゥ) (Haré+Guu)：TV动画+OVA+漫画+音乐+其他；日语音轨; 外挂简中字幕 (整理时间：2023.12.12)")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【归档】[整理搬运] 青出于蓝／青蓝姻缘／未婚关系 (藍より青し) (Ai Yori Aoshi)：TV动画 (2002年版、2003年版)+OVA+漫画+CD+其他；日语音轨; 外挂繁中字幕 (整理时间：2024.06.30)")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `690593-690592-690586-690585-690584-690583-690582-690581-690580-690579`() {
    kotlin.run {
    val r = parse("[Billion Meta Lab] 你的顏色 [1080P][WEBRIP][繁日內嵌雙語]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[无名记忆 Act.2 / Unnamed Memory Act.2][20][1080p][简日双语][招募翻译时轴]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 青之箱 - 21 [NF WebRip AI2160p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 青之箱 - 21 [NF WebRip 1080p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 终究、与你相恋 / Douse, Koishite Shimaunda - 08 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 在地下城寻求邂逅是否搞错了什么 第五季 / Dungeon ni Deai o Motomeru no wa Machigatte Iru Darouka： Familia Myth V [14][1080p][简繁内封]")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] Shangri-La Frontier / 香格里拉·开拓异境～粪作猎手挑战神作～ - 46 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("46..46", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕組] 在地下城尋求邂逅是否搞錯了什麼 第五季 / Dungeon ni Deai o Motomeru no wa Machigatte Iru Darouka： Familia Myth V [14][1080p][繁體內嵌]")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 在地下城寻求邂逅是否搞错了什么 第五季 / Dungeon ni Deai o Motomeru no wa Machigatte Iru Darouka： Familia Myth V [14][1080p][简体内嵌]")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[喵萌奶茶屋&LoliHouse] 金牌得主 / Medalist - 09 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `690578-690577-690576-690575-690574-690573-690572-690571-690570-690569`() {
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][TVB粵語]Let's Camp!露營少女/搖曳露營/Yuru Camp Season 3 - 06 [粵日雙語+內封繁體中文字幕][BD 1920x1080 x264 AACx2 SRT TVB CHT]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Billion Meta Lab] 你的颜色 [1080P][WEBRIP][简日内嵌双语]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[金牌得主 / Medalist][09][1080p][繁日雙語][招募翻譯]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[金牌得主 / Medalist][09][1080p][简日双语][招募翻译]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 我独自升级 第二季 -起于暗影- - 09 [CR WebRip 1080p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 香格里拉·開拓異境～糞作獵手挑戰神作～第2季 / Shangri-La Frontier 2nd Season - 21 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 超超超超超喜歡你的100個女朋友 / Hyakkano - 20 [ViuTV][WEB-RIP][1080p][AVC AAC][CHT][SRT][MKV]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 金肉人 完美超人始祖篇 Season 2 - 19 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 我想蹺掉太子妃培訓 / Kisaki Kyouiku kara Nigetai Watashi - 09 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 魔神創造傳 / Mashin Souzouden Wataru - 08 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690567-690566-690565-690564-690563-690562-690561-690559-690558-690557`() {
    kotlin.run {
    val r =
        parse("[猎户手抄部] 神剑闯江湖 -明治剑客浪漫谭- 京都动乱 / Rurouni Kenshin：Meiji Kenkaku Romantan - Kyoto Douran [44] [1080p] [繁日内嵌] [2024年10月番]")
    assertEquals("44..44", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户手抄部] 浪客剑心 -明治剑客浪漫谭- 京都动乱 / Rurouni Kenshin: Meiji Kenkaku Romantan - Kyoto Douran [44] [1080p] [简日内嵌] [2024年10月番]")
    assertEquals("44..44", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[芝士动物朋友] 石纪元 科学与未来 / Dr.STONE SCIENCE FUTURE [03][CR-WebRip][1080p][HEVC+AAC][简繁内封]（新石纪 第四季 S4）")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 我和班上最讨厌的女生结婚了。 / クラスの大嫌いな女子と結婚することになった。 / Kurakon - 09 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 超超超超超喜歡你的 100 個女朋友 - 20 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]我的可爱对黑岩目高不管用 / Kuroiwa Medaka ni Watashi no Kawaii ga Tsuujinai [08] [webRip] [AVC-8bit 1080P AAC] [繁日内嵌]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]我的可爱对黑岩目高不管用 / Kuroiwa Medaka ni Watashi no Kawaii ga Tsuujinai [08] [webRip] [AVC-8bit 1080P AAC] [简日内嵌]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★10月新番★[凍牌 / Touhai: Ura Rate Mahjong Touhai Roku][20][1080p][繁體][招募翻譯]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★10月新番★[冻牌 / Touhai: Ura Rate Mahjong Touhai Roku][20][1080p][简体][招募翻译]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 我想蹺掉太子妃培訓 - 09 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690556-690551-690549-690548-690547-690546-690545-690544-690543-690542`() {
    kotlin.run {
    val r = parse("[仙逆][第78集][国语中字][WEB-MKV][2160P]")
    assertEquals("78..78", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻櫻字幕組】【10月新番】【龍珠DAIMA Dragon Ball DAIMA】【19】【BIG5_MP4】【1920X1080】")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻樱字幕组】【10月新番】【龙珠DAIMA Dragon Ball DAIMA】【19】【GB_MP4】【1920X1080】")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻櫻字幕組】【10月新番】【龍珠DAIMA Dragon Ball DAIMA】【19】【BIG5_MP4】【1280X720】")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻樱字幕组】【10月新番】【龙珠DAIMA Dragon Ball DAIMA】【19】【GB_MP4】【1280X720】")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Mashin Creator Wataru / 魔神創造傳 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[新Sub][7月新番][不时轻声地以俄语遮羞的邻座艾莉同学_Tokidoki Bosotto Russia-go de Dereru Tonari no Alya-san][09][HEVC][10Bit][1080P][简日双语][招募翻译]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[雪飄工作室][ひみつのアイプリ/Himitsu_no_Aipri/秘密的美妙公主][47][简繁外挂](检索:/美妙旋律/美妙天堂/美妙頻道/美妙魔法)")
    assertEquals("47..47", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[雪飄工作室][ひみつのアイプリ/Himitsu_no_Aipri/秘密的美妙公主][720P][47][繁](检索:/美妙旋律/美妙天堂/美妙頻道/美妙魔法)")
    assertEquals("47..47", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 离开A级队伍的我，和从前的弟子往迷宫深处迈进 / Aparida - 08 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `690541-690540-690539-690538-690537-690536-690535-690533-690531-690530`() {
    kotlin.run {
    val r =
        parse("[LoliHouse] 我与尼特女忍者的莫名同居生活 / NEET Kunoichi - 09 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] ToiletBound Hanakokun S02 / 地縛少年花子君 第二季 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 香格里拉・開拓異境～糞作獵手挑戰神作～ 第二季 - 21 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Strange-Raw] 坂本日常 第一季 / SAKAMOTO DAYS S01 [08] [Bilibili] [WEB-DL] [1080P AVC-8Bits AAC 2.0] [简日内嵌字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[云歌字幕组][1月新番][欢迎来到日本，精灵小姐][08][HEVC][x265 10bit][1080p][简日双语]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[VCB-Studio] 刀剑神域 / Sword Art Online / ソードアート・オンライン 10-bit 1080p HEVC BDRip [S1-2+EE+序列之争 Reseed Fin]")
    assertEquals("01..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[悠哈璃羽字幕社&LoliHouse] 天久鹰央的推理病历表 / Ameku Takao's Detective Karte - 07 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] S级怪兽《贝希摩斯》被误认成小猫，成为精灵女孩的骑士（宠物）一起生活 / Beheneko - 10 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[雪飘工作室][キミとアイドルプリキュア♪/You and Idol Precure♪/与你同为 偶像光之美少女♪][1080p][05][简繁外挂](检索:Q娃)")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[雪飄工作室][キミとアイドルプリキュア♪/You and Idol Precure♪/与你同为 偶像光之美少女♪][720p][05][繁體內嵌](檢索:Q娃)")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `690529-690528-690527-690526-690525-690524-690523-690522-690517-690516`() {
    kotlin.run {
    val r =
        parse("[雪飘工作室][キミとアイドルプリキュア♪/You and Idol Precure♪/与你同为 偶像光之美少女♪][720p][05][简体内嵌](检索:Q娃)")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DBFC字幕组】【龙珠 大魔】【020end】【中日双语字幕】【1080P】【MP4】")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 我独自升级 第二季 -起于暗影- - 09 [CR WebRip AI2160p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[MingY] 隨興旅 -That's Journey- / Zatsu Tabi: That's Journey [01][先行版][WebRip][繁日內嵌]（檢索：雜旅）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社&西農YUI漢化組】[暗芝居 第十四季_Yami Shibai 14][08][x264 1080p][CHT]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[MingY] 随兴旅 -That's Journey- / Zatsu Tabi: That's Journey [01][先行版][WebRip][简日内嵌]（检索：杂旅）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社&西农YUI汉化组】[暗芝居 第十四季_Yami Shibai 14][08][x264 1080p][CHS]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 我独自升级 -起于暗影- / Ore dake Level Up na Ken / Solo Leveling - 21 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[雪飘工作室][魔法つかいプリキュア!!~MIRAI DAYS~/Maho_Girls_Precure!!/魔法少女 光之美少女!!~未来的日子~][1080p][08][简繁外挂](检索:Q娃)")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[雪飘工作室][魔法つかいプリキュア!!~MIRAI DAYS~/Maho_Girls_Precure!!/魔法少女 光之美少女!!~未来的日子~][720p][08][简体内嵌](检索:Q娃)")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `690515-690512-690511-690510-690509-690508-690507-690506-690505-690504`() {
    kotlin.run {
    val r =
        parse("[雪飄工作室][魔法つかいプリキュア!!~MIRAI DAYS~/Maho_Girls_Precure!!/魔法少女 光之美少女!!~未來的日子~][720p][08][繁體內嵌](檢索:Q娃)")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&澄空学园&华盟字幕社&千夏字幕组】★10月新番[冻牌][20][1080P][简体][MP4]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&澄空學園&華盟字幕社&千夏字幕組】★10月新番[凍牌][20][1080P][繁體][MP4]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕组】[青之驱魔师 终夜篇 / Ao no Exorcist _Yosuga Hen][09][简体][1080P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕組】[青之驅魔師 終夜篇 / Ao no Exorcist _Yosuga Hen][09][繁體][1080P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 药屋少女的呢喃 第二季 - 08 [CR WebRip 1080p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银色子弹字幕组][名侦探柯南][第1155集 追踪！侦探出租车2][WEBRIP][简繁日多语MKV][1080P]")
    assertEquals("1155..1155", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银色子弹字幕组][名侦探柯南][第1155集 追蹤！偵探計程車2][WEBRIP][繁日雙語MP4][1080P]")
    assertEquals("1155..1155", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银色子弹字幕组][名侦探柯南][第1155集 追踪！侦探出租车2][WEBRIP][简日双语MP4][1080P]")
    assertEquals("1155..1155", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 我獨自升級 第二季 -起於闇影- / Ore dake Level Up na Ken Season 2- 09 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690503-690502-690501-690500-690499-690498-690497-690496-690495-690494`() {
    kotlin.run {
    val r =
        parse("SAKAMOTO DAYS S01E09 All Aboard 1080p NF WEB-DL DDP5.1 H 264 MULTi-VARYG (Multi-Audio, Multi-Subs)")
    assertEquals("264..264", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[天月搬運組] 吸吸吸吸吸血鬼 / Babanba Banban Vampire - 08 (NetFlix 1920x1080 AVC AAC MKV)")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 在沖繩喜歡上的女孩方言講得太過令人困擾 - 09 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] Blue Exorcist / 青之驅魔師 終夜篇 - 09 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[霜庭雲花Sub][BanG Dream! Ave Mujica][08][1080P][AVC AAC][繁日雙語][WebRip]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[霜庭云花Sub][BanG Dream! Ave Mujica][08][1080P][AVC AAC][简日双语][WebRip]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 龙珠大魔 - 20 [CR WebRip 1080p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 异修罗 第二季 / Ishura S2 - 08 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 離開 A 級隊伍的我，和從前的弟子往迷宮深處邁進 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 我與尼特女忍者的莫名同居生活 - 09 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690493-690492-690491-690490-690489-690488-690487-690486-690485-690484`() {
    kotlin.run {
    val r =
        parse("[ANi] Solo Leveling S02 / 我獨自升級 第二季 －起於闇影－ - 21 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 想星的大天使 Myth of Emotions / Sousei no Aquarion: Myth of Emotions - 08 [WebRip 1080p HEVC-10bit AAC][无字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][TVB粵語]新幹線戰士 改變世界 / 新幹線變形機器人 變革世代 / Shinkalion Change the World - 31 [粵語+內封繁體中文字幕][WEB 1920x1080 AVC AAC SRT TVB YUE CHT]")
    assertEquals("31..31", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] S級怪獸《貝希摩斯》被誤認成小貓，成為精靈女孩的騎士（寵物）一起生活 / Neko to Machigawarete Elf Musume no Pet toshite Kurashitemasu - 10 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 青箱 / Ao no Hako / Blue Box - 20 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★10月新番★[青箱 / Ao no Hako / Blue Box][20][1080p][繁日雙語][招募翻譯]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] S 級怪獸《貝希摩斯》被誤認成小貓，成為精靈女孩的騎士（寵物）一起生活 - 10 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[哆啦字幕组][哆啦A梦大山版附映中篇 Doraemon1979][1998.3.7][1080P][WEB][简日&繁日][哆啦A梦归来了][数码修复版][MKV]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银色子弹字幕组][名侦探柯南][第60集 插画家杀人事件][WEBRIP][简日双语MP4/繁日雙語MP4/简繁日多语MKV][1080P]")
    assertEquals("60..60", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][斗破苍穹 第5季][Fights Break Sphere Ⅴ][2022][136][HEVC][GB][4K]")
    assertEquals("136..136", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690483-690482-690481-690479-690478-690477-690474-690473-690472-690471`() {
    kotlin.run {
    val r = parse("[GM-Team][国漫][斗破苍穹 第5季][Fights Break Sphere Ⅴ][2022][136][AVC][GB][1080P]")
    assertEquals("136..136", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 群花绽放，仿如修罗 / Hana wa Saku Shura no Gotoku - 08 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[白恋字幕组][超电磁侠波鲁吉斯V][超電磁マシーン ボルテスV][Chou Denji Machine Voltes V][1977][TV 01-40 Fin][1080p][内封简中字幕][REV]")
    assertEquals("01..40", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 药屋少女的呢喃 第二季 - 08 [CR WebRip AI2160p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[斗破苍穹年番][第136集][国语中字][WEB-MKV][2160P]")
    assertEquals("136..136", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 龙珠大魔 - 20 [CR WebRip AI2160p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 不幸职业【鉴定士】实则最强 / Fuguushoku 'Kanteishi' ga Jitsu wa Saikyou Datta - 08 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫猫字幕组][1月新番][BanG Dream! Ave Mujica][08][1080p][MP4][简中]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫貓字幕組][1月新番][BanG Dream! Ave Mujica][08][1080p][MP4][繁中]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫猫字幕组][1月新番][BanG Dream! Ave Mujica][07][1080p][MP4][简中]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690470-690469-690468-690467-690466-690465-690464-690463-690462-690461`() {
    kotlin.run {
    val r = parse("[漫貓字幕組][1月新番][BanG Dream! Ave Mujica][07][1080p][MP4][繁中]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] Blue Miburo / 青之壬生浪 - 20 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] Shangri-La Frontier / 香格里拉·开拓异境～粪作猎手挑战神作～ - 45 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("45..45", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[凡人修仙传][第132集][国语中字][WEB-MKV][2160P]")
    assertEquals("132..132", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[SweetSub][刹那之花][Momentary Lily][08][WebRip][1080P][AV1 10bit][简日内封]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 龙珠 大魔 / Dragon Ball Daima - 20 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★10月新番★[青箱 / Ao no Hako / Blue Box][20][1080p][简日双语][招募翻译]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[全修。 / Zenshuu. / ZENSHU][07][1080p][繁日雙語]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[SweetSub][刹那之花][Momentary Lily][07][WebRip][1080P][AV1 10bit][简日内封]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[SweetSub][刹那之花][Momentary Lily][06][WebRip][1080P][AV1 10bit][简日内封]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `690460-690459-690458-690457-690456-690455-690454-690453-690452-690451`() {
    kotlin.run {
    val r = parse("[SweetSub][刹那之花][Momentary Lily][05][WebRip][1080P][AV1 10bit][简日内封]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[SweetSub][刹那之花][Momentary Lily][04][WebRip][1080P][AV1 10bit][简日内封]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[SweetSub][刹那之花][Momentary Lily][03][WebRip][1080P][AV1 10bit][简日内封]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[SweetSub][刹那之花][Momentary Lily][02][WebRip][1080P][AV1 10bit][简日内封]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成][代理商粵語]膽大黨 / Dandadan - 10 [粵日雙語+內封繁體中文字幕](WEB 1920x1080 AVC AACx2 SRT MUSE CHT)")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[SweetSub][刹那之花][Momentary Lily][01][WebRip][1080P][AV1 10bit][简日内封]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 青之箱 / 青春之箱 / 青春盒子 / 蓝箱 / Ao no Hako / Blue Box [19][WebRip][HEVC_AAC][简繁日内封]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 青之箱 / 蓝箱 / Ao no Hako / Blue Box [19][WebRip][HEVC_AAC][简日内嵌]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 青春之箱 / 青春盒子 / Ao no Hako / Blue Box [19][WebRip][HEVC_AAC][繁日內嵌]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】[嘻嘻嘻嘻吸血鬼][Babanba Banban Vampire][03][1080P][CHS]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690450-690449-690447-690445-690444-690443-690442-690441-690440-690438`() {
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】[天久鷹央的推理病歷表_Ameku Takao no Suiri Karte][07][1080p][CHT]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】[天久鹰央的推理病历表_Ameku Takao no Suiri Karte][07][1080p][CHS]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Amor压制组][药师少女的独语 藥師少女的獨語 Kusuriya no Hitorigoto][32][简繁内封][WEBDL][1080P][MKV]")
    assertEquals("32..32", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][凡人修仙传 外海风云][Fan Ren Xiu Xian Zhuan][2024][01-08][AVC][GB][1080P]")
    assertEquals("01..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬運][ANi] 中年大叔轉生反派千金 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕·毁片党】 这家公司里有我喜欢的人 第08集 GB_CN HEVC_opus 1080p")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 大叔转生恶役大小姐 / Akuyaku Reijou Tensei Ojisan - 08 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬運][ANi] 我和班上最討厭的女生結婚了。 - 09 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[黄昏旅店 / Tasokare Hotel][09][1080p][简日双语][招募翻译]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Skymoon-Raws] 龍珠大魔 / Dragonball DaiMa - 20 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690436-690435-690434-690433-690432-690431-690430-690429-690428-690427`() {
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 雖然是公會的櫃檯小姐，但因為不想加班所以打算獨自討伐迷宮頭目 / Guild no Uketsukejou desu ga, Zangyou wa Iya nanode Boss wo Solo Toubatsu Shiyou to Omoimasu - 08 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕组】[药屋少女的呢喃（药师少女的独语）/ Kusuriya no Hitorigoto][32][简体][1080P][MP4]")
    assertEquals("32..32", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕組】[藥屋少女的呢喃（藥師少女的獨語）/ Kusuriya no Hitorigoto][32][繁體][1080P][MP4]")
    assertEquals("32..32", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][斗破苍穹 第5季][Fights Break Sphere Ⅴ][2022][131-135][HEVC][GB][4K]")
    assertEquals("131..135", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][斗破苍穹 第5季][Fights Break Sphere Ⅴ][2022][131-135][AVC][GB][1080P]")
    assertEquals("131..135", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][牧神记][Tales of Qin Mu][2024][15-19][AVC][GB][1080P]")
    assertEquals("15..19", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[GM-Team][国漫][斗罗大陆Ⅱ 绝世唐门][Soul Land Ⅱ：The Peerless Tang Clan][2023][85-90][AVC][GB][1080P]")
    assertEquals("85..90", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 凍牌~地下麻將鬥牌錄~ - 20 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][沧元图 第2季][The Demon Hunter Ⅱ][2024][09-14][GB][4K HEVC 10Bit]")
    assertEquals("09..14", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][神墓 第2季][Tomb of Fallen Gods Ⅱ][2024][27 END][GB][4K HEVC 10Bit]")
    assertEquals("27..27", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690426-690425-690424-690423-690422-690421-690420-690419-690418-690417`() {
    kotlin.run {
    val r = parse("[GM-Team][国漫][神墓 第2季][Tomb of Fallen Gods Ⅱ][2024][26][GB][4K HEVC 10Bit]")
    assertEquals("26..26", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][镇魂街 第4季 天武风雷篇][Rakshasa Street Ⅳ][2024][11-16][AVC][GB][1080P]")
    assertEquals("11..16", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][完美世界][Perfect World][2021][200-204][AVC][GB][1080P]")
    assertEquals("200..204", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[GM-Team][国漫][师兄啊师兄][My Senior Brother is Too Steady][2023][74-78][GB][4K HEVC 10Bit]")
    assertEquals("74..78", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][神印王座][Throne of Seal][2022][144-148][AVC][GB][1080P]")
    assertEquals("144..148", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][遮天][Shrouding the Heavens][2023][94-98][AVC][GB][1080P]")
    assertEquals("94..98", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] Sorairo Utility / 一桿青空 - 09 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] 雖然是公會的櫃檯小姐，但因為不想加班所以打算獨自討伐迷宮頭目 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[異修羅 第二季 / Ishura S2][08][1080p][繁日雙語][招募翻譯]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[天月搬運組] 藥師少女的獨語 (藥屋少女的呢喃) / Kusuriya no Hitorigoto - 32v2 (NetFlix 1920x1080 AVC AAC MKV)")
    assertEquals("32..32", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690416-690415-690413-690412-690411-690410-690409-690408-690407-690406`() {
    kotlin.run {
    val r =
        parse("[天月搬運組] 藥師少女的獨語 (藥屋少女的呢喃) / Kusuriya no Hitorigoto - 32 (NetFlix 1920x1080 AVC AAC MKV)")
    assertEquals("32..32", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&二次壓制][TVB粵語]愛犬訊號 / Dog Signal - 10 [粵語+內封繁體中文字幕][WEB 1920x1080 x264 AAC SRT TVB YUE CHT]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 无名记忆 第二季 / Unnamed Memory Act.2 [20] [1080p] [繁日内嵌] [2025年1月番]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[哆啦字幕組][哆啦A夢大山版附映中篇Doraemon1979][1998.3.7][2080P（准4K）][WEB][繁日雙語][哆啦A夢歸來了][数位修復版][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[哆啦字幕组][哆啦A梦大山版附映中篇 Doraemon1979][1998.3.7][2080P（准4K）][WEB][简日双语][哆啦A梦归来了][数码修复版][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 无名记忆 第二季 / Unnamed Memory Act.2 [20] [1080p] [简日内嵌] [2025年1月番]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[SweetSub][刹那之花][Momentary Lily][09][WebRip][1080P][AVC 8bit][简体内嵌]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] Farmagia / FARMAGIA 魔農傳記 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] Tasokare Hotel / 黃昏旅店 - 09 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户压制部] 我独自升级 第二季 -起于暗影- / Ore dake Level Up na Ken S2 [20] [1080p] [繁日内嵌] [KoVer] [2025年1月番]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `690405-690404-690403-690402-690401-690400-690399-690398-690389-690388`() {
    kotlin.run {
    val r =
        parse("[猎户压制部] 我独自升级 第二季 -起于暗影- / Ore dake Level Up na Ken S2 [20] [1080p] [简日内嵌] [KoVer] [2025年1月番]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Welcome to Japan Ms Elf / 歡迎來到日本，妖精小姐。 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【百冬練習組】【Re: 從零開始的異世界的生活_Re꞉ Zero kara Hajimeru Isekai Seikatsu】[62][WebRip 1080p AVC AAC][繁體]")
    assertEquals("62..62", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【百冬练习组】【Re: 从零开始的异世界的生活_Re꞉ Zero kara Hajimeru Isekai Seikatsu】[62][WebRip 1080p AVC AAC][简体]")
    assertEquals("62..62", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[镇魂街 第4季][第16集][国语中字][WEB-MKV][2160P]")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[斗罗大陆2绝世唐门][第90集][国语中字][WEB-MKV][2160P]")
    assertEquals("90..90", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 这公司有我喜欢的人 / この会社に好きな人がいます / Kono Kaisha ni Suki na Hito ga Imasu - 07 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 浪客剑心 -明治剑客浪漫谭- 京都动乱 / Rurouni Kenshin (2023) - 44 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("44..44", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[萌樱字幕组] BanG Dream! Ave Mujica [07][1080p]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[丸子家族][櫻桃小丸子第二期(Chibi Maruko-chan II)][1468]小丸子想沾花輪的光&友藏想疼愛宏誌[2025.02.23][BIG5][1080P][MP4]")
    assertEquals("1468..1468", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690387-690386-690385-690384-690383-690382-690381-690379-690378-690377`() {
    kotlin.run {
    val r =
        parse("[丸子家族][樱桃小丸子第二期(Chibi Maruko-chan II)][1468]小丸子想沾花轮的光&友藏想疼爱宏志[2025.02.23][GB][1080P][MP4]")
    assertEquals("1468..1468", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&VCB-Studio] 摇曳露营△ 第三季 / Yuru Camp Season 3 / ゆるキャン△ SEASON3 10-bit 1080p HEVC BDRip [Fin]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[不当舔狗制作组] BanG Dream! Ave Mujica - 09 [CR WebRip AI2160p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[不当舔狗制作组] BanG Dream! Ave Mujica - 09 [CR WebRip 1080p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【鈴風字幕組】【在地下城尋求邂逅是否搞錯了什麼 V/DanMachi V】[12][1080P][MKV][繁體外掛]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【枫叶字幕组】[宠物小精灵 / 宝可梦 地平线 烈空坐飞升][083-085][简体][1080P][MP4]")
    assertEquals("83..85", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【楓葉字幕組】[寵物小精靈 / 寶可夢 地平線 烈空坐飛升][083-085][繁體][1080P][MP4]")
    assertEquals("83..85", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【今晚月色真美】[青之箱 / アオのハコ / Ao no Hako][22][WEBrip][1080P][简日双语]")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[TensoRaws] BanG Dream! Ave Mujica [09] [WEBRip 2160p HEVC-10bit AAC]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GalaxyRailroad-888] 游戏王GO RUSH!! Yu-Gi-Oh! GO RUSH !! 146 720P [GB_简中]")
    assertEquals("146..146", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690376-690375-690374-690373-690372-690371-690370-690369-690368-690367`() {
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 中年大叔轉生反派千金 / Akuyaku Reijou Tensei Ojisan - 08 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌Production】★01月新番★[BanG Dream! Ave Mujica][09][1080p][简日双语][招募翻译]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Rurouni Kenshin / 神劍闖江湖 ―明治劍客浪漫譚― 京都動亂 - 20 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Anyway Im Falling in Love with You / 終究，與你相戀。 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] Trillion Game / 一兆＄遊戲 - 21 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 全修。 / Zenshuu. [08][1080p][简繁内封]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕組] 全修。 / Zenshuu. [08][1080p][繁體內嵌]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 全修。 / Zenshuu. [08][1080p][简体内嵌]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Billion Meta Lab] 昙花一现的百合 Momentary Lily [07][1080P][HEVC 10bit][简繁日内封][检索：刹那之花]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Billion Meta Lab] 曇花一現的百合 Momentary Lily [07][1080P][繁日內嵌][檢索：剎那之花]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `690366-690365-690364-690363-690362-690361-690360-690359-690358-690357`() {
    kotlin.run {
    val r = parse("[Billion Meta Lab] 昙花一现的百合 Momentary Lily [07][1080P][简日内嵌][检索：刹那之花]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Momentary Lily / MOMENTARY LILY 剎那之花 - 09 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[群花綻放，彷如修羅 / Hana wa Saku Shura no Gotoku][08][1080p][繁日雙語][招募翻譯]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[群花绽放，彷如修罗 / Hana wa Saku Shura no Gotoku][08][1080p][简日双语][招募翻译]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[异修罗 第二季 / Ishura S2][08][1080p][简日双语][招募翻译]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][TVB粵語]愛犬訊號 / Dog Signal - 09 [粵語+內封繁體中文字幕][WEB 1920x1080 AVC AAC SRT TVB YUE CHT]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[哆啦字幕組][哆啦A夢大山版附映中篇Doraemon1979][1998.3.7][1080P][WEB][繁日雙語][哆啦A夢歸來了][数位修復版][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[哆啦字幕组][哆啦A梦大山版附映中篇 Doraemon1979][1998.3.7][1080P][WEB][简日双语][哆啦A梦归来了][数码修复版][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 天久鹰央的推理病历表 / Ameku Takao's Detective Karte - 08 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户压制部] 我独自升级 第二季 -起于暗影- / Ore dake Level Up na Ken S2 [20] [1080p] [繁日内嵌] [JaVer] [2025年1月番]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `690356-690355-690354-690353-690352-690351-690350-690349-690348-690345`() {
    kotlin.run {
    val r =
        parse("[猎户压制部] 我独自升级 第二季 -起于暗影- / Ore dake Level Up na Ken S2 [20] [1080p] [简日内嵌] [JaVer] [2025年1月番]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户压制部] 香格里拉·开拓异境～粪作猎手挑战神作 第二季 / Shangri-La Frontier S2 [45] [1080p] [繁日内嵌] [2024年10月番]")
    assertEquals("45..45", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户压制部] 香格里拉·开拓异境～粪作猎手挑战神作 第二季 / Shangri-La Frontier S2 [45] [1080p] [简日内嵌] [2024年10月番]")
    assertEquals("45..45", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD制作组][4K(2160P)+HDR+1080P][BanG Dream! Ave Mujica][09][WEB-DL][简日双语内嵌][AAC][MKV]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 不幸職業【鑑定士】其實是最強的 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 在地下城寻求邂逅是否搞错了什么 第五季 / DanMachi S5 - 14 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] BanG Dream! Ave Mujica - 09 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[S1百综字幕组]BanG Dream! Ave Mujica [09][2160p][Bilibili][Web-DL][HDR][HEVC_AAC][简日双语]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Dr STONE S04 / Dr.STONE 新石紀 第四季 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 迟早是最强的炼金术师？ / Izure Saikyou no Renkinjutsushi? - 09 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `690344-690343-690341-690339-690338-690334-690333-690332-690331-690330`() {
    kotlin.run {
    val r = parse("[神印王座][第148集][国语中字][WEB-MKV][2160P]")
    assertEquals("148..148", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[完美世界][第204集][国语中字][WEB-MKV][2160P]")
    assertEquals("204..204", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("你的颜色.The.Colors.Within.2024.1080p.AMZN.WEB-DL.H264.DDP5.1-QuickIO")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 中年男的异世界网购生活 / Around 40 Otoko no Isekai Tsuuhan - 06 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 中年男的异世界网购生活 / Around 40 Otoko no Isekai Tsuuhan - 05 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕組] 群花綻放、彷如修羅 / Hana wa Saku, Shura no Gotoku [07][1080p][繁體內嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 群花绽放、彷如修罗 / Hana wa Saku, Shura no Gotoku [07][1080p][简体内嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] Re：从零开始的异世界生活 第三季 - 12 [CR WebRip AI2160p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 群花绽放、彷如修罗 / Hana wa Saku, Shura no Gotoku [07][1080p][简繁内封]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] Re：从零开始的异世界生活 第三季 - 12 [CR WebRip 1080p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `690328-690325-690324-690323-690322-690321-690320-690319-690318-690317`() {
    kotlin.run {
    val r =
        parse("[MagicStar] 蜡笔小新 剧场版 我们的恐龙日记 / 映画クレヨンしんちゃん オラたちの恐竜日記 2024 [WEBDL] [1080p] [U-NEXT]【生】")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【喵萌奶茶屋】★10月新番★[Chi。-关于地球的运动- / Chi. Chikyuu no Undou ni Tsuite][20][1080p][简日双语][招募翻译]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Ameku MD Doctor Detective / 天久鷹央的推理病歷表 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Honey Lemon Soda / 青春特調蜂蜜檸檬蘇打 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 在地下城尋求邂逅是否搞錯了什麼 第五季 / Is It Wrong to Try to Pick Up Girls in a Dungeon? V: Goddess of Fertility Arc - 14 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] Re：從零開始的異世界生活 第三季 / Re：Zero kara Hajimeru Isekai Seikatsu 3rd Season - 12 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 我独自升级 第二季 -起于暗影- - 08V3 [CR WebRip AI2160p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[冷番补完字幕组][天狼星的传说][シリウスの伝説][The Sea Prince and the Fire Child][1981]Movie[1080p][内封中英双语字幕]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("ENG", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] Re：從零開始的異世界生活 第三季 - 12 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 结缘甘神神社 / Amagami-san Chi no Enmusubi - 20 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `690316-690315-690314-690313-690312-690311-690308-690307-690306-690305`() {
    kotlin.run {
    val r =
        parse("[动漫国字幕组&LoliHouse] FAIRY TAIL 魔导少年 / 妖精尾巴 百年任务 [01-25 精校合集][WebRip 1080p HEVC-10bit AAC][简繁内封字幕][Fin]")
    assertEquals("01..25", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 蜂蜜柠檬苏打 / ハニーレモンソーダ / Honey Lemon Soda - 04 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 蜂蜜柠檬苏打 / ハニーレモンソーダ / Honey Lemon Soda - 03 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 遲早是最強的鍊金術師？ - 09 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 在地下城尋求邂逅是否搞錯了什麼 第五季 - 14 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[夜莺家族&YYQ字幕组]New Doraemon 哆啦A梦新番[851][2025.02.22][AVC][1080P][GB_JP]")
    assertEquals("851..851", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【幻櫻字幕組】【1月新番】【魔法製造者 ~異世界魔法的製作方法~ Magic Maker ~Isekai Mahou no Tsukurikata~】【07】【BIG5_MP4】【1920X1080】")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【幻樱字幕组】【1月新番】【魔法制造者 ~异世界魔法的制作方法~ Magic Maker ~Isekai Mahou no Tsukurikata~】【07】【GB_MP4】【1920X1080】")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][我的英雄学院剧场版：You're Next/Boku no Hero Academia the Movie You're Next][正片+特典映像][1080P][BDRip][HEVC-10bit][FLAC][MKV](僕のヒーローアカデミア THE MOVIE ユアネクスト)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("ENG", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[喵萌奶茶屋&LoliHouse] 全修。 / ZENSHU - 06 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `690304-690303-690302-690301-690300-690299-690298-690297-690296-690292`() {
    kotlin.run {
    val r =
        parse("[❀拨雪寻春❀] 悠久之翼 - 前尘遗响 / ef - a tale of memories - 02 [BDRip][HEVC-10bit 1080p][繁日内嵌]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[❀拨雪寻春❀] 悠久之翼 - 前尘遗响 / ef - a tale of memories - 02 [BDRip][HEVC-10bit 1080p][简日内嵌]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[❀拨雪寻春❀] 悠久之翼 - 前尘遗响 / ef - a tale of memories - 02 [BDRip][HEVC-10bit 1080p][简繁日内封]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[❀拨雪寻春❀] 悠久之翼 - 前尘遗响 / ef - a tale of memories - 01 [BDRip][HEVC-10bit 1080p][繁日内嵌]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[❀拨雪寻春❀] 悠久之翼 - 前尘遗响 / ef - a tale of memories - 01 [BDRip][HEVC-10bit 1080p][简日内嵌]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[❀拨雪寻春❀] 悠久之翼 - 前尘遗响 / ef - a tale of memories - 01 [BDRip][HEVC-10bit 1080p][简繁日内封]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[❀拨雪寻春❀] 悠久之翼 - 前尘遗响 / ef - a tale of memories - 00 [BDRip][HEVC-10bit 1080p][繁日内嵌]")
    assertEquals("00..00", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[❀拨雪寻春❀] 悠久之翼 - 前尘遗响 / ef - a tale of memories - 00 [BDRip][HEVC-10bit 1080p][简日内嵌]")
    assertEquals("00..00", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[❀拨雪寻春❀] 悠久之翼 - 前尘遗响 / ef - a tale of memories - 00 [BDRip][HEVC-10bit 1080p][简繁日内封]")
    assertEquals("00..00", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[黑白字幕组]监禁区域LevelX / Kankin Kuiki Level X [07][CHS](1920x1080 AVC AAC ).mp4")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690291-690290-690288-690287-690286-690285-690284-690274-690273-690272`() {
    kotlin.run {
    val r =
        parse("[黑白字幕组]虽然我是注定没落的贵族，闲来无事只好来深究魔法 / Botsuraku Yotei no Kizoku dakedo, Hima Datta kara Mahou wo Kiwametemita [06] [webRip] [AVC-8bit 1080P AAC] [繁日内嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]虽然我是注定没落的贵族，闲来无事只好来深究魔法 / Botsuraku Yotei no Kizoku dakedo, Hima Datta kara Mahou wo Kiwametemita [06] [webRip] [AVC-8bit 1080P AAC] [简日内嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[愛戀字幕社][1月新番][群花綻放，彷如修羅][Hana wa Saku, Shura no Gotoku][05][1080P][MP4][BIG5][繁中]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋字幕社][1月新番][群花绽放，彷如修罗][Hana wa Saku, Shura no Gotoku][05][1080P][MP4][GB][简中]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[愛戀字幕社][1月新番][群花綻放，彷如修羅][Hana wa Saku, Shura no Gotoku][04][1080P][MP4][BIG5][繁中]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋字幕社][1月新番][群花绽放，彷如修罗][Hana wa Saku, Shura no Gotoku][04][1080P][MP4][GB][简中]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 最弱技能《果实大师》 ～关于能无限食用技能果实（吃了就会死）这件事～ / Kinomi Master - 09 [WebRip 1080p HEVC-10bit AAC][无字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 青箱 / Ao no Hako / Blue Box - 19 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 我的幸福婚姻 2期 / Watashi no Shiawase na Kekkon S2 - 08 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[SweetSub&LoliHouse] 刹那之花 / Momentary Lily - 08 [WebRip 1080p HEVC-10bit AAC][简日内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `690271-690270-690269-690268-690267-690266-690265-690264-690263-690262`() {
    kotlin.run {
    val r =
        parse("[ANi] Hana wa Saku Shura no Gotoku / 群花綻放、彷如修羅 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[遮天][第98集][国语中字][WEB-MKV][2160P]")
    assertEquals("98..98", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 药师少女的独语 / 药屋少女的呢喃 / Kusuriya no Hitorigoto - 30 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("30..30", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[萌樱字幕组] 药屋少女的呢喃 [30][1080p][简繁日内封]")
    assertEquals("30..30", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[全修。 / Zenshuu. / ZENSHU][07][1080p][简日双语]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[全修。 / Zenshuu. / ZENSHU][06][1080p][繁日雙語]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[全修。 / Zenshuu. / ZENSHU][06][1080p][简日双语]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[我的幸福婚姻 2期 / Watashi no Shiawase na Kekkon S2][08][1080p][繁日雙語]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[我的幸福婚姻 2期 / Watashi no Shiawase na Kekkon S2][08][1080p][简日双语]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[药师少女的独语 / 药屋少女的呢喃 / Kusuriya no Hitorigoto][31][1080p][简日双语]")
    assertEquals("31..31", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690261-690260-690259-690258-690257-690256-690255-690254-690253-690252`() {
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[藥師少女的獨語 / 藥屋少女的呢喃 / Kusuriya no Hitorigoto][30][1080p][繁日雙語]")
    assertEquals("30..30", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[药师少女的独语 / 药屋少女的呢喃 / Kusuriya no Hitorigoto][30][1080p][简日双语]")
    assertEquals("30..30", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★10月新番★[青箱 / Ao no Hako / Blue Box][19][1080p][繁日雙語][招募翻譯]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★10月新番★[青箱 / Ao no Hako / Blue Box][19][1080p][简日双语][招募翻译]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[云光字幕组] 蜂蜜柠檬苏打水 Honey Lemon Soda [07][简体双语][1080p]招募翻译")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 魔法使的约定 / Mahoutsukai no Yakusoku - 08 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Amagamisan Chi no Enmusubi / 結緣甘神神社 - 20 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户压制部] 新石纪 第四季 科学与未来 / Dr.STONE：Science Future [03] [1080p] [繁日内嵌] [2025年1月番]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户压制部] 石纪元 第四季 科学与未来 / Dr.STONE：Science Future [03] [1080p] [简日内嵌] [2025年1月番]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户手抄部] 地。-关于地球的运动- / Chi. Chikyuu no Undou ni Tsuite [22] [1080p] [繁日内嵌] [2024年10月番]")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `690251-690250-690249-690248-690247-690246-690245-690240-690239-690236`() {
    kotlin.run {
    val r =
        parse("[猎户手抄部] 地。-关于地球的运动- / Chi. Chikyuu no Undou ni Tsuite [22] [1080p] [简日内嵌] [2024年10月番]")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]虽然我是注定没落的贵族，闲来无事只好来深究魔法 / Botsuraku Yotei no Kizoku dakedo, Hima Datta kara Mahou wo Kiwametemita [05] [webRip] [AVC-8bit 1080P AAC] [繁日内嵌]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]虽然我是注定没落的贵族，闲来无事只好来深究魔法 / Botsuraku Yotei no Kizoku dakedo, Hima Datta kara Mahou wo Kiwametemita [05] [webRip] [AVC-8bit 1080P AAC] [简日内嵌]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[织梦字幕组] 与猫共度的夜晚 Yoru wa Neko to Issho 3 [72] [HEVC-10bit 1080P] [简日双语] [特效字幕]")
    assertEquals("72..72", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[织梦字幕组] 与猫共度的夜晚 Yoru wa Neko to Issho 3 [71] [HEVC-10bit 1080P] [简日双语] [特效字幕]")
    assertEquals("71..71", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[织梦字幕组] 与猫共度的夜晚 Yoru wa Neko to Issho 3 [70] [HEVC-10bit 1080P] [简日双语] [特效字幕]")
    assertEquals("70..70", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 妖怪學校的菜鳥老師 - 20 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Yumezukuri] Yoru wa Neko to Issho 3 [69] [HEVC-10bit 1080P] [CHS＆JPN]")
    assertEquals("69..69", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[织梦字幕组] 与猫共度的夜晚 Yoru wa Neko to Issho 3 [68] [HEVC-10bit 1080P] [简日双语] [特效字幕]")
    assertEquals("68..68", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[夜莺家族][樱桃小丸子第二期(Chibi Maruko-chan II)][1468]小丸子想模仿花轮&友藏想疼爱宏志[2025.02.23][GB_JP][1080P][MP4]")
    assertEquals("1468..1468", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690235-690222-690221-690216-690215-690214-690200-690199-690198-690197`() {
    kotlin.run {
    val r = parse("[SweetSub][刹那之花][Momentary Lily][08][WebRip][1080P][AVC 8bit][简日内嵌]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[漫猫字幕社&猫恋汉化组][1月新番][药屋少女的呢喃][Kusuriya no Hitorigoto][31][1080P][MP4][GB&JP][简日双语]")
    assertEquals("31..31", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[漫貓字幕社&貓戀漢化組][1月新番][藥師少女的獨語][Kusuriya no Hitorigoto][31][1080P][MP4][BIG5&JP][繁日雙語]")
    assertEquals("31..31", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[霜庭雲花Sub][BanG Dream! Ave Mujica][07][1080P][AVC AAC][繁日雙語][WebRip]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[霜庭云花Sub][BanG Dream! Ave Mujica][07][1080P][AVC AAC][简日双语][WebRip]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][HOY粵語]寶可夢 地平線 / Pocket Monsters (2023) - 36 [粵語+內封繁體中文字幕](WEB 1920x1080 AVC AAC PGS+SRT iCABLE YUE CHT)")
    assertEquals("36..36", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 虽然我是注定没落的贵族，闲来无事只好来深究魔法 / Botsuraku Yotei no Kizoku dakedo, Hima Datta kara Mahou wo Kiwametemita - 08 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】[群花綻放 彷如修羅_Hana wa Saku,Shura no Gotoku][05][1080p][CHT]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】[群花绽放 彷如修罗_Hana wa Saku,Shura no Gotoku][05][1080p][CHS]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】[群花綻放 彷如修羅_Hana wa Saku,Shura no Gotoku][04][1080p][CHT]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690196-690193-690191-690190-690189-690185-690184-690183-690182-690181`() {
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】[群花绽放 彷如修罗_Hana wa Saku,Shura no Gotoku][04][1080p][CHS]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 黑岩目高不把我的可爱放在眼里 / Kuroiwa Medaka ni Watashi no Kawaii ga Tsuujinai - 08 [WebRip 1080p HEVC-10bit AAC][无字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[黑白字幕组]全修。 / Zenshuu. [08] [webRip] [AVC-8bit 1080P AAC] [繁日内嵌]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[黑白字幕组]全修。 / Zenshuu. [08] [webRip] [AVC-8bit 1080P AAC] [简日内嵌]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[VCB-Studio] 关于我转生变成史莱姆这档事 / Tensei Shitara Slime Datta Ken / 転生したらスライムだった件 10-bit 1080p HEVC BDRip [S1 Reseed + OAD Reseed + S2 Reseed + MOVIE + WEB + S3]")
    assertEquals("S1+OAD+S2+S?+S3", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][长生界][The Eternal Realm][2024][19][HEVC][GB][4K]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][长生界][The Eternal Realm][2024][18][HEVC][GB][4K]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕·毁片党】灰色：幻影扳机 第07-08集 GB_CN HEVC_opus 1080p")
    assertEquals("07..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][吞噬星空][Swallowed Star][2021][160][AVC][GB][1080P]")
    assertEquals("160..160", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][吞噬星空][Swallowed Star][2021][159[AVC][GB][1080P]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690180-690179-690178-690176-690175-690174-690173-690172-690171-690170`() {
    kotlin.run {
    val r =
        parse("[❀拨雪寻春❀] 葬送的芙莉莲 / 葬送のフリーレン / Sousou no Frieren - 12 [BDRip][HEVC-10bit 1080p][繁日内嵌]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[❀拨雪寻春❀] 葬送的芙莉莲 / 葬送のフリーレン / Sousou no Frieren - 12 [BDRip][HEVC-10bit 1080p][简日内嵌]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[❀拨雪寻春❀] 葬送的芙莉莲 / 葬送のフリーレン / Sousou no Frieren - 12 [BDRip][HEVC-10bit 1080p][简繁日内封]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[蓝白汉化] 战队红战士在异世界当冒险者 / Sentai Red Isekai de Boukensha ni Naru [07][1080p][WebRip][x264_AAC][CHS]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][發行商粵語]蠟筆小新劇場版：幽靈忍者珍風傳 / Crayon Shin-chan Mononoke Ninja Chinfuuden [粵日雙語+內封繁體中文字幕] (BD 1920x1080 x264 AACx2 PGS+SRT MOV CHT)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成][代理商粵語]膽大黨 / Dandadan - 09 [粵日雙語+內封繁體中文字幕](WEB 1920x1080 AVC AACx2 SRT MUSE CHT)")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 药屋少女的呢喃 / 药屋少女的独语 / Kusuriya no Hitorigoto [31][WebRip][HEVC_AAC][简繁日内封]")
    assertEquals("31..31", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 药屋少女的呢喃 / 药屋少女的独语 / Kusuriya no Hitorigoto [31][WebRip][HEVC_AAC][简日内嵌]")
    assertEquals("31..31", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 藥屋少女的呢喃 / 藥屋少女的獨語 / Kusuriya no Hitorigoto [31][WebRip][HEVC_AAC][繁日內嵌]")
    assertEquals("31..31", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]中年男的异世界网购生活 / Arafou Otoko no Isekai Tsuuhan Seikatsu [07] [webRip] [AVC-8bit 1080P AAC] [繁日内嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `690169-690168-690167-690166-690165-690164-690163-690162-690161-690160`() {
    kotlin.run {
    val r =
        parse("[黑白字幕组]中年男的异世界网购生活 / Arafou Otoko no Isekai Tsuuhan Seikatsu [07] [webRip] [AVC-8bit 1080P AAC] [简日内嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 坂本日常 / SAKAMOTO DAYS [05] [1080p] [繁日内嵌] [2025年1月番]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 坂本日常 / SAKAMOTO DAYS [05] [1080p] [简日内嵌] [2025年1月番]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] Promise of Wizard / 魔法使的約定 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 平凡上班族到異世界當上了四天王的故事 - 09 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户压制部] 新石纪 第四季 科学与未来 / Dr.STONE：Science Future [02] [1080p] [繁日内嵌] [2025年1月番]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户压制部] 石纪元 第四季 科学与未来 / Dr.STONE：Science Future [02] [1080p] [简日内嵌] [2025年1月番]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] I Have a Crush at Work / 這公司有我喜歡的人 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[云歌字幕组][1月新番][欢迎来到日本，精灵小姐][07][HEVC][x265 10bit][1080p][简日双语]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[晚街與燈][灼眼的夏娜 第一季_Shakugan no Shana I][06][BDRip_1080P_x264_AAC][繁日內嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `690159-690157-690154-690153-690152-690151-690150-690149-690147-690146`() {
    kotlin.run {
    val r = parse("[晚街与灯][灼眼的夏娜 第一季_Shakugan no Shana I][06][BDRip_1080P_x264_AAC][简日内嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[吞噬星空][第160集][国语中字][WEB-MKV][2160P]")
    assertEquals("160..160", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[❀拨雪寻春❀] 葬送的芙莉莲 / 葬送のフリーレン / Sousou no Frieren - 11 [BDRip][HEVC-10bit 1080p][繁日内嵌]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[❀拨雪寻春❀] 葬送的芙莉莲 / 葬送のフリーレン / Sousou no Frieren - 11 [BDRip][HEVC-10bit 1080p][简日内嵌]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[❀拨雪寻春❀] 葬送的芙莉莲 / 葬送のフリーレン / Sousou no Frieren - 11 [BDRip][HEVC-10bit 1080p][简繁日内封]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[❀拨雪寻春❀] 葬送的芙莉莲 / 葬送のフリーレン / Sousou no Frieren - 10 [BDRip][HEVC-10bit 1080p][繁日内嵌]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[❀拨雪寻春❀] 葬送的芙莉莲 / 葬送のフリーレン / Sousou no Frieren - 10 [BDRip][HEVC-10bit 1080p][简日内嵌]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[❀拨雪寻春❀] 葬送的芙莉莲 / 葬送のフリーレン / Sousou no Frieren - 10 [BDRip][HEVC-10bit 1080p][简繁日内封]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&VCB-Studio] 妖幻三重奏 / Ayakashi Triangle / あやかしトライアングル 10-bit 1080p HEVC BDRip [Reseed Fin]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 龙珠大魔 - 19V2 [CR WebRip AI2160p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `690143-690141-690140-690139-690138-690137-690136-690135-690131-690130`() {
    kotlin.run {
    val r =
        parse("[ANi] Neko ni Tensei Shita Ojisan / 轉生成貓咪的大叔 - 20 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][仙逆][Renegade Immortal][2023][77][AVC][GB][1080P]")
    assertEquals("77..77", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][TVB粵語]Let's Camp!露營少女/搖曳露營/Yuru Camp Season 3 - 05 [粵日雙語+內封繁體中文字幕][BD 1920x1080 x264 AACx2 SRT TVB CHT]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[愛戀字幕社&貓戀漢化組][夜櫻家的大作戰/夜櫻家大作戰][Yozakura-san Chi no Daisakusen][01-27][1080P][MP4][繁中]")
    assertEquals("01..27", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[爱恋字幕社&猫恋汉化组][夜樱家的大作战/夜樱家大作战][Yozakura-san Chi no Daisakusen][01-27][1080P][MP4][简中]")
    assertEquals("01..27", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[愛戀字幕社&貓戀漢化組][10月新番][緣結甘神家/結緣甘神神社][Amagami-san Chi no Enmusubi][15-17][1080P][MP4][BIG5][繁中]")
    assertEquals("15..17", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[爱恋字幕社&猫恋汉化组][10月新番][缘结甘神家/结缘甘神神社][Amagami-san Chi no Enmusubi][15-17][1080P][MP4][GB][简中]")
    assertEquals("15..17", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 我独自升级 第二季 -起于暗影- - 08V2 [CR WebRip AI2160p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[晚街與燈][灼眼的夏娜 第一季_Shakugan no Shana I][05][BDRip_1080P_x264_AAC][繁日內嵌]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[晚街与灯][灼眼的夏娜 第一季_Shakugan no Shana I][05][BDRip_1080P_x264_AAC][简日内嵌]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `690129-690128-690127-690126-690125-690124-690123-690122-690121-690120`() {
    kotlin.run {
    val r =
        parse("[黑白字幕组]魔术发明家 ~异世界魔法的制作方法~ / Magic Maker: Isekai Mahou no Tsukurikata [07] [webRip] [AVC-8bit 1080P AAC] [繁日内嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]魔术发明家 ~异世界魔法的制作方法~ / Magic Maker: Isekai Mahou no Tsukurikata [07] [webRip] [AVC-8bit 1080P AAC] [简日内嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【无戒汉化组】摇曳马娘 Umayuru [22][1080p][简中内嵌]")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 超超超超超喜歡你的100個女朋友 / Hyakkano - 19 [ViuTV][WEB-RIP][1080p][AVC AAC][CHT][SRT][MKV]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【枫叶字幕组】[宠物小精灵 / 宝可梦 地平线 烈空坐飞升][080-082][简体][1080P][MP4]")
    assertEquals("80..82", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【楓葉字幕組】[寵物小精靈 / 寶可夢 地平線 烈空坐飛升][080-082][繁體][1080P][MP4]")
    assertEquals("80..82", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 灰色：幻影扳机 / Grisaia：Phantom Trigger [08][1080p][简繁内封]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕組] 灰色：幻影扳機 / Grisaia：Phantom Trigger [08][1080p][繁體內嵌]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 灰色：幻影扳机 / Grisaia：Phantom Trigger [08][1080p][简体内嵌]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 我想蹺掉太子妃培訓 / Kisaki Kyouiku kara Nigetai Watashi - 08 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690119-690118-690117-690115-690114-690111-690110-690109-690108-690107`() {
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 魔神創造傳 / Mashin Souzouden Wataru - 07 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 香格里拉·開拓異境～糞作獵手挑戰神作～第2季 / Shangri-La Frontier 2nd Season - 20 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 金肉人 完美超人始祖篇 Season 2 - 18 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户手抄部] 神剑闯江湖 -明治剑客浪漫谭- 京都动乱 / Rurouni Kenshin：Meiji Kenkaku Romantan - Kyoto Douran [43] [1080p] [繁日内嵌] [2024年10月番]")
    assertEquals("43..43", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户手抄部] 浪客剑心 -明治剑客浪漫谭- 京都动乱 / Rurouni Kenshin: Meiji Kenkaku Romantan - Kyoto Douran [43] [1080p] [简日内嵌] [2024年10月番]")
    assertEquals("43..43", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 我和班上最讨厌的女生结婚了。 / Class no Daikirai na Joshi to Kekkon Suru Koto ni Natta. [08][1080p][简繁内封]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 超超超超超喜歡你的 100 個女朋友 - 19 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕組] 我和班上最討厭的女生結婚了。 / Class no Daikirai na Joshi to Kekkon Suru Koto ni Natta. [08][1080p][繁體內嵌]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 我和班上最讨厌的女生结婚了。 / Class no Daikirai na Joshi to Kekkon Suru Koto ni Natta. [08][1080p][简体内嵌]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 我想蹺掉太子妃培訓 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690106-690105-690104-690103-690101-690100-690096-690095-690094-690093`() {
    kotlin.run {
    val r = parse("[喵萌奶茶屋&LoliHouse] 金牌得主 / Medalist - 08 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【FSD粉羽社】魔法使光之美少女！！～MIRAI DAYS～[06][1080P][简体中字]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[仙逆][第77集][国语中字][WEB-MKV][2160P]")
    assertEquals("77..77", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語]隊長小翼第二季 青少年篇 / 世少篇 / Captain Tsubasa Season 2 - Junior Youth Hen - 21 (WEB 1920x1080 AVC AACx2 SRT ViuTV CHT)")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[雪飄工作室][ひみつのアイプリ/Himitsu_no_Aipri/秘密的美妙公主][46][简繁外挂](检索:/美妙旋律/美妙天堂/美妙頻道/美妙魔法)")
    assertEquals("46..46", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[雪飄工作室][ひみつのアイプリ/Himitsu_no_Aipri/秘密的美妙公主][720P][46][繁](检索:/美妙旋律/美妙天堂/美妙頻道/美妙魔法)")
    assertEquals("46..46", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★10月新番★[冻牌 / Touhai: Ura Rate Mahjong Touhai Roku][19][1080p][简体][招募翻译]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★10月新番★[凍牌 / Touhai: Ura Rate Mahjong Touhai Roku][19][1080p][繁體][招募翻譯]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[金牌得主 / Medalist][08][1080p][繁日雙語][招募翻譯]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[金牌得主 / Medalist][08][1080p][简日双语][招募翻译]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690092-690091-690089-690088-690087-690086-690085-690084-690083-690082`() {
    kotlin.run {
    val r = parse("[仙武传][第98-102集][国语中字][WEB-MKV][2160P]")
    assertEquals("98..102", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[牧神记][第18-19集][国语中字][WEB-MKV][2160P]")
    assertEquals("18..19", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] BanG Dream! Ave Mujica / 颂乐人偶 [06][WebRip][HEVC_AAC][简繁日内封]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] BanG Dream! Ave Mujica / 頌樂人偶 [06][WebRip][HEVC_AAC][繁日內嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] BanG Dream! Ave Mujica / 颂乐人偶 [06][WebRip][HEVC_AAC][简日内嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Mashin Creator Wataru / 魔神創造傳 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] ToiletBound Hanakokun S02 / 地縛少年花子君 第二季 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 香格里拉・開拓異境～糞作獵手挑戰神作～ 第二季 - 20 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[豌豆字幕组&LoliHouse] 青之驱魔师 终夜篇 / Ao no Exorcist Yosuga-hen - 08 [WebRip 1080p HEVC-10bit AAC][简繁外挂字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 离开A级队伍的我，和从前的弟子往迷宫深处迈进 / Aparida - 07 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `690081-690080-690079-690078-690077-690076-690075-690073-690072-690071`() {
    kotlin.run {
    val r =
        parse("[LoliHouse] 我与尼特女忍者的莫名同居生活 / NEET Kunoichi - 08 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 龙珠 大魔 / Dragon Ball Daima - 19 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&澄空学园&华盟字幕社&千夏字幕组】★10月新番[冻牌][19][1080P][简体][MP4]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&澄空學園&華盟字幕社&千夏字幕組】★10月新番[凍牌][19][1080P][繁體][MP4]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[雪飘工作室][キミとアイドルプリキュア♪/You and Idol Precure♪/与你同为 偶像光之美少女♪][1080p][04][简繁外挂](检索:Q娃)")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[雪飄工作室][キミとアイドルプリキュア♪/You and Idol Precure♪/与你同为 偶像光之美少女♪][720p][04][繁體內嵌](檢索:Q娃)")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[雪飘工作室][キミとアイドルプリキュア♪/You and Idol Precure♪/与你同为 偶像光之美少女♪][720p][04][简体内嵌](检索:Q娃)")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[SweetSub&VCB-Studio] 16位的辉煌 / 16bit Sensation Another Layer / 16bitセンセーション 10-bit 1080p HEVC BDRip [Fin]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][TVB粵語]新幹線戰士 改變世界 / 新幹線變形機器人 變革世代 / Shinkalion Change the World - 30 [粵語+內封繁體中文字幕][WEB 1920x1080 AVC AAC SRT TVB YUE CHT]")
    assertEquals("30..30", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DBFC字幕组】【龙珠 大魔】【019】【中日双语字幕】【1080P】【MP4】")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `690070-690069-690068-690067-690066-690065-690064-690062-690044-690042`() {
    kotlin.run {
    val r = parse("[不当舔狗制作组] 青之箱 - 20 [NF WebRip AI2160p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【百冬練習組】【Re: 從零開始的異世界的生活_Re꞉ Zero kara Hajimeru Isekai Seikatsu】[61][WebRip 1080p AVC AAC][繁體]")
    assertEquals("61..61", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【百冬练习组】【Re: 从零开始的异世界的生活_Re꞉ Zero kara Hajimeru Isekai Seikatsu】[61][WebRip 1080p AVC AAC][简体]")
    assertEquals("61..61", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 青之箱 - 20 [NF WebRip 1080p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 青之箱 - 19 [NF WebRip AI2160p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 青之箱 - 19 [NF WebRip 1080p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 我独自升级 第二季 -起于暗影- - 08 [CR WebRip AI2160p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 我独自升级 -起于暗影- / Ore dake Level Up na Ken / Solo Leveling - 20 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 终究、与你相恋 / Douse, Koishite Shimaunda - 07 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 我独自升级 第二季 -起于暗影- - 08 [CR WebRip 1080p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `690041-690040-690039-690038-690037-690036-690035-690034-690033-690032`() {
    kotlin.run {
    val r =
        parse("[雪飘工作室][魔法つかいプリキュア!!~MIRAI DAYS~/Maho_Girls_Precure!!/魔法少女 光之美少女!!~未来的日子~][1080p][07][简繁外挂](检索:Q娃)")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[雪飘工作室][魔法つかいプリキュア!!~MIRAI DAYS~/Maho_Girls_Precure!!/魔法少女 光之美少女!!~未来的日子~][720p][07][简体内嵌](检索:Q娃)")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[雪飄工作室][魔法つかいプリキュア!!~MIRAI DAYS~/Maho_Girls_Precure!!/魔法少女 光之美少女!!~未來的日子~][720p][07][繁體內嵌](檢索:Q娃)")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕组】[青之驱魔师 终夜篇 / Ao no Exorcist _Yosuga Hen][08][简体][1080P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕組】[青之驅魔師 終夜篇 / Ao no Exorcist _Yosuga Hen][08][繁體][1080P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[TensoRaws] BanG Dream! Ave Mujica [08] [WEBRip 2160p HEVC-10bit AAC]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银色子弹字幕组][名侦探柯南][第1154集 屋久岛的山姬（后篇）][WEBRIP][简繁日多语MKV][1080P]")
    assertEquals("1154..1154", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银色子弹字幕组][名侦探柯南][第1154集 屋久島的山姬（後篇）][WEBRIP][繁日雙語MP4][1080P]")
    assertEquals("1154..1154", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银色子弹字幕组][名侦探柯南][第1154集 屋久岛的山姬（后篇）][WEBRIP][简日双语MP4][1080P]")
    assertEquals("1154..1154", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] S级怪兽《贝希摩斯》被误认成小猫，成为精灵女孩的骑士（宠物）一起生活 / Beheneko - 09 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `690031-690030-690029-690028-690027-690026-690025-690024-690023-690022`() {
    kotlin.run {
    val r = parse("[ANi] 在沖繩喜歡上的女孩方言講得太過令人困擾 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] Blue Exorcist / 青之驅魔師 終夜篇 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 药屋少女的呢喃 第二季 - 07 [CR WebRip AI2160p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 我獨自升級 第二季 -起於闇影- / Ore dake Level Up na Ken Season 2- 08 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 離開 A 級隊伍的我，和從前的弟子往迷宮深處邁進 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 我與尼特女忍者的莫名同居生活 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Solo Leveling S02 / 我獨自升級 第二季 －起於闇影－ - 20 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[天月搬運組] 吸吸吸吸吸血鬼 / Babanba Banban Vampire - 07 (NetFlix 1920x1080 AVC AAC MKV)")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("SAKAMOTO DAYS S01E08 Sakamotos vs the Lab 1080p NF WEB-DL DDP5.1 H 264 MULTi-VARYG (Multi-Audio, Multi-Subs)")
    assertEquals("264..264", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Strange-Raw] 坂本日常 第一季 / SAKAMOTO DAYS S01 [07] [Bilibili] [WEB-DL] [1080P AVC-8Bits AAC 2.0] [简日内嵌字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `690021-690020-690019-690018-690013-690012-690011-690010-690009-690008`() {
    kotlin.run {
    val r = parse("[ANi] UniteUp! 眾星齊聚 -Uni：Birth- - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 药屋少女的呢喃 第二季 - 07 [CR WebRip 1080p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]我的可爱对黑岩目高不管用 / Kuroiwa Medaka ni Watashi no Kawaii ga Tsuujinai [07] [webRip] [AVC-8bit 1080P AAC] [繁日内嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]我的可爱对黑岩目高不管用 / Kuroiwa Medaka ni Watashi no Kawaii ga Tsuujinai [07] [webRip] [AVC-8bit 1080P AAC] [简日内嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]中年男的异世界网购生活 / Arafou Otoko no Isekai Tsuuhan Seikatsu [06] [webRip] [AVC-8bit 1080P AAC] [繁日内嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語]美妙寵物 光之美少女！ / Wonderful Precure! - 42 [粵語+無字幕] (WEB 1920x1080 AVC AAC YUE)")
    assertEquals("42..42", r.episodeRange.toString())
    assertEquals("CHC", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]中年男的异世界网购生活 / Arafou Otoko no Isekai Tsuuhan Seikatsu [06] [webRip] [AVC-8bit 1080P AAC] [简日内嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] S級怪獸《貝希摩斯》被誤認成小貓，成為精靈女孩的騎士（寵物）一起生活 / Neko to Machigawarete Elf Musume no Pet toshite Kurashitemasu - 09 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[冷番补完字幕组][BanG Dream! Ave Mujica][2025][WEB][07][1080p][内封简繁中字]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户压制部] 新石纪 第四季 科学与未来 / Dr.STONE：Science Future [01] [1080p] [繁日内嵌] [2025年1月番]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `690007-690006-690005-690004-690003-690002-690001-690000-689999-689997`() {
    kotlin.run {
    val r = parse("[猎户压制部] 石纪元 第四季 科学与未来 / Dr.STONE：Science Future [01] [1080p] [简日内嵌] [2025年1月番]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 龙珠大魔 - 19 [CR WebRip AI2160p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 坂本日常 / SAKAMOTO DAYS [04] [1080p] [繁日内嵌] [2025年1月番]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] S 級怪獸《貝希摩斯》被誤認成小貓，成為精靈女孩的騎士（寵物）一起生活 - 09 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 坂本日常 / SAKAMOTO DAYS [04] [1080p] [简日内嵌] [2025年1月番]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 龙珠大魔 - 19 [CR WebRip 1080p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕組] 在沖繩喜歡上的女孩方言講得太過令人困擾 / Okinawa de Suki ni Natta Ko ga Hougen Sugite Tsura Sugiru [07][1080p][繁日內嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 喜欢的冲绳妹说方言 / Okinawa de Suki ni Natta Ko ga Hougen Sugite Tsura Sugiru [07][1080p][简日内嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 喜欢的冲绳妹说方言 / Okinawa de Suki ni Natta Ko ga Hougen Sugite Tsura Sugiru [07][1080p][简繁日内封]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 我和班上最讨厌的女生结婚了。 / クラスの大嫌いな女子と結婚することになった。 / Kurakon - 08 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `689995-689994-689993-689992-689991-689989-689988-689987-689986-689985`() {
    kotlin.run {
    val r =
        parse("[雪飄工作室 & WBX][汪分精彩光之美少女！/Wonderful_Precure！/わんだふるぷりきゅあ！][大電影！心動不已 在遊戲的世界大冒險][1080P][繁](检索:Q娃/プリキュア)")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 大叔转生恶役大小姐 / Akuyaku Reijou Tensei Ojisan - 07 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 不幸职业【鉴定士】实则最强 / Fuguushoku 'Kanteishi' ga Jitsu wa Saikyou Datta - 07 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 我的幸福婚姻 2期 / Watashi no Shiawase na Kekkon S2 - 07 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[斗破苍穹年番][第135集][国语中字][WEB-MKV][2160P]")
    assertEquals("135..135", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[雪飄工作室 & WBX][汪分精彩光之美少女！/Wonderful_Precure！/わんだふるぷりきゅあ！][大电影！·心动不已 在游戏的世界大冒险][720P][简](检索:Q娃/プリキュア)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] Blue Miburo / 青之壬生浪 - 19 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌Production&LoliHouse] BanG Dream! Ave Mujica - 08 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌Production&LoliHouse] BanG Dream! Ave Mujica - 07 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 异修罗 第二季 / Ishura S2 - 07 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `689984-689983-689982-689981-689980-689979-689976-689974-689973-689972`() {
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[我的幸福婚姻 2期 / Watashi no Shiawase na Kekkon S2][07][1080p][繁日雙語]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[我的幸福婚姻 2期 / Watashi no Shiawase na Kekkon S2][07][1080p][简日双语]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌Production】★01月新番★[BanG Dream! Ave Mujica][08][1080p][繁日雙語][招募翻譯]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌Production】★01月新番★[BanG Dream! Ave Mujica][07][1080p][繁日雙語][招募翻譯]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 群花绽放，仿如修罗 / Hana wa Saku Shura no Gotoku - 07 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 精灵来日 / 歡迎來到日本，妖精小姐。 / 日本へようこそエルフさん。 / Nihon e Youkoso Elf-san. - 07 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 雖然是公會的櫃檯小姐，但因為不想加班所以打算獨自討伐迷宮頭目 / Guild no Uketsukejou desu ga, Zangyou wa Iya nanode Boss wo Solo Toubatsu Shiyou to Omoimasu - 07 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GalaxyRailroad-888] 游戏王GO RUSH!! Yu-Gi-Oh! GO RUSH !! 145 720P [GB_简中]")
    assertEquals("145..145", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Skymoon-Raws] 龍珠大魔 / Dragonball DaiMa - 19 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[凡人修仙传][第131集][国语中字][WEB-MKV][2160P]")
    assertEquals("131..131", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689967-689965-689964-689963-689962-689961-689960-689959-689958-689957`() {
    kotlin.run {
    val r = parse("【极影字幕·毁片党】 这家公司里有我喜欢的人 第07集 GB_CN HEVC_opus 1080p")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[黄昏旅店 / Tasokare Hotel][08][1080p][简日双语][招募翻译]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬運][ANi] 我和班上最討厭的女生結婚了。 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕组】[药屋少女的呢喃（药师少女的独语）/ Kusuriya no Hitorigoto][31][简体][1080P][MP4]")
    assertEquals("31..31", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕組】[藥屋少女的呢喃（藥師少女的獨語）/ Kusuriya no Hitorigoto][31][繁體][1080P][MP4]")
    assertEquals("31..31", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[不当舔狗制作组] BanG Dream! Ave Mujica - 08 [CR WebRip AI2160p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[不当舔狗制作组] BanG Dream! Ave Mujica - 08 [CR WebRip 1080p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 凍牌~地下麻將鬥牌錄~ - 19 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[银色子弹字幕组][名侦探柯南][特别篇WOOO 迷侦探!?毛利小五郎&演员优木真央美的烦恼][WEBRIP][简日双语MP4/繁日雙語MP4/简繁日多语MKV][1080P]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银色子弹字幕组][名侦探柯南][第59集 首次跑腿杀人事件][WEBRIP][简日双语MP4/繁日雙語MP4/简繁日多语MKV][1080P]")
    assertEquals("59..59", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `689956-689955-689954-689953-689952-689951-689950-689949-689948-689947`() {
    kotlin.run {
    val r =
        parse("[DBD-Raws][噗妮露是可爱史莱姆/Puniru wa Kawaii Slime][01-06TV][1080P][BDRip][HEVC-10bit][FLACx2][MKV](ぷにるはかわいいスライム)")
    assertEquals("01..06", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻櫻字幕組】【10月新番】【龍珠DAIMA Dragon Ball DAIMA】【13v2】【BIG5_MP4】【1920X1080】")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻櫻字幕組】【10月新番】【龍珠DAIMA Dragon Ball DAIMA】【13v2】【BIG5_MP4】【1280X720】")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] Sorairo Utility / 一桿青空 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 全修。 / Zenshuu. [07][1080p][简繁内封]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕組] 全修。 / Zenshuu. [07][1080p][繁體內嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 全修。 / Zenshuu. [07][1080p][简体内嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[澄空学园&动漫国字幕组&LoliHouse] 灰色: 幻影扳机 / グリザイア: ファントムトリガー / Grisaia: Phantom Trigger - 07 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] 雖然是公會的櫃檯小姐，但因為不想加班所以打算獨自討伐迷宮頭目 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[澄空学园&动漫国字幕组&LoliHouse] 灰色: 幻影扳机 / グリザイア: ファントムトリガー / Grisaia: Phantom Trigger - 06 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `689946-689945-689944-689943-689942-689941-689940-689939-689938-689937`() {
    kotlin.run {
    val r =
        parse("[澄空学园&动漫国字幕组&LoliHouse] 灰色: 幻影扳机 / グリザイア: ファントムトリガー / Grisaia: Phantom Trigger - 05 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[群花綻放，彷如修羅 / Hana wa Saku Shura no Gotoku][07][1080p][繁日雙語][招募翻譯]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[群花绽放，彷如修罗 / Hana wa Saku Shura no Gotoku][07][1080p][简日双语][招募翻译]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[天月搬運組] 藥師少女的獨語 (藥屋少女的呢喃) / Kusuriya no Hitorigoto - 31v2 (NetFlix 1920x1080 AVC AAC MKV)")
    assertEquals("31..31", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[天月搬運組] 藥師少女的獨語 (藥屋少女的呢喃) / Kusuriya no Hitorigoto - 31 (NetFlix 1920x1080 AVC AAC MKV)")
    assertEquals("31..31", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【澄空学园&动漫国字幕组】★01月新番[灰色 : 幻影扳机][05-07][1080P][简体][MP4]")
    assertEquals("05..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【澄空學園&動漫國字幕組】★01月新番[灰色 : 幻影扳機][05-07][1080P][繁體][MP4]")
    assertEquals("05..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Amor压制组][药师少女的独语 藥師少女的獨語 Kusuriya no Hitorigoto][31][简繁内封][WEBDL][1080P][MKV]")
    assertEquals("31..31", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户压制部] 我独自升级 第二季 -起于暗影- / Ore dake Level Up na Ken S2 [19] [1080p] [繁日内嵌] [KoVer] [2025年1月番]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户压制部] 我独自升级 第二季 -起于暗影- / Ore dake Level Up na Ken S2 [19] [1080p] [简日内嵌] [KoVer] [2025年1月番]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `689936-689935-689934-689933-689932-689931-689930-689929-689928-689927`() {
    kotlin.run {
    val r =
        parse("[LoliHouse] 浪客剑心 -明治剑客浪漫谭- 京都动乱 / Rurouni Kenshin (2023) - 43 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("43..43", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[镇魂街 第4季][第12-15集][国语中字][WEB-MKV][2160P]")
    assertEquals("12..15", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 无名记忆 第二季 / Unnamed Memory Act.2 [19] [1080p] [繁日内嵌] [2025年1月番]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 无名记忆 第二季 / Unnamed Memory Act.2 [19] [1080p] [简日内嵌] [2025年1月番]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] Farmagia / FARMAGIA 魔農傳記 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] Tasokare Hotel / 黃昏旅店 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻櫻字幕組】【10月新番】【龍珠DAIMA Dragon Ball DAIMA】【18】【BIG5_MP4】【1920X1080】")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻樱字幕组】【10月新番】【龙珠DAIMA Dragon Ball DAIMA】【18】【GB_MP4】【1920X1080】")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[丸子家族][櫻桃小丸子第二期(Chibi Maruko-chan II)][1467]藤木教永澤溜冰&小丸子想在危機中利用智慧[2025.02.16][BIG5][1080P][MP4]")
    assertEquals("1467..1467", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻櫻字幕組】【10月新番】【龍珠DAIMA Dragon Ball DAIMA】【18】【BIG5_MP4】【1280X720】")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689926-689925-689924-689923-689922-689920-689919-689918-689917-689915`() {
    kotlin.run {
    val r = parse("【幻樱字幕组】【10月新番】【龙珠DAIMA Dragon Ball DAIMA】【18】【GB_MP4】【1280X720】")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[丸子家族][樱桃小丸子第二期(Chibi Maruko-chan II)][1467]藤木教永泽溜冰&小丸子想在危机中利用智慧[2025.02.16][GB][1080P][MP4]")
    assertEquals("1467..1467", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕·毀片黨】灰色：幻影扳機 第04集 BIG5 AVC_aac 816p")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Welcome to Japan Ms Elf / 歡迎來到日本，妖精小姐。 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[斗罗大陆2绝世唐门][第89集][国语中字][WEB-MKV][2160P]")
    assertEquals("89..89", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 不幸职业【鉴定士】实则最强 / Fuguushoku 'Kanteishi' ga Jitsu wa Saikyou Datta - 06 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 虽然我是注定没落的贵族，闲来无事只好来深究魔法 / Botsuraku Yotei no Kizoku dakedo, Hima Datta kara Mahou wo Kiwametemita - 07 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 无名记忆 Act.2 / 無名記憶 Act.2 / Unnamed Memory Act.2 - 19 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组&LoliHouse] 坂本日常 / SAKAMOTO DAYS - 07 [WebRip 1080p HEVC-10bit AACx2][简繁日内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[異修羅 第二季 / Ishura S2][07][1080p][繁日雙語][招募翻譯]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689914-689912-689911-689910-689909-689908-689907-689904-689902-689898`() {
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][TVB粵語]愛犬訊號 / Dog Signal - 08 [粵語+內封繁體中文字幕][WEB 1920x1080 AVC AAC SRT TVB YUE CHT]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[SweetSub][刹那之花][Momentary Lily][08][WebRip][1080P][AVC 8bit][简体内嵌]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 想星的大天使 Myth of Emotions / Sousei no Aquarion: Myth of Emotions - 07 [WebRip 1080p HEVC-10bit AAC][无字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 坂本日常 / SAKAMOTO DAYS [07][WebRip][HEVC_AAC][简日內嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 坂本日常 / SAKAMOTO DAYS [07][WebRip][HEVC_AAC][繁日內嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 坂本日常 / SAKAMOTO DAYS [07][WebRip][HEVC_AAC][简繁日內封]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] 卡片戰鬥!! 先導者 Divinez 第三季「DELUXE篇」 - 06 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Rev][DBD-Raws][魔法纪录 魔法少女小圆外传 最终季 浅梦的黎明/Magia Record Mahou Shoujo Madoka Magica Gaiden Final - Asaki Yume no Akatsuki][01-04TV全集][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV](マギアレコード 魔法少女まどか☆マギカ外伝 Final Season -浅き夢の暁-)")
    assertEquals("01..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][魔法纪录 魔法少女小圆外传 第二季 觉醒前夜/Magia Record Mahou Shoujo Madoka Magica Gaiden S2 - Kakusei Zen`ya][01-08TV全集+特典][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV](マギアレコード 魔法少女まどか☆マギカ外伝 2nd SEASON -覚醒前夜-)")
    assertEquals("01..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[芝士动物朋友] 石纪元 科学与未来 / Dr.STONE SCIENCE FUTURE [02][CR-WebRip][1080p][HEVC+AAC][简繁内封]（新石纪 第四季 S4）")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `689895-689894-689893-689892-689891-689890-689889-689888-689887-689886`() {
    kotlin.run {
    val r = parse("【今晚月色真美】[青之箱 / アオのハコ / Ao no Hako][21][WEBrip][1080P][简日双语]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌Production】★01月新番★[BanG Dream! Ave Mujica][08][1080p][简日双语][招募翻译]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[無名記憶 Act.2 / Unnamed Memory Act.2][19][1080p][繁日雙語][招募翻譯時軸]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[SweetSub&LoliHouse] 刹那之花 / Momentary Lily - 07 [WebRip 1080p HEVC-10bit AAC][简日内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Rurouni Kenshin / 神劍闖江湖 ―明治劍客浪漫譚― 京都動亂 - 19 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 中年大叔轉生反派千金 / Akuyaku Reijou Tensei Ojisan - 07 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[异修罗 第二季 / Ishura S2][07][1080p][简日双语][招募翻译]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Anyway Im Falling in Love with You / 終究，與你相戀。 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] TRILLION GAME / 一兆＄遊戲 - 20 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[完美世界][第203集][国语中字][WEB-MKV][2160P]")
    assertEquals("203..203", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689885-689884-689883-689882-689881-689880-689879-689878-689877-689876`() {
    kotlin.run {
    val r =
        parse("[ANi] Momentary Lily / MOMENTARY LILY 剎那之花 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 坂本日常 - 02 [NF WebRip AI2160p HEVC-10bit E-AC-3][日英双语音频 官方简繁内封字幕]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT, ENG, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id
        })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 中年大叔轉生反派千金 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 坂本日常 - 02 [NF WebRip 1080p HEVC-10bit E-AC-3][日英双语音频 官方简繁内封字幕]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT, ENG, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id
        })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&二次壓制][TVB粵語]愛犬訊號 / Dog Signal - 07 [粵語+內封繁體中文字幕][WEB 1920x1080 x264 AAC SRT TVB YUE CHT]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 天久鹰央的推理病历表 / Ameku Takao's Detective Karte - 07 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户压制部] 我独自升级 第二季 -起于暗影- / Ore dake Level Up na Ken S2 [19] [1080p] [繁日内嵌] [JaVer] [2025年1月番]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户压制部] 我独自升级 第二季 -起于暗影- / Ore dake Level Up na Ken S2 [19] [1080p] [简日内嵌] [JaVer] [2025年1月番]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 不幸職業【鑑定士】其實是最強的 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD制作组][4K(2160P)+HDR+1080P][BanG Dream! Ave Mujica][08][WEB-DL][简日双语内嵌][AAC][MKV]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `689875-689874-689871-689870-689869-689868-689867-689866-689865-689859`() {
    kotlin.run {
    val r =
        parse("[猎户压制部] 香格里拉·开拓异境～粪作猎手挑战神作 第二季 / Shangri-La Frontier S2 [44] [1080p] [繁日内嵌] [2024年10月番]")
    assertEquals("44..44", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户压制部] 香格里拉·开拓异境～粪作猎手挑战神作 第二季 / Shangri-La Frontier S2 [44] [1080p] [简日内嵌] [2024年10月番]")
    assertEquals("44..44", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[S1百综字幕组]BanG Dream! Ave Mujica [08][2160p][Bilibili][Web-DL][HDR][HEVC_AAC][简日双语]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Dr STONE S04 / Dr.STONE 新石紀 第四季 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] BanG Dream! Ave Mujica - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][魔法纪录 魔法少女小圆外传 第一季/Magia Record Mahou Shoujo Madoka Magica Gaiden S1][01-13TV全集][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV](マギアレコード 魔法少女まどか☆マギカ外伝 S1)")
    assertEquals("01..13", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[悠哈璃羽字幕社&LoliHouse] 战队红战士在异世界当冒险者 / Sentai Red Isekai de Boukensha ni Naru - 02 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[SweetSub][刹那之花][Momentary Lily][07][WebRip][1080P][AVC 8bit][简日内嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] Re：从零开始的异世界生活 第三季 - 11 [CR WebRip AI2160p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 迟早是最强的炼金术师？ / Izure Saikyou no Renkinjutsushi? - 08 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `689857-689856-689855-689854-689853-689852-689851-689849-689848-689847`() {
    kotlin.run {
    val r =
        parse("[DBD-Raws][剧场版 魔法科高中的劣等生 呼唤繁星的少女/Gekijouban Mahouka Koukou no Rettousei Hoshi wo Yobu Shoujo][正片+特典映像][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC+DTS][MKV](劇場版 魔法科高校の劣等生 星を呼ぶ少女)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][魔法科高中的劣等生 追忆篇/Mahouka Koukou no Rettousei Tsuioku Hen][正片+特典映像][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV](魔法科高校の劣等生 追憶編)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][魔法科高中的劣等生 来访者篇(第二季)/Mahouka Koukou no Rettousei Raihousha Hen(S2)][01-13TV全集+特典映像][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV](魔法科高校の劣等生 来訪者編(S2))")
    assertEquals("01..13", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[SweetSub][小市民系列][Shoushimin Series][01-10 精校合集][BDRip][1080P][AVC 8bit][繁日雙語]")
    assertEquals("01..10", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[SweetSub][小市民系列][Shoushimin Series][01-10 精校合集][BDRip][1080P][AVC 8bit][简日双语]")
    assertEquals("01..10", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】[嘻嘻嘻嘻吸血鬼][Babanba Banban Vampire][05][1080P][CHT]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】[嘻嘻嘻嘻吸血鬼][Babanba Banban Vampire][05][1080P][CHS]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][魔法科高中的劣等生 第一季/Mahouka Koukou no Rettousei S1][01-26TV全集+特典映像][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV](魔法科高校の劣等生 S1)")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】[嘻嘻嘻嘻吸血鬼][Babanba Banban Vampire][04][1080P][CHT]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】[嘻嘻嘻嘻吸血鬼][Babanba Banban Vampire][04][1080P][CHS]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689845-689842-689841-689840-689839-689838-689837-689836-689833-689832`() {
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&VCB-Studio] 间谍过家家 / 间谍家家酒 / SPYxFAMILY 10-bit 1080p HEVC BDRip [S1-S2 + MOVIE Fin]")
    assertEquals("S1+S2+S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] Re：从零开始的异世界生活 第三季 - 11 [CR WebRip 1080p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[无名记忆 Act.2 / Unnamed Memory Act.2][19][1080p][简日双语][招募翻译时轴]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] Re：從零開始的異世界生活 第三季 / Re：Zero kara Hajimeru Isekai Seikatsu 3rd Season - 11 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Ameku MD Doctor Detective / 天久鷹央的推理病歷表 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【悠哈璃羽字幕社】[戰隊紅戰士在異世界當冒險者_Sentai Red Isekai de Boukensha ni Naru][02][x264 1080p][CHT]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【悠哈璃羽字幕社】[战队红战士在异世界当冒险者_Sentai Red Isekai de Boukensha ni Naru][02][x264 1080p][CHS]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Honey Lemon Soda / 青春特調蜂蜜檸檬蘇打 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 坂本日常 - 01 [NF WebRip AI2160p HEVC-10bit E-AC-3][日英双语音频 官方简繁内封字幕]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT, ENG, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id
        })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 地。-关于地球的运动- / Chi. Chikyuu no Undou ni Tsuite [19][WebRip][HEVC_AAC][简繁日内封]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `689831-689830-689829-689828-689827-689826-689825-689824-689823-689822`() {
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 地。-关于地球的运动- / Chi. Chikyuu no Undou ni Tsuite [19][WebRip][HEVC_AAC][简日内嵌]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 地。-關於地球的運動- / Chi. Chikyuu no Undou ni Tsuite [19][WebRip][HEVC_AAC][繁日內嵌]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 青之箱 / 青春之箱 / 青春盒子 / 蓝箱 / Ao no Hako / Blue Box [18][WebRip][HEVC_AAC][简繁日内封]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 青之箱 / 蓝箱 / Ao no Hako / Blue Box [18][WebRip][HEVC_AAC][简日内嵌]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 青春之箱 / 青春盒子 / Ao no Hako / Blue Box [18][WebRip][HEVC_AAC][繁日內嵌]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[不当舔狗制作组] 坂本日常 - 01 [NF WebRip 1080p HEVC-10bit E-AC-3][日英双语音频 官方简繁内封字幕]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT, ENG, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id
        })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[jibaketa自載][代理商粵語]熊熊勇闖異世界Punch! [粵語+影片內字幕](WEB 1920x1080 AVC AAC YUE MUSE CHT)")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[jibaketa自載][代理商粵語]熊熊勇闖異世界 [粵語+影片內字幕](WEB 1920x1080 AVC AAC YUE MUSE CHT)")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[云光字幕组] 蜂蜜柠檬苏打水 Honey Lemon Soda [06][简体双语][1080p]招募翻译")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[悠哈璃羽字幕社&LoliHouse] 天久鹰央的推理病历表 / Ameku Takao's Detective Karte - 06 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `689821-689820-689819-689818-689817-689816-689815-689814-689813-689811`() {
    kotlin.run {
    val r =
        parse("[猎户手抄部] 红战士在异世界成了冒险者 / Sentai Red Isekai de Boukensha ni Naru [06] [1080p] [繁日内嵌] [2025年1月番]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户手抄部] 战队红战士在异世界当冒险者 / Sentai Red Isekai de Boukensha ni Naru [06] [1080p] [简日内嵌] [2025年1月番]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] Re：從零開始的異世界生活 第三季 - 11 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][剧场版 为美好的世界献上祝福！ 红传说/Kono Subarashii Sekai ni Shukufuku o! Kurenai Densetsu][1080P][BDRip][HEVC-10bit][简繁外挂][FLACx2][MKV](この素晴らしい世界に祝福を! 紅伝説)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][为美好的世界献上祝福！ 第二季/Kono Subarashii Sekai ni Shukufuku o! 2][01-10TV全集+OVA][1080P][BDRip][HEVC-10bit][简繁外挂][FLACx2][MKV](この素晴らしい世界に祝福を! 2)")
    assertEquals("01..10+OVA", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][为美好的世界献上祝福！ 第一季/Kono Subarashii Sekai ni Shukufuku o!][01-10TV全集+OVA+特典映像][1080P][BDRip][HEVC-10bit][简繁外挂][FLACx2][MKV](この素晴らしい世界に祝福を!)")
    assertEquals("01..10+OVA", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 遲早是最強的鍊金術師？ - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[神印王座][第146-147集][国语中字][WEB-MKV][2160P]")
    assertEquals("146..147", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 平凡上班族到异世界当上了四天王的故事 / Sararyman ga Isekai ni Ittara Shitennou ni Natta Hanashi - 08 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[夜莺家族][樱桃小丸子第二期(Chibi Maruko-chan II)][1467]藤木教永泽滑冰&小丸子机智应对危机[2025.02.16][GB_JP][1080P][MP4]")
    assertEquals("1467..1467", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689810-689809-689808-689807-689804-689801-689800-689799-689798-689797`() {
    kotlin.run {
    val r = parse("[風車字幕組十四周年][名偵探柯南][1142-1143][亂步邸殺人事件（前後篇）][1080P][繁體][MP4]")
    assertEquals("1142..1143", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[风车字幕组十四周年][名侦探柯南][1142-1143][乱步邸杀人事件（前后篇）][1080P][简体][MP4]")
    assertEquals("1142..1143", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[風車字幕組十四周年][名偵探柯南][1141][看家的毛利一家][1080P][繁體][MP4]")
    assertEquals("1141..1141", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[风车字幕组十四周年][名侦探柯南][1141][看家的毛利一家][1080P][简体][MP4]")
    assertEquals("1141..1141", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("Bocchi the Rock S01 孤獨搖滾 第一季 [1080p][Taiwanese Hokkien Dub][臺灣閩南語配音][Hàn-jī/Tâi-lô/Mandarin Hardsub][漢字/華語/臺羅字幕]")
    assertEquals("S1", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 这公司有我喜欢的人 / この会社に好きな人がいます / Kono Kaisha ni Suki na Hito ga Imasu - 06 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 鹿乃子乃子乃子虎视眈眈 / Shikanoko Nokonoko Koshitantan [12][WebRip][HEVC_AAC][简繁日内封]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 鹿乃子乃子乃子虎视眈眈 / Shikanoko Nokonoko Koshitantan [12][WebRip][HEVC_AAC][简日内嵌]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 鹿乃子乃子乃子虎視眈眈 / Shikanoko Nokonoko Koshitantan [12][WebRip][HEVC_AAC][繁日內嵌]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 鹿乃子乃子乃子虎视眈眈 / Shikanoko Nokonoko Koshitantan [11][WebRip][HEVC_AAC][简繁日内封]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `689796-689795-689792-689790-689789-689775-689774-689773-689772-689771`() {
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 鹿乃子乃子乃子虎视眈眈 / Shikanoko Nokonoko Koshitantan [11][WebRip][HEVC_AAC][简日内嵌]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 鹿乃子乃子乃子虎視眈眈 / Shikanoko Nokonoko Koshitantan [11][WebRip][HEVC_AAC][繁日內嵌]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 结缘甘神神社 / Amagami-san Chi no Enmusubi - 19 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 最弱技能《果实大师》 ～关于能无限食用技能果实（吃了就会死）这件事～ / Kinomi Master - 08 [WebRip 1080p HEVC-10bit AAC][无字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&VCB-Studio] 夏日重现 / Summer Time Rendering / サマータイムレンダ 10-bit 1080p HEVC BDRip [Reseed Fin]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Hana wa Saku Shura no Gotoku / 群花綻放、彷如修羅 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[黑白字幕组]全修。 / Zenshuu. [07] [webRip] [AVC-8bit 1080P AAC] [繁日内嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[黑白字幕组]全修。 / Zenshuu. [07] [webRip] [AVC-8bit 1080P AAC] [简日内嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 「只能触摸一分钟哦...」合租房的秘密规则。 / 「1 Funkan dake Furete mo Ii yo...」 Share House no Himitsu Rule. [06][1080p][简繁内封]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕組] 「只能觸摸一分鐘哦...」合租房的秘密規則。 / 「1 Funkan dake Furete mo Ii yo...」 Share House no Himitsu Rule. [06][1080p][繁體內嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `689770-689769-689768-689767-689766-689765-689764-689763-689762-689761`() {
    kotlin.run {
    val r =
        parse("[桜都字幕组] 「只能触摸一分钟哦...」合租房的秘密规则。 / 「1 Funkan dake Furete mo Ii yo...」 Share House no Himitsu Rule. [06][1080p][简体内嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社&西农YUI汉化组】[暗芝居 第十四季_Yami Shibai 14][07][x264 1080p][CHS]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社&西農YUI漢化組】[暗芝居 第十四季_Yami Shibai 14][07][x264 1080p][CHT]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社&西農YUI漢化組】[暗芝居 第十四季_Yami Shibai 14][06][x264 1080p][CHT]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社&西农YUI汉化组】[暗芝居 第十四季_Yami Shibai 14][06][x264 1080p][CHS]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Billion Meta Lab] 昙花一现的百合 Momentary Lily [06][1080P][HEVC 10bit][简繁日内封][检索：刹那之花]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Billion Meta Lab] 曇花一現的百合 Momentary Lily [06][1080P][繁日內嵌][檢索：剎那之花]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Billion Meta Lab] 昙花一现的百合 Momentary Lily [06][1080P][简日内嵌][检索：刹那之花]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户手抄部] 地。-关于地球的运动- / Chi. Chikyuu no Undou ni Tsuite [21] [1080p] [繁日内嵌] [2024年10月番]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户手抄部] 地。-关于地球的运动- / Chi. Chikyuu no Undou ni Tsuite [21] [1080p] [简日内嵌] [2024年10月番]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `689760-689759-689758-689755-689753-689749-689748-689747-689746-689745`() {
    kotlin.run {
    val r =
        parse("[ANi] Amagamisan Chi no Enmusubi / 結緣甘神神社 - 19 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 魔法使的约定 / Mahoutsukai no Yakusoku - 07 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Strange-Raw] POKÉTOON [17] [WEB-DL] [1080P AVC-8Bits AAC 2.0×2] [简体内嵌字幕]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[夜莺家族&YYQ字幕组]New Doraemon 哆啦A梦新番[850][2025.02.15][AVC][1080P][GB_JP]")
    assertEquals("850..850", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[云歌字幕组][1月新番][欢迎来到日本，精灵小姐][06][HEVC][x265 10bit][1080p][简日双语]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 妖怪學校的菜鳥老師 - 19 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫猫字幕组][1月新番][BanG Dream! Ave Mujica][05][1080p][MP4][简中]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫貓字幕組][1月新番][BanG Dream! Ave Mujica][05][1080p][MP4][繁中]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫猫字幕组][1月新番][BanG Dream! Ave Mujica][06][1080p][MP4][简中]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫貓字幕組][1月新番][BanG Dream! Ave Mujica][06][1080p][MP4][繁中]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689744-689743-689740-689727-689719-689718-689717-689716-689715-689714`() {
    kotlin.run {
    val r =
        parse("【幻櫻字幕組】【1月新番】【魔法製造者 ~異世界魔法的製作方法~ Magic Maker ~Isekai Mahou no Tsukurikata~】【06】【BIG5_MP4】【1920X1080】")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【幻樱字幕组】【1月新番】【魔法制造者 ~异世界魔法的制作方法~ Magic Maker ~Isekai Mahou no Tsukurikata~】【06】【GB_MP4】【1920X1080】")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[遮天][第97集][国语中字][WEB-MKV][2160P]")
    assertEquals("97..97", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[冷番补完字幕组][BanG Dream! Ave Mujica][2025][WEB][06][1080p][内封简繁中字]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 大叔转生恶役大小姐 / Akuyaku Reijou Tensei Ojisan - 06 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 大叔转生恶役大小姐 / Akuyaku Reijou Tensei Ojisan - 05 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 大叔转生恶役大小姐 / Akuyaku Reijou Tensei Ojisan - 04 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 大叔转生恶役大小姐 / Akuyaku Reijou Tensei Ojisan - 03 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 大叔转生恶役大小姐 / Akuyaku Reijou Tensei Ojisan - 02 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]v2")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 大叔转生恶役大小姐 / Akuyaku Reijou Tensei Ojisan - 01 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]v2")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `689711-689709-689707-689706-689703-689702-689701-689700-689699-689698`() {
    kotlin.run {
    val r =
        parse("[DBD-Raws][4K_HDR][超时空要塞 可曾记得爱/The Super Dimension Fortress Macross - Do You Remember Love?][完全版+院线版][2160P][UHDBDRip][HEVC-10bit][简繁日双语外挂][FLAC][MKV](Choujikuu Yousai Macross: Ai Oboete Imasuka/超時空要塞マクロス 愛・おぼえていますか)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][HOY粵語]寶可夢 地平線 / Pocket Monsters (2023) - 35 [粵語+內封繁體中文字幕](WEB 1920x1080 AVC AAC PGS+SRT iCABLE YUE CHT)")
    assertEquals("35..35", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[诸神字幕组][群花绽放、彷如修罗][Hana wa Saku, Shura no Gotoku][01][TVRip][简繁日语字幕][1080P][HEVC MKV]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[诸神字幕组][群花绽放、彷如修罗][Hana wa Saku, Shura no Gotoku][01][TVRip][简日双语字幕][1080P][CHS MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[VCB-Studio] 转生为第七王子，随心所欲的魔法学习之路 / Tensei shitara Dainana Ouji Datta node, Kimama ni Majutsu wo Kiwamemasu / 転生したら第七王子だったので、気ままに魔術を極めます 10-bit 1080p HEVC BDRip [Fin]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组&LoliHouse] 坂本日常 / SAKAMOTO DAYS - 06 [WebRip 1080p HEVC-10bit AACx2][简繁日内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 黑岩目高不把我的可爱放在眼里 / Kuroiwa Medaka ni Watashi no Kawaii ga Tsuujinai - 07 [WebRip 1080p HEVC-10bit AAC][无字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组&LoliHouse] 地。-关于地球的运动- / Chi. Chikyuu no Undou ni Tsuite 18 [WebRip 1080p HEVC-10bit AAC ASSx2][简繁日内封字幕]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组&LoliHouse] 地。-关于地球的运动- / Chi. Chikyuu no Undou ni Tsuite 17 [WebRip 1080p HEVC-10bit AAC ASSx2][简繁日内封字幕]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬運][ANi] 平凡職業造就世界最強 第三季 - 16 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689697-689696-689695-689694-689693-689692-689691-689690-689686-689685`() {
    kotlin.run {
    val r =
        parse("[搬運][ANi] Promise of Wizard / 魔法使的約定 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬運][ANi] 平凡上班族到異世界當上了四天王的故事 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[搬運][ANi] I Have a Crush at Work / 這公司有我喜歡的人 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[搬運][ANi] Neko ni Tensei Shita Ojisan / 轉生成貓咪的大叔 - 19 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻櫻字幕組】【10月新番】【龍珠DAIMA Dragon Ball DAIMA】【17】【BIG5_MP4】【1920X1080】")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻樱字幕组】【10月新番】【龙珠DAIMA Dragon Ball DAIMA】【17】【GB_MP4】【1920X1080】")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻櫻字幕組】【10月新番】【龍珠DAIMA Dragon Ball DAIMA】【17】【BIG5_MP4】【1280X720】")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻樱字幕组】【10月新番】【龙珠DAIMA Dragon Ball DAIMA】【17】【GB_MP4】【1280X720】")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[霜庭云花Sub][欢迎来到日本 妖精小姐 / 日本へようこそエルフさん。 / Nihon e Youkoso Elf-san.][03][1080P][AVC AAC][简日双语][WebRip]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[霜庭云花Sub][欢迎来到日本 妖精小姐 / 日本へようこそエルフさん。 / Nihon e Youkoso Elf-san.][04][1080P][AVC AAC][简日双语][WebRip]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689684-689683-689682-689681-689680-689678-689677-689676-689675-689674`() {
    kotlin.run {
    val r = parse("[霜庭雲花Sub][BanG Dream! Ave Mujica][06][1080P][AVC AAC][繁日雙語][WebRip]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[霜庭云花Sub][BanG Dream! Ave Mujica][06][1080P][AVC AAC][简日双语][WebRip]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕組] 群花綻放、彷如修羅 / Hana wa Saku, Shura no Gotoku [06][1080p][繁體內嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 群花绽放、彷如修罗 / Hana wa Saku, Shura no Gotoku [06][1080p][简体内嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 群花绽放、彷如修罗 / Hana wa Saku, Shura no Gotoku [06][1080p][简繁内封]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[萌樱字幕组] BanG Dream! Ave Mujica [06][1080p]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] BanG Dream! Ave Mujica / 颂乐人偶 [05][WebRip][HEVC_AAC][简繁日内封]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] BanG Dream! Ave Mujica / 頌樂人偶 [05][WebRip][HEVC_AAC][繁日內嵌]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] BanG Dream! Ave Mujica / 颂乐人偶 [05][WebRip][HEVC_AAC][简日内嵌]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 药屋少女的呢喃 / 药屋少女的独语 / Kusuriya no Hitorigoto [30][WebRip][HEVC_AAC][简繁日内封]")
    assertEquals("30..30", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `689673-689672-689671-689670-689669-689668-689666-689665-689664-689663`() {
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 药屋少女的呢喃 / 药屋少女的独语 / Kusuriya no Hitorigoto [30][WebRip][HEVC_AAC][简日内嵌]")
    assertEquals("30..30", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 藥屋少女的呢喃 / 藥屋少女的獨語 / Kusuriya no Hitorigoto [30][WebRip][HEVC_AAC][繁日內嵌]")
    assertEquals("30..30", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 「只能触摸一分钟哦...」合租房的秘密规则。 / 「1 Funkan dake Furete mo Ii yo...」 Share House no Himitsu Rule. [05][1080p][简繁内封]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕組] 「只能觸摸一分鐘哦...」合租房的秘密規則。 / 「1 Funkan dake Furete mo Ii yo...」 Share House no Himitsu Rule. [05][1080p][繁體內嵌]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 「只能触摸一分钟哦...」合租房的秘密规则。 / 「1 Funkan dake Furete mo Ii yo...」 Share House no Himitsu Rule. [05][1080p][简体内嵌]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[轻之国度字幕组][恋语轻唱/恰如细语般的恋歌][01-12][720P][MP4]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[漫猫字幕社&猫恋汉化组][1月新番][药屋少女的呢喃][Kusuriya no Hitorigoto][30][1080P][MP4][GB&JP][简日双语]")
    assertEquals("30..30", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[漫貓字幕社&貓戀漢化組][1月新番][藥師少女的獨語][Kusuriya no Hitorigoto][30][1080P][MP4][BIG5&JP][繁日雙語]")
    assertEquals("30..30", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[吞噬星空][第158-159集][国语中字][WEB-MKV][2160P]")
    assertEquals("158..159", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】[嘻嘻嘻嘻吸血鬼][Babanba Banban Vampire][03][1080P][CHT]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689662-689661-689660-689659-689655-689654-689653-689652-689651-689650`() {
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】[嘻嘻嘻嘻吸血鬼][Babanba Banban Vampire][03][1080P][CHS]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 我和班上最讨厌的女生结婚了。 / Class no Daikirai na Joshi to Kekkon Suru Koto ni Natta. [07][1080p][简繁内封]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕組] 我和班上最討厭的女生結婚了。 / Class no Daikirai na Joshi to Kekkon Suru Koto ni Natta. [07][1080p][繁體內嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 我和班上最讨厌的女生结婚了。 / Class no Daikirai na Joshi to Kekkon Suru Koto ni Natta. [07][1080p][简体内嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】[天久鷹央的推理病歷表_Ameku Takao no Suiri Karte][06][1080p][CHT]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】[天久鹰央的推理病历表_Ameku Takao no Suiri Karte][06][1080p][CHS]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[NEO·QSW]想星的阿克艾利昂 情感神话 想星のアクエリオン Aquarion: Myth of Emotions 05[WEBRIP AVC 1080P]（搜索用：想星的大天使）")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 灰色：幻影扳机 / Grisaia：Phantom Trigger [07][1080p][简繁内封]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕組] 灰色：幻影扳機 / Grisaia：Phantom Trigger [07][1080p][繁體內嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 灰色：幻影扳机 / Grisaia：Phantom Trigger [07][1080p][简体内嵌]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `689649-689648-689647-689646-689645-689644-689643-689642-689641-689640`() {
    kotlin.run {
    val r = parse("[桜都字幕组] 灰色：幻影扳机 / Grisaia：Phantom Trigger [06][1080p][简繁内封]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕組] 灰色：幻影扳機 / Grisaia：Phantom Trigger [06][1080p][繁體內嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[NEO·QSW]想星的阿克艾利昂 情感神话 想星のアクエリオン Aquarion: Myth of Emotions 04[WEBRIP AVC 1080P]（搜索用：想星的大天使）")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 灰色：幻影扳机 / Grisaia：Phantom Trigger [06][1080p][简体内嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 灰色：幻影扳机 / Grisaia：Phantom Trigger [05][1080p][简繁内封]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕組] 灰色：幻影扳機 / Grisaia：Phantom Trigger [05][1080p][繁體內嵌]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 灰色：幻影扳机 / Grisaia：Phantom Trigger [05][1080p][简体内嵌]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[VCB-Studio] 异世界失格 / Isekai Shikkaku / 異世界失格 10-bit 1080p HEVC BDRip [Fin]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][星铳士俾斯麦/Sei Juushi Bismarck][01-51TV全集][美版/USA.Ver][1080P][BDRip][HEVC-10bit][FLAC][MKV](星銃士 ビスマルク/Star Musketeer Bismark)")
    assertEquals("01..51", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[萌樱字幕组] 药屋少女的呢喃 [29][1080p][简繁日内封]")
    assertEquals("29..29", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `689638-689637-689636-689635-689634-689633-689632-689631-689630-689629`() {
    kotlin.run {
    val r = parse("[搬運][ANi] 金肉人 完美超人始祖篇 Season 2 - 17 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬運][ANi] 超超超超超喜歡你的 100 個女朋友 - 18 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬運][ANi] 我想蹺掉太子妃培訓 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[搬運][ANi] Mashin Creator Wataru / 魔神創造傳 - 06 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[搬運][ANi] ToiletBound Hanakokun S02 / 地縛少年花子君 第二季 - 06 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬運][ANi] 香格里拉・開拓異境～糞作獵手挑戰神作～ 第二季 - 19 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[新沸羊羊制作] S级怪兽《贝希摩斯》被误认成小猫，成为精灵女孩的骑士(宠物)一起生活 - 08 [AMZN WebRip AI2160p HEVC-10bit E-AC-3][官方简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 超超超超超喜歡你的100個女朋友 / Hyakkano - 18 [ViuTV][WEB-RIP][1080p][AVC AAC][CHT][SRT][MKV]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 我想蹺掉太子妃培訓 / Kisaki Kyouiku kara Nigetai Watashi - 07 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 魔神創造傳 / Mashin Souzouden Wataru - 06 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689628-689627-689626-689625-689624-689622-689619-689618-689617-689616`() {
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 香格里拉·開拓異境～糞作獵手挑戰神作～第2季 / Shangri-La Frontier 2nd Season - 19 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★10月新番★[凍牌 / Touhai: Ura Rate Mahjong Touhai Roku][18][1080p][繁體][招募翻譯]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★10月新番★[冻牌 / Touhai: Ura Rate Mahjong Touhai Roku][18][1080p][简体][招募翻译]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【FSD粉羽社】魔法使光之美少女！！～MIRAI DAYS～[05][1080P][简体中字]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[新沸羊羊制作] 我独自升级 第二季 －起于暗影－ - 07 [CR WebRip AI2160p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語]隊長小翼第二季 青少年篇 / 世少篇 / Captain Tsubasa Season 2 - Junior Youth Hen - 20 (WEB 1920x1080 AVC AACx2 SRT ViuTV CHT)")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[喵萌奶茶屋&LoliHouse] 金牌得主 / Medalist - 07 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[豌豆字幕组&LoliHouse] 青之驱魔师 终夜篇 / Ao no Exorcist Yosuga-hen - 07 [WebRip 1080p HEVC-10bit AAC][简繁外挂字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[金牌得主 / Medalist][07][1080p][繁日雙語][招募翻譯]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[金牌得主 / Medalist][07][1080p][简日双语][招募翻译]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689615-689614-689613-689612-689611-689610-689609-689608-689607-689606`() {
    kotlin.run {
    val r = parse("【豌豆字幕組】[青之驅魔師 終夜篇 / Ao no Exorcist _Yosuga Hen][07v2][繁體][1080P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕组】[青之驱魔师 终夜篇 / Ao no Exorcist _Yosuga Hen][07v2][简体][1080P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 坂本日常 / SAKAMOTO DAYS [06][WebRip][HEVC_AAC][简繁日內封]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 坂本日常 / SAKAMOTO DAYS [06][WebRip][HEVC_AAC][简日內嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 坂本日常 / SAKAMOTO DAYS [06][WebRip][HEVC_AAC][繁日內嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户手抄部] 神剑闯江湖 -明治剑客浪漫谭- 京都动乱 / Rurouni Kenshin：Meiji Kenkaku Romantan - Kyoto Douran [42] [1080p] [繁日内嵌] [2024年10月番]")
    assertEquals("42..42", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户手抄部] 浪客剑心 -明治剑客浪漫谭- 京都动乱 / Rurouni Kenshin: Meiji Kenkaku Romantan - Kyoto Douran [42] [1080p] [简日内嵌] [2024年10月番]")
    assertEquals("42..42", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Strange-Raw] 战队红战士在异世界当冒险者 第一季 / Sentai Red Isekai de Boukensha ni Naru S01 [06] [Bilibili] [WEB-DL] [1080P AVC-8Bits AAC 2.0] [简日内嵌字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 青之箱 / 青春之箱 / 青春盒子 / 蓝箱 / Ao no Hako / Blue Box [17][WebRip][HEVC_AAC][简繁日内封]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 青春之箱 / 青春盒子 / Ao no Hako / Blue Box [17][WebRip][HEVC_AAC][繁日內嵌]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `689605-689604-689603-689602-689599-689598-689597-689596-689595-689593`() {
    kotlin.run {
    val r = parse("[北宇治字幕组] 青之箱 / 蓝箱 / Ao no Hako / Blue Box [17][WebRip][HEVC_AAC][简日内嵌]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【SW字幕组】[宠物小精灵 / 宝可梦 地平线 烈空坐之升龙崛起][77-83][简日双语字幕][1080P][AVC][MP4][CHS_JP]")
    assertEquals("77..83", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[哆啦字幕组][哆啦A梦大山版附映中篇 Doraemon1979][2000.3.11][1080P][WEB][简日&繁日][奶奶的回忆][数码修复版][MKV]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] S级怪兽《贝希摩斯》被误认成小猫，成为精灵女孩的骑士（宠物）一起生活 / Beheneko - 08 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[MingY] 劇場總集篇 孤獨搖滾！ Re: / Bocchi the Rock! Re [劇場版][BDRip][1080p][HEVC OPUS][繁日內嵌]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[MingY] 剧场总集篇 孤独摇滚！ Re: / Bocchi the Rock! Re [剧场版][BDRip][1080p][HEVC OPUS][简日内嵌]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[雪飄工作室][ひみつのアイプリ/Himitsu_no_Aipri/秘密的美妙公主][45][简繁外挂](检索:/美妙旋律/美妙天堂/美妙頻道/美妙魔法)")
    assertEquals("45..45", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[雪飄工作室][ひみつのアイプリ/Himitsu_no_Aipri/秘密的美妙公主][720P][45][繁](检索:/美妙旋律/美妙天堂/美妙頻道/美妙魔法)")
    assertEquals("45..45", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] Shangri-La Frontier / 香格里拉·开拓异境～粪作猎手挑战神作～ - 44 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("44..44", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][仙逆][Renegade Immortal][2023][76][AVC][GB][1080P]")
    assertEquals("76..76", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689592-689591-689590-689589-689588-689586-689585-689584-689583-689582`() {
    kotlin.run {
    val r =
        parse("[LoliHouse] 不幸职业【鉴定士】实则最强 / Fuguushoku 'Kanteishi' ga Jitsu wa Saikyou Datta - 05 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 不幸职业【鉴定士】实则最强 / Fuguushoku 'Kanteishi' ga Jitsu wa Saikyou Datta - 04 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 不幸职业【鉴定士】实则最强 / Fuguushoku 'Kanteishi' ga Jitsu wa Saikyou Datta - 03 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 虽然我是注定没落的贵族，闲来无事只好来深究魔法 / Botsuraku Yotei no Kizoku dakedo, Hima Datta kara Mahou wo Kiwametemita - 05 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][TVB粵語]Let's Camp!露營少女/搖曳露營/Yuru Camp Season 3 - 04 [粵日雙語+內封繁體中文字幕][BD 1920x1080 x264 AACx2 SRT TVB CHT]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[仙逆][第76集][国语中字][WEB-MKV][2160P]")
    assertEquals("76..76", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][长生界][The Eternal Realm][2024][17][HEVC][GB][4K]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][长生界][The Eternal Realm][2024][16][HEVC][GB][4K]")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][长生界][The Eternal Realm][2024][15][HEVC][GB][4K]")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[VCB-Studio] 空战魔导士候补生的教官 / Kuusen Madoushi Kouhosei no Kyoukan / 空戦魔導士候補生の教官 10-bit 1080p HEVC BDRip [Fin]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689581-689579-689578-689577-689576-689575-689574-689573-689572-689571`() {
    kotlin.run {
    val r =
        parse("[DBD-Raws][为美好的世界献上祝福！ 第三季/Kono Subarashii Sekai ni Shukufuku o! 3][01-11TV全集+特典映像][1080P][BDRip][HEVC-10bit][简繁外挂][FLACx2][MKV](この素晴らしい世界に祝福を! 3)")
    assertEquals("01..11", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][吞噬星空][Swallowed Star][2021][158][AVC][GB][1080P]")
    assertEquals("158..158", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][吞噬星空][Swallowed Star][2021][157][AVC][GB][1080P]")
    assertEquals("157..157", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][吞噬星空][Swallowed Star][2021][156][AVC][GB][1080P]")
    assertEquals("156..156", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][仙逆][Renegade Immortal][2023][75][AVC][GB][1080P]")
    assertEquals("75..75", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][仙逆][Renegade Immortal][2023][74][AVC][GB][1080P]")
    assertEquals("74..74", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GM-Team][国漫][仙逆][Renegade Immortal][2023][73][AVC][GB][1080P]")
    assertEquals("73..73", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 离开A级队伍的我，和从前的弟子往迷宫深处迈进 / Aparida - 06 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 我与尼特女忍者的莫名同居生活 / NEET Kunoichi - 07 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][超时空要塞 可曾记得爱/The Super Dimension Fortress Macross - Do You Remember Love?][完全版+院线版][1080P][BDRip][HEVC-10bit][简繁日双语外挂][FLAC][MKV](Choujikuu Yousai Macross: Ai Oboete Imasuka/超時空要塞マクロス 愛・おぼえていますか)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `689568-689567-689566-689563-689561-689560-689559-689557-689556-689555`() {
    kotlin.run {
    val r =
        parse("[雪飘工作室][キミとアイドルプリキュア♪/You and Idol Precure♪/与你同为 偶像光之美少女♪][1080p][03][简繁外挂](检索:Q娃)")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[雪飘工作室][キミとアイドルプリキュア♪/You and Idol Precure♪/与你同为 偶像光之美少女♪][720p][03][简体内嵌](检索:Q娃)")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[雪飄工作室][キミとアイドルプリキュア♪/You and Idol Precure♪/与你同为 偶像光之美少女♪][720p][03][繁體內嵌](檢索:Q娃)")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 我独自升级 -起于暗影- / Ore dake Level Up na Ken / Solo Leveling - 19 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 黄昏旅店 / Tasokare Hotel - 07 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 黄昏旅店 / Tasokare Hotel - 06 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 黄昏旅店 / Tasokare Hotel - 05 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬運][ANi] 在沖繩喜歡上的女孩方言講得太過令人困擾 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[搬運][ANi] Blue Exorcist / 青之驅魔師 終夜篇 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[搬運][ANi] 離開 A 級隊伍的我，和從前的弟子往迷宮深處邁進 - 06 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689554-689553-689552-689551-689550-689549-689548-689547-689546-689545`() {
    kotlin.run {
    val r = parse("[搬運][ANi] 我與尼特女忍者的莫名同居生活 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[搬運][ANi] Solo Leveling S02 / 我獨自升級 第二季 －起於闇影－ - 19 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[搬運][ANi] UniteUp! 眾星齊聚 -Uni：Birth- - 06 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[搬運][ANi] S 級怪獸《貝希摩斯》被誤認成小貓，成為精靈女孩的騎士（寵物）一起生活 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬運][ANi] Blue Miburo / 青之壬生浪 - 18 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[雪飘工作室][魔法つかいプリキュア!!~MIRAI DAYS~/Maho_Girls_Precure!!/魔法少女 光之美少女!!~未来的日子~][1080p][06][简繁外挂](检索:Q娃)")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[雪飘工作室][魔法つかいプリキュア!!~MIRAI DAYS~/Maho_Girls_Precure!!/魔法少女 光之美少女!!~未来的日子~][720p][06][简体内嵌](检索:Q娃)")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[雪飄工作室][魔法つかいプリキュア!!~MIRAI DAYS~/Maho_Girls_Precure!!/魔法少女 光之美少女!!~未來的日子~][720p][06][繁體內嵌](檢索:Q娃)")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&澄空学园&华盟字幕社&千夏字幕组】★10月新番[冻牌][18][1080P][简体][MP4]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&澄空學園&華盟字幕社&千夏字幕組】★10月新番[凍牌][18][1080P][繁體][MP4]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689542-689541-689540-689539-689538-689537-689536-689535-689534-689533`() {
    kotlin.run {
    val r =
        parse("【喵萌奶茶屋】★10月新番★[Chi。-關於地球的運動- / Chi. Chikyuu no Undou ni Tsuite][19][1080p][繁日雙語][招募翻譯]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【喵萌奶茶屋】★10月新番★[Chi。-關於地球的運動- / Chi. Chikyuu no Undou ni Tsuite][18][1080p][繁日雙語][招募翻譯]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【喵萌奶茶屋】★10月新番★[Chi。-關於地球的運動- / Chi. Chikyuu no Undou ni Tsuite][17][1080p][繁日雙語][招募翻譯]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【喵萌奶茶屋】★10月新番★[Chi。-關於地球的運動- / Chi. Chikyuu no Undou ni Tsuite][16][1080p][繁日雙語][招募翻譯]")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银色子弹字幕组][名侦探柯南][第1153集 屋久岛的山姬（前篇）][WEBRIP][简繁日多语MKV][1080P]")
    assertEquals("1153..1153", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银色子弹字幕组][名侦探柯南][第1153集 屋久島的山姬（前篇）][WEBRIP][繁日雙語MP4][1080P]")
    assertEquals("1153..1153", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银色子弹字幕组][名侦探柯南][第1153集 屋久岛的山姬（前篇）][WEBRIP][简日双语MP4][1080P]")
    assertEquals("1153..1153", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[黃昏旅店 / Tasokare Hotel][07][1080p][繁日雙語][招募翻譯]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[黃昏旅店 / Tasokare Hotel][06][1080p][繁日雙語][招募翻譯]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[黃昏旅店 / Tasokare Hotel][05][1080p][繁日雙語][招募翻譯]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689532-689531-689528-689527-689526-689525-689523-689520-689519-689518`() {
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 我獨自升級 第二季 -起於闇影- / Ore dake Level Up na Ken Season 2- 07 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[天月搬運組] 吸吸吸吸吸血鬼 / Babanba Banban Vampire - 06 (NetFlix 1920x1080 AVC AAC MKV)")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("SAKAMOTO DAYS S01E07 Jurassic Bastard 1080p NF WEB-DL DDP5.1 H 264 MULTi-VARYG (Multi-Audio, Multi-Subs)")
    assertEquals("264..264", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]我的可爱对黑岩目高不管用 / Kuroiwa Medaka ni Watashi no Kawaii ga Tsuujinai [06] [webRip] [AVC-8bit 1080P AAC] [繁日内嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]我的可爱对黑岩目高不管用 / Kuroiwa Medaka ni Watashi no Kawaii ga Tsuujinai [06] [webRip] [AVC-8bit 1080P AAC] [简日内嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] S級怪獸《貝希摩斯》被誤認成小貓，成為精靈女孩的騎士（寵物）一起生活 / Neko to Machigawarete Elf Musume no Pet toshite Kurashitemasu - 08 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][TVB粵語]新幹線戰士 改變世界 / 新幹線變形機器人 變革世代 / Shinkalion Change the World - 29 [粵語+內封繁體中文字幕][WEB 1920x1080 AVC AAC SRT TVB YUE CHT]")
    assertEquals("29..29", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[新沸羊羊制作] 龙珠大魔 - 18 [CR WebRip AI2160p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DBFC字幕组】【龙珠 大魔】【018】【中日双语字幕】【1080P】【MP4】")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Strange-Raw] 坂本日常 第一季 / SAKAMOTO DAYS S01 [06] [Bilibili] [WEB-DL] [1080P AVC-8Bits AAC 2.0] [简日内嵌字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `689517-689514-689513-689512-689511-689510-689509-689508-689507-689506`() {
    kotlin.run {
    val r =
        parse("[LoliHouse] 我和班上最讨厌的女生结婚了。 / クラスの大嫌いな女子と結婚することになった。 / Kurakon - 07 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 精灵来日 / 歡迎來到日本，妖精小姐。 / 日本へようこそエルフさん。 / Nihon e Youkoso Elf-san. - 06 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[新沸羊羊制作] 药师少女的独语 第二季 - 06 [CR WebRip AI2160p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[風車字幕組][名偵探柯南][1140][女子推理社3][1080P][繁體][MP4]")
    assertEquals("1140..1140", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[风车字幕组][名侦探柯南][1140][女子推理社3][1080P][简体][MP4]")
    assertEquals("1140..1140", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕·毁片党】 这家公司里有我喜欢的人 第06集 GB_CN HEVC_opus 1080p")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[斗破苍穹年番][第134集][国语中字][WEB-MKV][2160P]")
    assertEquals("134..134", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語]美妙寵物 光之美少女！ / Wonderful Precure! - 41 [粵語+無字幕] (WEB 1920x1080 AVC AAC YUE)")
    assertEquals("41..41", r.episodeRange.toString())
    assertEquals("CHC", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[H-Enc] 义妹生活 / Gimai Seikatsu (BDRip 1080p HEVC FLAC)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 迟早是最强的炼金术师？ / Izure Saikyou no Renkinjutsushi? - 07 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `689505-689504-689503-689501-689500-689497-689496-689495-689494-689493`() {
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 异修罗 第二季 / Ishura S2 - 06 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【百冬練習組】【Re: 從零開始的異世界的生活_Re꞉ Zero kara Hajimeru Isekai Seikatsu】[60][WebRip 1080p AVC AAC][繁體]")
    assertEquals("60..60", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【百冬练习组】【Re: 从零开始的异世界的生活_Re꞉ Zero kara Hajimeru Isekai Seikatsu】[60][WebRip 1080p AVC AAC][简体]")
    assertEquals("60..60", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[VCB-Studio] 圣剑使的禁咒咏唱 / Seiken Tsukai no World Break / 聖剣使いの禁呪詠唱 10-bit 1080p HEVC BDRip [Fin]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][小市民系列/Shoushimin Series][01-10TV全集+特典映像][1080P][BDRip][HEVC-10bit][简繁日双语外挂][FLAC][MKV](小市民シリーズ)")
    assertEquals("01..10", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 无名记忆 Act.2 / 無名記憶 Act.2 / Unnamed Memory Act.2 - 18 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 龙珠 大魔 / Dragon Ball Daima - 18 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[蜂蜜檸檬蘇打 / Honey Lemon Soda][04][1080p][繁體]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[蜂蜜柠檬苏打 / Honey Lemon Soda][04][1080p][简体]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[蜂蜜檸檬蘇打 / Honey Lemon Soda][03][1080p][繁體]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689492-689491-689490-689489-689488-689487-689486-689485-689484-689483`() {
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[蜂蜜柠檬苏打 / Honey Lemon Soda][03][1080p][简体]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[凡人修仙传][第130集][国语中字][WEB-MKV][2160P]")
    assertEquals("130..130", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【喵萌奶茶屋】★10月新番★[Chi。-关于地球的运动- / Chi. Chikyuu no Undou ni Tsuite][19][1080p][简日双语][招募翻译]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【喵萌奶茶屋】★10月新番★[Chi。-关于地球的运动- / Chi. Chikyuu no Undou ni Tsuite][18][1080p][简日双语][招募翻译]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[黄昏旅店 / Tasokare Hotel][07][1080p][简日双语][招募翻译]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 终究、与你相恋 / Douse, Koishite Shimaunda - 06 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕组】[药屋少女的呢喃（药师少女的独语）/ Kusuriya no Hitorigoto][30][简体][1080P][MP4]")
    assertEquals("30..30", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕組】[藥屋少女的呢喃（藥師少女的獨語）/ Kusuriya no Hitorigoto][30][繁體][1080P][MP4]")
    assertEquals("30..30", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬運][ANi] 凍牌~地下麻將鬥牌錄~ - 18 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬運][ANi] Sorairo Utility / 一桿青空 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689482-689481-689480-689479-689478-689477-689476-689470-689468-689467`() {
    kotlin.run {
    val r =
        parse("[搬運][ANi] 雖然是公會的櫃檯小姐，但因為不想加班所以打算獨自討伐迷宮頭目 - 06 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬運][ANi] 我和班上最討厭的女生結婚了。 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[搬運][ANi] Farmagia / FARMAGIA 魔農傳記 - 06 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬運][ANi] Tasokare Hotel / 黃昏旅店 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[搬運][ANi] Welcome to Japan Ms Elf / 歡迎來到日本，妖精小姐。 - 06 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[搬運][ANi] 卡片戰鬥!! 先導者 Divinez 第三季「DELUXE篇」 - 05 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银色子弹字幕组][名侦探柯南][第57-58集 福尔摩斯迷杀人事件][WEBRIP][简日双语MP4/繁日雙語MP4/简繁日多语MKV][1080P]")
    assertEquals("57..58", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Skymoon-Raws] 龍珠大魔 / Dragonball DaiMa - 18 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 想星的大天使 Myth of Emotions / Sousei no Aquarion: Myth of Emotions - 06 [WebRip 1080p HEVC-10bit AAC][无字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 雖然是公會的櫃檯小姐，但因為不想加班所以打算獨自討伐迷宮頭目 / Guild no Uketsukejou desu ga, Zangyou wa Iya nanode Boss wo Solo Toubatsu Shiyou to Omoimasu - 06 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689466-689465-689464-689463-689462-689461-689460-689459-689458-689457`() {
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[無名記憶 Act.2 / Unnamed Memory Act.2][18][1080p][繁日雙語][招募翻譯時軸]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[无名记忆 Act.2 / Unnamed Memory Act.2][18][1080p][简日双语][招募翻译时轴]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 在地下城寻求邂逅是否搞错了什么 第五季 / Dungeon ni Deai o Motomeru no wa Machigatte Iru Darouka： Familia Myth V [13][1080p][繁体内嵌]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 在地下城寻求邂逅是否搞错了什么 第五季 / Dungeon ni Deai o Motomeru no wa Machigatte Iru Darouka： Familia Myth V [13][1080p][简体内嵌]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 在地下城寻求邂逅是否搞错了什么 第五季 / Dungeon ni Deai o Motomeru no wa Machigatte Iru Darouka： Familia Myth V [13][1080p][简繁内封]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 在地下城寻求邂逅是否搞错了什么 第五季 / Dungeon ni Deai o Motomeru no wa Machigatte Iru Darouka： Familia Myth V [12][1080p][繁体内嵌]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 在地下城寻求邂逅是否搞错了什么 第五季 / Dungeon ni Deai o Motomeru no wa Machigatte Iru Darouka： Familia Myth V [12][1080p][简体内嵌]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 在地下城寻求邂逅是否搞错了什么 第五季 / Dungeon ni Deai o Motomeru no wa Machigatte Iru Darouka： Familia Myth V [12][1080p][简繁内封]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[天月搬運組] 藥師少女的獨語 (藥屋少女的呢喃) / Kusuriya no Hitorigoto - 30v2 (NetFlix 1920x1080 AVC AAC MKV)")
    assertEquals("30..30", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成][代理商粵語]膽大黨 / Dandadan - 08 [粵日雙語+內封繁體中文字幕](WEB 1920x1080 AVC AACx2 SRT MUSE CHT)")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `689456-689455-689454-689453-689452-689451-689450-689449-689448-689447`() {
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 群花绽放，仿如修罗 / Hana wa Saku Shura no Gotoku - 06 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[云光字幕组] 蜂蜜柠檬苏打水 Honey Lemon Soda [05][简体双语][1080p]招募翻译")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户压制部] 我独自升级 第二季 -起于暗影- / Ore dake Level Up na Ken S2 [18v2] [1080p] [繁日内嵌] [JaVer] [2025年1月番]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[群花綻放，彷如修羅 / Hana wa Saku Shura no Gotoku][06][1080p][繁日雙語][招募翻譯]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[群花绽放，彷如修罗 / Hana wa Saku Shura no Gotoku][06][1080p][简日双语][招募翻译]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户压制部] 我独自升级 第二季 -起于暗影- / Ore dake Level Up na Ken S2 [18v2] [1080p] [简日内嵌] [JaVer] [2025年1月番]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户压制部] 我独自升级 第二季 -起于暗影- / Ore dake Level Up na Ken S2 [18] [1080p] [简日内嵌] [KoVer] [2025年1月番]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[天月搬運組] 藥師少女的獨語 (藥屋少女的呢喃) / Kusuriya no Hitorigoto - 30 (NetFlix 1920x1080 AVC AAC MKV)")
    assertEquals("30..30", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Amor压制组][药师少女的独语 藥師少女的獨語 Kusuriya no Hitorigoto][30][简繁内封][WEBDL][1080P][MKV]")
    assertEquals("30..30", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[異修羅 第二季 / Ishura S2][06][1080p][繁日雙語][招募翻譯]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689445-689444-689443-689442-689441-689440-689439-689438-689437-689436`() {
    kotlin.run {
    val r =
        parse("[LoliHouse] 浪客剑心 -明治剑客浪漫谭- 京都动乱 / Rurouni Kenshin (2023) - 42 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("42..42", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[梦蓝字幕组]New Doraemon 哆啦A梦新番[849][2025.02.08][AVC][1080P][GB_JP][MP4]")
    assertEquals("849..849", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 群花绽放、彷如修罗 / Hana wa Saku, Shura no Gotoku [05][1080p][简繁内封]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 群花绽放、彷如修罗 / Hana wa Saku, Shura no Gotoku [05][1080p][简体内嵌]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕組] 群花綻放、彷如修羅 / Hana wa Saku, Shura no Gotoku [05][1080p][繁體內嵌]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 喜欢的冲绳妹说方言 / Okinawa de Suki ni Natta Ko ga Hougen Sugite Tsura Sugiru [06][1080p][简繁日内封]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 喜欢的冲绳妹说方言 / Okinawa de Suki ni Natta Ko ga Hougen Sugite Tsura Sugiru [06][1080p][简日内嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕組] 在沖繩喜歡上的女孩方言講得太過令人困擾 / Okinawa de Suki ni Natta Ko ga Hougen Sugite Tsura Sugiru [06][1080p][繁日內嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]魔术发明家 ~异世界魔法的制作方法~ / Magic Maker: Isekai Mahou no Tsukurikata [06] [webRip] [AVC-8bit 1080P AAC] [繁日内嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]魔术发明家 ~异世界魔法的制作方法~ / Magic Maker: Isekai Mahou no Tsukurikata [06] [webRip] [AVC-8bit 1080P AAC] [简日内嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `689435-689434-689433-689432-689431-689430-689429-689428-689427-689426`() {
    kotlin.run {
    val r =
        parse("[DBD-Raws][异世界悠闲农家/Isekai Nonbiri Nouka][01-12TV全集+特典映像][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV](異世界のんびり農家)")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 无名记忆 第二季 / Unnamed Memory Act.2 [18] [1080p] [繁日内嵌] [2025年1月番]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 无名记忆 第二季 / Unnamed Memory Act.2 [18] [1080p] [简日内嵌] [2025年1月番]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 全修。 / Zenshuu. [06][1080p][简体内嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕組] 全修。 / Zenshuu. [06][1080p][繁體內嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 全修。 / Zenshuu. [06][1080p][简繁内封]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 我和班上最讨厌的女生结婚了。 / Class no Daikirai na Joshi to Kekkon Suru Koto ni Natta. [06][1080p][简体内嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕組] 我和班上最討厭的女生結婚了。 / Class no Daikirai na Joshi to Kekkon Suru Koto ni Natta. [06][1080p][繁體內嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕组] 我和班上最讨厌的女生结婚了。 / Class no Daikirai na Joshi to Kekkon Suru Koto ni Natta. [06][1080p][简繁内封]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[斗罗大陆2绝世唐门][第88集][国语中字][WEB-MKV][2160P]")
    assertEquals("88..88", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689424-689423-689421-689418-689417-689416-689412-689409-689408-689407`() {
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][TVB粵語]愛犬訊號 / Dog Signal - 06 [粵語+內封繁體中文字幕][WEB 1920x1080 AVC AAC SRT TVB YUE CHT]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][前辈是男孩子/Senpai wa Otokonoko][01-12TV全集][1080P][BDRip][HEVC-10bit][简繁日双语外挂][FLAC][MKV](先輩はおとこのこ)")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][魔法科高中的劣等生 第三季/Mahouka Koukou no Rettousei S3][01-13TV全集][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV](魔法科高校の劣等生 第3シーズン)")
    assertEquals("01..13", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]中年男的异世界网购生活 / Arafou Otoko no Isekai Tsuuhan Seikatsu [05] [webRip] [AVC-8bit 1080P AAC] [繁日内嵌]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[黑白字幕组]中年男的异世界网购生活 / Arafou Otoko no Isekai Tsuuhan Seikatsu [05] [webRip] [AVC-8bit 1080P AAC] [简日内嵌]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][孤独摇滚！剧场总集篇/Gekijou Soushuuhen Bocchi the Rock!][正片+SP][1080P][BDRip][HEVC-10bit][FLAC+AC3][MKV](劇場総集編ぼっち・ざ・ろっく!)")
    assertEquals("SP..SP", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[新沸羊羊制作] Bang Dream Ave Mujica - 07 [CR WebRip AI2160p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[TensoRaws] BanG Dream! Ave Mujica [07] [WEBRip 2160p HEVC-10bit AAC]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【今晚月色真美】[青之箱 / アオのハコ / Ao no Hako][20][WEBrip][1080P][简日双语]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[搬運][ANi] Rurouni Kenshin / 神劍闖江湖 ―明治劍客浪漫譚― 京都動亂 - 18 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689406-689405-689404-689403-689402-689401-689400-689393-689392-689391`() {
    kotlin.run {
    val r =
        parse("[搬運][ANi] Anyway Im Falling in Love with You / 終究，與你相戀。 - 06 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬運][ANi] TRILLION GAME / 一兆＄遊戲 - 19 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[搬運][ANi] Momentary Lily / MOMENTARY LILY 剎那之花 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬運][ANi] 中年大叔轉生反派千金 - 06 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬運][ANi] 不幸職業【鑑定士】其實是最強的 - 06 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[搬運][ANi] Dr STONE S04 / Dr.STONE 新石紀 第四季 - 06 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬運][ANi] BanG Dream! Ave Mujica - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 中年大叔轉生反派千金 / Akuyaku Reijou Tensei Ojisan - 06 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[异修罗 第二季 / Ishura S2][06][1080p][简日双语][招募翻译]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌Production】★01月新番★[BanG Dream! Ave Mujica][07][1080p][简日双语][招募翻译]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689390-689389-689387-689386-689385-689384-689383-689382-689381-689380`() {
    kotlin.run {
    val r = parse("【极影字幕·毁片党】灰色：幻影扳机 第06集 GB_CN HEVC_opus 1080p")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][TVB粵語]愛犬訊號 / Dog Signal - 05 [粵語+內封繁體中文字幕][WEB 1920x1080 AVC AAC SRT TVB YUE CHT]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD制作组][4K(2160P)+HDR+1080P][BanG Dream! Ave Mujica][07][WEB-DL][简日双语内嵌][AAC][MKV]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[S1百综字幕组]BanG Dream! Ave Mujica [07][2160p][Bilibili][Web-DL][HDR][HEVC_AAC][简日双语]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 我的幸福婚姻 2期 / Watashi no Shiawase na Kekkon S2 - 06 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][机动战士高达ZZ/Mobile Suit Gundam ZZ][01-47TV全集+特典映像][1080P][BDRip][HEVC-10bit][简繁内封][FLAC][MKV](Kidou Senshi Gundam ZZ/機動戦士ガンダムZZ)")
    assertEquals("01..47", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[完美世界][第202集][国语中字][WEB-MKV][2160P]")
    assertEquals("202..202", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[晚街与灯][灼眼的夏娜 第一季_Shakugan no Shana I][04][BDRip_1080P_x264_AAC][简日内嵌]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[晚街與燈][灼眼的夏娜 第一季_Shakugan no Shana I][04][BDRip_1080P_x264_AAC][繁日內嵌]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[新沸羊羊制作] Re：从零开始的异世界生活 第三季 - 10V2 [CR WebRip AI2160p HEVC-10bit AAC][官方简繁内封字幕]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `689377-689364-689363-689362-689361-689360-689358-689357-689356-689355`() {
    kotlin.run {
    val r =
        parse("[DBD-Raws][入间同学入魔了！ 第二季/Mairimashita! Iruma-kun S2][01-21TV全集][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV](魔入りました！入間くん 第2シーズン)")
    assertEquals("01..21", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("OVA I''s Pure.2005.DVD720P.日语中字")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&喵萌奶茶屋&LoliHouse] 超超超超超喜歡你的100個女朋友 / Hyakkano - 15 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[搬運][ANi] Honey Lemon Soda / 青春特調蜂蜜檸檬蘇打 - 06 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬運][ANi] Re：從零開始的異世界生活 第三季 - 10 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬運][ANi] 遲早是最強的鍊金術師？ - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[GalaxyRailroad-888] 游戏王GO RUSH!! Yu-Gi-Oh! GO RUSH !! 144 720P [GB_简中]")
    assertEquals("144..144", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[我的幸福婚姻 2期 / Watashi no Shiawase na Kekkon S2][06][1080p][繁日雙語]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★01月新番★[我的幸福婚姻 2期 / Watashi no Shiawase na Kekkon S2][06][1080p][简日双语]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Billion Meta Lab] 曇花一現的百合 Momentary Lily [05][1080P][繁日內嵌][檢索：剎那之花]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `689354-689353-689351-689350-689349-689348-689347-689346-689345-689344`() {
    kotlin.run {
    val r =
        parse("[Billion Meta Lab] 昙花一现的百合 Momentary Lily [05][1080P][HEVC 10bit][简繁日内封][检索：刹那之花]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Billion Meta Lab] 昙花一现的百合 Momentary Lily [05][1080P][简日内嵌][检索：刹那之花]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][HOY粵語]寶可夢 地平線 / Pocket Monsters (2023) - 34 [粵語+內封繁體中文字幕](WEB 1920x1080 AVC AAC PGS+SRT iCABLE YUE CHT)")
    assertEquals("34..34", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户压制部] 我独自升级 第二季 -起于暗影- / Ore dake Level Up na Ken S2 [18] [1080p] [繁日内嵌] [JaVer] [2025年1月番]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户压制部] 我独自升级 第二季 -起于暗影- / Ore dake Level Up na Ken S2 [18] [1080p] [简日内嵌] [JaVer] [2025年1月番]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] Re：從零開始的異世界生活 第三季 / Re：Zero kara Hajimeru Isekai Seikatsu 3rd Season - 10 [ViuTV][WEB-DL][1080p][AVC AAC]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户手抄部] 红战士在异世界成了冒险者 / Sentai Red Isekai de Boukensha ni Naru [05] [1080p] [繁日内嵌] [2025年1月番]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户手抄部] 战队红战士在异世界当冒险者 / Sentai Red Isekai de Boukensha ni Naru [05] [1080p] [简日内嵌] [2025年1月番]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户压制部] 香格里拉·开拓异境～粪作猎手挑战神作 第二季 / Shangri-La Frontier S2 [43] [1080p] [繁日内嵌] [2024年10月番]")
    assertEquals("43..43", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户压制部] 香格里拉·开拓异境～粪作猎手挑战神作 第二季 / Shangri-La Frontier S2 [43] [1080p] [简日内嵌] [2024年10月番]")
    assertEquals("43..43", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `689343-689342-689341-689340-689339-689338-689337-689336-689335-689334`() {
    kotlin.run {
    val r =
        parse("[LoliHouse] 魔法使的约定 / Mahoutsukai no Yakusoku - 06 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 虽然我是注定没落的贵族，闲来无事只好来深究魔法 / Botsuraku Yotei no Kizoku dakedo, Hima Datta kara Mahou wo Kiwametemita - 06 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[漫貓字幕社&貓戀漢化組][1月新番][藥師少女的獨語][Kusuriya no Hitorigoto][29][1080P][MP4][BIG5&JP][繁日雙語]")
    assertEquals("29..29", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[漫猫字幕社&猫恋汉化组][1月新番][药屋少女的呢喃][Kusuriya no Hitorigoto][29][1080P][MP4][GB&JP][简日双语]")
    assertEquals("29..29", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("神之塔 Tower of God S02E26 1080p CR WEB-DL AAC2.0 H 264 DUAL-VARYG (多國語含中文) 第二季完")
    assertEquals("264..264", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("神之塔 Tower of God S02E25 1080p CR WEB-DL AAC2.0 H 264 DUAL-VARYG (多國語含中文)")
    assertEquals("264..264", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("神之塔 Tower of God S02E24 1080p CR WEB-DL AAC2.0 H 264 DUAL-VARYG (多國語含中文)")
    assertEquals("264..264", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("神之塔 Tower of God S02E23 1080p CR WEB-DL AAC2.0 H 264 DUAL-VARYG (多國語含中文)")
    assertEquals("264..264", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[梦蓝字幕组]Crayonshinchan 蜡笔小新[1270][2025.02.08][AVC][1080P][GB_JP][MP4]")
    assertEquals("1270..1270", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[梦蓝字幕组]Crayonshinchan 蜡笔小新[1269][2025.02.01][AVC][1080P][GB_JP][MP4]")
    assertEquals("1269..1269", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `689333-689332-689331-689330-689329-689325-689324-689323-689322-689321`() {
    kotlin.run {
    val r = parse("[梦蓝字幕组]New Doraemon 哆啦A梦新番[848][2025.02.01][AVC][1080P][GB_JP][MP4]")
    assertEquals("848..848", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 中年男的异世界网购生活 / Around 40 Otoko no Isekai Tsuuhan - 04 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 平凡上班族到异世界当上了四天王的故事 / Sararyman ga Isekai ni Ittara Shitennou ni Natta Hanashi - 07 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[萌樱字幕组] 药屋少女的呢喃 [28][1080p][简繁日内封]")
    assertEquals("28..28", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[夜莺家族][樱桃小丸子第二期(Chibi Maruko-chan II)][1466]富子想赢躲避球比赛&小丸子擅长安慰他人[2025.02.09][GB_JP][1080P][MP4]")
    assertEquals("1466..1466", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&VCB-Studio] 恋爱FLOPS / LOVE FLOPS / Renai Flops / 恋愛フロップス 10-bit 1080p HEVC BDRip [Fin]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 最弱技能《果实大师》 ～关于能无限食用技能果实（吃了就会死）这件事～ / Kinomi Master - 07 [WebRip 1080p HEVC-10bit AAC][无字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 结缘甘神神社 / Amagami-san Chi no Enmusubi - 18 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[新沸羊羊制作] S级怪兽《贝希摩斯》被误认成小猫，成为精灵女孩的骑士(宠物)一起生活 - 07 [AMZN WebRip AI2160p HEVC-10bit E-AC-3][官方简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[搬運][ANi] Hana wa Saku Shura no Gotoku / 群花綻放、彷如修羅 - 06 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }
}

// @formatter:on
