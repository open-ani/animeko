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
 * 原名: `吹响吧_上低音号`
 * 数据源: `dmhy`
 *
 * 由 `test-codegen` 的 `GenerateTests.kt` 生成, 不要手动修改!
 * 如果你优化了解析器, 这些 test 可能会失败, 请检查是否它是因为以前解析错误而现在解析正确了. 
 * 如果是, 请更新测试数据: 执行 `GenerateTests.kt`.
 */
public class PatternTitleParserTest吹响吧_上低音号 : PatternBasedTitleParserTestSuite() {
  @Test
  public fun `670314_4_Hibike_21_Euphonium_S3_06_BIG5_1080P_MP4`() {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第06話 BIG5 1080P MP4（字幕社招人內詳）")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `670313_4_Hibike_21_Euphonium_S3_06_BIG5_720P_MP4`() {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第06話 BIG5 720P MP4（字幕社招人內詳）")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `670276_Hibike_21_Euphonium_3_07_WebRip_HEVC_AAC`() {
    val r = parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [07][WebRip][HEVC_AAC][简繁日内封][招募时轴]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `670275_Hibike_21_Euphonium_3_07_WebRip_HEVC_AAC`() {
    val r = parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [07][WebRip][HEVC_AAC][简日内嵌][招募时轴]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `670274_Hibike_21_Euphonium_3_07_WebRip_HEVC_AAC`() {
    val r = parse("[北宇治字幕组] 吹響吧！上低音號 第三季 / Hibike! Euphonium 3 [07][WebRip][HEVC_AAC][繁日內嵌][招募時軸]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `670253_4_Hibike_21_Euphonium_S3_06_GB_1080P_MP4`() {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第06话 GB 1080P MP4（字幕社招人内详）")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `670252_4_Hibike_21_Euphonium_S3_06_GB_720P_MP4`() {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第06话 GB 720P MP4（字幕社招人内详）")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `670215_3_Hibike_21_Euphonium_S3_06_1080p`() {
    val r = parse("[云光字幕组]吹响吧！上低音号 3 Hibike! Euphonium S3 [06][简体双语][1080p]招募翻译")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `670099_ANi___07_1080P_Bilibili_WEB_DL_AAC_AVC_CHT_CHS_MP4`() {
    val r =
        parse("[ANi] 吹響吧！上低音號 第三季（僅限港澳台地區） - 07 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `669963_Sub_Hibike_21_Euphonium_3_06_1080P_AVC_AAC_WebRip`() {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹響吧！上低音號 第三季 / Hibike! Euphonium 3][06][1080P][AVC AAC][繁日双语][WebRip]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669962_Sub_Hibike_21_Euphonium_3_06_1080P_AVC_AAC_WebRip`() {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹响吧！上低音号 第三季 / Hibike! Euphonium 3][06][1080P][AVC AAC][简日双语][WebRip]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669928_4_Hibike_21_Euphonium_S3_05_BIG5_1080P_MP4`() {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第05話 BIG5 1080P MP4（字幕社招人內詳）")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669927_4_Hibike_21_Euphonium_S3_05_BIG5_720P_MP4`() {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第05話 BIG5 720P MP4（字幕社招人內詳）")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669910_4_Hibike_21_Euphonium_S3_06_1080P_MP4`() {
    val r = parse("[漫猫字幕社][4月新番][吹响吧！上低音号 第三季][Hibike! Euphonium S3][06][1080P][MP4][简日双语]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669909_4_Hibike_21_Euphonium_S3_06_1080P_MP4`() {
    val r = parse("[漫貓字幕社][4月新番][吹響吧！上低音號 第三季][Hibike! Euphonium S3][06][1080P][MP4][繁日雙語]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669902_4_Hibike_21_Euphonium_S3_05_1080P_MP4`() {
    val r = parse("[漫貓字幕社][4月新番][吹響吧！上低音號 第三季][Hibike! Euphonium S3][05][1080P][MP4][繁日雙語]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669901_4_Hibike_21_Euphonium_S3_05_1080P_MP4`() {
    val r = parse("[漫猫字幕社][4月新番][吹响吧！上低音号 第三季][Hibike! Euphonium S3][05][1080P][MP4][简日双语]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669843_3_Hibike_21_Euphonium_3_06_1080p`() {
    val r = parse("[桜都字幕組] 吹響吧！上低音號 3 / Hibike! Euphonium 3 [06][1080p][繁體內嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `669842_3_Hibike_21_Euphonium_3_06_1080p`() {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [06][1080p][简体内嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `669841_3_Hibike_21_Euphonium_3_06_1080p`() {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [06][1080p][简繁内封]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `669835_Sub_Hibike_21_Euphonium_3___06_WebRip_2160P_HEVC_AAC_ASSx3`() {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 - 06 [WebRip 2160P HEVC AAC][简繁日内封 ASSx3]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `669834_Sub_Hibike_21_Euphonium_3___06_WebRip_1080P_HEVC_AAC_ASSx3`() {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹响吧！上低音号 第三季 / Hibike! Euphonium 3 - 06 [WebRip 1080P HEVC AAC][简繁日内封 ASSx3]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `669692_Hibike_21_Euphonium_3_05_WebRip_HEVC_AAC`() {
    val r = parse("[北宇治字幕组] 吹響吧！上低音號 第三季 / Hibike! Euphonium 3 [06][WebRip][HEVC_AAC][繁日內嵌][招募時軸]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `669691_Hibike_21_Euphonium_3_06_WebRip_HEVC_AAC`() {
    val r = parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [06][WebRip][HEVC_AAC][简日内嵌][招募时轴]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `669690_Hibike_21_Euphonium_3_06_WebRip_HEVC_AAC`() {
    val r = parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [06][WebRip][HEVC_AAC][简繁日内封][招募时轴]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `669685_Sub_Hibike_21_Euphonium_3_05_1080P_AVC_AAC_WebRip`() {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹響吧！上低音號 第三季 / Hibike! Euphonium 3][05][1080P][AVC AAC][繁日双语][WebRip]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669684_Sub_Hibike_21_Euphonium_3_05_1080P_AVC_AAC_WebRip`() {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹响吧！上低音号 第三季 / Hibike! Euphonium 3][05][1080P][AVC AAC][简日双语][WebRip]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669433_LoliHouse_3_Hibike_21_Euphonium_3___05_WebRip_1080p_HEVC_10bit_AAC`() {
    val r =
        parse("[千夏字幕组&LoliHouse] 吹响吧！上低音号 3 / Hibike! Euphonium 3 - 05 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `669432_3_Hibike_21_Euphonium_3_05_1080p_AVC`() {
    val r = parse("[千夏字幕組][吹響吧！上低音號 3_Hibike! Euphonium 3][第05話][1080p_AVC][繁體]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669431_3_Hibike_21_Euphonium_3_05_1080p_AVC`() {
    val r = parse("[千夏字幕组][吹响吧！上低音号 3_Hibike! Euphonium 3][第05话][1080p_AVC][简体]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669404_ANi___06_1080P_Bilibili_WEB_DL_AAC_AVC_CHT_CHS_MP4`() {
    val r =
        parse("[ANi] 吹響吧！上低音號 第三季（僅限港澳台地區） - 06 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `669258_3_Hibike_21_Euphonium_S3_05_1080p`() {
    val r = parse("[云光字幕组]吹响吧！上低音号 3 Hibike! Euphonium S3 [05][简体双语][1080p]招募翻译")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669239_4_Hibike_21_Euphonium_S3_05_GB_1080P_MP4`() {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第05话 GB 1080P MP4（字幕社招人内详）")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669238_4_Hibike_21_Euphonium_S3_05_GB_720P_MP4`() {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第05话 GB 720P MP4（字幕社招人内详）")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669188_4_Hibike_21_Euphonium_S3_05_1080P_MP4`() {
    val r = parse("[漫貓字幕社][4月新番][吹響吧！上低音號 第三季][Hibike! Euphonium S3][05][1080P][MP4][繁日雙語]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669186_4_Hibike_21_Euphonium_S3_05_1080P_MP4`() {
    val r = parse("[漫猫字幕社][4月新番][吹响吧！上低音号 第三季][Hibike! Euphonium S3][05][1080P][MP4][简日双语]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `669165_3_Hibike_21_Euphonium_3_05_1080p`() {
    val r = parse("[桜都字幕組] 吹響吧！上低音號 3 / Hibike! Euphonium 3 [05][1080p][繁體內嵌]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `669137_3_Hibike_21_Euphonium_3_05_1080p`() {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [05][1080p][简体内嵌]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `669135_3_Hibike_21_Euphonium_3_05_1080p`() {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [05][1080p][简繁内封]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `668979_Hibike_21_Euphonium_3_05_WebRip_HEVC_AAC`() {
    val r = parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [05][WebRip][HEVC_AAC][简繁日内封][招募时轴]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `668978_Hibike_21_Euphonium_3_05_WebRip_HEVC_AAC`() {
    val r = parse("[北宇治字幕组] 吹響吧！上低音號 第三季 / Hibike! Euphonium 3 [05][WebRip][HEVC_AAC][繁日內嵌][招募時軸]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `668971_Hibike_21_Euphonium_3_05_WebRip_HEVC_AAC`() {
    val r = parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [05][WebRip][HEVC_AAC][简日内嵌][招募时轴]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `668898_3_Hibike_21_Euphonium_3_04_1080p_AVC`() {
    val r = parse("[千夏字幕组][吹响吧！上低音号 3_Hibike! Euphonium 3][第04话][1080p_AVC][简体]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668897_3_Hibike_21_Euphonium_3_04_1080p_AVC`() {
    val r = parse("[千夏字幕組][吹響吧！上低音號 3_Hibike! Euphonium 3][第04話][1080p_AVC][繁體]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668896_LoliHouse_3_Hibike_21_Euphonium_3___04_WebRip_1080p_HEVC_10bit_AAC`() {
    val r =
        parse("[千夏字幕组&LoliHouse] 吹响吧！上低音号 3 / Hibike! Euphonium 3 - 04 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `668891_4_Hibike_21_Euphonium_S3_04_BIG5_1080P_MP4`() {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第04話 BIG5 1080P MP4（字幕社招人內詳）")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668890_4_Hibike_21_Euphonium_S3_04_BIG5_720P_MP4`() {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第04話 BIG5 720P MP4（字幕社招人內詳）")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668882_3_Hibike_21_Euphonium_S3_04_1080p`() {
    val r = parse("[云光字幕组]吹响吧！上低音号 3 Hibike! Euphonium S3 [04][简体双语][1080p]招募翻译")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668878_ANi___05_1080P_Bilibili_WEB_DL_AAC_AVC_CHT_CHS_MP4`() {
    val r =
        parse("[ANi] 吹響吧！上低音號 第三季（僅限港澳台地區） - 05 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `668743_Sub_Hibike_21_Euphonium_3___04_WebRip_2160P_HEVC_AAC_ASSx3`() {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 - 04 [WebRip 2160P HEVC AAC][简繁日内封 ASSx3]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `668742_Sub_Hibike_21_Euphonium_3___04_WebRip_1080P_HEVC_AAC_ASSx3`() {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹响吧！上低音号 第三季 / Hibike! Euphonium 3 - 04 [WebRip 1080P HEVC AAC][简繁日内封 ASSx3]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `668694_4_Hibike_21_Euphonium_S3_04_GB_1080P_MP4`() {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第04话 GB 1080P MP4（字幕社招人内详）")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668693_4_Hibike_21_Euphonium_S3_04_GB_720P_MP4`() {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第04话 GB 720P MP4（字幕社招人内详）")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668552_3_Hibike_21_Euphonium_3_04_1080p`() {
    val r = parse("[桜都字幕組] 吹響吧！上低音號 3 / Hibike! Euphonium 3 [04][1080p][繁體內嵌]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `668550_3_Hibike_21_Euphonium_3_04_1080p`() {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [04][1080p][简体内嵌]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `668549_4_Hibike_21_Euphonium_S3_04_1080P_MP4`() {
    val r = parse("[漫貓字幕社][4月新番][吹響吧！上低音號 第三季][Hibike! Euphonium S3][04][1080P][MP4][繁日雙語]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668548_4_Hibike_21_Euphonium_S3_04_1080P_MP4`() {
    val r = parse("[漫猫字幕社][4月新番][吹响吧！上低音号 第三季][Hibike! Euphonium S3][04][1080P][MP4][简日双语]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668547_3_Hibike_21_Euphonium_3_04_1080p`() {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [04][1080p][简繁内封]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `668465_Hibike_21_Euphonium_3_04_WebRip_HEVC_AAC`() {
    val r = parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [04][WebRip][HEVC_AAC][简繁日内封][招募时轴]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `668464_Hibike_21_Euphonium_3_04_WebRip_HEVC_AAC`() {
    val r = parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [04][WebRip][HEVC_AAC][简日内嵌][招募时轴]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `668463_Hibike_21_Euphonium_3_04_WebRip_HEVC_AAC`() {
    val r = parse("[北宇治字幕组] 吹響吧！上低音號 第三季 / Hibike! Euphonium 3 [04][WebRip][HEVC_AAC][繁日內嵌][招募時軸]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `668410_4_Hibike_21_Euphonium_S3_03_BIG5_1080P_MP4`() {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第03話 BIG5 1080P MP4（字幕社招人內詳）")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668409_4_Hibike_21_Euphonium_S3_03_BIG5_720P_MP4`() {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第03話 BIG5 720P MP4（字幕社招人內詳）")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668361_ANi___04_1080P_Bilibili_WEB_DL_AAC_AVC_CHT_CHS_MP4`() {
    val r =
        parse("[ANi] 吹響吧！上低音號 第三季（僅限港澳台地區） - 04 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `668337_4_Hibike_21_Euphonium_S3_03_GB_1080P_MP4`() {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第03话 GB 1080P MP4（字幕社招人内详）")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668336_4_Hibike_21_Euphonium_S3_03_GB_720P_MP4`() {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第03话 GB 720P MP4（字幕社招人内详）")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668334_3_Hibike_21_Euphonium_3_03_1080p_AVC`() {
    val r = parse("[千夏字幕组][吹响吧！上低音号 3_Hibike! Euphonium 3][第03话][1080p_AVC][简体]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668333_3_Hibike_21_Euphonium_3_03_1080p_AVC`() {
    val r = parse("[千夏字幕組][吹響吧！上低音號 3_Hibike! Euphonium 3][第03話][1080p_AVC][繁體]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668332_LoliHouse_3_Hibike_21_Euphonium_3___03_WebRip_1080p_HEVC_10bit_AAC`() {
    val r =
        parse("[千夏字幕组&LoliHouse] 吹响吧！上低音号 3 / Hibike! Euphonium 3 - 03 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `668316_Sub_Hibike_21_Euphonium_3___03_WebRip_2160P_HEVC_AAC_ASSx3`() {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 - 03 [WebRip 2160P HEVC AAC][简繁日内封 ASSx3]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `668315_Sub_Hibike_21_Euphonium_3___03_WebRip_1080P_HEVC_AAC_ASSx3`() {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹响吧！上低音号 第三季 / Hibike! Euphonium 3 - 03 [WebRip 1080P HEVC AAC][简繁日内封 ASSx3]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `668309_Sub_Hibike_21_Euphonium_3_03_1080P_AVC_AAC_WebRip`() {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹響吧！上低音號 第三季 / Hibike! Euphonium 3][03][1080P][AVC AAC][繁日双语][WebRip]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668308_Sub_Hibike_21_Euphonium_3_03_1080P_AVC_AAC_WebRip`() {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹响吧！上低音号 第三季 / Hibike! Euphonium 3][03][1080P][AVC AAC][简日双语][WebRip]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668262_3_Hibike_21_Euphonium_S3_03_1080p`() {
    val r = parse("[云光字幕组]吹响吧！上低音号 3 Hibike! Euphonium S3 [03][简体双语][1080p]招募翻译")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668203_4_Hibike_21_Euphonium_S3_02_BIG5_1080P_MP4`() {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第02話 BIG5 1080P MP4（字幕社招人內詳）")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668202_4_Hibike_21_Euphonium_S3_02_BIG5_720P_MP4`() {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第02話 BIG5 720P MP4（字幕社招人內詳）")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668161_3_Hibike_21_Euphonium_3_03_1080p`() {
    val r = parse("[桜都字幕組] 吹響吧！上低音號 3 / Hibike! Euphonium 3 [03][1080p][繁體內嵌]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `668159_3_Hibike_21_Euphonium_3_03_1080p`() {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [03][1080p][简体内嵌]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `668158_3_Hibike_21_Euphonium_3_03_1080p`() {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [03][1080p][简繁内封]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `668151_4_Hibike_21_Euphonium_S3_03_1080P_MP4`() {
    val r = parse("[漫貓字幕社][4月新番][吹響吧！上低音號 第三季][Hibike! Euphonium S3][03][1080P][MP4][繁日雙語]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668150_4_Hibike_21_Euphonium_S3_03_1080P_MP4`() {
    val r = parse("[漫猫字幕社][4月新番][吹响吧！上低音号 第三季][Hibike! Euphonium S3][03][1080P][MP4][简日双语]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668149_4_Hibike_21_Euphonium_S3_02v2_1080P_MP4`() {
    val r = parse("[漫貓字幕社][4月新番][吹響吧！上低音號 第三季][Hibike! Euphonium S3][02v2][1080P][MP4][繁日雙語]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668148_4_Hibike_21_Euphonium_S3_02v2_1080P_MP4`() {
    val r = parse("[漫猫字幕社][4月新番][吹响吧！上低音号 第三季][Hibike! Euphonium S3][02v2][1080P][MP4][简日双语]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668111_4_Hibike_21_Euphonium_S3_02_GB_1080P_MP4`() {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第02话 GB 1080P MP4（字幕社招人内详）")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `668110_4_Hibike_21_Euphonium_S3_02_GB_720P_MP4`() {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第02话 GB 720P MP4（字幕社招人内详）")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667913_Hibike_21_Euphonium_3_03v2_WebRip_HEVC_AAC`() {
    val r =
        parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [03v2][WebRip][HEVC_AAC][简繁日内封][招募时轴]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `667912_Hibike_21_Euphonium_3_03v2_WebRip_HEVC_AAC`() {
    val r =
        parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [03v2][WebRip][HEVC_AAC][简日内嵌][招募时轴]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `667911_Hibike_21_Euphonium_3_03v2_WebRip_HEVC_AAC`() {
    val r =
        parse("[北宇治字幕组] 吹響吧！上低音號 第三季 / Hibike! Euphonium 3 [03v2][WebRip][HEVC_AAC][繁日內嵌][招募時軸]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `667884_Sub_Hibike_21_Euphonium_3___02_WebRip_1080P_HEVC_AAC_ASSx3`() {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹响吧！上低音号 第三季 / Hibike! Euphonium 3 - 02 [WebRip 1080P HEVC AAC][简繁日内封 ASSx3]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `667849_Sub_Hibike_21_Euphonium_3___02_WebRip_2160P_HEVC_AAC_ASSx3`() {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 - 02 [WebRip 2160P HEVC AAC][简繁日内封 ASSx3]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `667844_ANi___03_1080P_Bilibili_WEB_DL_AAC_AVC_CHT_CHS_MP4`() {
    val r =
        parse("[ANi] 吹響吧！上低音號 第三季（僅限港澳台地區） - 03 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `667840_3_Hibike_21_Euphonium_S3_02_1080p`() {
    val r = parse("[云光字幕组]吹响吧！上低音号 3 Hibike! Euphonium S3 [02][简体双语][1080p]招募翻译")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667775_Sub_Hibike_21_Euphonium_3_02_1080P_AVC_AAC_WebRip`() {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹响吧！上低音号 第三季 / Hibike! Euphonium 3][02][1080P][AVC AAC][简日双语][WebRip]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667745_4_Hibike_21_Euphonium_S3_02_1080P_MP4`() {
    val r = parse("[漫猫字幕社][4月新番][吹响吧！上低音号 第三季][Hibike! Euphonium S3][02][1080P][MP4][简日双语]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667693_4_Hibike_21_Euphonium_S3_02_1080P_MP4`() {
    val r = parse("[漫貓字幕社][4月新番][吹響吧！上低音號 第三季][Hibike! Euphonium S3][02][1080P][MP4][繁日雙語]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667610_LoliHouse_3_Hibike_21_Euphonium_3___02_WebRip_1080p_HEVC_10bit_AAC`() {
    val r =
        parse("[千夏字幕组&LoliHouse] 吹响吧！上低音号 3 / Hibike! Euphonium 3 - 02 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `667609_3_Hibike_21_Euphonium_3_02_1080p_AVC`() {
    val r = parse("[千夏字幕組][吹響吧！上低音號 3_Hibike! Euphonium 3][第02話][1080p_AVC][繁體]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667608_3_Hibike_21_Euphonium_3_02_1080p_AVC`() {
    val r = parse("[千夏字幕组][吹响吧！上低音号 3_Hibike! Euphonium 3][第02话][1080p_AVC][简体]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667520_3_Hibike_21_Euphonium_3_02_1080p`() {
    val r = parse("[桜都字幕組] 吹響吧！上低音號 3 / Hibike! Euphonium 3 [02][1080p][繁體內嵌]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `667517_3_Hibike_21_Euphonium_3_02_1080p`() {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [02][1080p][简体内嵌]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `667516_3_Hibike_21_Euphonium_3_02_1080p`() {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [02][1080p][简繁内封]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `667411_4_Hibike_21_Euphonium_S3_01_BIG5_1080P_MP4`() {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第01話 BIG5 1080P MP4（字幕社招人內詳）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667410_4_Hibike_21_Euphonium_S3_01_BIG5_720P_MP4`() {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第01話 BIG5 720P MP4（字幕社招人內詳）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667378_4_Hibike_21_Euphonium_S3_01v2_1080P_MP4`() {
    val r = parse("[漫貓字幕社][4月新番][吹響吧！上低音號 第三季][Hibike! Euphonium S3][01v2][1080P][MP4][繁日雙語]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667377_4_Hibike_21_Euphonium_S3_01v2_1080P_MP4`() {
    val r = parse("[漫猫字幕社][4月新番][吹响吧！上低音号 第三季][Hibike! Euphonium S3][01v2][1080P][MP4][简日双语]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667313_4_Hibike_21_Euphonium_S3_01_1080P_MP4`() {
    val r = parse("[漫貓字幕社][4月新番][吹響吧！上低音號 第三季][Hibike! Euphonium S3][01][1080P][MP4][繁日雙語]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667312_4_Hibike_21_Euphonium_S3_01_1080P_MP4`() {
    val r = parse("[漫猫字幕社][4月新番][吹响吧！上低音号 第三季][Hibike! Euphonium S3][01][1080P][MP4][简日双语]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667298_Hibike_21_Euphonium_3_02_WebRip_HEVC_AAC`() {
    val r = parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [02][WebRip][HEVC_AAC][简日内嵌][招募时轴]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `667297_Hibike_21_Euphonium_3_02_WebRip_HEVC_AAC`() {
    val r = parse("[北宇治字幕组] 吹響吧！上低音號 第三季 / Hibike! Euphonium 3 [02][WebRip][HEVC_AAC][繁日內嵌][招募時軸]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `667290_4_Hibike_21_Euphonium_S3_01_GB_1080P_MP4`() {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第01话 GB 1080P MP4（字幕社招人内详）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667289_4_Hibike_21_Euphonium_S3_01_GB_720P_MP4`() {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第01话 GB 720P MP4（字幕社招人内详）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `667286_Hibike_21_Euphonium_3_02_WebRip_HEVC_AAC`() {
    val r = parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [02][WebRip][HEVC_AAC][简繁日内封][招募时轴]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `667228_ANi___02_1080P_Bilibili_WEB_DL_AAC_AVC_CHT_CHS_MP4`() {
    val r =
        parse("[ANi] 吹響吧！上低音號 第三季（僅限港澳台地區） - 02 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `667130_3_Hibike_21_Euphonium_S3_01_1080p`() {
    val r = parse("[云光字幕组]吹响吧！上低音号 3 Hibike! Euphonium S3 [01][简体双语][1080p]招募翻译")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `666843_LoliHouse_3_Hibike_21_Euphonium_3___01_WebRip_1080p_HEVC_10bit_AAC`() {
    val r =
        parse("[千夏字幕组&LoliHouse] 吹响吧！上低音号 3 / Hibike! Euphonium 3 - 01 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `666842_3_Hibike_21_Euphonium_3_01_1080p_AVC`() {
    val r = parse("[千夏字幕组][吹响吧！上低音号 3_Hibike! Euphonium 3][第01话][1080p_AVC][简体]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `666841_3_Hibike_21_Euphonium_3_01_1080p_AVC`() {
    val r = parse("[千夏字幕組][吹響吧！上低音號 3_Hibike! Euphonium 3][第01話][1080p_AVC][繁體]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `666635_3_Hibike_21_Euphonium_3_01_1080p`() {
    val r = parse("[桜都字幕組] 吹響吧！上低音號 3 / Hibike! Euphonium 3 [01][1080p][繁體內嵌]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `666634_3_Hibike_21_Euphonium_3_01_1080p`() {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [01][1080p][简体内嵌]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `666633_3_Hibike_21_Euphonium_3_01_1080p`() {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [01][1080p][简繁内封]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `666601_Sub_Hibike_21_Euphonium_3___01_WebRip_2160P_HEVC_AAC_ASSx2`() {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub] 吹響吧！上低音號 第三季 / Hibike! Euphonium 3 - 01 [WebRip 2160P HEVC AAC][简繁日内封 ASSx2]【高压版】")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `666600_Sub_Hibike_21_Euphonium_3___01_WebRip_2160P_HEVC_AAC_ASSx2`() {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub] 吹響吧！上低音號 第三季 / Hibike! Euphonium 3 - 01 [WebRip 2160P HEVC AAC][简繁日内封 ASSx2]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `666599_Sub_Hibike_21_Euphonium_3___01_WebRip_1080P_HEVC_AAC_ASSx2`() {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹响吧！上低音号 第三季 / Hibike! Euphonium 3 - 01 [WebRip 1080P HEVC AAC][简繁日内封 ASSx2]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `666598_Sub_Hibike_21_Euphonium_3_01_1080P_AVC_AAC_WebRip`() {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹响吧！上低音号 第三季 / Hibike! Euphonium 3][01][1080P][AVC AAC][简日双语][WebRip]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `666567_Hibike_21_Euphonium_3_01v2_WebRip_HEVC_AAC`() {
    val r =
        parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [01v2][WebRip][HEVC_AAC][简繁日内封][招募时轴]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `666566_Hibike_21_Euphonium_3_01v2_WebRip_HEVC_AAC`() {
    val r =
        parse("[北宇治字幕组] 吹響吧！上低音號 第三季 / Hibike! Euphonium 3 [01v2][WebRip][HEVC_AAC][繁日內嵌][招募時軸]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `666420_Hibike_21_Euphonium_3_01v2_WebRip_HEVC_AAC`() {
    val r =
        parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [01v2][WebRip][HEVC_AAC][简日内嵌][招募时轴]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `666337_ANi___01_1080P_Bilibili_WEB_DL_AAC_AVC_CHT_CHS_MP4`() {
    val r =
        parse("[ANi] 吹響吧！上低音號 第三季（僅限港澳台地區） - 01 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `666150_Hibike_21_Euphonium_3_02_WebRip_HEVC_AAC_CHS_EN`() {
    val r =
        parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [02][先行版][WebRip][HEVC_AAC][CHS_EN][招募时轴]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `666027_Hibike_21_Euphonium_3_01_WebRip_HEVC_AAC_CHS_EN`() {
    val r =
        parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [01][先行版][WebRip][HEVC_AAC][CHS_EN][招募时轴]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `659668_Hibike_21_Euphonium_Special_Episode_of_Ensemble_Contest_1080p`() {
    val r =
        parse("[云光字幕组]特别篇 吹响吧！上低音号~合奏比赛~Hibike! Euphonium Special ~Episode of Ensemble Contest~ [简体双语][1080p]招募翻译")
    assertEquals("Special..Special", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `651923_Hibike_21_Euphonium_Ensemble_Contest_Hen_BDRip_AVC_8bit_1080p`() {
    val r =
        parse("[❀拨雪寻春❀] 吹響吧！上低音號～合奏比賽篇～ / Hibike! Euphonium Ensemble Contest Hen [BDRip][AVC-8bit 1080p][繁日内嵌]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `651922_Hibike_21_Euphonium_Ensemble_Contest_Hen_BDRip_AVC_8bit_1080p`() {
    val r =
        parse("[❀拨雪寻春❀] 吹响吧！上低音号～合奏比赛篇～ / Hibike! Euphonium Ensemble Contest Hen [BDRip][AVC-8bit 1080p][简日内嵌]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `651921_Hibike_21_Euphonium_Ensemble_Contest_Hen_BDRip_HEVC_10bit_1080p`() {
    val r =
        parse("[❀拨雪寻春❀] 吹响吧！上低音号～合奏比赛篇～ / Hibike! Euphonium Ensemble Contest Hen [BDRip][HEVC-10bit 1080p][简繁日内封]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public
      fun `649884_Tokubetsu_Hen_Hibike_21_Euphonium_Ensemble_Contest_Hen_BDrip_AVC_8bit_1080p_AAC`() {
    val r =
        parse("[桜都字幕組] 特別篇 吹響吧！上低音號 ~ 合奏比賽 ~ / Tokubetsu Hen Hibike! Euphonium: Ensemble Contest Hen [BDrip][AVC-8bit 1080p AAC][繁日內嵌]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `648009_Tokubetsu_Hen_Hibike_21_Euphonium_Ensemble_Contest_Hen_BDRip_1080p_AVC`() {
    val r =
        parse("[千夏字幕組][特別篇 吹響吧！上低音號 ~合奏競賽~_Tokubetsu Hen Hibike! Euphonium: Ensemble Contest Hen][劇場版][BDRip_1080p_AVC][繁日双语][招募新人]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `648008_Tokubetsu_Hen_Hibike_21_Euphonium_Ensemble_Contest_Hen_BDRip_1080p_AVC`() {
    val r =
        parse("[千夏字幕组][特别篇 吹响吧！上低音号 ~合奏竞赛~_Tokubetsu Hen Hibike! Euphonium: Ensemble Contest Hen][剧场版][BDRip_1080p_AVC][简日双语][招募新人]")
    assertEquals("特别篇 吹响吧！上低音号 ~合奏竞赛~_Tokubetsu Hen Hibike! Euphonium: Ensemble Contest Hen..特别篇 吹响吧！上低音号 ~合奏竞赛~_Tokubetsu Hen Hibike! Euphonium: Ensemble Contest Hen",
        r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public
      fun `647860_Tokubetsu_Hen_Hibike_21_Euphonium_Ensemble_Contest_Hen_BDRip_v2_1080p_HEVC_FLAC`() {
    val r =
        parse("[北宇治字幕组] 特別篇 吹響吧！上低音號 ~ 合奏比賽 ~ / Tokubetsu Hen Hibike! Euphonium ~ Ensemble Contest Hen ~ [BDRip v2][1080p][HEVC_FLAC][繁日內嵌]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public
      fun `647859_Tokubetsu_Hen_Hibike_21_Euphonium_Ensemble_Contest_Hen_BDRip_v2_1080p_HEVC_FLAC`() {
    val r =
        parse("[北宇治字幕组] 特别篇 吹响吧！上低音号 ~ 合奏比赛 ~ / Tokubetsu Hen Hibike! Euphonium ~ Ensemble Contest Hen ~ [BDRip v2][1080p][HEVC_FLAC][简日内嵌]")
    assertEquals("特别篇..特别篇", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `597732_5_EUPHO_5TH_REGULAR_CONCERT_BDRIP_1080p_x265_TrueHD_Atmos_TrueHD_2_0`() {
    val r =
        parse("吹响吧！上低音号5周年纪念公演[EUPHO 5TH REGULAR CONCERT][BDRIP_1080p_x265_TrueHD_Atmos_TrueHD_2.0]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `560011_DHR_Chikai_no_Finale_BDRip_720P_MP4`() {
    val r = parse("【DHR動研字幕組&茉語星夢】[吹響吧！上低音號～誓言的終章～_Chikai no Finale][劇場版][BDRip][繁體][720P][MP4]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public
      fun `540923_VCB_Studio_Gekijouban_Hibike_21_Euphonium_Chikai_no_Finale_10_bit_1080p_HEVC_BDRip_MOVIE`() {
    val r =
        parse("[千夏字幕组&VCB-Studio] Gekijouban Hibike! Euphonium Chikai no Finale / 剧场版 吹响吧！上低音号～誓言的终章～ 10-bit 1080p HEVC BDRip [MOVIE]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `536196_Movie_Hibike_21_Euphonium_S1_S2_SP_Moviex4_BD_1920x1080_HEVC_10bit_OPUS`() {
    val r =
        parse("吹响吧！上低音号 全两季+Movie Hibike! Euphonium S1+S2+SP+Moviex4 [BD 1920x1080 HEVC-10bit OPUS][简繁内封字幕]")
    assertEquals("S1+S2+SP+S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `535781_Gekijouban_Hibike_21_Euphonium_Chikai_no_Finale_v2_BDRip_1080p_AVC`() {
    val r =
        parse("【千夏字幕组】【剧场版 吹响吧！上低音号 ~誓言的终曲~_Gekijouban Hibike! Euphonium: Chikai no Finale】[剧场版_v2][BDRip_1080p_AVC][简体]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `535761_Gekijouban_Hibike_21_Euphonium_Chikai_no_Finale_v2_BDRip_1080p_AVC`() {
    val r =
        parse("【千夏字幕組】【劇場版 吹響吧！上低音號 ~誓言的終曲~_Gekijouban Hibike! Euphonium: Chikai no Finale】[劇場版_v2][BDRip_1080p_AVC][繁體]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public
      fun `535752_LoliHouse_Gekijouban_Hibike_21_Euphonium_Chikai_no_Finale_BDRip_1080p_HEVC_10bit_FLAC`() {
    val r =
        parse("[千夏字幕组&LoliHouse] 剧场版 吹响吧！上低音号 ~誓言的终曲~ / Gekijouban Hibike! Euphonium: Chikai no Finale [BDRip 1080p HEVC-10bit FLAC][简繁外挂字幕]（索引：誓言的终章）")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public
      fun `532705_U3_Web_2019_Movie_BIG5_GP_WEB_DL_1080p_AVC_AAC_2_0_5_1_MP4_Gekijouban_Hibike_21_Euphonium_Chikai_no_Finale_Sound_21_Euphonium_the_Movie__Our_Promise_A_Brand_New_Day_`() {
    val r =
        parse("[U3-Web] 電影版 吹響吧！上低音號～誓言的終章～ / 劇場版 響け！ユーフォニアム～誓いのフィナーレ～ [2019][Movie][繁體中文內嵌字幕][BIG5][GP WEB-DL 1080p AVC AAC(2.0+5.1) MP4] (剧场版 吹响！悠风号 ～誓言的终章～(誓言的終曲) / Gekijouban Hibike! Euphonium : Chikai no Finale / Sound! Euphonium, the Movie -Our Promise: A Brand New Day-)")
    assertEquals("2019..2019", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public
      fun `532580_U3_Web_2019_Movie_1080p_HEVC_10bit_AAC_AC_3_DD5_1_MKV_Gekijouban_Hibike_21_Euphonium_Chikai_no_Finale_Sound_21_Euphonium_the_Movie__Our_Promise_A_Brand_New_Day_`() {
    val r =
        parse("[U3-Web] 電影版 吹響吧！上低音號～誓言的終章～ / 劇場版 響け！ユーフォニアム～誓いのフィナーレ～ [2019][Movie][繁體中文內封字幕][BIG5][1080p HEVC-10bit AAC AC-3(DD5.1) MKV] (剧场版 吹响！悠风号 ～誓言的终章～(誓言的終曲) / Gekijouban Hibike! Euphonium : Chikai no Finale / Sound! Euphonium, the Movie -Our Promise: A Brand New Day-) [WEB-DL --> WebRip]")
    assertEquals("2019..2019", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `518747_DHR_Liz_to_Aoi_Tori_BDRip_720P_MP4`() {
    val r = parse("【DHR動研字幕組&茉語星夢】[吹響吧！上低音號～莉茲與青鳥～_Liz to Aoi Tori][劇場版][BDRip][繁體][720P][MP4]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `506290_Hibike_21_Euphonium_BDrip_1080P_HEVC`() {
    val r = parse("【极影字幕社】★[吹响吧！上低音号 Hibike! Euphonium][BDrip][1080P][HEVC]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public
      fun `505589_U3_Project_Liz_and_the_Blue_Bird_Liz_to_Aoi_Tori_MOVIE_BD_AVC_1080p_DTS_HDMA_FLAC_U3`() {
    val r =
        parse("[U3-Project] 吹響吧！上低音號～莉茲與青鳥～ / 利茲與青鳥 / Liz and the Blue Bird / Liz to Aoi Tori / リズと青い鳥 [MOVIE][BD AVC 1080p DTS-HDMA FLAC]【U3自購團招人 / 新增合購計畫 , 內詳】")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `505581_U3_Project_Liz_and_the_Blue_Bird_Liz_to_Aoi_Tori_BDMV_1080p_MOVIE_U3`() {
    val r =
        parse("[U3-Project] 吹響吧！上低音號～莉茲與青鳥～ / Liz and the Blue Bird / Liz to Aoi Tori / リズと青い鳥 [台本付数量限定版][BDMV][1080p][MOVIE]【U3自購團招人 / 新增合購計畫 , 內詳】")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public
      fun `504907_VCB_Studio_Gekijouban_Hibike_21_Euphonium_Todoketai_Melody_10_bit_1080p_HEVC_BDRip_Movie_Fin`() {
    val r =
        parse("[VCB-Studio] Gekijouban Hibike! Euphonium: Todoketai Melody / 剧场版 吹响吧！上低音号~想要传达的旋律~ 10-bit 1080p HEVC BDRip [Movie Fin]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public
      fun `484696_Gekijouban_Hibike_21_Euphonium_Todoketai_Melody_BDRip_1920x1080_AVC_8bit_AAC`() {
    val r =
        parse("[届恋字幕组] 剧场版 吹响吧！上低音号 ～想要传达的旋律～ / Gekijouban Hibike! Euphonium Todoketai Melody [BDRip 1920x1080 AVC-8bit AAC][繁体内嵌字幕]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public
      fun `484695_Gekijouban_Hibike_21_Euphonium_Todoketai_Melody_BDRip_1920x1080_AVC_8bit_AAC`() {
    val r =
        parse("[届恋字幕组] 剧场版 吹响吧！上低音号 ～想要传达的旋律～ / Gekijouban Hibike! Euphonium Todoketai Melody [BDRip 1920x1080 AVC-8bit AAC][简体内嵌字幕]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `484593_Moozzi2_Hibike_21_Euphonium_Todoketai_Melody_BD_1920x1080_x264_Flac`() {
    val r =
        parse("[Moozzi2] 劇場版 吹響吧！上低音號～想要傳達的旋律～ Hibike! Euphonium Todoketai Melody (BD 1920x1080 x264 Flac)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public
      fun `484106_Eupho_Fans_Hibike_21_Euphonium_Movie_Todoketai_Melody_BDRip_x264_1920x1080_AAC_DTS_PGS_MKV`() {
    val r =
        parse("[Eupho-Fans] 劇場版 吹響吧！上低音號～想要傳達的旋律～ / 劇場版 響け！ユーフォニアム～届けたいメロディ～ / Hibike! Euphonium Movie: Todoketai Melody [BDRip x264 1920x1080 AAC DTS PGS MKV]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `478260_Hibike_21_Euphonium_S2_BDrip_1080P_HEVC`() {
    val r = parse("【极影字幕社】★[吹响吧！上低音号二期 Hibike! Euphonium S2][BDrip][1080P][HEVC]")
    assertEquals("S2", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `478169_UHA_WINGS_2_Hibike_21_Euphonium_2_BDSP_MKV`() {
    val r = parse("【悠哈璃羽字幕社】[UHA-WINGS][吹响吧！上低音号 第2季/Hibike! Euphonium 2][BDSP][MKV 简日_繁日外挂]")
    assertEquals("BDSP..BDSP", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `476911_DHR_Sound_21Euphonium_S1_S2_Movie_BDRip_1080P_AVC_P10_FLAC_OPUSx2_SUP`() {
    val r =
        parse("【DHR動研&茉語星夢&千夏】[吹響吧！上低音號_Sound!Euphonium][S1+S2+Movie][繁體外掛字幕][BDRip][1080P][AVC_P10_FLAC_OPUSx2_SUP]")
    assertEquals("S1+S2+S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `474771_DHR_Sound_21Euphonium_2_13_SP_BDRip_1080P_AVC_P10_FLAC_OPUSx2_SUP__21`() {
    val r =
        parse("【DHR動研&茉語星夢&千夏】[吹響吧！上低音號 第二季_Sound!Euphonium 2][全13話+SP][繁體外掛字幕][BDRip][1080P][AVC_P10_FLAC_OPUSx2_SUP] 單身?沒錢? 來看姬情百合片吧!")
    assertEquals("全13話+SP..全13話+SP", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `472387_DHR_Sound_21Euphonium_S2_SP01_07_720P_MP4`() {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][SP01-07全][繁體][720P][MP4]")
    assertEquals("SP01..SP07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `467213_VCB_Studio_Hibike_21_Euphonium_2_2__21_2_10_bit_1080p_HEVC_BDRip_Fin`() {
    val r =
        parse("[VCB-Studio] Hibike! Euphonium 2 / 吹响吧！上低音号 2 / 響け! ユーフォニアム 2 10-bit 1080p HEVC BDRip [Fin]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `467212_VCB_Studio_Hibike_21_Euphonium_2_2__21_2_8_bit_1080p_HEVC_BDRip_MP4_Ver`() {
    val r =
        parse("[VCB-Studio] Hibike! Euphonium 2 / 吹响吧！上低音号 2 / 響け! ユーフォニアム 2 8-bit 1080p HEVC BDRip [MP4 Ver.]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public
      fun `466528_UHA_WINGS_ANK_Raws_Hibike_21_Euphonium_01_01_13_MKV_BDrip_1920x1080_HEVC_YUV420P10_FLAC`() {
    val r =
        parse("[UHA-WINGS＆ANK-Raws][吹响吧！上低音号 第二季][Hibike! Euphonium 02][01-13][MKV 简日_繁日双语外挂][BDrip 1920x1080 HEVC-YUV420P10 FLAC]")
    assertEquals("01..13", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public
      fun `466330_UHA_WINGS_ANK_Raws_Hibike_21_Euphonium_2_01_13_MKV_BDrip_1920x1080_HEVC_10bit`() {
    val r =
        parse("[UHA-WINGS＆ANK-Raws][吹响吧！上低音号 第二季][Hibike! Euphonium 2][01-13][MKV 简日_繁日双语外挂][BDrip 1920x1080 HEVC-10bit]")
    assertEquals("01..13", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `465556_BD720P`() {
    val r = parse("【浩天个人发布】吹响吧！上低音号第二季 BD720P内嵌简体字幕")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `464608_Xrip_2_Sound_21Euphonium_2_BDrip_Vol_01_07_1080P_x265_10bit_flac`() {
    val r = parse("【Xrip】[吹響吧！上低音號 2][Sound!Euphonium 2][BDrip][Vol.01_07][1080P][x265_10bit_flac]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `464607_Xrip_2_Sound_21Euphonium_2_BDrip_Vol_01_07_1080P_x264_10bit_flac`() {
    val r = parse("【Xrip】[吹響吧！上低音號 2][Sound!Euphonium 2][BDrip][Vol.01_07][1080P][x264_10bit_flac]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `463600_2_SP7_1080P_MP4`() {
    val r = parse("【悠风社】吹响吧！上低音号 2 SP7 [简][1080P][MP4]")
    assertEquals("SP07..SP07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public
      fun `461337_UHA_WINGS_ANK_Raws_Hibike_21_Euphonium_2_01_07_BDrip_1920x1080_HEVC_10bit_MKV`() {
    val r =
        parse("[UHA-WINGS＆ANK-Raws][吹响吧！上低音号 第二季][Hibike! Euphonium 2][01-07][BDrip 1920x1080 HEVC-10bit][MKV 简日_繁日外挂]")
    assertEquals("01..07", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `461023_2_SP6_1080P_MP4`() {
    val r = parse("【悠风社】吹响吧！上低音号 2 SP6 [简][1080P][MP4]")
    assertEquals("SP06..SP06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `458613_2_SP5_1080P_MP4`() {
    val r = parse("【悠风社】吹响吧！上低音号 2 SP5 [简][1080P][MP4]")
    assertEquals("SP05..SP05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `456299_2_SP1_4_720P_MP4`() {
    val r = parse("【悠风社】吹响吧！上低音号 2 SP1~4 [简][720P][MP4]")
    assertEquals("SP01..SP04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `454096_2_Hibike_21_Euphonium_2_1_13END_1280x720_GB_BIG5`() {
    val r =
        parse("【悠哈璃羽字幕社】[合集][吹响吧！上低音号 第2季/Hibike! Euphonium 2][1-13END][1280x720][简繁合集][GB&BIG5]")
    assertEquals("01..13", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `451163_DHR_Sound_21Euphonium_S2_01_13_720P_MP4`() {
    val r =
        parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][01-13全][繁體][720P][MP4](合集版本)")
    assertEquals("01..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `451096_DHR_Sound_21Euphonium_S2_13_720P_MP4`() {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][13完][繁體][720P][MP4]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `450972_DHR_Sound_21Euphonium_S2_12_720P_MP4`() {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][12][繁體][720P][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `450888_10_Hibike_21_Euphonium_S2_13END_1080P_BIG5_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][13END][1080P][BIG5][MP4]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `450887_10_Hibike_21_Euphonium_S2_13END_1080P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][13END][1080P][GB][MP4]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `450666_10_Hibike_21_Euphonium_S2_13END_720P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][13END][720P][GB][MP4]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `450664_10_Hibike_21_Euphonium_S2_13END_720P_BIG5_MP4`() {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][13END][720P][BIG5][MP4]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `450432_DHR_Sound_21Euphonium_S2_11_720P_MP4`() {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][11][繁體][720P][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `450427_10_2_Hibike_21_Euphonium_10_720P_GB_MP4`() {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][10][720P][GB][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `450426_10_2_Hibike_21_Euphonium_10_720P_BIG5_MP4`() {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][10][720P][BIG5][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `450425_10_2_Hibike_21_Euphonium_11_720P_BIG5_MP4`() {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][11][720P][BIG5][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `450424_10_2_Hibike_21_Euphonium_11_720P_GB_MP4`() {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][11][720P][GB][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `450376_10_Hibike_21_Euphonium_S2_12_1080P_BIG5_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][12][1080P][BIG5][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `450375_10_Hibike_21_Euphonium_S2_12_1080P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][12][1080P][GB][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `450085_10_Hibike_21_Euphonium_S2_12_720P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][12][720P][GB][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `450082_10_Hibike_21_Euphonium_S2_12_720P_BIG5_MP4`() {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][12][720P][BIG5][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `449623_DHR_Sound_21Euphonium_S2_10_720P_MP4`() {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][10][繁體][720P][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `449583_10_Hibike_21_Euphonium_S2_11_1080P_BIG5_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][11][1080P][BIG5][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `449582_10_Hibike_21_Euphonium_S2_11_1080P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][11][1080P][GB][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `449577_10_Hibike_21_Euphonium_S2_10_1080P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][10][1080P][GB][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `449576_10_Hibike_21_Euphonium_S2_10_1080P_BIG5_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][10][1080P][BIG5][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `449575_10_Hibike_21_Euphonium_S2_11_720P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][11][720P][GB][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `449572_10_Hibike_21_Euphonium_S2_11_720P_BIG5_MP4_z`() {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][11][720P][BIG5][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `449463_DHR_Sound_21Euphonium_S2_09_720P_MP4`() {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][09][繁體][720P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `449441_10_2_Hibike_21_Euphonium_09_720P_BIG5_MP4`() {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][09][720P][BIG5][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `449439_10_2_Hibike_21_Euphonium_09_720P_GB_MP4`() {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][09][720P][GB][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `449023_10_Hibike_21_Euphonium_S2_10_720P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][10][720P][GB][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `449019_10_Hibike_21_Euphonium_S2_10_720P_BIG5_MP4_z`() {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][10][720P][BIG5][MP4]z")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `448515_10_Hibike_21_Euphonium_S2_09_1080P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][09][1080P][GB][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `448514_10_Hibike_21_Euphonium_S2_09_1080P_BIG5_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][09][1080P][BIG5][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `448391_10_Hibike_21_Euphonium_S2_09_720P_BIG5_MP4`() {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][09][720P][BIG5][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `448384_10_Hibike_21_Euphonium_S2_09_720P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][09][720P][GB][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `448361_10_2_Hibike_21_Euphonium_08_720P_BIG5_MP4`() {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][08][720P][BIG5][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `448360_10_2_Hibike_21_Euphonium_08_720P_GB_MP4`() {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][08][720P][GB][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `448293_DHR_Sound_21Euphonium_S2_07_08_720P_MP4`() {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][07-08][繁體][720P][MP4]")
    assertEquals("07..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `447983_10_Hibike_21_Euphonium_S2_08_1080P_BIG5_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][08][1080P][BIG5][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `447982_10_Hibike_21_Euphonium_S2_08_1080P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][08][1080P][GB][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `447895_10_Hibike_21_Euphonium_S2_08_720P_BIG5_MP4`() {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][08][720P][BIG5][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `447893_10_Hibike_21_Euphonium_S2_08_720P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][08][720P][GB][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `447792_10_2_Hibike_21_Euphonium_07_720P_BIG5_MP4`() {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][07][720P][BIG5][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `447786_10_2_Hibike_21_Euphonium_07_720P_GB_MP4`() {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][07][720P][GB][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `447525_10_Hibike_21_Euphonium_S2_07_1080P_BIG5_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][07][1080P][BIG5][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `447524_10_Hibike_21_Euphonium_S2_07_1080P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][07][1080P][GB][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `447413_10_Hibike_21_Euphonium_S2_07_720P_BIG5_MP4`() {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][07][720P][BIG5][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `447407_10_Hibike_21_Euphonium_S2_07_720P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][07][720P][GB][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `447289_DHR_Sound_21Euphonium_S2_06_720P_MP4`() {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][06][繁體][720P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `447100_10_2_Hibike_21_Euphonium_06_720P_BIG5_MP4`() {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][06][720P][BIG5][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `447099_10_2_Hibike_21_Euphonium_06_720P_GB_MP4`() {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][06][720P][GB][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `446939_10_Hibike_21_Euphonium_S2_06_1080P_BIG5_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][06][1080P][BIG5][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `446938_10_Hibike_21_Euphonium_S2_06_1080P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][06][1080P][GB][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `446890_10_Hibike_21_Euphonium_S2_06_720P_BIG5_MP4`() {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][06][720P][BIG5][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `446886_10_Hibike_21_Euphonium_S2_06_720P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][06][720P][GB][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `446678_DHR_Sound_21Euphonium_S2_05_720P_MP4`() {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][05][繁體][720P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `446626_10_2_Hibike_21_Euphonium_05_720P_BIG5_MP4`() {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][05][720P][BIG5][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `446625_10_2_Hibike_21_Euphonium_05_720P_GB_MP4`() {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][05][720P][GB][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `446464_10_Hibike_21_Euphonium_S2_05_1080P_BIG5_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][05][1080P][BIG5][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `446463_10_Hibike_21_Euphonium_S2_04_1080P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][05][1080P][GB][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `446443_10_Hibike_21_Euphonium_S2_05_720P_BIG5_MP4`() {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][05][720P][BIG5][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `446442_10_Hibike_21_Euphonium_S2_05_720P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][05][720P][GB][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `446286_DHR_Sound_21Euphonium_S2_04_720P_MP4`() {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][04][繁體][720P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `446248_10_Hibike_21_Euphonium_S2_04_1080P_BIG5_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][04][1080P][BIG5][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `446173_10_Hibike_21_Euphonium_S2_04_1080P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][04][1080P][GB][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `446123_10_2_Hibike_21_Euphonium_04_720P_BIG5_MP4`() {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][04][720P][BIG5][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `446122_10_2_Hibike_21_Euphonium_04_720P_GB_MP4`() {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][04][720P][GB][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `445927_10_Hibike_21_Euphonium_S2_04_720P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][04][720P][GB][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `445925_10_Hibike_21_Euphonium_S2_04_720P_BIG5_MP4`() {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][04][720P][BIG5][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `445712_DHR_Sound_21Euphonium_S2_03_720P_MP4`() {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][03][繁體][720P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `445674_10_Hibike_21_Euphonium_S2_03_1080P_BIG5_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][03][1080P][BIG5][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `445608_10_2_Hibike_21_Euphonium_03_720P_BIGB_MP4`() {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][03][720P][BIGB][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `445607_10_2_Hibike_21_Euphonium_03_720P_GB_MP4`() {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][03][720P][GB][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `445475_10_Hibike_21_Euphonium_S2_03_1080P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][03][1080P][GB][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `445366_10_Hibike_21_Euphonium_S2_03_720P_BIG5_MP4`() {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][03][720P][BIG5][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `445364_10_Hibike_21_Euphonium_S2_03_720P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][03][720P][GB][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `445317_DHR_Sound_21Euphonium_S2_02_720P_MP4`() {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][02][繁體][720P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `444957_10_2_Hibike_21_Euphonium_02_720P_GB_MP4`() {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][02][720P][GB][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `444791_10_Hibike_21_Euphonium_S2_02_1080P_BIG5_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][02][1080P][BIG5][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `444790_10_Hibike_21_Euphonium_S2_02_1080P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][02][1080P][GB][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `444764_10_Hibike_21_Euphonium_S2_02_720P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][02][720P][GB][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `444762_10_Hibike_21_Euphonium_S2_02_720P_BIG5_MP4`() {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][02][720P][BIG5][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `444585_DHR_Sound_21Euphonium_S2_01_720P_MP4`() {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][01][繁體][720P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `444487_10_2_Hibike_21_Euphonium_720P_GB_MP4`() {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][01][720P][GB][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `444238_10_Hibike_21_Euphonium_S2_01_1080P_BIG5_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][01][1080P][BIG5][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `444209_10_Hibike_21_Euphonium_S2_01_1080P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][01][1080P][GB][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `444155_10_Hibike_21_Euphonium_S2_01_720P_BIG5_MP4`() {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][01][720P][BIG5][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `444127_10_Hibike_21_Euphonium_S2_01_720P_GB_MP4`() {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][01][720P][GB][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public
      fun `442933_LowPower_Raws_Hibike_21_Euphonium_Kitauji_Koukou_Suisougaku_Bu_e_Youkoso___Movie_BD_1080P_x264_FLAC`() {
    val r =
        parse("[LowPower-Raws]吹响吧！上低音号～欢迎加入北宇治高中管乐团～ Hibike! Euphonium Kitauji Koukou Suisougaku Bu e Youkoso - Movie (BD 1080P x264 FLAC)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `442833_BDRip_MKV`() {
    val r = parse("【极影字幕社】吹响吧！上低音号 剧场版 [繁体内嵌] [BDRip][MKV]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `442728_ReinForce_Gekijouban_Hibike_21_Euphonium_BDRip_1920x1080_x264_FLAC`() {
    val r =
        parse("[ReinForce] 劇場版 吹響吧！上低音號～歡迎加入北宇治高中管樂團～Gekijouban Hibike! Euphonium (BDRip 1920x1080 x264 FLAC) [字幕内附]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public
      fun `442727_Ohys_Raws_Gekijouban_Hibike_21_Euphonium___Kitauji_Koukou_Suisougaku_Bu_e_Youkoso_BD_1280x720_x264_AACx3`() {
    val r =
        parse("[Ohys-Raws]劇場版 吹響吧！上低音號～歡迎加入北宇治高中管樂團～Gekijouban Hibike! Euphonium - Kitauji Koukou Suisougaku Bu e Youkoso (BD 1280x720 x264 AACx3) [字幕内附]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `442306_BDRip_MKV`() {
    val r = parse("【极影字幕社】吹响吧！上低音号 剧场版 [简体内嵌] [BDRip][MKV]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `434464_DHR_Sound_21Euphonium_Vol_1_Vol_7_OVA_BDRip_1080P_AVC_P10_FLAC_OPUSx2_SUP`() {
    val r =
        parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號～歡迎加入北宇治高中管樂團～_Hibike!Euphonium][Vol.1-Vol.7+OVA][繁體外掛字幕][BDRip][1080P][AVC_P10_FLAC_OPUSx2_SUP]")
    assertEquals("Vol.1-Vol.7+OVA..Vol.1-Vol.7+OVA", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `433693__Hibike_21_Euphonium___Vol_1__BDRip_1920x1080_x264_AC_3_MP4`() {
    val r =
        parse("【极影字幕社】吹响吧！上低音号-Hibike! Euphonium - Vol.1-[简体内嵌] [BDRip 1920x1080 x264 AC-3][MP4]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `425284_BD720P`() {
    val r = parse("【浩天个人发布】吹响吧！上低音号 BD720P内嵌简体字幕")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `422536_F_SP_01_07_1080P_MP4`() {
    val r = parse("【F宅】吹响吧！上低音号 SP特典合集 01-07 简 1080P MP4")
    assertEquals("01..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `419873__11_Hibike_21_Euphonium_01_14_BDRIP_1080P_X264_FLAC_AAC`() {
    val r = parse("[异域-11番小队][吹响吧！上低音号 Hibike! Euphonium][01-14][BDRIP][1080P][X264_FLAC_AAC]")
    assertEquals("01..14", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `419515_Hibike_21_Euphonium_720P_BIG5`() {
    val r = parse("【极影字幕社】★[吹响吧！上低音号Hibike! Euphonium ][番外篇][720P]BIG5")
    assertEquals("番外篇..番外篇", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `419510_Xrip_Sound_21Euphonium_BDrip_Vol_01_07_1080P_x264_10bit_flac`() {
    val r = parse("【Xrip】[吹響吧！上低音號][Sound!Euphonium][BDrip][Vol.01_07][1080P][x264_10bit_flac]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `419458_Hibike_21_Euphonium_720P_GB`() {
    val r = parse("【极影字幕社】★[吹响吧！上低音号Hibike! Euphonium ][番外篇][720P]GB")
    assertEquals("番外篇..番外篇", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `419401_VCB_Studio_Hibike_21_Euphonium_10bit_1080p_AVC_BDRip_Fin`() {
    val r = parse("[VCB-Studio] Hibike! Euphonium/吹响吧！上低音号 10bit 1080p AVC BDRip [Fin]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `419400_VCB_Studio_Hibike_21_Euphonium_8bit_1080p_BDRip_MP4_ver`() {
    val r = parse("[VCB-Studio] Hibike! Euphonium/吹响吧！上低音号 8bit 1080p BDRip [MP4 ver]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `419155__11_Hibike_21_Euphonium_01_14_BDRIP_720P_X264_10bit_AAC`() {
    val r =
        parse("[异域-11番小队][吹响！悠风号/吹响吧！上低音号 Hibike! Euphonium][01-14][BDRIP][720P][X264_10bit_AAC]")
    assertEquals("01..14", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `418769_F_OVA_1080P_MP4`() {
    val r = parse("【F宅】吹响吧！上低音号 番外篇 OVA 中日双语 1080P MP4")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `418264_F_OVA_720P_MP4`() {
    val r = parse("【F宅】吹响吧！上低音号 番外篇 OVA 中日双语 720P MP4")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `401721_04_Hibike_21_Euphonium_01__13_720P_GB_MP4`() {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第01--13话][合集][720P]GB.MP4")
    assertEquals("01..13", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `399548_04_Hibike_21_Euphonium_01__13_720P_BIG5_MP4`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第01--13話][合集][720P]BIG5.MP4")
    assertEquals("01..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `399320_04_Hibike_21_Euphonium_01__13_1080P_MKV`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第01--13話][合集][1080P][繁简内封][附字体].MKV")
    assertEquals("01..13", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `399295_04_Hibike_21_Euphonium_13_END_1080P_MKV`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第13話]END[1080P][繁简内封][附字体].MKV")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `399294_04_Hibike_21_Euphonium_13_END_720P_BIG5_mp4`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第13話]END[720P][BIG5].mp4")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `399292_04_Hibike_21_Euphonium_13_END_720P_GB_mp4`() {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第13话]END[720P][GB].mp4")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `398496_04_Hibike_21_Euphonium_12_720P_BIG5_mp4`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第12話][720P][BIG5].mp4")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `398494_04_Hibike_21_Euphonium_12_720P_GB_mp4`() {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第12话][720P][GB].mp4")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `398492_04_Hibike_21_Euphonium_12_1080P_MKV`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第12話][1080P][繁简内封][附字体].MKV")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `397689_04_Hibike_21_Euphonium_11_1080P_MKV`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第11話][1080P][繁简内封][附字体].MKV")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `397688_04_Hibike_21_Euphonium_11_720P_BIG5_mp4`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第11話][720P][BIG5].mp4")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `397683_04_Hibike_21_Euphonium_11_720P_GB_mp4`() {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第11话][720P][GB].mp4")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `396942_04_Hibike_21_Euphonium_10_1080P_MKV`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第10話][1080P][繁简内封][附字体].MKV")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `396941_04_Hibike_21_Euphonium_10_720P_BIG5_mp4`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第10話][720P][BIG5].mp4")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `396932_04_Hibike_21_Euphonium_10_720P_GB_mp4`() {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第10话][720P][GB].mp4")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `396164_04_Hibike_21_Euphonium_09_720P_BIG5_mp4`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第09話][720P][BIG5].mp4")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `396160_04_Hibike_21_Euphonium_09_1080P_MKV`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第09話][1080P][繁简内封][附字体].MKV")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `396156_04_Hibike_21_Euphonium_09_720P_GB_mp4`() {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第09话][720P][GB].mp4")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `395399_04_Hibike_21_Euphonium_08_1080P_MKV`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第08話][1080P][繁简内封][附字体].MKV")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `395398_04_Hibike_21_Euphonium_08_720P_BIG5_mp4`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第08話][720P][BIG5].mp4")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `395393_04_Hibike_21_Euphonium_08_720P_GB_mp4`() {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第08话][720P][GB].mp4")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `394612_04_Hibike_21_Euphonium_07_1080P_MKV`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第07話][1080P][繁简内封][附字体].MKV")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `394611_04_Hibike_21_Euphonium_07_720P_BIG5_mp4`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第07話][720P][BIG5].mp4")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `394607_04_Hibike_21_Euphonium_07_720P_GB_mp4`() {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第07话][720P][GB].mp4")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `393818_04_Hibike_21_Euphonium_06_V2_720P_BIG5_mp4`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第06話V2版][720P][BIG5].mp4")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `393805_04_Hibike_21_Euphonium_06_720P_BIG5_mp4`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第06話][720P][BIG5].mp4")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `393785_04_Hibike_21_Euphonium_06_720P_GB_mp4`() {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第06话][720P][GB].mp4")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `393784_04_Hibike_21_Euphonium_06_1080P_MKV`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第06話][1080P][繁简内封][附字体].MKV")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `392969_04_Hibike_21_Euphonium_05_1080P_MKV`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第05話][1080P][繁简内封][附字体].MKV")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `392965_04_Hibike_21_Euphonium_05_720P_BIG5_mp4`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第05話][720P][BIG5].mp4")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `392955_04_Hibike_21_Euphonium_05_720P_GB_mp4`() {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第05话][720P][GB].mp4")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `392109_04_Hibike_21_Euphonium_04_1080P_MKV`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第04話][1080P][繁简内封][附字体].MKV")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `392108_04_Hibike_21_Euphonium_04_720P_BIG5_mp4`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第04話][720P][BIG5].mp4")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `392105_04_Hibike_21_Euphonium_04_720P_GB_mp4`() {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第04话][720P][GB].mp4")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `391310_04_Hibike_21_Euphonium_03_v2_720P_BIG5_mp4`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第03話v2版][720P][BIG5].mp4")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `391273_04_Hibike_21_Euphonium_03_1080P_MKV`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第03話][1080P][繁简内封][附字体].MKV")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `391272_04_Hibike_21_Euphonium_03_720P_BIG5_mp4`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第03話][720P][BIG5].mp4")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `391267_04_Hibike_21_Euphonium_03_720P_GB_mp4`() {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第03话][720P][GB].mp4")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `390515_04_Hibike_21_Euphonium_02_V2_720P_BIG5_mp4`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第02話V2版][720P][BIG5].mp4")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `390406_4_Hibike_21_Euphonium_02_720P_BIG5_mp4`() {
    val r = parse("【極影字幕社】★4月新番[吹響吧！上低音號Hibike! Euphonium ][第02話][720P][BIG5].mp4")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `390404_04_Hibike_21_Euphonium_02_720P_GB_mp4`() {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第02话][720P][GB].mp4")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `390401_04_Hibike_21_Euphonium_02_1080P_MKV`() {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第02話][1080P][繁简内封][附字体].MKV")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `389589_4_Hibike_21_Euphonium_01_720P_BIG5_mp4`() {
    val r = parse("【極影字幕社】★4月新番[吹響吧！上低音號Hibike! Euphonium ][第01話][720P][BIG5].mp4")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `389588_4_Hibike_21_Euphonium_01_1080P_MKV`() {
    val r = parse("【極影字幕社】★4月新番[吹響吧！上低音號Hibike! Euphonium ][第01話][1080P][繁简内封][附字体].MKV")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `389581_4_Hibike_21_Euphonium_01_720P_GB_mp4`() {
    val r = parse("【极影字幕社】★4月新番[吹响吧！上低音号Hibike! Euphonium ][第01话][720P][GB].mp4")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }
}

// @formatter:on
