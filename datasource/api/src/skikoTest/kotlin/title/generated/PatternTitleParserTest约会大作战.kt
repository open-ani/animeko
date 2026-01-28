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
 * 原名: `约会大作战`
 * 数据源: `dmhy`
 *
 * 由 `test-codegen` 的 `GenerateTests.kt` 生成, 不要手动修改!
 * 如果你优化了解析器, 这些 test 可能会失败, 请检查是否它是因为以前解析错误而现在解析正确了. 
 * 如果是, 请更新测试数据: 执行 `GenerateTests.kt`.
 */
public class PatternTitleParserTest约会大作战 : PatternBasedTitleParserTestSuite() {
  @Test
  public fun `685031_Fatum_Fatalis_V_Date_a_Live_V_BD_REENCODE_EP01_12_SP_Fin`() {
    kotlin.run {
    val r =
        parse("[Fatum Fatalis] 约会大作战 第五季 / デート・ア・ライブV / Date a Live V (BD REENCODE EP01-12+SP Fin)")
    assertEquals("01..12+SP", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `679848_7_ACG_5_Date_A_Live_S05_01_12_BDrip_1080p_x265_FLAC_2_0`() {
    kotlin.run {
    val r =
        parse("[7³ACG] 约会大作战5/ デート・ア・ライブ Ⅴ/Date A Live S05 | 01-12 [简繁字幕] BDrip 1080p x265 FLAC 2.0")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `675404_DBD_Raws_Date_a_Live_IV_IV_01_12TV_1080P_BDRip_HEVC_10bit_FLAC_MKV`() {
    kotlin.run {
    val r =
        parse("[DBD-Raws][约会大作战 第四季/Date a Live IV/デート・ア・ライブIV][01-12TV全集][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `675383_DBD_Raws_Date_a_Live_III_III_01_12TV_1080P_BDRip_HEVC_10bit_FLAC_MKV`() {
    kotlin.run {
    val r =
        parse("[DBD-Raws][约会大作战 第三季/Date a Live III/デート・ア・ライブIII][01-12TV全集][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `675368_DBD_Raws_Date_a_Live_II_II_01_10TV_OAD_1080P_BDRip_HEVC_10bit_FLAC_MKV`() {
    kotlin.run {
    val r =
        parse("[DBD-Raws][约会大作战 第二季/Date a Live II/デート・ア・ライブ II][01-10TV全集+OAD+特典映像][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV]")
    assertEquals("01..10+OAD", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public
      fun `675285_DBD_Raws_Date_a_Live_S1_Director_s_Cut_01_12TV_OAD_1080P_BDRip_HEVC_10bit_FLAC_MKV`() {
    kotlin.run {
    val r =
        parse("[DBD-Raws][约会大作战 第一季/Date a Live S1/デート・ア・ライブ][导演剪辑版/Director's Cut/ディレクターズカット版][01-12TV全集+OAD][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV]")
    assertEquals("01..12+OAD", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `675279_DBD_Raws_Date_a_Live_S1_01_12TV_OAD_1080P_BDRip_HEVC_10bit_FLAC_MKV`() {
    kotlin.run {
    val r =
        parse("[DBD-Raws][约会大作战 第一季/Date a Live S1/デート・ア・ライブ][01-12TV全集+OAD][1080P][BDRip][HEVC-10bit][FLAC][MKV]")
    assertEquals("01..12+OAD", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `674235_Date_A_Live_V_01_12Fin_WEBRIP_1080p_AVC_AAC_MP4_2024_4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组] 约会大作战/Date A Live V (01-12Fin WEBRIP 1080p AVC AAC MP4 2024年4月 简中)")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `674234_Date_A_Live_V_01_12Fin_WEBRIP_1080p_AVC_AAC_MP4_2024_4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組] 約會大作戰/Date A Live V (01-12Fin WEBRIP 1080p AVC AAC MP4 2024年4月 繁中)")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `673571_Date_A_Live_V_V_12_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第12话 MP4 720p 完")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `673096_4_Date_A_Live_V_12_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][12][1080p][MP4][简中]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `673095_4_Date_A_Live_V_12_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][12][1080p][MP4][繁中]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `672920_ANi_Date_A_Live_DATE_A_LIVE_V___12_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    kotlin.run {
    val r =
        parse("[ANi] Date A Live / 約會大作戰 DATE A LIVE V - 12 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `672917_4_Date_A_Live_V_11_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][11][1080p][MP4][简中]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `672916_4_Date_A_Live_V_11_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][11][1080p][MP4][繁中]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `672876_Date_A_Live_V_V_11_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第11话 MP4 720p")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `672875_Date_A_Live_V_V_10_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第10话 MP4 720p")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `672874_Date_A_Live_V_V_09_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第09话 MP4 720p")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `672454_ANi_Date_A_Live_DATE_A_LIVE_V___11_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    kotlin.run {
    val r =
        parse("[ANi] Date A Live / 約會大作戰 DATE A LIVE V - 11 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `672110_4_Date_A_Live_V_10_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][10][1080p][MP4][繁中]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `672109_4_Date_A_Live_V_10_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][10][1080p][MP4][繁中]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `671976_ANi_Date_A_Live_DATE_A_LIVE_V___10_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    kotlin.run {
    val r =
        parse("[ANi] Date A Live / 約會大作戰 DATE A LIVE V - 10 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `671725_Lilith_Raws_Date_A_Live_S05___09_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    kotlin.run {
    val r = parse("[Lilith-Raws] 約會大作戰 / Date A Live S05 - 09 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `671601_4_Date_A_Live_V_09_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][09][1080p][MP4][简中]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `671600_4_Date_A_Live_V_09_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][09][1080p][MP4][繁中]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `671437_ANi_Date_A_Live_DATE_A_LIVE_V___09_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    kotlin.run {
    val r =
        parse("[ANi] Date A Live / 約會大作戰 DATE A LIVE V - 09 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `671221_4_Date_A_Live_V_08_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][08][1080p][MP4][简中]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `671220_4_Date_A_Live_V_08_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][08][1080p][MP4][繁中]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `671219_Date_A_Live_V_V_08_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第08话 MP4 720p")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `671124_Date_A_Live_V_V_07_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第07话 MP4 720p")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `671123_Date_A_Live_V_V_06_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第06话 MP4 720p")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `670990_Lilith_Raws_Date_A_Live_S05___08_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    kotlin.run {
    val r = parse("[Lilith-Raws] 約會大作戰 / Date A Live S05 - 08 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `670937_ANi_Date_A_Live_DATE_A_LIVE_V___08_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    kotlin.run {
    val r =
        parse("[ANi] Date A Live / 約會大作戰 DATE A LIVE V - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `670879_4_Date_A_Live_V_07_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][07][1080p][MP4][简中]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `670878_4_Date_A_Live_V_07_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][07][1080p][MP4][繁中]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `670562_Lilith_Raws_Date_A_Live_S05___07_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    kotlin.run {
    val r = parse("[Lilith-Raws] 約會大作戰 / Date A Live S05 - 07 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `670222_4_Date_A_Live_V_06_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][06][1080p][MP4][简中]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `670221_4_Date_A_Live_V_06_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][06][1080p][MP4][繁中]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `670174_Lilith_Raws_Date_A_Live_S05___06_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    kotlin.run {
    val r = parse("[Lilith-Raws] 約會大作戰 / Date A Live S05 - 06 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `670132_Lilith_Raws_Date_A_Live_S05___05_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    kotlin.run {
    val r = parse("[Lilith-Raws] 約會大作戰 / Date A Live S05 - 05 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669837_ANi_Date_a_Live_DATE_A_LIVE_V___06_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    kotlin.run {
    val r =
        parse("[ANi] Date a Live / 約會大作戰 DATE A LIVE V - 06 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669660_4_Date_A_Live_V_05_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][05][1080p][MP4][简中]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669659_4_Date_A_Live_V_05_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][05][1080p][MP4][繁中]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669658_4_Date_A_Live_V_05_720p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][05][720p][MP4][简中]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669657_4_Date_A_Live_V_05_720p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][05][720p][MP4][繁中]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669656_4_Date_A_Live_V_04v2_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][04v2][1080p][MP4][简中]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669655_4_Date_A_Live_V_04v2_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][04v2][1080p][MP4][繁中]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669654_4_Date_A_Live_V_04v2_720p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][04v2][720p][MP4][简中]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669653_4_Date_A_Live_V_04v2_720p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][04v2][720p][MP4][繁中]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669652_4_Date_A_Live_V_03_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][03][1080p][MP4][简中]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669651_4_Date_A_Live_V_03_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][03][1080p][MP4][繁中]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669650_4_Date_A_Live_V_03_720p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][03][720p][MP4][简中]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669649_4_Date_A_Live_V_03_720p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][03][720p][MP4][繁中]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669648_4_Date_A_Live_V_02_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][02][1080p][MP4][简中]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669647_4_Date_A_Live_V_02_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][02][1080p][MP4][繁中]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669646_4_Date_A_Live_V_02_720p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][02][720p][MP4][简中]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669645_4_Date_A_Live_V_02_720p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][02][720p][MP4][繁中]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669644_4_Date_A_Live_V_01_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][01][1080p][MP4][简中]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669643_4_Date_A_Live_V_01_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][01][1080p][MP4][繁中]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669642_4_Date_A_Live_V_01_720p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][01][720p][MP4][简中]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669641_4_Date_A_Live_V_01_720p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][01][720p][MP4][繁中]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669517_Date_A_Live_V_V_05_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第05话 MP4 720p")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669127_ANi_Date_a_Live_DATE_A_LIVE_V___05_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    kotlin.run {
    val r =
        parse("[ANi] Date a Live / 約會大作戰 DATE A LIVE V - 05 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `668975_Date_A_Live_V_V_04_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第04话 MP4 720p")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `668974_Date_A_Live_V_V_03_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第03话 MP4 720p")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `668968_4_V_Date_A_Live_V_04_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战 V][Date A Live V][04][1080p][MP4][简中]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `668967_4_V_Date_A_Live_V_04_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰 V][Date A Live V][04][1080p][MP4][繁中]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `668708_Lilith_Raws_Date_A_Live_S05___04_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    kotlin.run {
    val r = parse("[Lilith-Raws] 約會大作戰 / Date A Live S05 - 04 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `668564_ANi_Date_a_Live_DATE_A_LIVE_V___04_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    kotlin.run {
    val r =
        parse("[ANi] Date a Live / 約會大作戰 DATE A LIVE V - 04 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `668145_Lilith_Raws_Date_A_Live_S05___03_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    kotlin.run {
    val r = parse("[Lilith-Raws] 約會大作戰 / Date A Live S05 - 03 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `668096_ANi_Date_a_Live_DATE_A_LIVE_V___03_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    kotlin.run {
    val r =
        parse("[ANi] Date a Live / 約會大作戰 DATE A LIVE V - 03 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `668057_Date_A_Live_V_V_02_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第02话 MP4 720p")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `667653_Lilith_Raws_Date_A_Live_S05___02_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    kotlin.run {
    val r = parse("[Lilith-Raws] 約會大作戰 / Date A Live S05 - 02 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `667530_ANi_Date_a_Live_DATE_A_LIVE_V___02_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    kotlin.run {
    val r =
        parse("[ANi] Date a Live / 約會大作戰 DATE A LIVE V - 02 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `667334_Date_A_Live_V_V_01_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第01话 MP4 720p")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `666861_BakeSubs_Date_A_Bullet_BD_1080p`() {
    kotlin.run {
    val r = parse("[BakeSubs] Date A Bullet [BD][1080p] 约会大作战约战狂三外传前篇虚或实篇后篇红或白篇")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `666850_Lilith_Raws_Date_A_Live_S05___01_Baha_WebDL_1080p_AVC_AAC_CHT`() {
    kotlin.run {
    val r = parse("[Lilith-Raws] 約會大作戰 / Date A Live S05 - 01 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `666848_ANi_DATE_A_LIVE_V___01_1080P_Baha_WEB_DL_AAC_AVC_CHT_MP4`() {
    kotlin.run {
    val r = parse("[ANi] 約會大作戰 DATE A LIVE V - 01 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `617525_7_ACG_IV_Date_A_Live_IV_IV_01_12_BDrip_1080p_x265_FLAC`() {
    kotlin.run {
    val r =
        parse("[7³ACG] 约会大作战IV/Date A Live IV/デート・ア・ライブ IV | 01-12[简繁英字幕] BDrip 1080p x265 FLAC")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT, ENG", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `607120_LoliHouse_IV_Date_A_Live_IV___12_WebRip_1080p_HEVC_10bit_AAC_End`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 12 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕][End]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `607119_LoliHouse_IV_Date_A_Live_IV___11_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 11 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `607101_IV_Date_A_Live_IV_12_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第12話][1080p_AVC][繁體][完]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `607100_IV_Date_A_Live_IV_12_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第12话][1080p_AVC][简体][完]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `607099_IV_Date_A_Live_IV_11_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第11話][1080p_AVC][繁體]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `607098_IV_Date_A_Live_IV_11_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第11话][1080p_AVC][简体]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `606271_LoliHouse_IV_Date_A_Live_IV___10_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 10 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `606270_LoliHouse_IV_Date_A_Live_IV___09_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 09 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `606234_IV_Date_A_Live_IV_10_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第10話][1080p_AVC][繁體]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `606233_IV_Date_A_Live_IV_10_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第10话][1080p_AVC][简体]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `606232_IV_Date_A_Live_IV_09_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第09話][1080p_AVC][繁體]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `606231_IV_Date_A_Live_IV_09_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第09话][1080p_AVC][简体]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `605058_Date_A_Live_IV_IV_12_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第12话 MP4 720p 完")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `604944_Date_A_Live_IV_IV_11_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第11话 MP4 720p")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `604936_Date_A_Live_IV_IV_10_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第10话 MP4 720p")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `604641_Date_A_Live_IV_01_12Fin_WEBRIP_1080p_AVC_AAC_2022_4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组] 约会大作战/Date A Live IV (01-12Fin WEBRIP 1080p AVC AAC 2022年4月 简中)")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `604638_Date_A_Live_IV_01_12Fin_WEBRIP_1080p_AVC_AAC_2022_4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組] 約會大作戰/Date A Live IV (01-12Fin WEBRIP 1080p AVC AAC 2022年4月 繁中)")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `604636_4_Date_A_Live_IV_12_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][12][1080p][MP4][简中]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `604635_4_Date_A_Live_IV_12_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][12][1080p][MP4][繁中]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `604618_4_Date_A_Live_IV_11_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][11][1080p][MP4][简中]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `604617_4_Date_A_Live_IV_11_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][11][1080p][MP4][繁中]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `604616_4_Date_A_Live_IV_10_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][10][1080p][MP4][简中]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `604615_4_Date_A_Live_IV_10_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][10][1080p][MP4][繁中]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `604295_LoliHouse_IV_Date_A_Live_IV___08_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 08 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `604264_IV_Date_A_Live_IV_08_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第08話][1080p_AVC][繁體]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `604263_IV_Date_A_Live_IV_08_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第08话][1080p_AVC][简体]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `604133_4_Date_A_Live_IV_09_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][09][1080p][MP4][简中]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `604132_4_Date_A_Live_IV_09_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][09][1080p][MP4][繁中]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `603875_c_c_4_01_12_BIG5_1080P_MP4`() {
    kotlin.run {
    val r = parse("[c.c動漫][4月新番][約會大作戰 第四季][01-12][合集][BIG5][1080P][MP4]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `603874_4_Date_A_Live_IV_08_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][08][1080p][MP4][简中]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `603873_4_Date_A_Live_IV_08_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][08][1080p][MP4][繁中]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `603818_c_c_4_12_BIG5_1080P_MP4_END`() {
    kotlin.run {
    val r = parse("[c.c動漫][4月新番][約會大作戰 第四季][12][BIG5][1080P][MP4][END]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `603814_ANi_DATE_A_LIVE___12_1080P_Bilibili_WEB_DL_AAC_AVC_CHT_CHS_MP4`() {
    kotlin.run {
    val r =
        parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳台地區） - 12 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `603813_NC_Raws_Date_A_Live_S4___12_B_Global_1920x1080_HEVC_AAC_MKV`() {
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 12 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `603595_4_Date_A_Live_IV_07_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][07][1080p][MP4][简中]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `603594_4_Date_A_Live_IV_07_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][07][1080p][MP4][繁中]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `603295_c_c_4_11_BIG5_1080P_MP4`() {
    kotlin.run {
    val r = parse("[c.c動漫][4月新番][約會大作戰 第四季][11][BIG5][1080P][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `603225_ANi_DATE_A_LIVE___11_1080P_Bilibili_WEB_DL_AAC_AVC_CHT_CHS_MP4`() {
    kotlin.run {
    val r =
        parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳台地區） - 11 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `603224_NC_Raws_Date_A_Live_S4___11_B_Global_1920x1080_HEVC_AAC_MKV`() {
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 11 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `602841_c_c_4_10_BIG5_1080P_MP4`() {
    kotlin.run {
    val r = parse("[c.c動漫][4月新番][約會大作戰 第四季][10][BIG5][1080P][MP4][内详]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `602815_4_Date_A_Live_IV_06_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][06][1080p][MP4][简中]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `602814_4_Date_A_Live_IV_06_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][06][1080p][MP4][繁中]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `602813_4_Date_A_Live_IV_05_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][05][1080p][MP4][简中]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `602812_4_Date_A_Live_IV_05_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][05][1080p][MP4][繁中]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `602603_NC_Raws_Date_A_Live_S4___10_B_Global_1920x1080_HEVC_AAC_MKV`() {
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 10 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `602602_ANi_DATE_A_LIVE___10_1080P_Bilibili_WEB_DL_AAC_AVC_CHT_CHS_MP4`() {
    kotlin.run {
    val r =
        parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳台地區） - 10 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `602574_Date_A_Live_IV_IV_09_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第09话 MP4 720p")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `601987_c_c_4_09_BIG5_1080P_MP4`() {
    kotlin.run {
    val r = parse("[c.c動漫][4月新番][約會大作戰 第四季][09][BIG5][1080P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `601977_ANi_DATE_A_LIVE___09_1080P_Bilibili_WEB_DL_AAC_AVC_CHT_CHS_MP4`() {
    kotlin.run {
    val r =
        parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳台地區） - 09 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `601974_NC_Raws_Date_A_Live_S4___09_B_Global_1920x1080_HEVC_AAC_MKV`() {
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 09 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `601952_Date_A_Live_IV_IV_08_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第08话 MP4 720p")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `601951_Date_A_Live_IV_IV_07_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第07话 MP4 720p")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `601950_Date_A_Live_IV_IV_06_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第06话 MP4 720p")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `601949_Date_A_Live_IV_IV_05_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第05话 MP4 720p")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `601812_LoliHouse_IV_Date_A_Live_IV___07_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 07 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `601807_IV_Date_A_Live_IV_07_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第07話][1080p_AVC][繁體]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `601806_IV_Date_A_Live_IV_07_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第07话][1080p_AVC][简体]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `601731_LoliHouse_IV_Date_A_Live_IV___06_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 06 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `601729_IV_Date_A_Live_IV_06_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第06話][1080p_AVC][繁體]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `601728_IV_Date_A_Live_IV_06_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第06话][1080p_AVC][简体]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `601630_c_c_4_03_08_BIG5_1080P_MP4`() {
    kotlin.run {
    val r = parse("[c.c動漫][4月新番][約會大作戰 第四季][03-08][BIG5][1080P][MP4]")
    assertEquals("03..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `601386_ANi_DATE_A_LIVE___08_1080P_Bilibili_WEB_DL_AAC_AVC_CHT_CHS_MP4`() {
    kotlin.run {
    val r =
        parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳台地區） - 08 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `601384_NC_Raws_Date_A_Live_S4___08_B_Global_1920x1080_HEVC_AAC_MKV`() {
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 08 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `601033_LoliHouse_IV_Date_A_Live_IV___05_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 05 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `601027_IV_Date_A_Live_IV_05_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第05話][1080p_AVC][繁體]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `601026_IV_Date_A_Live_IV_05_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第05话][1080p_AVC][简体]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `600754_ANi_DATE_A_LIVE___07_1080P_Bilibili_WEB_DL_AAC_AVC_CHT_CHS_MP4`() {
    kotlin.run {
    val r =
        parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳台地區） - 07 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `600752_NC_Raws_Date_A_Live_S4___07_B_Global_1920x1080_HEVC_AAC_MKV`() {
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 07 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `600492_4_Date_A_Live_IV_04_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][04][1080p][MP4][简中]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `600491_4_Date_A_Live_IV_04_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][04][1080p][MP4][繁中]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `600185_ANi_DATE_A_LIVE___06_1080P_Bilibili_WEB_DL_AAC_AVC_CHT_CHS_MP4`() {
    kotlin.run {
    val r =
        parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳台地區） - 06 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `600183_NC_Raws_Date_A_Live_S4___06_B_Global_1920x1080_HEVC_AAC_MKV`() {
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 06 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `599803_LoliHouse_IV_Date_A_Live_IV___04_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 04 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `599802_IV_Date_A_Live_IV_04_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第04話][1080p_AVC][繁體]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `599801_IV_Date_A_Live_IV_04_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第04话][1080p_AVC][简体]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `599524_ANi_DATE_A_LIVE___05_1080P_Bilibili_WEB_DL_AAC_AVC_CHT_CHS_MP4`() {
    kotlin.run {
    val r =
        parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳台地區） - 05 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `599522_NC_Raws_Date_A_Live_S4___05_B_Global_1920x1080_HEVC_AAC_MKV`() {
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 05 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `599508_Date_A_Live_IV_IV_04_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第04话 MP4 720p")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `599507_Date_A_Live_IV_IV_03_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第03话 MP4 720p")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `599392_4_Date_A_Live_IV_03_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][03][1080p][MP4][简中]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `599391_4_Date_A_Live_IV_03_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][03][1080p][MP4][繁中]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `598923_ANi_DATE_A_LIVE___04_1080P_Bilibili_WEB_DL_AAC_AVC_CHT_CHS_MP4`() {
    kotlin.run {
    val r =
        parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳台地區） - 04 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `598918_NC_Raws_Date_A_Live_S4___04_B_Global_1920x1080_HEVC_AAC_MKV`() {
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 04 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `598909_LoliHouse_IV_Date_A_Live_IV___03_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 03 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `598904_IV_Date_A_Live_IV_03_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第03話][1080p_AVC][繁體]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `598902_IV_Date_A_Live_IV_03_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第03话][1080p_AVC][简体]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `598603_4_Date_A_Live_IV_02_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][02][1080p][MP4][简中]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `598602_4_Date_A_Live_IV_02_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][02][1080p][MP4][繁中]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `598507_LoliHouse_IV_Date_A_Live_IV___02_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 02 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `598414_IV_Date_A_Live_IV_02_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第02話][1080p_AVC][繁體]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `598413_IV_Date_A_Live_IV_02_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第02话][1080p_AVC][简体]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `598280_ANi_DATE_A_LIVE___03_1080P_Bilibili_WEB_DL_AAC_AVC_CHT_CHS_MP4`() {
    kotlin.run {
    val r =
        parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳台地區） - 03 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `598272_NC_Raws_Date_A_Live_S4___03_B_Global_1920x1080_HEVC_AAC_MKV`() {
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 03 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `597949_NC_Raws_Date_A_Live_S4___02_B_Global_1920x1080_HEVC_AAC_MKV`() {
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 02 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `597780_c_c_4_02_BIG5_1080P_MP4`() {
    kotlin.run {
    val r = parse("[c.c動漫][4月新番][約會大作戰 第四季][02][BIG5][1080P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `597726_4_Date_A_Live_IV_01_1080p_MP4`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][01][1080p][MP4][简中]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `597725_4_Date_A_Live_IV_01_1080p_MP4`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][01][1080p][MP4][繁中]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `597691_Date_A_Live_IV_IV_02_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第02话 MP4 720p")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `597522_ANi_DATE_A_LIVE___02_1080P_Bilibili_WEB_DL_AAC_AVC_CHT_CHS_MP4`() {
    kotlin.run {
    val r =
        parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳台地區） - 02 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `597519_NC_Raws_Date_A_Live_S4___02_B_Global_1920x1080_HEVC_AAC_MKV`() {
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 02 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `597482_Date_A_Live_IV_IV_01_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第01话 MP4 720p")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `597383_IV_Date_A_Live_IV_01_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第01話][1080p_AVC][繁體]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `597382_IV_Date_A_Live_IV_01_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第01话][1080p_AVC][简体]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `597379_LoliHouse_IV_Date_A_Live_IV___01_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 01 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `596933_c_c_4_01_BIG5_1080P_MP4`() {
    kotlin.run {
    val r = parse("[c.c動漫][4月新番][約會大作戰 第四季][01][BIG5][1080P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `596847_ANi_DATE_A_LIVE_01_1080P_Bilibili_WEB_DL_AAC_AVC_MP4`() {
    kotlin.run {
    val r = parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳臺地區）[01][1080P][Bilibili][WEB-DL][AAC AVC][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `596842_NC_Raws_Date_A_Live_S4___01_B_Global_1920x1080_HEVC_AAC_MKV`() {
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 01 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public
      fun `569109_Lilith_Raws_Date_a_Live___Date_a_Bullet_Dead_or_Bullet_Baha_WEB_DL_1080p_AVC_AAC_CHT_MP4`() {
    kotlin.run {
    val r =
        parse("[Lilith-Raws] 約會大作戰赤黑新章：虛或實 / Date a Live - Date a Bullet：Dead or Bullet [Baha][WEB-DL][1080p][AVC AAC][CHT][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public
      fun `569108_Lilith_Raws_Date_a_Live___Date_a_Bullet_Nightmare_or_Queen_Baha_WEB_DL_1080p_AVC_AAC_CHT_MP4`() {
    kotlin.run {
    val r =
        parse("[Lilith-Raws] 約會大作戰赤黑新章：紅或白 / Date a Live - Date a Bullet：Nightmare or Queen [Baha][WEB-DL][1080p][AVC AAC][CHT][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `569105_NC_Raws_DATE_A_BULLET_DATE_A_BULLET___Baha_WEB_DL_1080p_AVC_AAC_CHT_MP4`() {
    kotlin.run {
    val r =
        parse("[NC-Raws] 約會大作戰 DATE A BULLET 赤黑新章：虛或實 / DATE A BULLET - 電影 [Baha][WEB-DL][1080p][AVC AAC][CHT][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `569104_NC_Raws_DATE_A_BULLET_DATE_A_BULLET___Baha_WEB_DL_1080p_AVC_AAC_CHT_MP4`() {
    kotlin.run {
    val r =
        parse("[NC-Raws] 約會大作戰 DATE A BULLET 赤黑新章：紅或白 / DATE A BULLET - 電影 [Baha][WEB-DL][1080p][AVC AAC][CHT][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public
      fun `556066_Skymoon_Raws_Date_A_Bullet_Dead_or_Bullet_Nightmare_or_Queen_BiliBili_WEB_DL_1080p_AVC_AAC_CHS_MP4`() {
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 約會大作戰 赤黑新章：紅或白 / Date A Bullet : Dead or Bullet Nightmare or Queen [BiliBili][WEB-DL][1080p][AVC AAC][CHS][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public
      fun `556065_Skymoon_Raws_Date_A_Bullet_zenpen_Dead_or_Bullet_BiliBili_WEB_DL_1080p_AVC_AAC_CHS_MP4`() {
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 約會大作戰 赤黑新章：虛或實 / Date A Bullet: zenpen Dead or Bullet [BiliBili][WEB-DL][1080p][AVC AAC][CHS][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public
      fun `536000_VCB_Studio_Date_A_Live_10_bit_1080p_HEVC_AVC_BDRip_S1_S2_MOVIE_Reseed_V3_Fin`() {
    kotlin.run {
    val r =
        parse("[VCB-Studio] Date A Live / 约会大作战 / デート・ア・ライブ 10-bit 1080p HEVC/AVC BDRip [S1-S2 + MOVIE Reseed V3 Fin]")
    assertEquals("S1+S2+S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `535336_Date_A_Live_S1_S2_S3_Movie_BD_1920x1080_HEVC_10bit_OPUS`() {
    kotlin.run {
    val r = parse("约会大作战 全三季 Date A Live S1+S2+S3+Movie [BD 1920x1080 HEVC-10bit OPUS][简繁内封字幕]")
    assertEquals("S1+S2+S3+S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `528456_Snow_Raws_Date_a_Live_III_III_BD_1920x1080_HEVC_YUV420P10_FLAC`() {
    kotlin.run {
    val r =
        parse("[Snow-Raws] 约会大作战 第三季/Date a Live III/デート・ア・ライブ III(BD 1920x1080 HEVC-YUV420P10 FLAC)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `528455_Snow_Raws_Date_a_Live_II_II_BD_1920x1080_HEVC_YUV420P10_FLAC`() {
    kotlin.run {
    val r =
        parse("[Snow-Raws] 约会大作战 第二季/Date a Live II/デート・ア・ライブ II(BD 1920x1080 HEVC-YUV420P10 FLAC)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `528454_Snow_Raws_Date_a_Live_BD_1920x1080_HEVC_YUV420P10_FLAC`() {
    kotlin.run {
    val r = parse("[Snow-Raws] 约会大作战 第一季/Date a Live/デート・ア・ライブ(BD 1920x1080 HEVC-YUV420P10 FLAC)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `522715_VCB_Studio_Date_A_Live_10_bit_720p_HEVC_BDRip_MP4_Ver`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&VCB-Studio] Date A Live Ⅲ / 约会大作战Ⅲ / デート・ア・ライブⅢ 10-bit 720p HEVC BDRip [MP4 Ver]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `522714_VCB_Studio_Date_A_Live_10_bit_1080p_HEVC_BDRip_Fin`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&VCB-Studio] Date A Live Ⅲ / 约会大作战Ⅲ / デート・ア・ライブⅢ 10-bit 1080p HEVC BDRip [Fin]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `521581_DHR_DATE_A_LIVE_III_01_12_BDRip_720P_MP4`() {
    kotlin.run {
    val r = parse("【DHR動研字幕組】[約會大作戰 第三季_DATE A LIVE III][01-12全][BDRip][繁體][720P][MP4](合集版本)")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `520653_DHR_DATE_A_LIVE_III_09_12_BDRip_720P_MP4`() {
    kotlin.run {
    val r = parse("【DHR動研字幕組】[約會大作戰 第三季_DATE A LIVE III][09-12完][BDRip][繁體][720P][MP4]")
    assertEquals("09..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `519355_Moozzi2_Date_A_Live_S3_BD_BOX_1_12_BD_1920x1080_x_264_Flac`() {
    kotlin.run {
    val r = parse("[Moozzi2] 约会大作战第三季 Date A Live S3 BD-BOX 1-12 (BD 1920x1080 x.264 Flac)")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `517728_ReinForce_DATE_A_LIVE_S3_Vol_1_01_06_BDrip_1920x1680_mkv`() {
    kotlin.run {
    val r = parse("【ReinForce】【约会大作战 第三季】【DATE A LIVE S3 Vol 1】【01-06】【BDrip】【1920x1680】【mkv】")
    assertEquals("01..06", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `514522_Date_A_Live_III_III_12_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live III 约会大作战III 第12话 MP4 720p 完")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `514489_LoliHouse_III_Date_A_Live_III___12_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 III / Date A Live III - 12 [WebRip 1080p HEVC-10bit AAC][繁体内封字幕]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `514156_III_Date_A_Live_III_12_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第12話_完][1080p_AVC][繁體]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `514155_III_Date_A_Live_III_12_720p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第12話_完][720p_AVC][繁體]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `514001_c_c_1_DATE_A_LIVE_S3_01_12_BIG5_HEVC_1080P`() {
    kotlin.run {
    val r = parse("[c.c動漫][1月新番][約會大作戰 第三季][DATE A LIVE S3][01-12][合集][BIG5][HEVC][1080P][網盤]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `513846_III_12_1080p`() {
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 12 [1080p][简体中文字幕]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `513772_LoliHouse_III_Date_A_Live_III___11_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 III / Date A Live III - 11 [WebRip 1080p HEVC-10bit AAC][繁体内封字幕]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `513620_III_Date_A_Live_III_11_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第11話][1080p_AVC][繁體]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `513619_III_Date_A_Live_III_11_720p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第11話][720p_AVC][繁體]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `513552_Date_A_Live_III_III_11_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live III 约会大作战III 第11话 MP4 720p")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `513404_III_11_1080p`() {
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 11 [1080p][简体中文字幕]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `513290_LoliHouse_III_Date_A_Live_III___10_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 III / Date A Live III - 10 [WebRip 1080p HEVC-10bit AAC][繁体内封字幕]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `513178_Date_A_Live_III_III_10_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live III 约会大作战III 第10话 MP4 720p")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `513155_LoliHouse_III_Date_A_Live_III___09_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 III / Date A Live III - 09 [WebRip 1080p HEVC-10bit AAC][繁体内封字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `513100_III_Date_A_Live_III_10_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第10話][1080p_AVC][繁體]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `513099_III_Date_A_Live_III_10_720p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第10話][720p_AVC][繁體]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `513027_III_Date_A_Live_III_09_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第09話][1080p_AVC][繁體]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `513026_III_Date_A_Live_III_09_720p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第09話][720p_AVC][繁體]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `512975_DHR_DATE_A_LIVE_III_08_720P_MP4`() {
    kotlin.run {
    val r = parse("【DHR動研字幕組】[約會大作戰 第三季_DATE A LIVE III][08][繁體][720P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `512933_III_09_1080p`() {
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 09 [1080p][简体中文字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `512889_Date_A_Live_III_III_09_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live III 约会大作战III 第09话 MP4 720p")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `512452_III_09_1080p`() {
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 09 [1080p][简体中文字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `512347_LoliHouse_III_Date_A_Live_III___08_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 III / Date A Live III - 08 [WebRip 1080p HEVC-10bit AAC][繁体内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `512305_Date_A_Live_III_III_08_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live III 约会大作战III 第08话 MP4 720p")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `512260_III_Date_A_Live_III_08_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第08話][1080p_AVC][繁體]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `512259_III_Date_A_Live_III_08_720p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第08話][720p_AVC][繁體]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `512209_LoliHouse_III_Date_A_Live_III___07_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 III / Date A Live III - 07 [WebRip 1080p HEVC-10bit AAC][繁体内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `512207_III_Date_A_Live_III_07_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第07話][1080p_AVC][繁體]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `512206_III_Date_A_Live_III_07_720p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第07話][720p_AVC][繁體]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `512010_DHR_DATE_A_LIVE_III_07_720P_MP4`() {
    kotlin.run {
    val r = parse("【DHR動研字幕組】[約會大作戰 第三季_DATE A LIVE III][07][繁體][720P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `511922_III_08_1080p`() {
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 08 [1080p][简体中文字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `511908_Moozzi2_Date_a_Live_Mayuri_Judgement_BD_1920x1080_x_264_2Audio`() {
    kotlin.run {
    val r =
        parse("[Moozzi2] 约会大作战 剧场版 万由里审判 Date a Live Mayuri Judgement (BD 1920x1080 x.264 2Audio)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `511907_Moozzi2_Date_A_Live_S2_1_10_OAD_BD_1920x1080_x_264_Flac`() {
    kotlin.run {
    val r = parse("[Moozzi2] 约会大作战第二季 Date A Live S2 1-10+OAD (BD 1920x1080 x.264 Flac)")
    assertEquals("01..10+OAD", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `511906_Moozzi2_Date_A_Live_S1_Director_s_Cut_1_12_OAD_BD_1920x1080_x_264_Flac`() {
    kotlin.run {
    val r =
        parse("[Moozzi2] 约会大作战第一季 Date A Live S1 Director's Cut 1-12+OAD (BD 1920x1080 x.264 Flac)")
    assertEquals("01..12+OAD", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `511476_DHR_DATE_A_LIVE_III_06_720P_MP4`() {
    kotlin.run {
    val r = parse("【DHR動研字幕組】[約會大作戰 第三季_DATE A LIVE III][06][繁體][720P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `511462_Date_A_Live_III_III_07_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live III 约会大作战III 第07话 MP4 720p")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `511385_LoliHouse_III_Date_A_Live_III___06_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 III / Date A Live III - 06 [WebRip 1080p HEVC-10bit AAC][繁体内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `511369_III_Date_A_Live_III_06_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第06話][1080p_AVC][繁體]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `511368_III_Date_A_Live_III_06_720p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第06話][720p_AVC][繁體]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `511343_III_07_1080p`() {
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 07 [1080p][简体中文字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `511293_Date_A_Live_III_III_06_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live III 约会大作战III 第06话 MP4 720p")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `510952_06`() {
    kotlin.run {
    val r = parse("[搬运]约会大作战第三季第06集")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `510900_DHR_DATE_A_LIVE_III_05_720P_MP4`() {
    kotlin.run {
    val r = parse("【DHR動研字幕組】[約會大作戰 第三季_DATE A LIVE III][05][繁體][720P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `510619_LoliHouse_III_Date_A_Live_III___05_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 III / Date A Live III - 05 [WebRip 1080p HEVC-10bit AAC][繁体内封字幕]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `510589_III_Date_A_Live_III_05_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第05話][1080p_AVC][繁體]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `510588_III_Date_A_Live_III_05_720p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第05話][720p_AVC][繁體]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `510559_Date_A_Live_III_III_05_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live III 约会大作战III 第05话 MP4 720p")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `510435_DHR_DATE_A_LIVE_III_04_720P_MP4`() {
    kotlin.run {
    val r = parse("【DHR動研字幕組】[約會大作戰 第三季_DATE A LIVE III][04][繁體][720P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `510382_LoliHouse_III_Date_A_Live_III___01_04_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 III / Date A Live III - 01-04 [WebRip 1080p HEVC-10bit AAC][繁体内封字幕]")
    assertEquals("01..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `510346_III_05_1080p`() {
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 05 [1080p][简体中文字幕]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `510238_III_Date_A_Live_III_04_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第04話][1080p_AVC][繁體]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `510237_III_Date_A_Live_III_04_720p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第04話][720p_AVC][繁體]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `510217_III_Date_A_Live_III_03_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第03話][1080p_AVC][繁體]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `510216_III_Date_A_Live_III_03_720p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第03話][720p_AVC][繁體]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `510129_III_Date_A_Live_III_02_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第02話][1080p_AVC][繁體]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `510128_III_Date_A_Live_III_02_720p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第02話][720p_AVC][繁體]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `510100_III_Date_A_Live_III_01_1080p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第01話][1080p_AVC][繁體]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `510099_III_Date_A_Live_III_01_720p_AVC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第01話][720p_AVC][繁體]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `509923_DHR_DATE_A_LIVE_III_03_720P_MP4`() {
    kotlin.run {
    val r = parse("【DHR動研字幕組】[約會大作戰 第三季_DATE A LIVE III][03][繁體][720P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `509908_Date_A_Live_III_III_04_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live III 约会大作战III 第04话 MP4 720p")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `509850_Seed_Raws_2013_2015_Date_a_Live___S1_S2_MOVIE_BD_720p_AVC_MP4`() {
    kotlin.run {
    val r = parse("[Seed-Raws][2013-2015] 約會大作戰 Date a Live - S1+S2+MOVIE [BD][720p][AVC][MP4][補種]")
    assertEquals("2013..2015", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `509847_III_04_1080p`() {
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 04 [1080p][简体中文字幕]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `509575_DHR_DATE_A_LIVE_III_02_720P_MP4`() {
    kotlin.run {
    val r = parse("【DHR動研字幕組】[約會大作戰 第三季_DATE A LIVE III][02][繁體][720P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `509440_1_Date_A_Live_III_III_03_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][1月新番] Date A Live III 约会大作战III 第03话 MP4 720p")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `509373_LoliHouse_III_Date_A_Live_III___03_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[LoliHouse] 约会大作战 III / Date A Live III - 03 [WebRip 1080p HEVC-10bit AAC][英文内封字幕]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("ENG", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `509319_III_03_1080p`() {
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 03 [1080p][简体中文字幕]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `508970_LoliHouse_III_Date_A_Live_III___02_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[LoliHouse] 约会大作战 III / Date A Live III - 02 [WebRip 1080p HEVC-10bit AAC][英文内封字幕]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("ENG", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `508910_DHR_DATE_A_LIVE_III_01_720P_MP4`() {
    kotlin.run {
    val r = parse("【DHR動研字幕組】[約會大作戰 第三季_DATE A LIVE III][01][繁體][720P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `508879_1_Date_A_Live_III_III_02_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][1月新番] Date A Live III 约会大作战III 第02话 MP4 720p")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `508766_VCB_Studio_Date_A_Live_10_bit_1080p_AVC_BDRip_S1_OAD_Reseed_Fin`() {
    kotlin.run {
    val r =
        parse("[VCB-Studio] Date A Live / 约会大作战 / デート・ア・ライブ 10-bit 1080p AVC BDRip [S1 Reseed Fin]")
    assertEquals("S1", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `508711_III_02_1080p`() {
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 02 [1080p][简体中文字幕]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `508324_LoliHouse_III_Date_A_Live_III___01_WebRip_1080p_HEVC_10bit_AAC`() {
    kotlin.run {
    val r =
        parse("[LoliHouse] 约会大作战 III / Date A Live III - 01 [WebRip 1080p HEVC-10bit AAC][英文内封字幕]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("ENG", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `508274_1_Date_A_Live_III_III_01_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][1月新番] Date A Live III 约会大作战III 第01话 MP4 720p")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `508127_III_01_1080p`() {
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 01 [1080p][简体中文字幕]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `508095_VCB_Studio_Date_A_Live_10_bit_1080p_AVC_HEVC_BDRip_S1_S2_M1_Fin`() {
    kotlin.run {
    val r =
        parse("[VCB-Studio] Date A Live / 约会大作战 / デート・ア・ライブ 10-bit 1080p AVC/HEVC BDRip [S1(DC)+S2+M1 Reseed Fin]")
    assertEquals("S2", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `466903_ANK_Raws_DATE_A_LIVE_TV_01_10Fin_OVA_BDrip_1920x1080_HEVC_YUV420P10_FLAC`() {
    kotlin.run {
    val r =
        parse("[ANK-Raws] デート・ア・ライブⅡ DATE A LIVEⅡ 约会大作战Ⅱ TV 01-10Fin OVA (BDrip 1920x1080 HEVC-YUV420P10 FLAC)")
    assertEquals("01..10", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `466752_ANK_Raws_DATE_A_LIVE_TV_01_12Fin_OVA_BDrip_1920x1080_HEVC_YUV420P10_FLAC`() {
    kotlin.run {
    val r =
        parse("[ANK-Raws] デート・ア・ライブ DATE A LIVE 约会大作战 TV 01-12Fin OVA (BDrip 1920x1080 HEVC-YUV420P10 FLAC)")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `458348_DHR_DATE_A_LIVE_II_01_10_720P_MP4`() {
    kotlin.run {
    val r = parse("(個人發布)【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][01-10完][繁體][720P][MP4]")
    assertEquals("01..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `457636_F_II_11_OVA_720P_MP4`() {
    kotlin.run {
    val r = parse("【F宅】约会大作战II 11话 OVA 简 720P MP4")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `457635_F_13_OVA_720P_MP4`() {
    kotlin.run {
    val r = parse("【F宅】约会大作战 13话 OVA 简 720P MP4")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `457470_F_II_11_OVA_1080P_MKV`() {
    kotlin.run {
    val r = parse("【F宅】约会大作战II 11话 OVA 简 1080P MKV")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `457469_F_13_OVA_1080P_MKV`() {
    kotlin.run {
    val r = parse("【F宅】约会大作战 13话 OVA 简 1080P MKV")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public
      fun `427591_Array_Stars___Gekijouban_Date_a_Live___Mayuri_Judgement_BDrip_1080P_BIG5_GB_Jap_mkv`() {
    kotlin.run {
    val r =
        parse("[繁星字幕組][Array Stars][劇場版 約會大作戰 - 萬由里審判][Gekijouban Date a Live - Mayuri Judgement ][BDrip][1080P][BIG5&GB&Jap][中日雙語簡繁內掛][mkv][字幕招新]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `427251_F_DATE_A_LIVE_1080P_MP4`() {
    kotlin.run {
    val r = parse("【F宅】约会大作战【剧场版】DATE A LIVE 万由里裁决 简 1080P MKV")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `427107_Date_A_Live_BDBOX_BDRip`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 导演剪辑版BDBOX BDRip 简繁外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public
      fun `426437_Array_Stars___Gekijouban_Date_a_Live___Mayuri_Judgement_BDrip_720P_BIG5_Jap_mp4`() {
    kotlin.run {
    val r =
        parse("[繁星字幕組][Array Stars][劇場版 約會大作戰 - 萬由里審判][Gekijouban Date a Live - Mayuri Judgement ][BDrip][720P][BIG5&Jap][中日雙語繁體][mp4][字幕招新]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public
      fun `426241_Makino_House_VCB_S_philosophy_raws_Date_A_Live_The_Movie_Mayuri_Judgement_10bit_1080p_HEVC_BDRip`() {
    kotlin.run {
    val r =
        parse("[Makino House&VCB-S&philosophy-raws] Date A Live The Movie：Mayuri Judgement/约会大作战剧场版 万由里裁决 10bit 1080p HEVC BDRip")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `425985_Array_Stars___Gekijouban_Date_a_Live___Mayuri_Judgement_BDRIP_720P_GB_Jap`() {
    kotlin.run {
    val r =
        parse("[繁星字幕组][Array Stars][剧场版 约会大作战 - 万由里审判][Gekijouban Date a Live - Mayuri Judgement ][BDrip][720P][GB&Jap][中日双语简体][mp4]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `425643_rip_720P`() {
    kotlin.run {
    val r = parse("约会大作战 万由里裁决[漏勺rip 720P]（外挂字幕）")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `425240_Date_A_Live_Mayuri_Judgement_BDRip`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] 剧场版 Date A Live 约会大作战 万由里裁决 Mayuri Judgement BDRip 简繁外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `425043_F_DATE_A_LIVE_720P_MP4`() {
    kotlin.run {
    val r = parse("【F宅】约会大作战【剧场版】DATE A LIVE 万由里裁决 简 720P MP4")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `425018_Special_Video_GB_BIG5_BDRip_1080p_x264_8bit_AAC_Go_Summer_Girl_MV_SP_SV`() {
    kotlin.run {
    val r =
        parse("【莳乃汉化组】约会大作战 剧场版：万由里审判 [Special Video][GB+BIG5][BDRip_1080p_x264_8bit][AAC]（诱宵美九:Go☆Summer Girl MV 内嵌）【请注意这个不是SP，是SV】")
    assertEquals("SP..SP", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `424999_GBv2_BDRip_1080p_x264_8bit_AAC`() {
    kotlin.run {
    val r = parse("【莳乃汉化组】约会大作战 剧场版：万由里审判 [GBv2][BDRip_1080p_x264_8bit][AAC]【请下载本版本，上个版本压成繁体了】")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `424922_GB_BDRip_1080p_x264_8bit_AAC`() {
    kotlin.run {
    val r =
        parse("【莳乃汉化组】约会大作战 剧场版：万由里审判 [GB][BDRip_1080p_x264_8bit][AAC]【这个其实是压成了BIG5版的。。。。。下载转v2】")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `424896_BIG5_BDRip_1080p_x264_8bit_AAC`() {
    kotlin.run {
    val r = parse("【蒔乃漢化組】約會大作戰 劇場版：萬由里審判 [BIG5][BDRip_1080p_x264_8bit][AAC]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `424729_ReinForce__DATE_A_LIVE___Mayuri_Judgement_BDRip_1920x1080_x264_FLAC`() {
    kotlin.run {
    val r =
        parse("[ReinForce]約會大作戰劇場版 萬由里審判-DATE A LIVE - Mayuri Judgement (BDRip 1920x1080 x264 FLAC) [字幕内附]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `424579_DATE_A_LIVE_Mayuri_Judgement_BDRIP_1080P_x264_Hi10P_FLAC`() {
    kotlin.run {
    val r =
        parse("【千夏字幕組】【約會大作戰 萬由里審判_DATE_A_LIVE Mayuri Judgement】[劇場版][BDRIP_1080P][x264-Hi10P_FLAC][繁體外掛]（修正字體包）")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `424572_DATE_A_LIVE_Mayuri_Judgement_1080P_MKV_AVC_FLAC_Hi10P`() {
    kotlin.run {
    val r =
        parse("【漫猫字幕组&爱恋字幕社】【约会大作战 万由里审判/DATE_A_LIVE Mayuri Judgement】[剧场版][1080P][MKV][简繁外挂][ x264_FLAC_Hi10P]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `424492_DATE_A_LIVE_Mayuri_Judgement_BDRIP_720P_x264_AAC`() {
    kotlin.run {
    val r =
        parse("【千夏字幕組】【約會大作戰 萬由里審判_DATE_A_LIVE Mayuri Judgement】[劇場版][BDRIP_720P][x264_AAC][繁體內嵌]（正式版）")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `424436_MGRT_GB_1080P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【MGRT&幻之字幕组】约会大作战 万由里审判 [GB][1080P][PSV&PC][MP4][新人招募]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `424435_MGRT_GB_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【MGRT&幻之字幕组】约会大作战 万由里审判 [GB][720P][PSV&PC][MP4][新人招募]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `424395_DATE_A_LIVE_Mayuri_Judgement_720P_x264_AAC`() {
    kotlin.run {
    val r = parse("【漫猫字幕组&爱恋字幕社】【约会大作战 万由里审判/DATE_A_LIVE Mayuri Judgement】[剧场版]简体][720P][x264_AAC]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `424354_DATE_A_LIVE_Mayuri_Judgement_720P_x264_AAC`() {
    kotlin.run {
    val r =
        parse("【漫貓字幕組&愛戀字幕社】【約會大作戰 萬由裡審判/DATE_A_LIVE Mayuri Judgement】[劇場版][繁體][720P][x264_AAC]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `424256_Date_A_Live_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] 剧场版 Date A Live 约会大作战 万由里裁决 Mayuri Judgement MP4 720p")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `424150_BIG5_DVDRip_720p_x264_8bit_AAC`() {
    kotlin.run {
    val r = parse("【蒔乃漢化組】約會大作戰 劇場版：萬由里審判 [BIG5][DVDRip_720p_x264_8bit][AAC]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `424129_GB_DVDRip_720p_x264_8bit_AAC`() {
    kotlin.run {
    val r = parse("【莳乃汉化组】约会大作战 剧场版：万由里审判 [GB][DVDRip_720p_x264_8bit][AAC]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `417427_philosophy_raws_BDRIP_1080p_10bit_TV_01_13_Director_s_cut_Edition_SP`() {
    kotlin.run {
    val r =
        parse("[philosophy-raws][约会大作战][BDRIP][1080p 10bit][TV 01-13+Director's cut Edition+SP][日英双语][英文字幕]")
    assertEquals("01..13+SP", r.episodeRange.toString())
    assertEquals("ENG, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `417426_VCB_S_philosophy_raws_BDRIP_1080p_10bit_TV_01_13_SP`() {
    kotlin.run {
    val r = parse("[VCB-S&philosophy-raws][约会大作战][BDRIP][1080p 10bit][TV 01-13+SP][导演剪辑版]")
    assertEquals("01..13+SP", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `406743_BD720P_MP4`() {
    kotlin.run {
    val r = parse("【浩天个人发布】约会大作战第二季 BD720P MP4内嵌简体字幕")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `406031_Xrip_Date_A_Live_Director_s_Cut_BDrip_BD_BOX_1080P_x264_10bit_flac`() {
    kotlin.run {
    val r =
        parse("【Xrip】[約會大作戰 導演剪接版][Date A Live][Director's_Cut][BDrip][BD-BOX][1080P][x264_10bit_flac]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `406024_BD720P_MP4`() {
    kotlin.run {
    val r = parse("【浩天个人发布】约会大作战第一季 BD720P MP4内嵌简体字幕")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `402130_2_Date_a_Live2_BDrip_1920x1080_TV11_CM5_OPED5_pv1_menu8_x264_m4a_10bit`() {
    kotlin.run {
    val r =
        parse("约会大作战2 Date a Live2[BDrip][1920x1080][TV11+CM5+OPED5+pv1+menu8][x264_m4a][10bit]加刘景长压制")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public
      fun `397951_Date_a_Live_BDrip_1920x1080_TV12_OAD_CM10_OPED11_info3_menu14_x264_m4a_10bit`() {
    kotlin.run {
    val r =
        parse("约会大作战 Date a Live[BDrip][1920x1080][TV12+OAD+CM10+OPED11+info3+menu14][x264_m4a][10bit]加刘景长压制")
    assertEquals("OAD..OAD", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `392118_Date_A_Live_II_II_BDRip`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II BDRip 简繁外挂 合集")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `385057_Date_A_Live_II_II_BDRip_TV_11`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II BDRip TV未放送第11话 简繁外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `384760_Date_A_Live_II_II_BDRip_Vol_3_5`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II BDRip Vol.3-5 简繁外挂")
    assertEquals("03..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `383828__11_Date_A_Live_BDRIP_720P_X264_10bit_AAC`() {
    kotlin.run {
    val r = parse("[异域-11番小队][约会大作战合集Date A Live][BDRIP][720P][X264-10bit_AAC][春节贺礼第十八弹]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `381865_FFF_DATE_A_LIVE_BD_1080p_FLAC`() {
    kotlin.run {
    val r = parse("[FFF] DATE A LIVE [BD][1080p-FLAC] 约会大作战 第一季 含OVA [内封英文字幕]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("ENG", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `378454_U2_Rip_Date_a_Live_II_2_10bit_1080p_BDRip_Fin`() {
    kotlin.run {
    val r = parse("[U2-Rip] Date a Live II/约会大作战2期 10bit 1080p BDRip [Fin]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `378453_VCB_Studio_TUcaptions_Date_a_Live_II_2_10bit_1080p_BDRip_Fin`() {
    kotlin.run {
    val r = parse("[VCB-Studio & TUcaptions] Date a Live II/约会大作战2期 10bit 1080p BDRip [Fin] 繁简外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `378121_II_Date_A_Live_II_rip_720P`() {
    kotlin.run {
    val r = parse("《约会大作战II》（Date A Live II）[漏勺rip 720P]（外挂字幕）")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `377243_F_BD_Date_a_Live_II_01_11_720P_MP4`() {
    kotlin.run {
    val r = parse("【F宅】【BD版】约会大作战 Date a Live II 01-11【合集】 简 720P MP4")
    assertEquals("01..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public
      fun `377211_AE_LE_Project_Date_A_Live_II_II___11_TV_GB_BIG5_JPN_BDRip_1080p_x264_10bit_FLAC`() {
    kotlin.run {
    val r =
        parse("【莳乃字幕屋&AE漫画组&LE Project】Date A Live II 约会大作战 II - 第11话（TV未放送）[GB-BIG5-JPN][BDRip_1080p_x264_10bit][FLAC]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `377068_AE_LE_Project_Date_A_Live_II_II___11_TV_GB_JPN_1080P_PSV_PC`() {
    kotlin.run {
    val r =
        parse("【蒔乃字幕屋&AE漫畫組&LE Project】Date A Live II 約會大作戰 II - 第11話（TV未放送）[BIG5-JPN][1080P][PSV&PC][招募內詳]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `376296_AE_LE_Project_Date_A_Live_II_II___11_TV_GB_1080P_PSV_PC`() {
    kotlin.run {
    val r =
        parse("【莳乃字幕屋&AE漫画组&LE Project】Date A Live II 约会大作战 II - 第11话（TV未放送）[GB-JPN][1080P][PSV&PC][招募内详]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `375889_TUcaptions_2014_II_2_11_OVA_720P_MP4_BIG5`() {
    kotlin.run {
    val r = parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 11 // OVA // 720P-MP4-BIG5(繁)")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `375756_Date_A_Live_II_II_TV_11_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战 II TV未放送第11话 MP4 720p")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `375516_DATE_A_LIVE_II_11_TV_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第11話_TV未放送][1280x720][MP4_PC&PSV兼容][繁體]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `373910_VCB_Studio_Date_a_Live_S1_10bit_1080p_BDRip_Fin_CD`() {
    kotlin.run {
    val r = parse("[VCB-Studio] Date a Live/约会大作战 S1 10bit 1080p BDRip [Fin] (附CD+扫图+特典)")
    assertEquals("S1", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `373648_Xrip_Date_A_Live_BDrip_Vol_01_06_OVA_1080P_x264_10bit_flac`() {
    kotlin.run {
    val r = parse("【Xrip】[約會大作戰][Date A Live][BDrip][Vol.01_06+OVA][1080P][x264_10bit_flac]")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `371722_Xrip_S2_Date_A_Live_S2_Vol_01_05_BDrip_1080P_x264_10bit_flac`() {
    kotlin.run {
    val r = parse("【Xrip】[約會大作戰 S2][Date A Live S2][Vol.01_05][BDrip][1080P][x264_10bit_flac] (內詳)")
    assertEquals("S2", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `369308_Date_A_Live_II_II_BDRip_Vol_1_2`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II BDRip Vol.1-2 简繁外挂")
    assertEquals("01..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `358687_Date_A_Live_BDRip`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 BDRip 简繁外挂 合集")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `358089_Date_A_Live_BDRip_TV_13`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 BDRip TV未放送第13话 简繁外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `357273_Date_A_Live_BDRip_Vol_6`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 BDRip Vol.6 简繁外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `356631_DATE_A_LIVE_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][合集][BIG5][四月新番][720P][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `355164_TUcaptions_2014_II_2_1_10_720P_MP4_BIG5`() {
    kotlin.run {
    val r =
        parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 01~10 // 720P-MP4-BIG5(繁)【內附特效招募漫畫】")
    assertEquals("01..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `354651_10_720P`() {
    kotlin.run {
    val r = parse("【附百度网盘】【修罗字幕组】【约会大作战　第二季】【10】【720P】【中日双语字幕】")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `354536_Date_A_Live_II_II_01_10_MKV_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第01-10话 MKV 720p 简繁外挂 合集")
    assertEquals("01..10", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `354503_4_II_Date_A_Live_II_01_10`() {
    kotlin.run {
    val r = parse("[4月新番][约会大作战II][Date A Live II][第01-10话][HEVC][MP4][无字幕]")
    assertEquals("01..10", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `354255_Date_A_Live_II_II_01_10_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第01-10话 MP4 720p 合集")
    assertEquals("01..10", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `354187_F_Date_a_Live_II_720P_MP4`() {
    kotlin.run {
    val r = parse("【F宅】Date a Live II 约会大作战II【简】【合集】 720P MP4")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `354087_Date_A_Live_II_10_GB_1280_720_MP4`() {
    kotlin.run {
    val r = parse("【萌物百科字幕组】[约会大作战 第二季_Date A Live II][10][END][GB][1280×720][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `354083_KNA_2_Date_A_Live_ll_10_Fin_720P_MP4`() {
    kotlin.run {
    val r = parse("【恶俗×KNA】[约会大作战 2期][Date A Live ll][10][Fin][720P][简体][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `354077_Date_A_Live_II_II_09_MKV_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第09话 MKV 720p 简繁外挂")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `354076_Date_A_Live_II_II_10_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第10话 MP4 720p 完")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `354058_4_Date_A_Live_II_01_10_GB_BIG5_720P_MKV`() {
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[01-10][GB_BIG5][720P][MKV][全]")
    assertEquals("01..10", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `354057_4_Date_A_Live_II_01_10_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[01-10][BIG5][720P][MP4][全]")
    assertEquals("01..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `354053_4_Date_A_Live_II_01_10_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[01-10][GB][720P][MP4][全]")
    assertEquals("01..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `354051_TUcaptions_2014_II_2_10_End_720P_MP4_BIG5`() {
    kotlin.run {
    val r =
        parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 10 (End) // 720P-MP4-BIG5(繁)【內附特效招募漫畫】")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `354004_4_Date_A_Live_II_10_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[10][BIG5][720P][MP4][完]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `354001_4_Date_A_Live_II_10_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[10][GB][720P][MP4][完]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `353997_DHR_DATE_A_LIVE_II_10_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][10完][繁體][720P][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `353996_DHR_DATE_A_LIVE_II_10_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战 第二季_DATE A LIVE II][10完][简体][720P][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `353995_DATE_A_LIVE_II_10_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第10話_完][1280x720][MP4_PC&PSV兼容][繁體]（劇場版製作決定）")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `353989_DHR_DATE_A_LIVE_II_09_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][09][繁體][720P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `353988_DHR_DATE_A_LIVE_II_09_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战 第二季_DATE A LIVE II][09][简体][720P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `353777_DATE_A_LIVE_09_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][09][BIG5][四月新番][720P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `353776_DATE_A_LIVE_09_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][09][GB][四月新番][720P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `353773_09_720P`() {
    kotlin.run {
    val r = parse("【附百度网盘】【修罗字幕组】【约会大作战　第二季】【09】【720P】【中日双语字幕】")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `353691_08_720P`() {
    kotlin.run {
    val r = parse("【附百度网盘】【修罗字幕组】【约会大作战　第二季】【08】【720P】【中日双语字幕】")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `353470_Date_A_Live_II_09_GB_1280_720_MP4`() {
    kotlin.run {
    val r = parse("【萌物百科字幕组】[约会大作战 第二季_Date A Live II][09][GB][1280×720][MP4](内附网盘地址）")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `353436_DHR_DATE_A_LIVE_II_08_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][08][繁體][720P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `353435_DHR_DATE_A_LIVE_II_08_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战 第二季_DATE A LIVE II][08][简体][720P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `353401_Date_A_Live_II_II_09_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第09话 MP4 720p")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `353332_KNA_2_Date_A_Live_ll_09_720P_MP4`() {
    kotlin.run {
    val r = parse("【恶俗×KNA】[约会大作战 2期][Date A Live ll][09][720P][简体][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `353295_TUcaptions_2014_II_2_09_720P_MP4_BIG5`() {
    kotlin.run {
    val r =
        parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 09 // 720P-MP4-BIG5(繁)【內附校正招募漫畫】")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `353262_4_Date_A_Live_II_09_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[09][BIG5][720P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `353260_4_Date_A_Live_II_09_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[09][GB][720P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `353251_DATE_A_LIVE_II_09_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第09話][1280x720][MP4_PC&PSV兼容][繁體]（翻譯、校對招募中）")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `353008_Date_A_Live_II_II_08_MKV_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第08话 MKV 720p 简繁外挂")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `352620_DATE_A_LIVE_08_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][08][BIG5][四月新番][720P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `352618_DATE_A_LIVE_08_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][08][GB][四月新番][720P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `352615_DATE_A_LIVE_07_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][07][BIG5][四月新番][720P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `352603_DHR_DATE_A_LIVE_II_07_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][07][繁體][720P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `352602_DHR_DATE_A_LIVE_II_07_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战 第二季_DATE A LIVE II][07][简体][720P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `352553_DATE_A_LIVE_07_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][07][GB][四月新番][720P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `352551_Date_A_Live_II_08_GB_1280_720_MP4`() {
    kotlin.run {
    val r = parse("【萌物百科字幕组】[约会大作战 第二季_Date A Live II][08][GB][1280×720][MP4](内附网盘地址）")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `352499_KNA_2_Date_A_Live_ll_08_720P_MP4`() {
    kotlin.run {
    val r = parse("【恶俗×KNA】[约会大作战 2期][Date A Live ll][08][720P][简体][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `352490_Date_A_Live_II_II_08_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第08话 MP4 720p")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `352480_Date_A_Live_II_II_07_MKV_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第07话 MKV 720p 简繁外挂")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `352461_TUcaptions_2014_II_2_08_720P_MP4_BIG5`() {
    kotlin.run {
    val r =
        parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 08 // 720P-MP4-BIG5(繁)【內附校正招募漫畫】")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `352432_4_Date_A_Live_II_08_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[08][BIG5][720P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `352431_4_Date_A_Live_II_08_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[08][GB][720P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `352430_DATE_A_LIVE_II_08_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第08話][1280x720][MP4_PC&PSV兼容][繁體]（翻譯、校對招募中）")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `352420_08`() {
    kotlin.run {
    val r = parse("約會大作戰 第二季 08 英文字幕")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("ENG", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `351938_DATE_A_LIVE_II_07_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第07話][1280x720][MP4_PC&PSV兼容][繁體]（翻譯、校對招募中）")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `351732_TUcaptions_2014_II_2_07_720P_MP4_BIG5`() {
    kotlin.run {
    val r =
        parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 07 // 720P-MP4-BIG5(繁)【內附校正招募漫畫】")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `351626_Date_A_Live_II_II_07_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第07话 MP4 720p")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `351604_Date_A_Live_II_07_GB_1280_720_MP4`() {
    kotlin.run {
    val r = parse("【萌物百科字幕组】[约会大作战 第二季_Date A Live II][07][GB][1280×720][MP4](内附网盘地址）")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `351559_KNA_2_Date_A_Live_ll_07_720P_MP4`() {
    kotlin.run {
    val r = parse("【恶俗×KNA】[约会大作战 2期][Date A Live ll][07][720P][简体][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `351530_06_720P`() {
    kotlin.run {
    val r = parse("【附百度网盘】【修罗字幕组】【约会大作战　第二季】【06】【720P】【中日双语字幕】")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `351528_4_Date_A_Live_II_07_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[07][BIG5][720P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `351524_4_Date_A_Live_II_07_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[07][GB][720P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `351399_DHR_DATE_A_LIVE_II_06_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][06][繁體][720P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `351398_DHR_DATE_A_LIVE_II_06_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战 第二季_DATE A LIVE II][06][简体][720P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `351393_DHR_DATE_A_LIVE_II_05_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][05][繁體][720P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `351392_DHR_DATE_A_LIVE_II_05_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战 第二季_DATE A LIVE II][05][简体][720P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `351389_Date_A_Live_II_II_06_MKV_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第06话 MKV 720p 简繁外挂")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `351138_Date_A_Live_II_06_GB_1280_720_MP4`() {
    kotlin.run {
    val r = parse("【萌物百科字幕组】[约会大作战 第二季_Date A Live II][06][GB][1280×720][MP4](内附网盘地址）")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `351086_DATE_A_LIVE_06_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][06][BIG5][四月新番][720P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `351085_DATE_A_LIVE_06_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][06][GB][四月新番][720P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `350793_Date_A_Live_II_II_06_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第06话 MP4 720p")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `350735_Erosuri_KNA_Date_A_Live_ll_05_GB_1280X720`() {
    kotlin.run {
    val r = parse("【恶俗×KNA】[约会大作战 2期][Date A Live ll][06][720P][简体][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `350689_TUcaptions_2014_2_06_720P_MP4_BIG5`() {
    kotlin.run {
    val r =
        parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 06 // 720P-MP4-BIG5(繁)【內附翻譯招募漫畫】")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `350676_4_Date_A_Live_II_06_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[06][BIG5][720P][MP4][內詳]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `350672_4_Date_A_Live_II_06_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[06][GB][720P][MP4][内详]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `350645_DATE_A_LIVE_II_06_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第06話][1280x720][MP4_PC&PSV兼容][繁體]（翻譯、校對招募中）")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `350643_PV`() {
    kotlin.run {
    val r = parse("約會大作戰 第一季 PV")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `350458_05_720P`() {
    kotlin.run {
    val r = parse("【附百度网盘】【修罗字幕组】【约会大作战　第二季】【05】【720P】【中日双语字幕】")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `350399_Date_A_Live_II_II_05_MKV_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第05话 MKV 720p 简繁外挂")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `350135_DATE_A_LIVE_05_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][05][BIG5][四月新番][720P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `350133_DATE_A_LIVE_05_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][05][GB][四月新番][720P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `350022_Date_a_Live___05_BIG5_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕組】約會大作戰Ⅱ Date a Live Ⅱ - 05[BIG5][720P][PSV&PC][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `350020_Date_a_Live___05_GB_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组】约会大作战Ⅱ Date a Live Ⅱ - 05[GB][720P][PSV&PC][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `350019_Date_a_Live___04_BIG5_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕組】約會大作戰Ⅱ Date a Live Ⅱ - 04[BIG5][720P][PSV&PC][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `350018_Date_a_Live___04_GB_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组】约会大作战Ⅱ Date a Live Ⅱ - 04[GB][720P][PSV&PC][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `350016_Date_a_Live___03_v2_BIG5_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕組】約會大作戰Ⅱ Date a Live Ⅱ - 03_v2[BIG5][720P][PSV&PC][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `350015_Date_a_Live___03_v2_GB_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组】约会大作战Ⅱ Date a Live Ⅱ - 03_v2[GB][720P][PSV&PC][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `349958_Date_A_Live_II_05_GB_1280_720_MP4`() {
    kotlin.run {
    val r = parse("【萌物百科字幕组】[约会大作战 第二季_Date A Live II][05][GB][1280×720][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `349877_KNA_2_Date_A_Live_ll_05_720P_MP4`() {
    kotlin.run {
    val r = parse("【恶俗×KNA】[约会大作战 2期][Date A Live ll][05][720P][简体][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `349865_Date_A_Live_II_II_05_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第05话 MP4 720p")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `349810_TUcaptions_2014_2_01_720P_MP4_BIG5`() {
    kotlin.run {
    val r = parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 05 // 720P-MP4-BIG5(繁)")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `349783_04_720P`() {
    kotlin.run {
    val r = parse("【附百度网盘】【修罗字幕组】【约会大作战　第二季】【04】【720P】【中日双语字幕】")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `349772_4_Date_A_Live_II_05_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[05][BIG5][720P][MP4][內詳]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `349763_4_Date_A_Live_II_05_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[05][GB][720P][MP4][内详]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `349762_DATE_A_LIVE_II_05_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第05話][1280x720][MP4_PC&PSV兼容][繁體]（翻譯、校對招募中）")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `349681_DATE_A_LIVE_04_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][04][BIG5][四月新番][720P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `349679_DATE_A_LIVE_04_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][04][GB][四月新番][720P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `349546_Date_A_Live_II_II_04_MKV_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第04话 MKV 720p 简繁外挂")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `349522_DHR_DATE_A_LIVE_II_04_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][04][繁體][720P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `349521_DHR_DATE_A_LIVE_II_04_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战 第二季_DATE A LIVE II][04][简体][720P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `349470_Date_A_Live_II_04_GB_1280_720_MP4`() {
    kotlin.run {
    val r = parse("【萌物百科字幕组】[约会大作战 第二季_Date A Live II][04][GB][1280×720][MP4](内附网盘地址）")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `349195_03_720P`() {
    kotlin.run {
    val r = parse("【附百度网盘】【修罗字幕组】【约会大作战　第二季】【03】【720P】【中日双语字幕】")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `349114_Date_A_Live_II_II_04_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第04话 MP4 720p")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `348910_KNA_2_Date_A_Live_ll_04_720P_MP4`() {
    kotlin.run {
    val r = parse("【恶俗×KNA】[约会大作战 2期][Date A Live ll][04][720P][简体][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `348854_TUcaptions_2014_2_04_720P_MP4_BIG5`() {
    kotlin.run {
    val r = parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 04 // 720P-MP4-BIG5(繁)")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `348802_4_Date_A_Live_II_II_03_MKV_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live II 约会大作战II 第03话 MKV 720p 简繁外挂")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `348797_4_Date_A_Live_II_04_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[04][BIG5][720P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `348796_4_Date_A_Live_II_04_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[04][GB][720P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `348793_DATE_A_LIVE_II_04_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第04話][1280x720][MP4_PC&PSV兼容][繁體]（翻譯、校對招募中）")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `348466_Date_a_Live___03_BIG5_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕組】約會大作戰Ⅱ Date a Live Ⅱ - 03[BIG5][720P][PSV&PC][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `348465_Date_a_Live___03_GB_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组】约会大作战Ⅱ Date a Live Ⅱ - 03[GB][720P][PSV&PC][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `348422_DATE_A_LIVE_03_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][03][BIG5][四月新番][720P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `348420_DATE_A_LIVE_03_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][03][GB][四月新番][720P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `348334_Date_A_Live_II_03_GB_1280_720_MP4`() {
    kotlin.run {
    val r = parse("【萌物百科字幕组】[约会大作战 第二季_Date A Live II][03][GB][1280×720][MP4](内附网盘地址）")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347912_KNA_2_Date_A_Live_ll_03_720P_MP4`() {
    kotlin.run {
    val r = parse("【恶俗×KNA】[约会大作战 2期][Date A Live ll][03][720P][简体][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347897_TUcaptions_2014_2_03_720P_MP4_BIG5`() {
    kotlin.run {
    val r = parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 03 // 720P-MP4-BIG5(繁)【問卷內詳】")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347883_DHR_DATE_A_LIVE_II_03_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][03][繁體][720P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347882_DHR_DATE_A_LIVE_II_03_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战 第二季_DATE A LIVE II][03][简体][720P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347853_4_Date_A_Live_II_03_BIG5_720P_MP4_V2`() {
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[03][BIG5][720P][MP4][V2]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347844_4_Date_A_Live_II_03_GB_720P_MP4_V2`() {
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[03][GB][720P][MP4][V2]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347823_4_Date_A_Live_II_03_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[03][BIG5][720P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347820_DATE_A_LIVE_II_03_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第03話][1280x720][MP4_PC&PSV兼容][繁體]（翻譯、校對招募中）")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347819_4_Date_A_Live_II_03_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[03][GB][720P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347708_4_Date_A_Live_II_II_02_MKV_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live II 约会大作战II 第02话 MKV 720p 简繁外挂")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `347628_NTR_4_DATE_A_LIVE_II_II_02_BIG5_PSP_480P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE II 約會大作戰II][02][BIG5][PSP-480P]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347627_NTR_4_DATE_A_LIVE_II_II_02_BIG5_PSV_720P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE II 約會大作戰II][02][BIG5][PSV-720P]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347562_01_720P`() {
    kotlin.run {
    val r = parse("【附百度网盘】【修罗字幕组】【约会大作战　第二季】【01】【720P】【中日双语字幕】")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347561_02_720P`() {
    kotlin.run {
    val r = parse("【附百度网盘】【修罗字幕组】【约会大作战　第二季】【02】【720P】【中日双语字幕】")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347284_DATE_A_LIVE_II_02_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第02話][1280x720][MP4_PC&PSV兼容][繁體]（內詳）")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347151_DATE_A_LIVE_02_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][02][BIG5][四月新番][720P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347113_DATE_A_LIVE_02_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][02][GB][四月新番][720P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347087_DHR_DATE_A_LIVE_II_02_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][02][繁體][720P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347086_DHR_DATE_A_LIVE_II_02_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战 第二季_DATE A LIVE II][02][简体][720P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347079_Date_a_Live___02_BIG5_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕組】約會大作戰Ⅱ Date a Live Ⅱ - 02[BIG5][720P][PSV&PC][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347072_Date_a_Live___02_GB_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组】约会大作战Ⅱ Date a Live Ⅱ - 02[GB][720P][PSV&PC][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347018_Date_A_Live_II_02_GB_1280_720_MP4`() {
    kotlin.run {
    val r = parse("【萌物百科字幕组】[约会大作战 第二季_Date A Live II][02][GB][1280×720][MP4](内附网盘地址）")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `346887_KNA_2_Date_A_Live_ll_02_720P_MP4`() {
    kotlin.run {
    val r = parse("【恶俗×KNA】[约会大作战 2期][Date A Live ll][02][720P][简体][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `346873_4_Date_A_Live_II_II_02_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live II 约会大作战II 第02话 MP4 720p")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `346852_TUcaptions_2014_02_720P_MP4_BIG5`() {
    kotlin.run {
    val r = parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 02 // 720P-MP4-BIG5(繁)")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `346798_4_Date_A_Live_II_02_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[02][BIG5][720P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `346774_4_Date_A_Live_II_02_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[02][GB][720P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `346657_4_Date_A_Live_II_II_01_MKV_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live II 约会大作战II 第01话 MKV 720p 简繁外挂")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `346295_DATE_A_LIVE_01_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][01][BIG5][四月新番][720P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `346176_DATE_A_LIVE_01_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][01][GB][四月新番][720P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `346128_NTR_4_DATE_A_LIVE_II_II_01_BIG5_PSP_480P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE II 約會大作戰II][01][BIG5][PSP-480P]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `346127_NTR_4_DATE_A_LIVE_II_II_01_BIG5_PSV_720P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE II 約會大作戰II][01][BIG5][PSV-720P]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `345955_Date_a_Live___01_BIG5_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕組】約會大作戰Ⅱ Date a Live Ⅱ - 01[BIG5][720P][PSV&PC][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `345953_Date_a_Live___01_GB_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组】约会大作战Ⅱ Date a Live Ⅱ - 01[GB][720P][PSV&PC][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `345819_KNA_2_Date_A_Live_ll_01_720P_MP4`() {
    kotlin.run {
    val r = parse("【恶俗×KNA】[约会大作战 2期][Date A Live ll][01][720P][简体][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `345812_TUcaptions_2014_01_720P_MP4_BIG5`() {
    kotlin.run {
    val r = parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 01 // 720P-MP4-BIG5(繁)")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `345796_4_Date_A_Live_II_II_01_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live II 约会大作战II 第01话 MP4 720p")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `345773_Date_A_Live_II_01_GB_1280_720_MP4`() {
    kotlin.run {
    val r = parse("[萌物百科字幕组][约会大作战 第二季_Date A Live II][01][GB][1280×720][MP4](内附网盘地址）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `345772_DHR_DATE_A_LIVE_II_01_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战 第二季_DATE A LIVE II][01][简体][720P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `345766_DHR_DATE_A_LIVE_II_01_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][01][繁體][720P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `345727_4_Date_A_Live_II_01_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[01][BIG5][720P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `345722_DATE_A_LIVE_II_01_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第01話][1280x720][MP4_PC&PSV兼容][繁體]（翻譯、校對招募中）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `345715_4_Date_A_Live_II_01_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[01][GB][720P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `343090_DATE_A_LIVE_01_12_OAD_BDRIP_720P_x264_AAC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第01～12+OAD話][BDRIP_720P][x264_AAC][繁體內嵌]")
    assertEquals("01..12+OAD", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `342780_ANK_Raws_DATE_A_LIVE_VOL_06_BDRIP_1080P_x264_Hi10P_FLAC`() {
    kotlin.run {
    val r =
        parse("【千夏字幕＆ANK-Raws】【約會大作戰_DATE_A_LIVE】[VOL.06][BDRIP_1080P][x264-Hi10P_FLAC][繁體外掛]（內詳")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `342779_ANK_Raws_DATE_A_LIVE_VOL_05_BDRIP_1080P_x264_Hi10P_FLAC`() {
    kotlin.run {
    val r =
        parse("【千夏字幕＆ANK-Raws】【約會大作戰_DATE_A_LIVE】[VOL.05][BDRIP_1080P][x264-Hi10P_FLAC][繁體外掛]（內詳")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `342778_ANK_Raws_DATE_A_LIVE_VOL_04_BDRIP_1080P_x264_Hi10P_FLAC`() {
    kotlin.run {
    val r =
        parse("【千夏字幕＆ANK-Raws】【約會大作戰_DATE_A_LIVE】[VOL.04][BDRIP_1080P][x264-Hi10P_FLAC][繁體外掛]（內詳")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `342777_ANK_Raws_DATE_A_LIVE_VOL_01_06_OAD_BDRIP_1080P_x264_Hi10P_FLAC`() {
    kotlin.run {
    val r =
        parse("【千夏字幕＆ANK-Raws】【約會大作戰_DATE_A_LIVE】[VOL.01～06+OAD][BDRIP_1080P][x264-Hi10P_FLAC][繁體外掛]（內詳")
    assertEquals("01..06+OAD", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `337409_undefined`() {
    kotlin.run {
    val r = parse("約會大作戰 時崎狂三野艷舞蹈狂露底褲")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `336790_TUcaptions_2013_DATE_A_LIVE_01_13End_BD1080P_MKV_BIG5`() {
    kotlin.run {
    val r =
        parse("[TUcaptions] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 01~13End // BD1080P 10bit-MKV-BIG5(繁)【附特典】")
    assertEquals("01..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `335879_DHR_DATE_A_LIVE_13_OVA_480P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][13話_OVA][繁體][480P][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `335868_DHR_DATE_A_LIVE_13_OVA_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][13话_OVA][简体][720P][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `335867_DHR_DATE_A_LIVE_13_OVA_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][13話_OVA][繁體][720P][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `335447_PV_2`() {
    kotlin.run {
    val r = parse("約會大作戰Ⅱ 先行ロングPV(2分鐘版)")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `332129_NTR_4_DATE_A_LIVE_13_BIG5_PSV_PSP`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][13][BIG5][PSV&PSP][未放送話]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `332078_PV`() {
    kotlin.run {
    val r = parse("約會大作戰 第二季 PV")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `331843_TUcaptions_2013_DATE_A_LIVE_13_TV_720P_MP4_BIG5`() {
    kotlin.run {
    val r =
        parse("[TUcaptions] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 13(TV未放送) // 720P-MP4-BIG5(繁)")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `330563_Date_A_Live_TV_13_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 TV未放送第13话 MP4 720p")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `329968_ANK_Raws_DATE_A_LIVE_VOL_OVA_BDRIP_1080P_x264_Hi10P_FLAC`() {
    kotlin.run {
    val r = parse("【千夏字幕＆ANK-Raws】【約會大作戰_DATE_A_LIVE】[VOL.OVA][BDRIP_1080P][x264-Hi10P_FLAC][繁體外掛]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `329719__11_Date_a_Live_BDRIP_720P_X264_AAC`() {
    kotlin.run {
    val r = parse("[异域-11番小队][约会大作战Date a Live][BDRIP][720P][X264_AAC]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `329654_4_DATE_A_LIVE_13_TV_720p_BIG5_MP4`() {
    kotlin.run {
    val r = parse("【極影字幕社】★約會大作戰_DATE A LIVE 第13話(TV未放送) 720p BIG5 MP4")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `329630_DATE_A_LIV_E_13_TV_720p_GB_MP4`() {
    kotlin.run {
    val r = parse("【极影字幕社】★约会大作战_DATE A LIVE 第13话(TV未放送) 720p GB MP4")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `329601_ANK_Raws_DATE_A_LIVE_VOL_02_03_BDRIP_1080P_x264_Hi10P_FLAC`() {
    kotlin.run {
    val r =
        parse("【千夏字幕＆ANK-Raws】【約會大作戰_DATE_A_LIVE】[VOL.02～03][BDRIP_1080P][x264-Hi10P_FLAC][繁體外掛]（內詳")
    assertEquals("02..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `329504_DATE_A_LIVE_13_TV_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第13话_TV未放送][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `329320_Date_a_Live___13_GB_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组】约会大作战 Date a Live - 13[GB][720P][PSV&PC][MP4](成員招募)")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `329308_Date_a_Live___13_BIG5_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕組】約會大作戰 Date a Live - 13[BIG5][720P][PSV&PC][MP4](成員招募)")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `329170_DATE_A_LIVE_13_TV_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第13話_TV未放送][1280x720][MP4_PC&PSV兼容][繁體]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `325935_ANK_Raws_DATE_A_LIVE_VOL_02_BDRIP_1080P_x264_Hi10P_FLAC`() {
    kotlin.run {
    val r = parse("【千夏字幕＆ANK-Raws】【約會大作戰_DATE_A_LIVE】[VOL.02][BDRIP_1080P][x264-Hi10P_FLAC][繁體外掛]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `325791_Date_A_Live_BDRip_Vol_5`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 BDRip Vol.5 简繁外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `323712_Date_A_Live_BDRip_Vol_4`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 BDRip Vol.4 简繁外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `323325_Date_A_Live_BDRip_Vol_3`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 BDRip Vol.3 简繁外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `323220_Date_A_Live_BDRip_Vol_1_2`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 BDRip Vol.1-2 简繁外挂")
    assertEquals("01..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `316264_1_12END_BIG5_RMVB_848X480`() {
    kotlin.run {
    val r = parse("【华盟字幕社&澄空学园】[约会大作战][1-12END] [BIG5][RMVB][848X480]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `315091_DHR_DATE_A_LIVE_12_480P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][全12話][繁體][480P][MP4](合集版本)")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `315092_DHR_DATE_A_LIVE_12_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][全12话][简体][720P][MP4](合集版本)")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `315090_DHR_DATE_A_LIVE_12_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][全12話][繁體][720P][MP4](合集版本)")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `313005_DHR_DATE_A_LIVE_01_12_720P_AVC_Hi10P_AAC_MKV`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][01-12全][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `312310_FREEWIND_DATE_A_LIVE_1080P_BDRIP_BD2_3_4_SP`() {
    kotlin.run {
    val r = parse("◆[漫游FREEWIND工作室] DATE A LIVE 約會大作戰 1080P BDRIP BD2 第3~4話+SP")
    assertEquals("03..04+SP", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `309228_NTR_4_DATE_A_LIVE_1_12_BIG5_PSP_480P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][1-12][BIG5][PSP-480P][合集版本]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `309227_NTR_4_DATE_A_LIVE_1_12_BIG5_PSV_720P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][1-12][BIG5][PSV-720P][合集版本]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `309215_DATE_A_LIVE_01_02_BDRIP_720P_x264_AAC`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第01～02話][BDRIP_720P][x264_AAC][繁體內嵌](戰勇內詳)")
    assertEquals("01..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `308635_4_DATE_A_LIVE_011v2_12v2_BIG5_1280x720_MP4`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][011v2-12v2][BIG5][1280x720][MP4]")
    assertEquals("11..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `308521_TUcaptions_THK_2013_DATE_A_LIVE_1_12End_720P_MP4_BIG5__21_21`() {
    kotlin.run {
    val r =
        parse("[TUcaptions] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 01v2~12v2 // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `308395_DHR_DATE_A_LIVE_12_720P_AVC_Hi10P_AAC_MKV`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][12完][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `308281_FREEWIND_DATE_A_LIVE_1080P_BDRIP_1_2_SP`() {
    kotlin.run {
    val r = parse("◆[漫游FREEWIND工作室] DATE A LIVE 約會大作戰 1080P BDRIP 第1~2話+SP")
    assertEquals("01..02+SP", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `307934_ANK_Raws_DATE_A_LIVE_VOL_01_BDRIP_1080P_x264_Hi10P_FLAC`() {
    kotlin.run {
    val r = parse("【千夏字幕＆ANK-Raws】【約會大作戰_DATE_A_LIVE】[VOL.01][BDRIP_1080P][x264-Hi10P_FLAC][繁體外掛]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `307844_4_DATE_A_LIVE_01_12_BIG5_1280x720_MP4`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][01-12][BIG5][1280x720][MP4]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `307689_Date_A_Live_01_12_MKV_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第01-12话 MKV 720p 简繁外挂 合集")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `307655_NTR_4_DATE_A_LIVE_12_FIN_BIG5_PSV_PSP`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][12][FIN][BIG5][PSV&PSP]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `307647_4_DATE_A_LIVE_11_12_BIG5_1024x576_MP4`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][11-12][BIG5][1024x576][MP4][招組員]")
    assertEquals("11..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `307620_Date_A_Live_01_12_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第01-12话 MP4 720p 合集")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `307544_DATE_A_LIVE_01_12_1440x810_x264_Hi10P_MP4`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第01～12話_完][1440x810][x264-Hi10P_MP4][簡繁外掛]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `307439_DATE_A_LIVE_01_12_X264_AAC_720P_MKV`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第01-12話 完結合集 简繁外挂字幕 X264_AAC 720P MKV")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `307401_4_DATE_A_LIVE_09_10_BIG5_1280x720_MP4`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][09-10][BIG5][1280x720][MP4]")
    assertEquals("09..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `307316_Date_a_Live_01_12_GB_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live 01-12[GB][720P][PSV&PC][MP4]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `307314_Date_a_Live_01_12_BIG5_480P_PSP_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live 01-12[BIG5][480P][PSP&PC][MP4]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `307141_Date_a_Live___12_GB_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 12[GB][720P][PSV&PC][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `307122_Date_a_Live___12_BIG5_480P_PSP_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 12[BIG5][480P][PSP&PC][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `307027_DHR_DATE_A_LIVE_11_720P_AVC_Hi10P_AAC_MKV`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][11][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `307011_DATE_A_LIVE_12v2_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第12v2話_完][1280x720][MP4_PC&PSV兼容][繁體]（換源補上二期預告）")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306992_DATE_A_LIVE_12_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第12話 完結 BIG5繁體 720P MP4")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306991_DATE_A_LIVE_12_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第12话 完结 GB简体 720P MP4")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306969_Date_A_Live_11_MKV_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第11话 MKV 720p 简繁外挂")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `306965_DATE_A_LIVE_12_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第12话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306958_DHR_DATE_A_LIVE_12_480P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][12完][繁體][480P][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306957_DHR_DATE_A_LIVE_12_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][12完][简体][720P][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306951_DHR_DATE_A_LIVE_12_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][12完][繁體][720P][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306928_TUcaptions_THK_2013_DATE_A_LIVE_12End_720P_MP4_BIG5__21_21`() {
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 12End // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306916_4_DATE_A_LIVE_12_720p_BIG5_MP4`() {
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE第12話 720p BIG5 MP4[第一季完]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306911_Date_A_Live_12_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第12话 MP4 720p 完")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306910_4_DATE_A_LIVE_12_720p_GB_MP4`() {
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第12话 720p GB MP4[第一季完]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306600_NTR_4_DATE_A_LIVE_11_BIG5_PSP_480P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][11][BIG5][PSP-480P]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306599_NTR_4_DATE_A_LIVE_11_BIG5_PSV_720P_7`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][11][BIG5][PSV-720P]<招募7月新番挖坑員>")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306322_Date_a_Live___11_GB_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 11[GB][720P][PSV&PC][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306300_Date_a_Live___11_BIG5_480P_PSP_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 11[BIG5][480P][PSP&PC][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306282_DATE_A_LIVE_11_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第11话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306255_DATE_A_LIVE_12_848x480_MP4_NICO`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第12話_完][848x480][MP4_NICO先行][繁體]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306198_4_DATE_A_LIVE_10_BIG5_1024x576_MP4`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][10][BIG5][1024x576][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306170_DHR_DATE_A_LIVE_10_720P_AVC_Hi10P_AAC_MKV`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][10][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `306157_DATE_A_LIVE_11_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第11話 BIG5繁體 720P MP4")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306156_DATE_A_LIVE_11_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第11话 GB简体 720P MP4")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306150_DHR_DATE_A_LIVE_11_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][11][简体][720P][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306138_DHR_DATE_A_LIVE_11_480P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][11][繁體][480P][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306128_DHR_DATE_A_LIVE_11_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][11][繁體][720P][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306115_4_DATE_A_LIVE_11_720p_BIG5_MP4`() {
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE 第11話 720p BIG5 MP4")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306112_4_DATE_A_LIVE_11_720p_GB_MP4`() {
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第11话 720p GB MP4")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306111_DATE_A_LIVE_11_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第11話][1280x720][MP4_PC&PSV兼容][繁體]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306109_Date_A_Live_11_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第11话 MP4 720p")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306108_TUcaptions_THK_2013_DATE_A_LIVE_11_720P_MP4_BIG5__21_21`() {
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 11 // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306027_Date_A_Live_10_MKV_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第10话 MKV 720p 简繁外挂")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `306019_DATE_A_LIVE_10_1440x810_x264_Hi10P_MP4`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第10話][1440x810][x264-Hi10P_MP4][簡繁外掛]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `305867_4_DATE_A_LIVE_09_BIG5_1024x576_MP4`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][09][BIG5][1024x576][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `305814_NTR_4_DATE_A_LIVE_10_BIG5_PSP_480P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][10][BIG5][PSP-480P]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `305813_NTR_4_DATE_A_LIVE_10_BIG5_PSV_720P_7`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][10][BIG5][PSV-720P] ( 7月新番招募組員")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `305510_DATE_A_LIVE_11_848x480_MP4_NICO`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第11話][848x480][MP4_NICO先行][繁體]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `305478_Date_a_Live___10_BIG5_480P_PSP_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 10[BIG5][480P][PSP&PC][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `305477_Date_a_Live___10_GB_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 10[GB][720P][PSV&PC][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `305443_DHR_DATE_A_LIVE_09_720P_AVC_Hi10P_AAC_MKV`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][09][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `305415_Date_A_Live_09_MKV_720p_EDPV`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第09话 MKV 720p 简繁外挂 附EDPV")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `305410_DATE_A_LIVE_10_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第10話 BIG5繁體 720P MP4")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `305409_DATE_A_LIVE_10_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第10话 GB简体 720P MP4")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `305390_DATE_A_LIVE_10_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第10话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `305372_DHR_DATE_A_LIVE_10_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][10][简体][720P][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `305371_DHR_DATE_A_LIVE_10_480P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][10][繁體][480P][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `305366_DHR_DATE_A_LIVE_10_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][10][繁體][720P][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `305347_4_DATE_A_LIVE_10_720p_BIG5_MP4`() {
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE 第10話 720p BIG5 MP4")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `305346_4_DATE_A_LIVE_10_720p_GB_MP4`() {
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第10话 720p GB MP4")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `305342_DATE_A_LIVE_09_1440x810_x264_Hi10P_MP4`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第09話][1440x810][x264-Hi10P_MP4][簡繁外掛]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `305340_DATE_A_LIVE_10_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第10話][1280x720][MP4_PC&PSV兼容][繁體]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `305333_Date_A_Live_10_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第10话 MP4 720p")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `305332_TUcaptions_THK_2013_DATE_A_LIVE_10_720P_MP4_BIG5__21_21`() {
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 10 // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `304836_NTR_4_DATE_A_LIVE_09_BIG5_PSP_480P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][09][BIG5][PSP-480P]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `304835_NTR_4_DATE_A_LIVE_09_BIG5_PSV_720P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][09][BIG5][PSV-720P] ( 招募時軸")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `304790_DATE_A_LIVE_10_848x480_MP4_NICO`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第10話][848x480][MP4_NICO先行][繁體]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `304614_Date_a_Live___09_BIG5_480P_PSP_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 09[BIG5][480P][PSP&PC][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `304612_Date_a_Live___09_GB_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 09[GB][720P][PSV&PC][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `304565_DHR_DATE_A_LIVE_08_720P_AVC_Hi10P_AAC_MKV`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][08][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `304540_DATE_A_LIVE_09_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第09話 BIG5繁體 720P MP4")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `304539_DATE_A_LIVE_09_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第09话 GB简体 720P MP4")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `304532_DATE_A_LIVE_09_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第09话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `304509_4_DATE_A_LIVE_09_720p_BIG5_MP4`() {
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE 第09話 720p BIG5 MP4")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `304508_4_DATE_A_LIVE_09_720p_GB_MP4`() {
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第09话 720p GB MP4")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `304482_DHR_DATE_A_LIVE_09_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][09][简体][720P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `304481_DHR_DATE_A_LIVE_09_480P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][09][繁體][480P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `304473_DHR_DATE_A_LIVE_09_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][09][繁體][720P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `304472_DATE_A_LIVE_09_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第09話][1280x720][MP4_PC&PSV兼容][繁體]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `304467_Date_A_Live_09_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第09话 MP4 720p")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `304460_TUcaptions_THK_2013_DATE_A_LIVE_09_720P_MP4_BIG5__21_21`() {
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 09 // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `304309_Date_A_Live_08_MKV_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第08话 MKV 720p 简繁外挂")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `304284_4_DATE_A_LIVE_08_BIG5_1280x720_MP4`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][08][BIG5][1280x720][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `304171_DATE_A_LIVE_01_08_1440x810_x264_Hi10P_MP4`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第01～08話][1440x810][x264-Hi10P_MP4][簡繁外掛](內詳)")
    assertEquals("01..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `304139_4_DATE_A_LIVE_08_BIG5_1024x576_MP4`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][08][BIG5][1024x576][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `304105_DATE_A_LIVE_08_X264_AAC_720P_MKV`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第08話 简繁外挂字幕 X264_AAC 720P MKV")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `304072_NTR_4_DATE_A_LIVE_08_BIG5_PSP_480P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][08][BIG5][PSP-480P]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `304071_NTR_4_DATE_A_LIVE_08_BIG5_PSV_720P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][08][BIG5][PSV-720P] ( 招募時軸")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `303711_DATE_A_LIVE_09_848x480_MP4_NICO`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第09話][848x480][MP4_NICO先行][繁體]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `303653_Date_a_Live___08_BIG5_480P_PSP_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 08[BIG5][480P][PSP&PC][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `303649_Date_a_Live___08_GB_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 08[GB][720P][PSV&PC][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `303644_DHR_DATE_A_LIVE_07_720P_AVC_Hi10P_AAC_MKV`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][07][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `303621_Date_A_Live_07_MKV_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第07话 MKV 720p 简繁外挂")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `303617_DATE_A_LIVE_08_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第08話 BIG5繁體 720P MP4")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `303616_DATE_A_LIVE_08_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第08话 GB简体 720P MP4")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `303612_4_DATE_A_LIVE_06_07_BIG5_1280x720_MP4`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][06-07][BIG5][1280x720][MP4]")
    assertEquals("06..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `303593_DATE_A_LIVE_08_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第08话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `303568_DHR_DATE_A_LIVE_08_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][08][简体][720P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `303567_DHR_DATE_A_LIVE_08_480P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][08][繁體][480P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `303555_DATE_A_LIVE_08_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第08話][1280x720][MP4_PC&PSV兼容][繁體]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `303553_DHR_DATE_A_LIVE_08_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][08][繁體][720P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `303546_4_DATE_A_LIVE_0_720p_BIG5_MP4`() {
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE 第08話 720p BIG5 MP4")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `303544_4_DATE_A_LIVE_08_720p_GB_MP4`() {
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第08话 720p GB MP4")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `303543_Date_A_Live_08_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第08话 MP4 720p")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `303541_TUcaptions_THK_2013_DATE_A_LIVE_08_720P_MP4_BIG5__21_21`() {
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 08 // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `303336_4_DATE_A_LIVE_07_BIG5_1024x576_MP4`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][07][BIG5][1024x576][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `303273_4_DATE_A_LIVE_06_BIG5_1024x576_MP4`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][06][BIG5][1024x576][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `303208_Date_A_Live_06_MKV_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第06话 MKV 720p 简繁外挂")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `303185_DATE_A_LIVE_07_X264_AAC_720P_MKV`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第07話 简繁外挂字幕 X264_AAC 720P MKV")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `303045_NTR_4_DATE_A_LIVE_07_BIG5_PSP_480P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][07][BIG5][PSP-480P]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `303044_NTR_4_DATE_A_LIVE_07_BIG5_PSV_720P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][07][BIG5][PSV-720P] ( 招募時軸")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `302911_DATE_A_LIVE_08_848x480_MP4_NICO`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第08話][848x480][MP4_NICO先行][繁體]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `302803_DHR_DATE_A_LIVE_06_720P_AVC_Hi10P_AAC_MKV`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][06][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `302791_4_DATE_A_LIVE_07_720p_BIG5_MP4`() {
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE 第07話 720p BIG5 MP4")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `302790_4_DATE_A_LIVE_07_720p_GB_MP4`() {
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第07话 720p GB MP4")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `302783_Date_a_Live___07_BIG5_480P_PSP_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 07[BIG5][480P][PSP&PC][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `302775_Date_a_Live___07_GB_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 07[GB][720P][PSV&PC][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `302758_DATE_A_LIVE_07_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第07話 BIG5繁體 720P MP4")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `302757_DATE_A_LIVE_07_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第07话 GB简体 720P MP4")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `302753_DATE_A_LIVE_07_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第07话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `302746_DATE_A_LIVE_07_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第07話][1280x720][MP4_PC&PSV兼容][繁體]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `302737_DHR_DATE_A_LIVE_07_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][07][简体][720P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `302736_DHR_DATE_A_LIVE_07_480P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][07][繁體][480P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `302730_DHR_DATE_A_LIVE_07_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][07][繁體][720P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `302718_Date_A_Live_07_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第07话 MP4 720p")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `302717_TUcaptions_THK_2013_DATE_A_LIVE_07_720P_MP4_BIG5__21_21`() {
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 07 // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `302555_DATE_A_LIVE_06_X264_AAC_720P_MKV`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第06話 简繁外挂字幕 X264_AAC 720P MKV")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `302386_NTR_4_DATE_A_LIVE_06_BIG5_PSP_480P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][06][BIG5][PSP-480P]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `302385_NTR_4_DATE_A_LIVE_06_BIG5_PSV_720P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][06][BIG5][PSV-720P] ( 招募校對")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `302347_DATE_A_LIVE_07_848x480_MP4_NICO`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第07話][848x480][MP4_NICO先行][繁體](重要內詳)")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `302013_DATE_A_LIVE_06_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第06話 BIG5繁體 720P MP4")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `302012_DATE_A_LIVE_06_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第06话 GB简体 720P MP4")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301971_Date_a_Live___06_BIG5_480P_PSP_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 06[BIG5][480P][PSP&PC][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301969_Date_a_Live___06_GB_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 06[GB][720P][PSV&PC][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301965_DATE_A_LIVE_06_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第06话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301964_Date_A_Live_05_MKV_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第05话 MKV 720p 简繁外挂")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `301952_DHR_DATE_A_LIVE_05_720P_AVC_Hi10P_AAC_MKV`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][05][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `301938_DHR_DATE_A_LIVE_06_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][06][简体][720P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301937_DHR_DATE_A_LIVE_06_480P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][06][繁體][480P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301917_DHR_DATE_A_LIVE_06_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][06][繁體][720P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301914_DATE_A_LIVE_06_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第06話][1280x720][MP4_PC&PSV兼容][繁體](招募片源錄製)")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301904_TUcaptions_THK_2013_DATE_A_LIVE_06_720P_MP4_BIG5__21_21`() {
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 06 // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301903_4_DATE_A_LIVE_06_720p_BIG5_MP4`() {
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE 第06話 720p BIG5 MP4")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301899_Date_A_Live_06_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第06话 MP4 720p")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301895_4_DATE_A_LIVE_06_720p_GB_MP4`() {
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第06话 720p GB MP4")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301629_4_DATE_A_LIVE_05_BIG5_1280x720_MP4`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][05][BIG5][1280x720][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301581_DATE_A_LIVE_05_X264_AAC_720P_MKV`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第05話 简繁外挂字幕 X264_AAC 720P MKV")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `301501_NTR_4_DATE_A_LIVE_05_BIG5_PSP_480P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][05][BIG5][PSP-480P]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301500_NTR_4_DATE_A_LIVE_05_BIG5_PSV_720P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][05][BIG5][PSV-720P] ( 招募校對")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301395_4_DATE_A_LIVE_04_BIG5_1280x720_MP4`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][04][BIG5][1280x720][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301351_4_DATE_A_LIVE_05_BIG5_1024x576_MP4`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][05][BIG5][1024x576][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301350_Date_a_Live___05_BIG5_480P_PSP_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 05[BIG5][480P][PSP&PC][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301349_Date_a_Live___05_GB_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 05[GB][720P][PSV&PC][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301200_Date_A_Live_04_MKV_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第04话 MKV 720p 简繁外挂")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `301173_DATE_A_LIVE_06_848x480_MP4_NICO`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第06話][848x480][MP4_NICO先行][繁體](重要內詳)")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301028_DATE_A_LIVE_05_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第05話 BIG5繁體 720P MP4")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301027_DATE_A_LIVE_05_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第05话 GB简体 720P MP4")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301015_DATE_A_LIVE_05_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第05话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301002_DHR_DATE_A_LIVE_05_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][05][简体][720P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301001_DHR_DATE_A_LIVE_05_480P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][05][繁體][480P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300997_DATE_A_LIVE_05_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第05話][1280x720][MP4_PC&PSV兼容][繁體](招募片源錄製)")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300996_DHR_DATE_A_LIVE_05_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][05][繁體][720P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300995_DHR_DATE_A_LIVE_04_720P_AVC_Hi10P_AAC_MKV`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][04][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `300987_4_DATE_A_LIVE_04_BIG5_1024x576_MP4`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][04][BIG5][1024x576][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300986_4_DATE_A_LIVE_05_720p_BIG5_MP4`() {
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE 第05話 720p BIG5 MP4")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300976_Date_A_Live_05_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第05话 MP4 720p")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300970_4_DATE_A_LIVE_05_720p_GB_MP4`() {
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第05话 720p GB MP4")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300969_TUcaptions_THK_2013_DATE_A_LIVE_05_720P_MP4_BIG5__21_21`() {
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 05 // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300724_DATE_A_LIVE_04_X264_AAC_720P_MKV`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第04話 简繁外挂字幕 X264_AAC 720P MKV")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `300651_DATE_A_LIVE_03_1440x810_x264_Hi10P_MP4`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第03話][1440x810][x264-Hi10P_MP4][簡繁外掛]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `300606_4_DATE_A_LIVE_03_BIG5_1280x720_MP4`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][03][BIG5][1280x720][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300389_NTR_4_DATE_A_LIVE_04_BIG5_PSP_480P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][04][BIG5][PSP-480P]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300387_NTR_4_DATE_A_LIVE_04_BIG5_PSV_720P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][04][BIG5][PSV-720P]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300320_DATE_A_LIVE_05_848x480_MP4_NICO`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第05話][848x480][MP4_NICO先行][繁體](重要內詳)")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300251_DATE_A_LIVE_04_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第04话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300187_4_DATE_A_LIVE_03_BIG5_1024x576_MP4`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][03][BIG5][1024x576][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300161_Date_a_Live___04_GB_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 04[GB][720P][PSV&PC][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300160_Date_a_Live___04_BIG5_480P_PSP_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 04[BIG5][480P][PSP&PC][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300129_DATE_A_LIVE_04_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第04話 BIG5繁體 720P MP4")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300127_DATE_A_LIVE_04_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第04话 GB简体 720P MP4")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300102_4_Date_A_Live_03_MKV_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live 约会大作战 第03话 MKV 720p 简繁外挂")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `300085_DHR_DATE_A_LIVE_04_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][04][简体][720P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300084_DHR_DATE_A_LIVE_04_480P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][04][繁體][480P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300072_DHR_DATE_A_LIVE_04_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][04][繁體][720P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300071_DHR_DATE_A_LIVE_03_720P_AVC_Hi10P_AAC_MKV`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][03][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `300068_DATE_A_LIVE_04_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第04話][1280x720][MP4_PC&PSV兼容][繁體](招募片源錄製)")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300062_4_DATE_A_LIVE_04_720p_BIG5_MP4`() {
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE 第04話 720p BIG5 MP4")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300061_TUcaptions_THK_2013_DATE_A_LIVE_04_720P_MP4_BIG5__21_21`() {
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 04 // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300060_4_Date_A_Live_04_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live 约会大作战 第04话 MP4 720p")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300058_4_DATE_A_LIVE_04_720p_GB_MP4`() {
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第04话 720p GB MP4")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `299846_DATE_A_LIVE_03_X264_AAC_720P_MKV`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第03話 简繁外挂字幕 X264_AAC 720P MKV")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `299728_Date_a_Live___03_BIG5_480P_PSP_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 03[BIG5][480P][PSP&PC][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `299727_Date_a_Live___03_GB_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 03[GB][720P][PSV&PC][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `299643_Date_a_Live___02_BIG5_480P_PSP_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 02[BIG5][480P][PSP&PC][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `299642_Date_a_Live___02_GB_720P_PSV_PC_MP4`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 02[GB][720P][PSV&PC][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `299354_4_DATE_A_LIVE_02_BIG5_1280x720_MP4`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][02][BIG5][1280x720][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `299333_DATE_A_LIVE_04_848x480_MP4_NICO`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第04話][848x480][MP4_NICO先行][繁體]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `299301_NTR_4_DATE_A_LIVE_03_BIG5_PSP_480P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][03][BIG5][PSP-480P]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `299295_NTR_4_DATE_A_LIVE_03_BIG5_PSV_720P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][03][BIG5][PSV-720P](雅安加油！")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `299275_DATE_A_LIVE_03_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第03话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `299265_DHR_DATE_A_LIVE_03_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][03][简体][720P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `299264_DHR_DATE_A_LIVE_03_480P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][03][繁體][480P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `299249_DHR_DATE_A_LIVE_03_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][03][繁體][720P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `299194_4_Date_A_Live_02_MKV_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live 约会大作战 第02话 MKV 720p 简繁外挂")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `299189_DATE_A_LIVE_03_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第03話 BIG5繁體 720P MP4")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `299188_DATE_A_LIVE_03_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第03话 GB简体 720P MP4")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `299176_DATE_A_LIVE_03_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第03話][1280x720][MP4_PC&PSV兼容][繁體](招募片源錄製)")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `299166_TUcaptions_THK_2013_DATE_A_LIVE_03_720P_MP4_BIG5__21_21`() {
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 03 // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `299154_4_DATE_A_LIVE_03_720p_BIG5_MP4`() {
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE 第03話 720p BIG5 MP4")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `299150_4_DATE_A_LIVE_03_720p_GB_MP4`() {
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第03话 720p GB MP4")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `299148_4_Date_A_Live_03_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live 约会大作战 第03话 MP4 720p")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `299138_DHR_DATE_A_LIVE_01_02_720P_AVC_Hi10P_AAC_MKV`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][01-02][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("01..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `299055_4_DATE_A_LIVE_02_BIG5_1024x576_MP4`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][02][BIG5][1024x576][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `298929_DATE_A_LIVE_02_X264_AAC_720P_MKV`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第02話 简繁外挂字幕 X264_AAC 720P MKV")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `298722_DATE_A_LIVE_02_BIG5_1280X720_Mp4`() {
    kotlin.run {
    val r = parse("【风之圣殿字幕组】【★四月新番】[约会大作战 DATE A LIVE][02][BIG5][1280X720][Mp4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `298514_NTR_4_DATE_A_LIVE_02_BIG5_PSP_480P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][02][BIG5][PSP-480P]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `298512_NTR_4_DATE_A_LIVE_02_BIG5_PSV_720P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][02][BIG5][PSV-720P](招募校對、後期工")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `298433_DATE_A_LIVE_02_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第02話 BIG5繁體 720P MP4")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `298432_DATE_A_LIVE_02_GB_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第02话 GB简体 720P MP4")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `298428_DATE_A_LIVE_03_848x480_MP4_NICO`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第03話][848x480][MP4_NICO先行][繁體]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `298328_DATE_A_LIVE_02_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第02话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `298327_DHR_DATE_A_LIVE_02_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][02][简体][720P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `298326_DHR_DATE_A_LIVE_02_480P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][02][繁體][480P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `298313_DHR_DATE_A_LIVE_02_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][02][繁體][720P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `298309_TUcaptions_THK_2013_DATE_A_LIVE_01v2_02_720P_MP4_BIG5__21_21`() {
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 01v2-02 // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("01..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `298307_DATE_A_LIVE_02_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第02話][1280x720][MP4_PC&PSV兼容][繁體](招募片源錄製)")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `298304_4_DATE_A_LIVE_02_720p_BIG5_MP4`() {
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE 第02話 720p BIG5 MP4【字幕社招募片源录制】")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `298301_4_DATE_A_LIVE_02_720p_GB`() {
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第02话 720p GB【字幕社招募片源录制】")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `298293_4_Date_A_Live_02_MP4_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live 约会大作战 第02话 MP4 720p")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `298265_4_Date_A_Live_01_MKV_720p`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live 约会大作战 第01话 MKV 720p 简繁外挂")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `298121_4_DATE_A_LIVE_01_BIG5_1280x720_MP4`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][01][BIG5][1280x720][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `298078_DATE_A_LIVE_01_1440x810_x264_Hi10P_MP4_R_I_P`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第01話][1440x810][x264-Hi10P_MP4][簡繁外掛]（山口昇老師R.I.P）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `297985_4_DATE_A_LIVE_01_BIG5_1024x576_MP4`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][01][BIG5][1024x576][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `297960_DATE_A_LIVE_01_X264_AAC_720P_MKV`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第01話 简繁外挂字幕 X264_AAC 720P MKV")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `297773_NTR_4_DATE_A_LIVE_01_BIG5_PSP_480P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][01][BIG5][PSP-480P]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `297767_NTR_4_DATE_A_LIVE_01_BIG5_PSV_720P`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][01][BIG5][PSV-720P](招募後期工")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `297699_DATE_A_LIVE_01_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第01话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `297631_DATE_A_LIVE_02_848x480_MP4_NICO`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第02話][848x480][MP4_NICO先行][繁體]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `297554_DATE_A_LIVE_01_1280x720_MP4_PC_PSV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第01話][1280x720][MP4_PC&PSV兼容][繁體]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `297524_TUcaptions_THK_2013_DATE_A_LIVE_01_720P_MP4_BIG5__21_21`() {
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 01 // 720P-MP4-BIG5(繁)【新論壇正式開站!!】")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `297509_DATE_A_LIVE_01_BIG5_720P_MP4`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第01話 BIG5繁體 720P MP4")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `297508_DATE_A_LIVE_01_GB_720P`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第01话 GB简体 720P")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `297503_DHR_DATE_A_LIVE_01_480P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][01][繁體][480P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `297502_DHR_DATE_A_LIVE_01_720P_MP4`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][01][簡体][720P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `297489_4_01_720p_BIG5_MP4`() {
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰 第01話[720p] BIG5 MP4")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `297484_4_01_720p_GB_MP4`() {
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战 第01话[720p] GB MP4")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `297482_DHR_DATE_A_LIVE_01_720P_MP4`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][01][繁體][720P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `296759_DATE_A_LIVE_01_848x480_MP4_NICO`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第01話][848x480][MP4_NICO先行][繁體]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `296754_4_01_nico_854x480_BIG5_MP4`() {
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰 第01話[先行版][nico 854x480] BIG5 MP4")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `296737_4_01_nico_854x480_GB_MP4`() {
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战 第01话[先行版][nico 854x480] GB MP4")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `296718_DATE_A_LIVE_01_360P_MKV`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第01話][先行_360P][MKV][繁簡內掛]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("360P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `296665_date_a_live_01_nico_854x480_x264`() {
    kotlin.run {
    val r = parse("デート·ア·ライブ_date a live_約會大作戰[01][nico 854x480 x264]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `167544_1_Kaitou_Reinya_08_RV10`() {
    kotlin.run {
    val r = parse("【星尘字幕组】1月迷你新番【怪盗丽奈 Kaitou Reinya】【08 诶~丽奈约会大作战】【RV10】")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }
}

// @formatter:on
