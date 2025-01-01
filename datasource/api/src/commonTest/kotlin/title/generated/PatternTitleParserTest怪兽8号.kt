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
 * 原名: `怪兽8号`
 * 数据源: `dmhy`
 *
 * 由 `test-codegen` 的 `GenerateTests.kt` 生成, 不要手动修改!
 * 如果你优化了解析器, 这些 test 可能会失败, 请检查是否它是因为以前解析错误而现在解析正确了. 
 * 如果是, 请更新测试数据: 执行 `GenerateTests.kt`.
 */
public class PatternTitleParserTest怪兽8号 : PatternBasedTitleParserTestSuite() {
  @Test
  public fun `670353_8_Kaijuu_8_Gou_06_1080p_2024_4`() {
    val r = parse("[猎户压制部] 怪兽8号 / Kaijuu 8 Gou [06] [1080p] [繁日内嵌] [2024年4月番]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `670352_8_Kaijuu_8_Gou_06_1080p_2024_4`() {
    val r = parse("[猎户压制部] 怪兽8号 / Kaijuu 8 Gou [06] [1080p] [简日内嵌] [2024年4月番]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `670191_Lilith_Raws_8_Kaijuu_8_gou___06_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    val r = parse("[Lilith-Raws] 怪獸 8 號 / Kaijuu 8-gou - 06 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `670150_Lilith_Raws_8_Kaijuu_8_gou___05_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    val r = parse("[Lilith-Raws] 怪獸 8 號 / Kaijuu 8-gou - 05 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `670109_Lilith_Raws_8_Kaijuu_8_gou___04_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    val r = parse("[Lilith-Raws] 怪獸 8 號 / Kaijuu 8-gou - 04 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `670034_LoliHouse_8_Kaijuu_8_gou___06_WebRip_1080p_HEVC_10bit_AAC`() {
    val r = parse("[LoliHouse] 怪兽8号 / Kaijuu 8-gou - 06 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `670030_ANi_Kaijuu_8_gou_8___06_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    val r = parse("[ANi] Kaijuu 8 gou / 怪獸 8 號 - 06 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669747_8_Kaijuu_8_Gou_05_1080p_2024_4`() {
    val r = parse("[猎户压制部] 怪兽8号 / Kaijuu 8 Gou [05] [1080p] [繁日内嵌] [2024年4月番]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `669746_8_Kaijuu_8_Gou_05_1080p_2024_4`() {
    val r = parse("[猎户压制部] 怪兽8号 / Kaijuu 8 Gou [05 [1080p] [简日内嵌] [2024年4月番]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `669385_LoliHouse_8_Kaijuu_8_gou___05_WebRip_1080p_HEVC_10bit_AAC`() {
    val r = parse("[LoliHouse] 怪兽8号 / Kaijuu 8-gou - 05 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `669303_ANi_Kaijuu_8_gou_8___05_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    val r = parse("[ANi] Kaijuu 8 gou / 怪獸 8 號 - 05 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669104_8_Kaijuu_8_Gou_04_1080p_2024_4`() {
    val r = parse("[猎户压制部] 怪兽8号 / Kaijuu 8 Gou [04] [1080p] [繁日内嵌] [2024年4月番]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `669103_8_Kaijuu_8_Gou_04_1080p_2024_4`() {
    val r = parse("[猎户压制部] 怪兽8号 / Kaijuu 8 Gou [04] [1080p] [简日内嵌] [2024年4月番]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `668821_LoliHouse_8_Kaijuu_8_gou___04_WebRip_1080p_HEVC_10bit_AAC`() {
    val r = parse("[LoliHouse] 怪兽8号 / Kaijuu 8-gou - 04 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `668808_ANi_Kaijuu_8_gou_8___04_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    val r = parse("[ANi] Kaijuu 8 gou / 怪獸 8 號 - 04 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668666_Lilith_Raws_8_Kaijuu_8_gou___03_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    val r = parse("[Lilith-Raws] 怪獸 8 號 / Kaijuu 8-gou - 03 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668476_8_Kaijuu_8_Gou_03_1080p_2024_4`() {
    val r = parse("[猎户压制部] 怪兽8号 / Kaijuu 8 Gou [03] [1080p] [繁日内嵌] [2024年4月番]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `668475_8_Kaijuu_8_Gou_03_1080p_2024_4`() {
    val r = parse("[猎户压制部] 怪兽8号 / Kaijuu 8 Gou [03] [1080p] [简日内嵌] [2024年4月番]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `668321_LoliHouse_8_Kaijuu_8_gou___03_WebRip_1080p_HEVC_10bit_AAC`() {
    val r = parse("[LoliHouse] 怪兽8号 / Kaijuu 8-gou - 03 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `668314_ANi_Kaiju_No_8_8___03_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    val r = parse("[ANi] Kaiju No 8 / 怪獸 8 號 - 03 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668300_8_Kaijuu_8_gou_02_1080P_WEBrip_MP4`() {
    val r = parse("[星空字幕組][怪獸8號 / Kaijuu 8-gou][02][繁日雙語][1080P][WEBrip][MP4]（急招校對、後期）")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668299_8_Kaijuu_8_gou_02_1080P_WEBrip_MP4`() {
    val r = parse("[星空字幕组][怪兽8号 / Kaijuu 8-gou][02][简日双语][1080P][WEBrip][MP4]（急招校对、后期）")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668298_8_Kaijuu_8_gou_01_1080P_WEBrip_MP4`() {
    val r = parse("[星空字幕組][怪獸8號 / Kaijuu 8-gou][01][繁日雙語][1080P][WEBrip][MP4]（急招校對、後期）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668297_8_Kaijuu_8_gou_01_1080P_WEBrip_MP4`() {
    val r = parse("[星空字幕组][怪兽8号 / Kaijuu 8-gou][01][简日双语][1080P][WEBrip][MP4]（急招校对、后期）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667999_8_Kaijuu_8_Gou_02_1080p_2024_4`() {
    val r = parse("[猎户压制部] 怪兽8号 / Kaijuu 8 Gou [02] [1080p] [繁日内嵌] [2024年4月番]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `667998_8_Kaijuu_8_Gou_02_1080p_2024_4`() {
    val r = parse("[猎户压制部] 怪兽8号 / Kaijuu 8 Gou [02] [1080p] [简日内嵌] [2024年4月番]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `667814_Lilith_Raws_8_Kaijuu_8_gou___02_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    val r = parse("[Lilith-Raws] 怪獸 8 號 / Kaijuu 8-gou - 02 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667776_LoliHouse_8_Kaijuu_8_gou___02_WebRip_1080p_HEVC_10bit_AAC`() {
    val r = parse("[LoliHouse] 怪兽8号 / Kaijuu 8-gou - 02 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `667757_ANi_Kaijuu_8gou_8___02_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    val r = parse("[ANi] Kaijuu 8gou / 怪獸 8 號 - 02 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667525_8_Kaijuu_8_Gou_01v2_1080p_2024_4`() {
    val r = parse("[猎户压制部] 怪兽8号 / Kaijuu 8 Gou [01v2] [1080p] [繁日内嵌] [2024年4月番]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `667524_8_Kaijuu_8_Gou_01v2_1080p_2024_4`() {
    val r = parse("[猎户压制部] 怪兽8号 / Kaijuu 8 Gou [01v2] [1080p] [简日内嵌] [2024年4月番]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `667461_LoliHouse_8_Kaijuu_8_gou___01_WebRip_1080p_HEVC_10bit_AAC`() {
    val r = parse("[LoliHouse] 怪兽8号 / Kaijuu 8-gou - 01 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `667370_8_Kaijuu_8_Gou_01_1080p_2024_4`() {
    val r = parse("[猎户压制部] 怪兽8号 / Kaijuu 8 Gou [01] [1080p] [简日内嵌] [2024年4月番]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `667141_Lilith_Raws_8_Kaijuu_8_gou___01_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    val r = parse("[Lilith-Raws] 怪獸 8 號 / Kaijuu 8-gou - 01 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667129_ANi_Kaijuu_8gou_8___01_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    val r = parse("[ANi] Kaijuu 8gou / 怪獸 8 號 - 01 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }
}

// @formatter:on
