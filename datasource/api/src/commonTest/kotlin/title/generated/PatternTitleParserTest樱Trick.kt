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
 * 原名: `樱Trick`
 * 数据源: `dmhy`
 *
 * 由 `test-codegen` 的 `GenerateTests.kt` 生成, 不要手动修改!
 * 如果你优化了解析器, 这些 test 可能会失败, 请检查是否它是因为以前解析错误而现在解析正确了. 
 * 如果是, 请更新测试数据: 执行 `GenerateTests.kt`.
 */
public class PatternTitleParserTest樱Trick : PatternBasedTitleParserTestSuite() {
  @Test
  public fun `656570_Trick_Sakura_Trick_01_12_avc_flac_mkv`() {
    val r = parse("[愛戀&漫猫字幕社]櫻Trick Sakura Trick 01-12 avc_ flac mkv繁體內嵌合集（急招時軸）")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `656569_Trick_Sakura_Trick_01_12_avc_flac_mkv`() {
    val r = parse("[爱恋&漫猫字幕社]樱Trick Sakura Trick 01-12 avc_flac mkv 简体内嵌合集(急招时轴)")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `521448_Snow_Raws_Trick_Sakura_Trick_Trick_BD_1920x1080_HEVC_YUV420P10_FLAC`() {
    val r = parse("[Snow-Raws] 樱Trick/Sakura Trick/桜Trick(BD 1920x1080 HEVC-YUV420P10 FLAC)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `451596_DMG_Hakugetsu_VCB_Studio_Sakura_Trick_Trick_10_bit_1080p_BDRip_Reseed_Fin`() {
    val r =
        parse("[DMG&Hakugetsu&VCB-Studio] Sakura Trick / 樱Trick 10-bit 1080p BDRip [Reseed Fin]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `367159_Sakura_Trick_Trick_BDRip`() {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][BDRip][简繁日外挂][庆祝华盟字幕社成立十周年]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `363710_Trick_Sakura_Trick_rip_720P`() {
    val r = parse("《樱Trick》（Sakura_Trick）[漏勺rip 720P]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `363209_VCB_S_Sakura_Trick_trick_10bit_1080p_BDRip_Fin`() {
    val r = parse("[动漫国 & 白月 & VCB-S] Sakura Trick/樱trick 10bit 1080p BDRip Fin 繁简外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `363207_VCB_S_Sakura_Trick_trick_8bit_720p_BDRip_Fin`() {
    val r = parse("[动漫国 & 白月 & VCB-S] Sakura Trick/樱trick 8bit 720p BDRip Fin 繁简外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `362481__11_Trick_Sakura_Trick_BDRIP_1_12_SP_720P_X264_10bit_AAC`() {
    val r = parse("[异域-11番小队][樱Trick Sakura Trick][BDRIP][1-12+SP][720P][X264-10bit_AAC]")
    assertEquals("01..12+SP", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `352064_Sakura_Trick_Trick_01_12_Fin_720pHQ_MP4`() {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][01-12_Fin][720pHQ_MP4][简繁外挂字幕]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `350616_01_Trick_01_12_720P_MP4`() {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick/樱谋诡计][01-12(全集)][720P][简体][MP4](内详)")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `350615_01_Trick_01_12_720P_MP4`() {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[櫻Trick/櫻謀詭計][01-12(全集)][720P][繁體][MP4](內詳)")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `345137_01_Trick_01_12_1280x720_MKV`() {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][01-12][1280x720][簡繁外掛][MKV]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `345108_1_Trick_Sakura_Trick_01_12_720p_MP4`() {
    val r = parse("【动漫先锋字幕组】◆1月新番【樱Trick _Sakura Trick_樱之恋】第01-12话[全][720p][MP4][繁体]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `344785_01_Trick_11_1280x720_MKV`() {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][11][1280x720][簡繁外掛][MKV]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `344380_Trick_Sakura_Trick_01_12_GB_720P_MP4`() {
    val r = parse("[雪漫影字幕组&丸子家族][樱Trick(Sakura Trick)][01-12][合集][GB][720P][MP4]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `344324_Sakura_Trick_Trick_01_12_Fin_GB_720p_MP4`() {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][01-12_Fin][GB][720p_MP4]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `344292_Sakura_Trick_Trick_12_GB_720p_MP4`() {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][12][GB][720p_MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `343869_Trick_Sakura_Trick_12END_1280x720_MP4`() {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][12END][1280x720][简体][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `343868_Trick_Sakura_Trick_12END_1280x720_MP4`() {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][12END][1280x720][繁體][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `343843_1_Sakura_Trick_Trick_01_12_GB_MP4_720p`() {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第01-12话 GB MP4 720p 合集")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `343805_Trick_Sakura_Trick_12_1280x720_MP4_PC_PSV`() {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第12話_完][1280x720][MP4_PC&PSV兼容][繁體]（招募中")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `343793_01_Trick_12END_720P_MP4`() {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][12END][720P][简体][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `343792_trick_sakura_trick_12_1280x720_MP4`() {
    val r = parse("【千夏字幕组现充爆破分队】【樱trick_sakura_trick】[第12话完][1280x720][MP4][简体]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `343779_1_Sakura_Trick_Trick_12_GB_MP4_720p`() {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第12话 GB MP4 720p 完")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `343776_01_Trick_12_END_720P_MP4`() {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[櫻Trick][12 END][720P][繁體][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `343698_Sakura_Trick_Trick_11_GB_720p_MP4`() {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][11][GB][720p_MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `343071_Trick_Sakura_Trick_11_1280x720_MP4`() {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][11][1280x720][繁體][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `343070_Trick_Sakura_Trick_11_1280x720_MP4`() {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][11][1280x720][简体][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `342993_Trick_Sakura_Trick_11_1280x720_MP4_PC_PSV`() {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第11話][1280x720][MP4_PC&PSV兼容][繁體]（招募中")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `342865_1_Sakura_Trick_Trick_11_GB_MP4_720p_Rev`() {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第11话 GB MP4 720p Rev")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `342864_trick_sakura_trick_11_1280x720_MP4`() {
    val r = parse("【千夏字幕组现充爆破分队】【樱trick_sakura_trick】[第11话][1280x720][MP4][简体]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `342857_01_Trick_11_720P_MP4`() {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][11][720P][简体][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `342845_01_Trick_11_720P_MP4`() {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[櫻Trick][11][720P][繁體][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `342821_01_Trick_10_1280x720_MKV`() {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][10][1280x720][簡繁外掛][MKV]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `342683_Sakura_Trick_Trick_10_GB_720p_MP4`() {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][10][GB][720p_MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `342554_01_Trick_09_1280x720_MKV`() {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][09][1280x720][簡繁外掛][MKV]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `342064_Trick_Sakura_Trick_10_1280x720_MP4`() {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][10][1280x720][简体][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `341956_01_Trick_10_720P_MP4`() {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][10][720P][简体][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `341955_1_Sakura_Trick_Trick_10_GB_MP4_720p`() {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第10话 GB MP4 720p")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `341947_Trick_Sakura_Trick_10_1280x720_MP4_PC_PSV`() {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第10話][1280x720][MP4_PC&PSV兼容][繁體]（招募中")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `341945_trick_sakura_trick_10_1280x720_MP4`() {
    val r = parse("【千夏字幕组现充爆破分队】【樱trick_sakura_trick】[第10话][1280x720][MP4][简体]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `341944_01_Trick_10_720P_MP4`() {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[櫻Trick][10][720P][繁體][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `341943_1_Trick_Sakura_Trick_10_1280x720_BIG5_MP4`() {
    val r = parse("【東京不夠熱】【1月新番】櫻Trick Sakura Trick【10】【1280x720】【繁體】【BIG5_MP4】")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `341942_1_Trick_Sakura_Trick_10_1280x720_GB_MP4`() {
    val r = parse("【东京不够热】【1月新番】樱Trick Sakura Trick【10】【1280x720】【简体】【GB_MP4】")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `341654_01_Trick_08_1280x720_MKV`() {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][08][1280x720][簡繁外掛][MKV]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `341626_Sakura_Trick_Trick_08_09_GB_720p_MP4`() {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][08-09][GB][720p_MP4]")
    assertEquals("08..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `341126_Trick_Sakura_Trick_09_1280x720_MP4`() {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][09][1280x720][繁體][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `341125_Trick_Sakura_Trick_09_1280x720_MP4`() {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][09][1280x720][简体][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `341050_Trick_Sakura_Trick_09_1280x720_MP4_PC_PSV`() {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第09話][1280x720][MP4_PC&PSV兼容][繁體]（招募中")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `341013_01_Trick_09_720P_MP4`() {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][09][720P][简体][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `341005_01_Trick_09_720P_MP4`() {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[櫻Trick][09][720P][繁體][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `341002_1_Sakura_Trick_Trick_09_GB_MP4_720p`() {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第09话 GB MP4 720p")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `340995_trick_sakura_trick_08_1280x720_MP4`() {
    val r = parse("【千夏字幕组现充爆破分队】【樱trick_sakura_trick】[第09话][1280x720][MP4][简体]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `340527_01_Trick_07_1280x720_MKV`() {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][07][1280x720][簡繁外掛][MKV]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `340162_trick_sakura_trick_08_1280x720_MP4`() {
    val r = parse("【千夏字幕组现充爆破分队】【樱trick_sakura_trick】[第08话][1280x720][MP4][简体]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `340161_trick_sakura_trick_07_1280x720_MP4`() {
    val r = parse("【千夏字幕组现充爆破分队】【樱trick_sakura_trick】[第07话][1280x720][MP4][简体]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `340160_trick_sakura_trick_06_1280x720_MP4`() {
    val r = parse("【千夏字幕组现充爆破分队】【樱trick_sakura_trick】[第06话][1280x720][MP4][简体]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `340159_trick_sakura_trick_05_1280x720_MP4`() {
    val r = parse("【千夏字幕组现充爆破分队】【樱trick_sakura_trick】[第05话][1280x720][MP4][简体]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `340158_trick_sakura_trick_04_1280x720_MP4`() {
    val r = parse("【千夏字幕组现充爆破分队】【樱trick_sakura_trick】[第04话][1280x720][MP4][简体]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `340137_Trick_Sakura_Trick_08_1280x720_MP4`() {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][08][1280x720][繁體][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `340136_Trick_Sakura_Trick_08_1280x720_MP4`() {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][08][1280x720][简体][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `340017_01_Trick_08_720P_MP4`() {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][08][720P][简体][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `340014_1_Sakura_Trick_Trick_08_GB_MP4_720p`() {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第08话 GB MP4 720p")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `340002_01_Trick_08_720P_MP4`() {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[櫻Trick][08][720P][繁體][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `340000_Trick_Sakura_Trick_08_1280x720_MP4_PC_PSV`() {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第08話][1280x720][MP4_PC&PSV兼容][繁體]（招募中")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `339990_1_Trick_Sakura_Trick_08_1280x720_BIG5_MP4`() {
    val r = parse("【東京不夠熱】【1月新番】櫻Trick Sakura Trick【08】【1280x720】【繁體】【BIG5_MP4】")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `339989_1_Trick_Sakura_Trick_08_1280x720_GB_MP4`() {
    val r = parse("【东京不够热】【1月新番】樱Trick Sakura Trick【08】【1280x720】【简体】【GB_MP4】")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `339734_Sakura_Trick_Trick_07_GB_720p_MP4`() {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][07][GB][720p_MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `339173_Trick_Sakura_Trick_07v2_1280x720_MP4`() {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][07v2][1280x720][繁體][MP4][修正不同步]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `339172_Trick_Sakura_Trick_07v2_1280x720_MP4`() {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][07v2][1280x720][简体][MP4][修正不同步]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `339118_Trick_Sakura_Trick_07_1280x720_MP4`() {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][07][1280x720][繁體][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `339117_Trick_Sakura_Trick_07_1280x720_MP4`() {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][07][1280x720][简体][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `339080_01_Trick_06_1280x720_MKV`() {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][06][1280x720][簡繁外掛][MKV]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `339029_01_Trick_07_720P_MP4`() {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][07][720P][简体][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `339027_1_Sakura_Trick_Trick_07_GB_MP4_720p`() {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第07话 GB MP4 720p")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `339017_01_Trick_07_720P_MP4`() {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[櫻Trick][07][720P][繁體][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `339012_Trick_Sakura_Trick_07_1280x720_MP4_PC_PSV`() {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第07話][1280x720][MP4_PC&PSV兼容][繁體]（招募中")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `339011_Trick_Sakura_Trick_06_1280x720_MP4_PC_PSV`() {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第06話][1280x720][MP4_PC&PSV兼容][繁體]（招募中")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `339010_Trick_Sakura_Trick_05_1280x720_MP4_PC_PSV`() {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第05話][1280x720][MP4_PC&PSV兼容][繁體]（招募中")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `339009_Trick_Sakura_Trick_04_1280x720_MP4_PC_PSV`() {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第04話][1280x720][MP4_PC&PSV兼容][繁體]（招募中")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `339007_1_Trick_Sakura_Trick_07_1280x720_BIG5_MP4`() {
    val r = parse("【東京不夠熱】【1月新番】櫻Trick Sakura Trick【07】【1280x720】【繁體】【BIG5_MP4】")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `339006_1_Trick_Sakura_Trick_07_1280x720_GB_MP4`() {
    val r = parse("【东京不够热】【1月新番】樱Trick Sakura Trick【07】【1280x720】【简体】【GB_MP4】")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `338884_Sakura_Trick_Trick_04_06_GB_720p_MP4`() {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][04-06][GB][720p_MP4]")
    assertEquals("04..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `338697_01_Trick_05_1280x720_MKV`() {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][05][1280x720][簡繁外掛][MKV]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `338575_1_Trick_Sakura_Trick_06_720p_MP4`() {
    val r = parse("【动漫先锋字幕组】◆1月新番【樱Trick _Sakura Trick】第06话[720p][MP4][繁体]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `338574_1_Trick_Sakura_Trick_05_720p_MP4`() {
    val r = parse("【动漫先锋字幕组】◆1月新番【樱Trick _Sakura Trick】第05话[720p][MP4][繁体]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `338245_Trick_Sakura_Trick_06_1280x720_MP4`() {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][06][1280x720][繁體][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `338244_Trick_Sakura_Trick_06_1280x720_MP4`() {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][06][1280x720][简体][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `338137_01_Trick_06_720P_MP4`() {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][06][720P][简体][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `338135_1_Sakura_Trick_Trick_06_GB_MP4_720p`() {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第06话 GB MP4 720p")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `338111_01_Trick_06_720P_MP4`() {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[櫻Trick][06][720P][繁體][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `338109_1_Trick_Sakura_Trick_06_1280x720_BIG5_MP4`() {
    val r = parse("【東京不夠熱】【1月新番】櫻Trick Sakura Trick【06】【1280x720】【繁體】【BIG5_MP4】")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `338108_1_Trick_Sakura_Trick_06_1280x720_GB_MP4`() {
    val r = parse("【东京不够热】【1月新番】樱Trick Sakura Trick【06】【1280x720】【简体】【GB_MP4】")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `337296_Trick_Sakura_Trick_05_1280x720_MP4`() {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][05][1280x720][繁體][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `337293_Trick_Sakura_Trick_05_1280x720_MP4`() {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][05][1280x720][简体][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `337243_01_Trick_04_1280x720_MKV`() {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][04][1280x720][簡繁外掛][MKV]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `337201_01_Trick_05_720P_MP4`() {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][05][720P][简体][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `337198_01_Trick_05_720P_MP4`() {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[櫻Trick][05][720P][繁體][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `337182_1_Sakura_Trick_Trick_05_GB_MP4_720p`() {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第05话 GB MP4 720p")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `337174_1_Trick_Sakura_Trick_05_1280x720_BIG5_MP4`() {
    val r = parse("【東京不夠熱】【1月新番】櫻Trick Sakura Trick【05】【1280x720】【繁體】【BIG5_MP4】")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `337172_1_Trick_Sakura_Trick_05_1280x720_GB_MP4`() {
    val r = parse("【东京不够热】【1月新番】樱Trick Sakura Trick【05】【1280x720】【简体】【GB_MP4】")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `337124_1_Trick_Sakura_Trick_04_720p_MP4`() {
    val r = parse("【动漫先锋字幕组】◆1月新番【樱Trick _Sakura Trick】第04话[720p][MP4][繁体]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `336412_Trick_Sakura_Trick_04_1280x720_MP4`() {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][04][1280x720][繁體][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `336411_Trick_Sakura_Trick_04_1280x720_MP4`() {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][04][1280x720][简体][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `336272_01_Trick_04_720P_MP4`() {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][04][720P][简体][MP4] 大家新年快乐～")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `336267_01_Trick_04_720P_MP4`() {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[樱Trick][04][720P][繁體][MP4]大家新年快樂~")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `336264_1_Sakura_Trick_Trick_04_GB_MP4_720p`() {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第04话 GB MP4 720p 祝大家春节快乐")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `336257_1_Trick_Sakura_Trick_04_1280x720_BIG5_MP4`() {
    val r = parse("【東京不夠熱】【1月新番】櫻Trick Sakura Trick【04】【1280x720】【繁體】【BIG5_MP4】[春節快樂]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `336255_1_Trick_Sakura_Trick_04_1280x720_GB_MP4`() {
    val r = parse("【东京不够热】【1月新番】樱Trick Sakura Trick【04】【1280x720】【简体】【GB_MP4】[春节快乐]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `336216_01_Trick_03_1280x720_MKV`() {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][03][1280x720][簡繁外掛][MKV](新年快樂)")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `335938_Trick_Sakura_Trick_03_1280x720_MP4_PC_PSV`() {
    val r = parse("【千夏字幕组现充爆破分队】【樱Trick_Sakura_Trick】[第03话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `335798_HKG_Sakura_Trick_Trick_02_03_BIG5_480P_MP4`() {
    val r = parse("[HKG字幕組][Sakura Trick 櫻Trick][02-03][BIG5][480P_MP4]")
    assertEquals("02..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `335631_Sakura_Trick_Trick_03_GB_720p_MP4`() {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][03][GB][720p_MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `335470_Trick_Sakura_Trick_03_1280x720_MP4`() {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][03][1280x720][繁體][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `335467_Trick_Sakura_Trick_03_1280x720_MP4`() {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][03][1280x720][简体][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `335398_1_Trick_Sakura_Trick_03_720p_MP4`() {
    val r = parse("【动漫先锋字幕组】◆1月新番【樱Trick _Sakura Trick】第03话[720p][MP4][繁体]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `335372_01_Trick_02_1280x720_MKV`() {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][02][1280x720][簡繁外掛][MKV]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `335331_Trick_Sakura_Trick_03_1280x720_MP4_PC_PSV`() {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第03話][1280x720][MP4_PC&PSV兼容][繁體]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `335295_1_Sakura_Trick_Trick_03_GB_MP4_720p`() {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第03话 GB MP4 720p (附重要通知)")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `335291_01_Trick_03_720P_MP4`() {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][03][720P][简体][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `335288_01_Trick_03_720P_MP4`() {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[樱Trick][03][720P][繁體][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `335274_1_Trick_Sakura_Trick_03_1280x720_BIG5_MP4`() {
    val r = parse("【東京不夠熱】【1月新番】櫻Trick Sakura Trick【03】【1280x720】【繁體】【BIG5_MP4】")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `335271_1_Trick_Sakura_Trick_03_1280x720_GB_MP4`() {
    val r = parse("【东京不够热】【1月新番】樱Trick Sakura Trick【03】【1280x720】【简体】【GB_MP4】")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `335081_1_Trick_Sakura_Trick_01_1280x720_BIG5_MP4`() {
    val r = parse("【東京不夠熱】【1月新番】櫻Trick Sakura Trick【01】【1280x720】【繁體】【BIG5_MP4】")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `334600_1_Trick_Sakura_Trick_02_720p_x264_BIG5`() {
    val r = parse("【动漫先锋字幕组】◆1月新番【樱Trick _Sakura Trick】第02话[720p][x264][BIG5]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `334518_Trick_Sakura_Trick_02_1280x720_MP4`() {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][02][1280x720][繁體][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `334517_Trick_Sakura_Trick_02_1280x720_MP4`() {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][02][1280x720][简体][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `334406_Sakura_Trick_Trick_02_GB_720p_MP4`() {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][02][GB][720p_MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `334262_01_Trick_02_720P_MP4`() {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][02][720P][简体][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `334260_1_Sakura_Trick_Trick_02_GB_MP4_720p`() {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第02话 GB MP4 720p")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `334253_01_Trick_02_720P_MP4`() {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[樱Trick][02][720P][繁體][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `334247_trick_sakura_trick_02_1280x720_MP4`() {
    val r = parse("【千夏字幕组现充爆破分队】【樱Trick_Sakura_Trick】[第02话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `334246_Trick_Sakura_Trick_02_1280x720_MP4_PC_PSV`() {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第02話][1280x720][MP4_PC&PSV兼容][繁體]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `334241_1_Trick_Sakura_Trick_02_1280x720_BIG5_MP4`() {
    val r = parse("【東京不夠熱】【1月新番】櫻Trick Sakura Trick【02】【1280x720】【繁體】【BIG5_MP4】")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `334239_1_Trick_Sakura_Trick_02_1280x720_GB_MP4`() {
    val r = parse("【东京不够热】【1月新番】樱Trick Sakura Trick【02】【1280x720】【简体】【GB_MP4】")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `334055_01_Trick_01_1280x720_MKV`() {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][01][1280x720][簡繁外掛][MKV]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `333807_HKG_Sakura_Trick_Trick_01_BIG5_480P_MP4`() {
    val r = parse("[HKG字幕組][Sakura Trick 櫻Trick][01][BIG5][480P_MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `333574_Trick_Sakura_Trick_01_1280x720_MP4`() {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][01][1280x720][繁體][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `333573_Trick_Sakura_Trick_01_1280x720_MP4`() {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][01][1280x720][简体][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `333359_10_Trick_Sakura_Trick_01_720p_x264_BIG5`() {
    val r = parse("【动漫先锋字幕组】◆1月新番【樱Trick _Sakura Trick】第01话[720p][x264][BIG5]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `333336_Sakura_Trick_Trick_01_GB_720p_MP4`() {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][01][GB][720p_MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `333312_Trick_Sakura_Trick_01_1280x720_MP4_PC_PSV`() {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第01話][1280x720][MP4_PC&PSV兼容][繁體]（內詳")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `333249_01_Trick_01_720P_MP4`() {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][01][720P][简体][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `333246_1_Sakura_Trick_Trick_01_GB_MP4_720p`() {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第01话 GB MP4 720p")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `333245_01_Trick_01_720P_MP4`() {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[樱Trick][01][720P][繁體][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `333240_trick_sakura_trick_01_1280x720_MP4`() {
    val r = parse("【千夏字幕组现充爆破分队】【樱trick_sakura_trick】[第01話][1280x720][MP4][简体]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `333233_Trick_Sakura_Trick_01_1280x720_GB_MP4`() {
    val r = parse("【东京不够热】【1月新番】樱Trick Sakura Trick【01】【1280x720】【简体】【GB_MP4】")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }
}

// @formatter:on
