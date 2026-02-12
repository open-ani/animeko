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
 * 原名: `樱Trick`
 * 数据源: `dmhy`
 *
 * 由 `test-codegen` 的 `GenerateTests.kt` 生成, 不要手动修改!
 * 如果你优化了解析器, 这些 test 可能会失败, 请检查是否它是因为以前解析错误而现在解析正确了. 
 * 如果是, 请更新测试数据: 执行 `GenerateTests.kt`.
 */
public class PatternTitleParserTest樱Trick : PatternBasedTitleParserTestSuite() {
  @Test
  public fun `656570-656569-521448-451596-367159-363710-363209-363207-362481-352064`() {
    kotlin.run {
    val r = parse("[愛戀&漫猫字幕社]櫻Trick Sakura Trick 01-12 avc_ flac mkv繁體內嵌合集（急招時軸）")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕社]樱Trick Sakura Trick 01-12 avc_flac mkv 简体内嵌合集(急招时轴)")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Snow-Raws] 樱Trick/Sakura Trick/桜Trick(BD 1920x1080 HEVC-YUV420P10 FLAC)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DMG&Hakugetsu&VCB-Studio] Sakura Trick / 樱Trick 10-bit 1080p BDRip [Reseed Fin]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][BDRip][简繁日外挂][庆祝华盟字幕社成立十周年]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("《樱Trick》（Sakura_Trick）[漏勺rip 720P]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[动漫国 & 白月 & VCB-S] Sakura Trick/樱trick 10bit 1080p BDRip Fin 繁简外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[动漫国 & 白月 & VCB-S] Sakura Trick/樱trick 8bit 720p BDRip Fin 繁简外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[异域-11番小队][樱Trick Sakura Trick][BDRIP][1-12+SP][720P][X264-10bit_AAC]")
    assertEquals("01..12+SP", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][01-12_Fin][720pHQ_MP4][简繁外挂字幕]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `350616-350615-345137-345108-344785-344380-344324-344292-343869-343868`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick/樱谋诡计][01-12(全集)][720P][简体][MP4](内详)")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[櫻Trick/櫻謀詭計][01-12(全集)][720P][繁體][MP4](內詳)")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][01-12][1280x720][簡繁外掛][MKV]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫先锋字幕组】◆1月新番【樱Trick _Sakura Trick_樱之恋】第01-12话[全][720p][MP4][繁体]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][11][1280x720][簡繁外掛][MKV]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[雪漫影字幕组&丸子家族][樱Trick(Sakura Trick)][01-12][合集][GB][720P][MP4]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][01-12_Fin][GB][720p_MP4]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][12][GB][720p_MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][12END][1280x720][简体][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][12END][1280x720][繁體][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `343843-343805-343793-343792-343779-343776-343698-343071-343070-342993`() {
    kotlin.run {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第01-12话 GB MP4 720p 合集")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第12話_完][1280x720][MP4_PC&PSV兼容][繁體]（招募中")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][12END][720P][简体][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组现充爆破分队】【樱trick_sakura_trick】[第12话完][1280x720][MP4][简体]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第12话 GB MP4 720p 完")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[櫻Trick][12 END][720P][繁體][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][11][GB][720p_MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][11][1280x720][繁體][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][11][1280x720][简体][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第11話][1280x720][MP4_PC&PSV兼容][繁體]（招募中")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `342865-342864-342857-342845-342821-342683-342554-342064-341956-341955`() {
    kotlin.run {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第11话 GB MP4 720p Rev")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组现充爆破分队】【樱trick_sakura_trick】[第11话][1280x720][MP4][简体]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][11][720P][简体][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[櫻Trick][11][720P][繁體][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][10][1280x720][簡繁外掛][MKV]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][10][GB][720p_MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][09][1280x720][簡繁外掛][MKV]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][10][1280x720][简体][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][10][720P][简体][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第10话 GB MP4 720p")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `341947-341945-341944-341943-341942-341654-341626-341126-341125-341050`() {
    kotlin.run {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第10話][1280x720][MP4_PC&PSV兼容][繁體]（招募中")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组现充爆破分队】【樱trick_sakura_trick】[第10话][1280x720][MP4][简体]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[櫻Trick][10][720P][繁體][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【東京不夠熱】【1月新番】櫻Trick Sakura Trick【10】【1280x720】【繁體】【BIG5_MP4】")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【东京不够热】【1月新番】樱Trick Sakura Trick【10】【1280x720】【简体】【GB_MP4】")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][08][1280x720][簡繁外掛][MKV]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][08-09][GB][720p_MP4]")
    assertEquals("08..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][09][1280x720][繁體][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][09][1280x720][简体][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第09話][1280x720][MP4_PC&PSV兼容][繁體]（招募中")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `341013-341005-341002-340995-340527-340162-340161-340160-340159-340158`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][09][720P][简体][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[櫻Trick][09][720P][繁體][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第09话 GB MP4 720p")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组现充爆破分队】【樱trick_sakura_trick】[第09话][1280x720][MP4][简体]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][07][1280x720][簡繁外掛][MKV]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组现充爆破分队】【樱trick_sakura_trick】[第08话][1280x720][MP4][简体]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组现充爆破分队】【樱trick_sakura_trick】[第07话][1280x720][MP4][简体]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组现充爆破分队】【樱trick_sakura_trick】[第06话][1280x720][MP4][简体]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组现充爆破分队】【樱trick_sakura_trick】[第05话][1280x720][MP4][简体]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组现充爆破分队】【樱trick_sakura_trick】[第04话][1280x720][MP4][简体]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `340137-340136-340017-340014-340002-340000-339990-339989-339734-339173`() {
    kotlin.run {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][08][1280x720][繁體][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][08][1280x720][简体][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][08][720P][简体][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第08话 GB MP4 720p")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[櫻Trick][08][720P][繁體][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第08話][1280x720][MP4_PC&PSV兼容][繁體]（招募中")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【東京不夠熱】【1月新番】櫻Trick Sakura Trick【08】【1280x720】【繁體】【BIG5_MP4】")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【东京不够热】【1月新番】樱Trick Sakura Trick【08】【1280x720】【简体】【GB_MP4】")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][07][GB][720p_MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][07v2][1280x720][繁體][MP4][修正不同步]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `339172-339118-339117-339080-339029-339027-339017-339012-339011-339010`() {
    kotlin.run {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][07v2][1280x720][简体][MP4][修正不同步]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][07][1280x720][繁體][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][07][1280x720][简体][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][06][1280x720][簡繁外掛][MKV]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][07][720P][简体][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第07话 GB MP4 720p")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[櫻Trick][07][720P][繁體][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第07話][1280x720][MP4_PC&PSV兼容][繁體]（招募中")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第06話][1280x720][MP4_PC&PSV兼容][繁體]（招募中")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第05話][1280x720][MP4_PC&PSV兼容][繁體]（招募中")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `339009-339007-339006-338884-338697-338575-338574-338245-338244-338137`() {
    kotlin.run {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第04話][1280x720][MP4_PC&PSV兼容][繁體]（招募中")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【東京不夠熱】【1月新番】櫻Trick Sakura Trick【07】【1280x720】【繁體】【BIG5_MP4】")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【东京不够热】【1月新番】樱Trick Sakura Trick【07】【1280x720】【简体】【GB_MP4】")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][04-06][GB][720p_MP4]")
    assertEquals("04..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][05][1280x720][簡繁外掛][MKV]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫先锋字幕组】◆1月新番【樱Trick _Sakura Trick】第06话[720p][MP4][繁体]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫先锋字幕组】◆1月新番【樱Trick _Sakura Trick】第05话[720p][MP4][繁体]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][06][1280x720][繁體][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][06][1280x720][简体][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][06][720P][简体][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `338135-338111-338109-338108-337296-337293-337243-337201-337198-337182`() {
    kotlin.run {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第06话 GB MP4 720p")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[櫻Trick][06][720P][繁體][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【東京不夠熱】【1月新番】櫻Trick Sakura Trick【06】【1280x720】【繁體】【BIG5_MP4】")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【东京不够热】【1月新番】樱Trick Sakura Trick【06】【1280x720】【简体】【GB_MP4】")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][05][1280x720][繁體][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][05][1280x720][简体][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][04][1280x720][簡繁外掛][MKV]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][05][720P][简体][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[櫻Trick][05][720P][繁體][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第05话 GB MP4 720p")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `337174-337172-337124-336412-336411-336272-336267-336264-336257-336255`() {
    kotlin.run {
    val r = parse("【東京不夠熱】【1月新番】櫻Trick Sakura Trick【05】【1280x720】【繁體】【BIG5_MP4】")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【东京不够热】【1月新番】樱Trick Sakura Trick【05】【1280x720】【简体】【GB_MP4】")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫先锋字幕组】◆1月新番【樱Trick _Sakura Trick】第04话[720p][MP4][繁体]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][04][1280x720][繁體][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][04][1280x720][简体][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][04][720P][简体][MP4] 大家新年快乐～")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[樱Trick][04][720P][繁體][MP4]大家新年快樂~")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第04话 GB MP4 720p 祝大家春节快乐")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【東京不夠熱】【1月新番】櫻Trick Sakura Trick【04】【1280x720】【繁體】【BIG5_MP4】[春節快樂]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【东京不够热】【1月新番】樱Trick Sakura Trick【04】【1280x720】【简体】【GB_MP4】[春节快乐]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `336216-335938-335798-335631-335470-335467-335398-335372-335331-335295`() {
    kotlin.run {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][03][1280x720][簡繁外掛][MKV](新年快樂)")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组现充爆破分队】【樱Trick_Sakura_Trick】[第03话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[HKG字幕組][Sakura Trick 櫻Trick][02-03][BIG5][480P_MP4]")
    assertEquals("02..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][03][GB][720p_MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][03][1280x720][繁體][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][03][1280x720][简体][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫先锋字幕组】◆1月新番【樱Trick _Sakura Trick】第03话[720p][MP4][繁体]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][02][1280x720][簡繁外掛][MKV]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第03話][1280x720][MP4_PC&PSV兼容][繁體]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第03话 GB MP4 720p (附重要通知)")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `335291-335288-335274-335271-335081-334600-334518-334517-334406-334262`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][03][720P][简体][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[樱Trick][03][720P][繁體][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【東京不夠熱】【1月新番】櫻Trick Sakura Trick【03】【1280x720】【繁體】【BIG5_MP4】")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【东京不够热】【1月新番】樱Trick Sakura Trick【03】【1280x720】【简体】【GB_MP4】")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【東京不夠熱】【1月新番】櫻Trick Sakura Trick【01】【1280x720】【繁體】【BIG5_MP4】")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫先锋字幕组】◆1月新番【樱Trick _Sakura Trick】第02话[720p][x264][BIG5]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][02][1280x720][繁體][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][02][1280x720][简体][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][02][GB][720p_MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][02][720P][简体][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `334260-334253-334247-334246-334241-334239-334055-333807-333574-333573`() {
    kotlin.run {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第02话 GB MP4 720p")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[樱Trick][02][720P][繁體][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组现充爆破分队】【樱Trick_Sakura_Trick】[第02话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第02話][1280x720][MP4_PC&PSV兼容][繁體]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【東京不夠熱】【1月新番】櫻Trick Sakura Trick【02】【1280x720】【繁體】【BIG5_MP4】")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【东京不够热】【1月新番】樱Trick Sakura Trick【02】【1280x720】【简体】【GB_MP4】")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【白月字幕組&動漫國字幕組】★01月新番[樱Trick][01][1280x720][簡繁外掛][MKV]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[HKG字幕組][Sakura Trick 櫻Trick][01][BIG5][480P_MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【異域字幕組】★ [櫻Trick][Sakura Trick][01][1280x720][繁體][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【异域字幕组】★ [樱Trick][Sakura Trick][01][1280x720][简体][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `333359-333336-333312-333249-333246-333245-333240-333233`() {
    kotlin.run {
    val r = parse("【动漫先锋字幕组】◆1月新番【樱Trick _Sakura Trick】第01话[720p][x264][BIG5]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【华盟字幕社】[Sakura_Trick][樱Trick][01][GB][720p_MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組現充爆破分隊】【櫻Trick_Sakura_Trick】[第01話][1280x720][MP4_PC&PSV兼容][繁體]（內詳")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&白月字幕组】★01月新番[樱Trick][01][720P][简体][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★1月新番 Sakura Trick / 樱Trick 第01话 GB MP4 720p")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&白月字幕組】★01月新番[樱Trick][01][720P][繁體][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组现充爆破分队】【樱trick_sakura_trick】[第01話][1280x720][MP4][简体]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【东京不够热】【1月新番】樱Trick Sakura Trick【01】【1280x720】【简体】【GB_MP4】")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }
}

// @formatter:on
