/*
 * Copyright (C) 2024 OpenAni and contributors.
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
 * 原名: `怪人的沙拉碗`
 * 数据源: `dmhy`
 *
 * 由 `test-codegen` 的 `GenerateTests.kt` 生成, 不要手动修改!
 * 如果你优化了解析器, 这些 test 可能会失败, 请检查是否它是因为以前解析错误而现在解析正确了. 
 * 如果是, 请更新测试数据: 执行 `GenerateTests.kt`.
 */
public class PatternTitleParserTest怪人的沙拉碗 : PatternBasedTitleParserTestSuite() {
  @Test
  public fun `671495_ANi_Henjin_no_Salad_Bowl___10_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    val r =
        parse("[ANi] Henjin no Salad Bowl / 怪人的沙拉碗 - 10 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `671468_LoliHouse_Henjin_no_Salad_Bowl___09_WebRip_1080p_HEVC_10bit_AAC`() {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 怪人的沙拉碗 / Henjin no Salad Bowl - 09 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `671434_04_Henjin_no_Salad_Bowl_09_1080p`() {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][09][1080p][简体][招募翻译时轴]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `671433_04_Henjin_no_Salad_Bowl_09_1080p`() {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][09][1080p][繁體][招募翻譯時軸]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `671061_Lilith_Raws_Henjin_no_Salad_Bowl___09_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    val r =
        parse("[Lilith-Raws] 怪人的沙拉碗 / Henjin no Salad Bowl - 09 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `671027_ANi_Henjin_no_Salad_Bowl___09_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    val r =
        parse("[ANi] Henjin no Salad Bowl / 怪人的沙拉碗 - 09 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `670845_LoliHouse_Henjin_no_Salad_Bowl___08_WebRip_1080p_HEVC_10bit_AAC`() {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 怪人的沙拉碗 / Henjin no Salad Bowl - 08 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `670826_04_Henjin_no_Salad_Bowl_08_1080p`() {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][08][1080p][繁體][招募翻譯時軸]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `670825_04_Henjin_no_Salad_Bowl_08_1080p`() {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][08][1080p][简体][招募翻译时轴]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `670568_Lilith_Raws_Henjin_no_Salad_Bowl___08_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    val r =
        parse("[Lilith-Raws] 怪人的沙拉碗 / Henjin no Salad Bowl - 08 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `670477_ANi_Henjin_no_Salad_Bowl___08_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    val r =
        parse("[ANi] Henjin no Salad Bowl / 怪人的沙拉碗 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `670251_LoliHouse_Henjin_no_Salad_Bowl___07_WebRip_1080p_HEVC_10bit_AAC`() {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 怪人的沙拉碗 / Henjin no Salad Bowl - 07 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `670247_04_Henjin_no_Salad_Bowl_07_1080p`() {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][07][1080p][繁體][招募翻譯時軸]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `670246_04_Henjin_no_Salad_Bowl_07_1080p`() {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][07][1080p][简体][招募翻译时轴]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `670182_Lilith_Raws_Henjin_no_Salad_Bowl___07_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    val r =
        parse("[Lilith-Raws] 怪人的沙拉碗 / Henjin no Salad Bowl - 07 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `670138_Lilith_Raws_Henjin_no_Salad_Bowl___06_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    val r =
        parse("[Lilith-Raws] 怪人的沙拉碗 / Henjin no Salad Bowl - 06 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669913_ANi_Henjin_no_Salad_Bowl___07_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    val r =
        parse("[ANi] Henjin no Salad Bowl / 怪人的沙拉碗 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669798_LoliHouse_Henjin_no_Salad_Bowl___06_WebRip_1080p_HEVC_10bit_AAC`() {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 怪人的沙拉碗 / Henjin no Salad Bowl - 06 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `669793_04_Henjin_no_Salad_Bowl_06_1080p`() {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][06][1080p][繁體][招募翻譯時軸]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669792_04_Henjin_no_Salad_Bowl_06_1080p`() {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][06][1080p][简体][招募翻译时轴]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669195_ANi_Henjin_no_Salad_Bowl___06_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    val r =
        parse("[ANi] Henjin no Salad Bowl / 怪人的沙拉碗 - 06 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668956_04_Henjin_no_Salad_Bowl_05_1080p`() {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][05][1080p][繁體][招募翻譯時軸]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668955_04_Henjin_no_Salad_Bowl_05_1080p`() {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][05][1080p][简体][招募翻译时轴]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668954_LoliHouse_Henjin_no_Salad_Bowl___05_WebRip_1080p_HEVC_10bit_AAC`() {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 怪人的沙拉碗 / Henjin no Salad Bowl - 05 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `668714_Lilith_Raws_Henjin_no_Salad_Bowl___05_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    val r =
        parse("[Lilith-Raws] 怪人的沙拉碗 / Henjin no Salad Bowl - 05 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668652_Lilith_Raws_Henjin_no_Salad_Bowl___04_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    val r =
        parse("[Lilith-Raws] 怪人的沙拉碗 / Henjin no Salad Bowl - 04 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668632_ANi_Henjin_no_Salad_Bowl___05_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    val r =
        parse("[ANi] Henjin no Salad Bowl / 怪人的沙拉碗 - 05 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668468_LoliHouse_Henjin_no_Salad_Bowl___04_WebRip_1080p_HEVC_10bit_AAC`() {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 怪人的沙拉碗 / Henjin no Salad Bowl - 04 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `668441_04_Henjin_no_Salad_Bowl_04_1080p`() {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][04][1080p][繁體][招募翻譯時軸]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668440_04_Henjin_no_Salad_Bowl_04_1080p`() {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][04][1080p][简体][招募翻译时轴]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668193_ANi_Henjin_no_Salad_Bowl___04_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    val r =
        parse("[ANi] Henjin no Salad Bowl / 怪人的沙拉碗 - 04 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667889_LoliHouse_Henjin_no_Salad_Bowl___03_WebRip_1080p_HEVC_10bit_AAC`() {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 怪人的沙拉碗 / Henjin no Salad Bowl - 03 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `667882_04_Henjin_no_Salad_Bowl_03_1080p`() {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][03][1080p][繁體][招募翻譯時軸]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667881_04_Henjin_no_Salad_Bowl_03_1080p`() {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][03][1080p][简体][招募翻译时轴]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667659_Lilith_Raws_Henjin_no_Salad_Bowl___03_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    val r =
        parse("[Lilith-Raws] 怪人的沙拉碗 / Henjin no Salad Bowl - 03 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667591_ANi_Henjin_no_Salad_Bowl___03_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    val r =
        parse("[ANi] Henjin no Salad Bowl / 怪人的沙拉碗 - 03 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667371_LoliHouse_Henjin_no_Salad_Bowl___02_WebRip_1080p_HEVC_10bit_AAC`() {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 怪人的沙拉碗 / Henjin no Salad Bowl - 02 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `667369_04_Henjin_no_Salad_Bowl_02_1080p`() {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][02][1080p][繁體][招募翻譯時軸]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667368_04_Henjin_no_Salad_Bowl_02_1080p`() {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][02][1080p][简体][招募翻译时轴]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667045_Lilith_Raws_Henjin_no_Salad_Bowl___02_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    val r =
        parse("[Lilith-Raws] 怪人的沙拉碗 / Henjin no Salad Bowl - 02 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `666934_ANi_Henjin_no_Salad_Bowl___02_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    val r =
        parse("[ANi] Henjin no Salad Bowl / 怪人的沙拉碗 - 02 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `666569_LoliHouse_Henjin_no_Salad_Bowl___01_WebRip_1080p_HEVC_10bit_AAC`() {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 怪人的沙拉碗 / Henjin no Salad Bowl - 01 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `666374_04_Henjin_no_Salad_Bowl_01_1080p`() {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][01][1080p][繁體][招募翻譯時軸]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `666373_04_Henjin_no_Salad_Bowl_01_1080p`() {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][01][1080p][简体][招募翻译时轴]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `666169_Lilith_Raws_Henjin_no_Salad_Bowl___01_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    val r =
        parse("[Lilith-Raws] 怪人的沙拉碗 / Henjin no Salad Bowl - 01 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `666130_ANi_Henjin_no_Salad_Bowl___01_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    val r =
        parse("[ANi] Henjin no Salad Bowl / 怪人的沙拉碗 - 01 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }
}

// @formatter:on
