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
  public fun `685031-679848-675404-675383-675368-675285-675279-674235-674234-673571`() {
    kotlin.run {
    val r =
        parse("[Fatum Fatalis] 约会大作战 第五季 / デート・ア・ライブV / Date a Live V (BD REENCODE EP01-12+SP Fin)")
    assertEquals("01..12+SP", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[7³ACG] 约会大作战5/ デート・ア・ライブ Ⅴ/Date A Live S05 | 01-12 [简繁字幕] BDrip 1080p x265 FLAC 2.0")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][约会大作战 第四季/Date a Live IV/デート・ア・ライブIV][01-12TV全集][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][约会大作战 第三季/Date a Live III/デート・ア・ライブIII][01-12TV全集][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][约会大作战 第二季/Date a Live II/デート・ア・ライブ II][01-10TV全集+OAD+特典映像][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV]")
    assertEquals("01..10+OAD", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][约会大作战 第一季/Date a Live S1/デート・ア・ライブ][导演剪辑版/Director's Cut/ディレクターズカット版][01-12TV全集+OAD][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV]")
    assertEquals("01..12+OAD", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][约会大作战 第一季/Date a Live S1/デート・ア・ライブ][01-12TV全集+OAD][1080P][BDRip][HEVC-10bit][FLAC][MKV]")
    assertEquals("01..12+OAD", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组] 约会大作战/Date A Live V (01-12Fin WEBRIP 1080p AVC AAC MP4 2024年4月 简中)")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組] 約會大作戰/Date A Live V (01-12Fin WEBRIP 1080p AVC AAC MP4 2024年4月 繁中)")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第12话 MP4 720p 完")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `673096-673095-672920-672917-672916-672876-672875-672874-672454-672110`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][12][1080p][MP4][简中]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][12][1080p][MP4][繁中]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Date A Live / 約會大作戰 DATE A LIVE V - 12 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][11][1080p][MP4][简中]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][11][1080p][MP4][繁中]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第11话 MP4 720p")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第10话 MP4 720p")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第09话 MP4 720p")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Date A Live / 約會大作戰 DATE A LIVE V - 11 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][10][1080p][MP4][繁中]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `672109-671976-671725-671601-671600-671437-671221-671220-671219-671124`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][10][1080p][MP4][繁中]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Date A Live / 約會大作戰 DATE A LIVE V - 10 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Lilith-Raws] 約會大作戰 / Date A Live S05 - 09 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][09][1080p][MP4][简中]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][09][1080p][MP4][繁中]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Date A Live / 約會大作戰 DATE A LIVE V - 09 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][08][1080p][MP4][简中]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][08][1080p][MP4][繁中]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第08话 MP4 720p")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第07话 MP4 720p")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `671123-670990-670937-670879-670878-670562-670222-670221-670174-670132`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第06话 MP4 720p")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Lilith-Raws] 約會大作戰 / Date A Live S05 - 08 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Date A Live / 約會大作戰 DATE A LIVE V - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][07][1080p][MP4][简中]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][07][1080p][MP4][繁中]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Lilith-Raws] 約會大作戰 / Date A Live S05 - 07 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][06][1080p][MP4][简中]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][06][1080p][MP4][繁中]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Lilith-Raws] 約會大作戰 / Date A Live S05 - 06 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Lilith-Raws] 約會大作戰 / Date A Live S05 - 05 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669837-669660-669659-669658-669657-669656-669655-669654-669653-669652`() {
    kotlin.run {
    val r =
        parse("[ANi] Date a Live / 約會大作戰 DATE A LIVE V - 06 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][05][1080p][MP4][简中]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][05][1080p][MP4][繁中]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][05][720p][MP4][简中]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][05][720p][MP4][繁中]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][04v2][1080p][MP4][简中]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][04v2][1080p][MP4][繁中]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][04v2][720p][MP4][简中]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][04v2][720p][MP4][繁中]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][03][1080p][MP4][简中]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669651-669650-669649-669648-669647-669646-669645-669644-669643-669642`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][03][1080p][MP4][繁中]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][03][720p][MP4][简中]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][03][720p][MP4][繁中]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][02][1080p][MP4][简中]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][02][1080p][MP4][繁中]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][02][720p][MP4][简中]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][02][720p][MP4][繁中]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][01][1080p][MP4][简中]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][01][1080p][MP4][繁中]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live V][01][720p][MP4][简中]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669641-669517-669127-668975-668974-668968-668967-668708-668564-668145`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live V][01][720p][MP4][繁中]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第05话 MP4 720p")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Date a Live / 約會大作戰 DATE A LIVE V - 05 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第04话 MP4 720p")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第03话 MP4 720p")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战 V][Date A Live V][04][1080p][MP4][简中]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰 V][Date A Live V][04][1080p][MP4][繁中]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Lilith-Raws] 約會大作戰 / Date A Live S05 - 04 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Date a Live / 約會大作戰 DATE A LIVE V - 04 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Lilith-Raws] 約會大作戰 / Date A Live S05 - 03 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `668096-668057-667653-667530-667334-666861-666850-666848-617525-607120`() {
    kotlin.run {
    val r =
        parse("[ANi] Date a Live / 約會大作戰 DATE A LIVE V - 03 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第02话 MP4 720p")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Lilith-Raws] 約會大作戰 / Date A Live S05 - 02 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Date a Live / 約會大作戰 DATE A LIVE V - 02 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live V 约会大作战V 第01话 MP4 720p")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[BakeSubs] Date A Bullet [BD][1080p] 约会大作战约战狂三外传前篇虚或实篇后篇红或白篇")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Lilith-Raws] 約會大作戰 / Date A Live S05 - 01 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 約會大作戰 DATE A LIVE V - 01 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[7³ACG] 约会大作战IV/Date A Live IV/デート・ア・ライブ IV | 01-12[简繁英字幕] BDrip 1080p x265 FLAC")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT, ENG", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
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
  public fun `607119-607101-607100-607099-607098-606271-606270-606234-606233-606232`() {
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 11 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第12話][1080p_AVC][繁體][完]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第12话][1080p_AVC][简体][完]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第11話][1080p_AVC][繁體]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第11话][1080p_AVC][简体]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 10 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 09 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第10話][1080p_AVC][繁體]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第10话][1080p_AVC][简体]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第09話][1080p_AVC][繁體]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `606231-605058-604944-604936-604641-604638-604636-604635-604618-604617`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第09话][1080p_AVC][简体]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第12话 MP4 720p 完")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第11话 MP4 720p")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第10话 MP4 720p")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组] 约会大作战/Date A Live IV (01-12Fin WEBRIP 1080p AVC AAC 2022年4月 简中)")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組] 約會大作戰/Date A Live IV (01-12Fin WEBRIP 1080p AVC AAC 2022年4月 繁中)")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][12][1080p][MP4][简中]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][12][1080p][MP4][繁中]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][11][1080p][MP4][简中]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][11][1080p][MP4][繁中]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `604616-604615-604295-604264-604263-604133-604132-603875-603874-603873`() {
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][10][1080p][MP4][简中]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][10][1080p][MP4][繁中]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 08 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第08話][1080p_AVC][繁體]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第08话][1080p_AVC][简体]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][09][1080p][MP4][简中]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][09][1080p][MP4][繁中]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c動漫][4月新番][約會大作戰 第四季][01-12][合集][BIG5][1080P][MP4]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][08][1080p][MP4][简中]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][08][1080p][MP4][繁中]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `603818-603814-603813-603595-603594-603295-603225-603224-602841-602815`() {
    kotlin.run {
    val r = parse("[c.c動漫][4月新番][約會大作戰 第四季][12][BIG5][1080P][MP4][END]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳台地區） - 12 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 12 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][07][1080p][MP4][简中]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][07][1080p][MP4][繁中]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c動漫][4月新番][約會大作戰 第四季][11][BIG5][1080P][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳台地區） - 11 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 11 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c動漫][4月新番][約會大作戰 第四季][10][BIG5][1080P][MP4][内详]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][06][1080p][MP4][简中]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `602814-602813-602812-602603-602602-602574-601987-601977-601974-601952`() {
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][06][1080p][MP4][繁中]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][05][1080p][MP4][简中]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][05][1080p][MP4][繁中]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 10 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳台地區） - 10 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第09话 MP4 720p")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c動漫][4月新番][約會大作戰 第四季][09][BIG5][1080P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳台地區） - 09 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 09 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第08话 MP4 720p")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `601951-601950-601949-601812-601807-601806-601731-601729-601728-601630`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第07话 MP4 720p")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第06话 MP4 720p")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第05话 MP4 720p")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 07 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第07話][1080p_AVC][繁體]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第07话][1080p_AVC][简体]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 06 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第06話][1080p_AVC][繁體]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第06话][1080p_AVC][简体]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c動漫][4月新番][約會大作戰 第四季][03-08][BIG5][1080P][MP4]")
    assertEquals("03..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `601386-601384-601033-601027-601026-600754-600752-600492-600491-600185`() {
    kotlin.run {
    val r =
        parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳台地區） - 08 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 08 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 05 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第05話][1080p_AVC][繁體]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第05话][1080p_AVC][简体]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳台地區） - 07 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 07 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][04][1080p][MP4][简中]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][04][1080p][MP4][繁中]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
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
  public fun `600183-599803-599802-599801-599524-599522-599508-599507-599392-599391`() {
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 06 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 04 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第04話][1080p_AVC][繁體]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第04话][1080p_AVC][简体]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳台地區） - 05 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 05 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第04话 MP4 720p")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第03话 MP4 720p")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][03][1080p][MP4][简中]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][03][1080p][MP4][繁中]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `598923-598918-598909-598904-598902-598603-598602-598507-598414-598413`() {
    kotlin.run {
    val r =
        parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳台地區） - 04 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 04 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 03 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第03話][1080p_AVC][繁體]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第03话][1080p_AVC][简体]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][02][1080p][MP4][简中]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][02][1080p][MP4][繁中]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 02 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第02話][1080p_AVC][繁體]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第02话][1080p_AVC][简体]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `598280-598272-597949-597780-597726-597725-597691-597522-597519-597482`() {
    kotlin.run {
    val r =
        parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳台地區） - 03 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 03 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 02 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c動漫][4月新番][約會大作戰 第四季][02][BIG5][1080P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[爱恋&漫猫字幕组][4月新番][约会大作战][Date A Live IV][01][1080p][MP4][简中]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[愛戀&漫貓字幕組][4月新番][約會大作戰][Date A Live IV][01][1080p][MP4][繁中]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第02话 MP4 720p")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳台地區） - 02 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 02 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live IV 约会大作战IV 第01话 MP4 720p")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `597383-597382-597379-596933-596847-596842-569109-569108-569105-569104`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰IV_Date A Live IV】[第01話][1080p_AVC][繁體]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战IV_Date A Live IV】[第01话][1080p_AVC][简体]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 IV / Date A Live IV - 01 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c動漫][4月新番][約會大作戰 第四季][01][BIG5][1080P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[ANi] 約會大作戰 DATE A LIVE 第四季（僅限港澳臺地區）[01][1080P][Bilibili][WEB-DL][AAC AVC][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NC-Raws] 约会大作战 第四季 / Date A Live S4 - 01 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Lilith-Raws] 約會大作戰赤黑新章：虛或實 / Date a Live - Date a Bullet：Dead or Bullet [Baha][WEB-DL][1080p][AVC AAC][CHT][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Lilith-Raws] 約會大作戰赤黑新章：紅或白 / Date a Live - Date a Bullet：Nightmare or Queen [Baha][WEB-DL][1080p][AVC AAC][CHT][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[NC-Raws] 約會大作戰 DATE A BULLET 赤黑新章：虛或實 / DATE A BULLET - 電影 [Baha][WEB-DL][1080p][AVC AAC][CHT][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
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
  public fun `556066-556065-536000-535336-528456-528455-528454-522715-522714-521581`() {
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 約會大作戰 赤黑新章：紅或白 / Date A Bullet : Dead or Bullet Nightmare or Queen [BiliBili][WEB-DL][1080p][AVC AAC][CHS][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Skymoon-Raws] 約會大作戰 赤黑新章：虛或實 / Date A Bullet: zenpen Dead or Bullet [BiliBili][WEB-DL][1080p][AVC AAC][CHS][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[VCB-Studio] Date A Live / 约会大作战 / デート・ア・ライブ 10-bit 1080p HEVC/AVC BDRip [S1-S2 + MOVIE Reseed V3 Fin]")
    assertEquals("S1+S2+S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("约会大作战 全三季 Date A Live S1+S2+S3+Movie [BD 1920x1080 HEVC-10bit OPUS][简繁内封字幕]")
    assertEquals("S1+S2+S3+S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Snow-Raws] 约会大作战 第三季/Date a Live III/デート・ア・ライブ III(BD 1920x1080 HEVC-YUV420P10 FLAC)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Snow-Raws] 约会大作战 第二季/Date a Live II/デート・ア・ライブ II(BD 1920x1080 HEVC-YUV420P10 FLAC)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Snow-Raws] 约会大作战 第一季/Date a Live/デート・ア・ライブ(BD 1920x1080 HEVC-YUV420P10 FLAC)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&VCB-Studio] Date A Live Ⅲ / 约会大作战Ⅲ / デート・ア・ライブⅢ 10-bit 720p HEVC BDRip [MP4 Ver]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&VCB-Studio] Date A Live Ⅲ / 约会大作战Ⅲ / デート・ア・ライブⅢ 10-bit 1080p HEVC BDRip [Fin]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DHR動研字幕組】[約會大作戰 第三季_DATE A LIVE III][01-12全][BDRip][繁體][720P][MP4](合集版本)")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `520653-519355-517728-514522-514489-514156-514155-514001-513846-513772`() {
    kotlin.run {
    val r = parse("【DHR動研字幕組】[約會大作戰 第三季_DATE A LIVE III][09-12完][BDRip][繁體][720P][MP4]")
    assertEquals("09..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Moozzi2] 约会大作战第三季 Date A Live S3 BD-BOX 1-12 (BD 1920x1080 x.264 Flac)")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【ReinForce】【约会大作战 第三季】【DATE A LIVE S3 Vol 1】【01-06】【BDrip】【1920x1680】【mkv】")
    assertEquals("01..06", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live III 约会大作战III 第12话 MP4 720p 完")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 III / Date A Live III - 12 [WebRip 1080p HEVC-10bit AAC][繁体内封字幕]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第12話_完][1080p_AVC][繁體]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第12話_完][720p_AVC][繁體]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c動漫][1月新番][約會大作戰 第三季][DATE A LIVE S3][01-12][合集][BIG5][HEVC][1080P][網盤]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 12 [1080p][简体中文字幕]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
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
  public fun `513620-513619-513552-513404-513290-513178-513155-513100-513099-513027`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第11話][1080p_AVC][繁體]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第11話][720p_AVC][繁體]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live III 约会大作战III 第11话 MP4 720p")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 11 [1080p][简体中文字幕]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 III / Date A Live III - 10 [WebRip 1080p HEVC-10bit AAC][繁体内封字幕]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live III 约会大作战III 第10话 MP4 720p")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 III / Date A Live III - 09 [WebRip 1080p HEVC-10bit AAC][繁体内封字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第10話][1080p_AVC][繁體]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第10話][720p_AVC][繁體]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第09話][1080p_AVC][繁體]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `513026-512975-512933-512889-512452-512347-512305-512260-512259-512209`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第09話][720p_AVC][繁體]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DHR動研字幕組】[約會大作戰 第三季_DATE A LIVE III][08][繁體][720P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 09 [1080p][简体中文字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live III 约会大作战III 第09话 MP4 720p")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 09 [1080p][简体中文字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 III / Date A Live III - 08 [WebRip 1080p HEVC-10bit AAC][繁体内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live III 约会大作战III 第08话 MP4 720p")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第08話][1080p_AVC][繁體]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第08話][720p_AVC][繁體]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
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
  public fun `512207-512206-512010-511922-511908-511907-511906-511476-511462-511385`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第07話][1080p_AVC][繁體]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第07話][720p_AVC][繁體]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DHR動研字幕組】[約會大作戰 第三季_DATE A LIVE III][07][繁體][720P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 08 [1080p][简体中文字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Moozzi2] 约会大作战 剧场版 万由里审判 Date a Live Mayuri Judgement (BD 1920x1080 x.264 2Audio)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Moozzi2] 约会大作战第二季 Date A Live S2 1-10+OAD (BD 1920x1080 x.264 Flac)")
    assertEquals("01..10+OAD", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Moozzi2] 约会大作战第一季 Date A Live S1 Director's Cut 1-12+OAD (BD 1920x1080 x.264 Flac)")
    assertEquals("01..12+OAD", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DHR動研字幕組】[約會大作戰 第三季_DATE A LIVE III][06][繁體][720P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live III 约会大作战III 第07话 MP4 720p")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
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
  public fun `511369-511368-511343-511293-510952-510900-510619-510589-510588-510559`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第06話][1080p_AVC][繁體]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第06話][720p_AVC][繁體]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 07 [1080p][简体中文字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live III 约会大作战III 第06话 MP4 720p")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬运]约会大作战第三季第06集")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DHR動研字幕組】[約會大作戰 第三季_DATE A LIVE III][05][繁體][720P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 III / Date A Live III - 05 [WebRip 1080p HEVC-10bit AAC][繁体内封字幕]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第05話][1080p_AVC][繁體]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第05話][720p_AVC][繁體]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live III 约会大作战III 第05话 MP4 720p")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `510435-510382-510346-510238-510237-510217-510216-510129-510128-510100`() {
    kotlin.run {
    val r = parse("【DHR動研字幕組】[約會大作戰 第三季_DATE A LIVE III][04][繁體][720P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 约会大作战 III / Date A Live III - 01-04 [WebRip 1080p HEVC-10bit AAC][繁体内封字幕]")
    assertEquals("01..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 05 [1080p][简体中文字幕]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第04話][1080p_AVC][繁體]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第04話][720p_AVC][繁體]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第03話][1080p_AVC][繁體]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第03話][720p_AVC][繁體]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第02話][1080p_AVC][繁體]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第02話][720p_AVC][繁體]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第01話][1080p_AVC][繁體]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `510099-509923-509908-509850-509847-509575-509440-509373-509319-508970`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰III_Date A Live III】[第01話][720p_AVC][繁體]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DHR動研字幕組】[約會大作戰 第三季_DATE A LIVE III][03][繁體][720P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live III 约会大作战III 第04话 MP4 720p")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[Seed-Raws][2013-2015] 約會大作戰 Date a Live - S1+S2+MOVIE [BD][720p][AVC][MP4][補種]")
    assertEquals("2013..2015", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 04 [1080p][简体中文字幕]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DHR動研字幕組】[約會大作戰 第三季_DATE A LIVE III][02][繁體][720P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][1月新番] Date A Live III 约会大作战III 第03话 MP4 720p")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 约会大作战 III / Date A Live III - 03 [WebRip 1080p HEVC-10bit AAC][英文内封字幕]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("ENG", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 03 [1080p][简体中文字幕]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
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
  public fun `508910-508879-508766-508711-508324-508274-508127-508095-466903-466752`() {
    kotlin.run {
    val r = parse("【DHR動研字幕組】[約會大作戰 第三季_DATE A LIVE III][01][繁體][720P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][1月新番] Date A Live III 约会大作战III 第02话 MP4 720p")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[VCB-Studio] Date A Live / 约会大作战 / デート・ア・ライブ 10-bit 1080p AVC BDRip [S1 Reseed Fin]")
    assertEquals("S1", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 02 [1080p][简体中文字幕]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[LoliHouse] 约会大作战 III / Date A Live III - 01 [WebRip 1080p HEVC-10bit AAC][英文内封字幕]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("ENG", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][1月新番] Date A Live III 约会大作战III 第01话 MP4 720p")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[搬运] 约会大作战III 01 [1080p][简体中文字幕]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[VCB-Studio] Date A Live / 约会大作战 / デート・ア・ライブ 10-bit 1080p AVC/HEVC BDRip [S1(DC)+S2+M1 Reseed Fin]")
    assertEquals("S2", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANK-Raws] デート・ア・ライブⅡ DATE A LIVEⅡ 约会大作战Ⅱ TV 01-10Fin OVA (BDrip 1920x1080 HEVC-YUV420P10 FLAC)")
    assertEquals("01..10", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
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
  public fun `458348-457636-457635-457470-457469-427591-427251-427107-426437-426241`() {
    kotlin.run {
    val r = parse("(個人發布)【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][01-10完][繁體][720P][MP4]")
    assertEquals("01..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【F宅】约会大作战II 11话 OVA 简 720P MP4")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【F宅】约会大作战 13话 OVA 简 720P MP4")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【F宅】约会大作战II 11话 OVA 简 1080P MKV")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【F宅】约会大作战 13话 OVA 简 1080P MKV")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[繁星字幕組][Array Stars][劇場版 約會大作戰 - 萬由里審判][Gekijouban Date a Live - Mayuri Judgement ][BDrip][1080P][BIG5&GB&Jap][中日雙語簡繁內掛][mkv][字幕招新]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【F宅】约会大作战【剧场版】DATE A LIVE 万由里裁决 简 1080P MKV")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 导演剪辑版BDBOX BDRip 简繁外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[繁星字幕組][Array Stars][劇場版 約會大作戰 - 萬由里審判][Gekijouban Date a Live - Mayuri Judgement ][BDrip][720P][BIG5&Jap][中日雙語繁體][mp4][字幕招新]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
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
  public fun `425985-425643-425240-425043-425018-424999-424922-424896-424729-424579`() {
    kotlin.run {
    val r =
        parse("[繁星字幕组][Array Stars][剧场版 约会大作战 - 万由里审判][Gekijouban Date a Live - Mayuri Judgement ][BDrip][720P][GB&Jap][中日双语简体][mp4]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("约会大作战 万由里裁决[漏勺rip 720P]（外挂字幕）")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] 剧场版 Date A Live 约会大作战 万由里裁决 Mayuri Judgement BDRip 简繁外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【F宅】约会大作战【剧场版】DATE A LIVE 万由里裁决 简 720P MP4")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【莳乃汉化组】约会大作战 剧场版：万由里审判 [Special Video][GB+BIG5][BDRip_1080p_x264_8bit][AAC]（诱宵美九:Go☆Summer Girl MV 内嵌）【请注意这个不是SP，是SV】")
    assertEquals("Special..Special", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【莳乃汉化组】约会大作战 剧场版：万由里审判 [GBv2][BDRip_1080p_x264_8bit][AAC]【请下载本版本，上个版本压成繁体了】")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【莳乃汉化组】约会大作战 剧场版：万由里审判 [GB][BDRip_1080p_x264_8bit][AAC]【这个其实是压成了BIG5版的。。。。。下载转v2】")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【蒔乃漢化組】約會大作戰 劇場版：萬由里審判 [BIG5][BDRip_1080p_x264_8bit][AAC]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ReinForce]約會大作戰劇場版 萬由里審判-DATE A LIVE - Mayuri Judgement (BDRip 1920x1080 x264 FLAC) [字幕内附]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
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
  public fun `424572-424492-424436-424435-424395-424354-424256-424150-424129-417427`() {
    kotlin.run {
    val r =
        parse("【漫猫字幕组&爱恋字幕社】【约会大作战 万由里审判/DATE_A_LIVE Mayuri Judgement】[剧场版][1080P][MKV][简繁外挂][ x264_FLAC_Hi10P]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【千夏字幕組】【約會大作戰 萬由里審判_DATE_A_LIVE Mayuri Judgement】[劇場版][BDRIP_720P][x264_AAC][繁體內嵌]（正式版）")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【MGRT&幻之字幕组】约会大作战 万由里审判 [GB][1080P][PSV&PC][MP4][新人招募]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【MGRT&幻之字幕组】约会大作战 万由里审判 [GB][720P][PSV&PC][MP4][新人招募]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【漫猫字幕组&爱恋字幕社】【约会大作战 万由里审判/DATE_A_LIVE Mayuri Judgement】[剧场版]简体][720P][x264_AAC]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【漫貓字幕組&愛戀字幕社】【約會大作戰 萬由裡審判/DATE_A_LIVE Mayuri Judgement】[劇場版][繁體][720P][x264_AAC]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] 剧场版 Date A Live 约会大作战 万由里裁决 Mayuri Judgement MP4 720p")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【蒔乃漢化組】約會大作戰 劇場版：萬由里審判 [BIG5][DVDRip_720p_x264_8bit][AAC]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【莳乃汉化组】约会大作战 剧场版：万由里审判 [GB][DVDRip_720p_x264_8bit][AAC]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
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
  public fun `417426-406743-406031-406024-402130-397951-392118-385057-384760-383828`() {
    kotlin.run {
    val r = parse("[VCB-S&philosophy-raws][约会大作战][BDRIP][1080p 10bit][TV 01-13+SP][导演剪辑版]")
    assertEquals("01..13+SP", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【浩天个人发布】约会大作战第二季 BD720P MP4内嵌简体字幕")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【Xrip】[約會大作戰 導演剪接版][Date A Live][Director's_Cut][BDrip][BD-BOX][1080P][x264_10bit_flac]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【浩天个人发布】约会大作战第一季 BD720P MP4内嵌简体字幕")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("约会大作战2 Date a Live2[BDrip][1920x1080][TV11+CM5+OPED5+pv1+menu8][x264_m4a][10bit]加刘景长压制")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("约会大作战 Date a Live[BDrip][1920x1080][TV12+OAD+CM10+OPED11+info3+menu14][x264_m4a][10bit]加刘景长压制")
    assertEquals("OAD..OAD", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II BDRip 简繁外挂 合集")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II BDRip TV未放送第11话 简繁外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II BDRip Vol.3-5 简繁外挂")
    assertEquals("03..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[异域-11番小队][约会大作战合集Date A Live][BDRIP][720P][X264-10bit_AAC][春节贺礼第十八弹]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `381865-378454-378453-378121-377243-377211-377068-376296-375889-375756`() {
    kotlin.run {
    val r = parse("[FFF] DATE A LIVE [BD][1080p-FLAC] 约会大作战 第一季 含OVA [内封英文字幕]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("ENG", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[U2-Rip] Date a Live II/约会大作战2期 10bit 1080p BDRip [Fin]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[VCB-Studio & TUcaptions] Date a Live II/约会大作战2期 10bit 1080p BDRip [Fin] 繁简外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("《约会大作战II》（Date A Live II）[漏勺rip 720P]（外挂字幕）")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【F宅】【BD版】约会大作战 Date a Live II 01-11【合集】 简 720P MP4")
    assertEquals("01..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【莳乃字幕屋&AE漫画组&LE Project】Date A Live II 约会大作战 II - 第11话（TV未放送）[GB-BIG5-JPN][BDRip_1080p_x264_10bit][FLAC]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【蒔乃字幕屋&AE漫畫組&LE Project】Date A Live II 約會大作戰 II - 第11話（TV未放送）[BIG5-JPN][1080P][PSV&PC][招募內詳]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【莳乃字幕屋&AE漫画组&LE Project】Date A Live II 约会大作战 II - 第11话（TV未放送）[GB-JPN][1080P][PSV&PC][招募内详]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 11 // OVA // 720P-MP4-BIG5(繁)")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战 II TV未放送第11话 MP4 720p")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `375516-373910-373648-371722-369308-358687-358089-357273-356631-355164`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第11話_TV未放送][1280x720][MP4_PC&PSV兼容][繁體]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[VCB-Studio] Date a Live/约会大作战 S1 10bit 1080p BDRip [Fin] (附CD+扫图+特典)")
    assertEquals("S1", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【Xrip】[約會大作戰][Date A Live][BDrip][Vol.01_06+OVA][1080P][x264_10bit_flac]")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【Xrip】[約會大作戰 S2][Date A Live S2][Vol.01_05][BDrip][1080P][x264_10bit_flac] (內詳)")
    assertEquals("S2", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II BDRip Vol.1-2 简繁外挂")
    assertEquals("01..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 BDRip 简繁外挂 合集")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 BDRip TV未放送第13话 简繁外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 BDRip Vol.6 简繁外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][合集][BIG5][四月新番][720P][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
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
  public fun `354651-354536-354503-354255-354187-354087-354083-354077-354076-354058`() {
    kotlin.run {
    val r = parse("【附百度网盘】【修罗字幕组】【约会大作战　第二季】【10】【720P】【中日双语字幕】")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第01-10话 MKV 720p 简繁外挂 合集")
    assertEquals("01..10", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[4月新番][约会大作战II][Date A Live II][第01-10话][HEVC][MP4][无字幕]")
    assertEquals("01..10", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第01-10话 MP4 720p 合集")
    assertEquals("01..10", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【F宅】Date a Live II 约会大作战II【简】【合集】 720P MP4")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【萌物百科字幕组】[约会大作战 第二季_Date A Live II][10][END][GB][1280×720][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶俗×KNA】[约会大作战 2期][Date A Live ll][10][Fin][720P][简体][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第09话 MKV 720p 简繁外挂")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第10话 MP4 720p 完")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[01-10][GB_BIG5][720P][MKV][全]")
    assertEquals("01..10", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `354057-354053-354051-354004-354001-353997-353996-353995-353989-353988`() {
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[01-10][BIG5][720P][MP4][全]")
    assertEquals("01..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[01-10][GB][720P][MP4][全]")
    assertEquals("01..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 10 (End) // 720P-MP4-BIG5(繁)【內附特效招募漫畫】")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[10][BIG5][720P][MP4][完]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[10][GB][720P][MP4][完]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][10完][繁體][720P][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战 第二季_DATE A LIVE II][10完][简体][720P][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第10話_完][1280x720][MP4_PC&PSV兼容][繁體]（劇場版製作決定）")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][09][繁體][720P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战 第二季_DATE A LIVE II][09][简体][720P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `353777-353776-353773-353691-353470-353436-353435-353401-353332-353295`() {
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][09][BIG5][四月新番][720P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][09][GB][四月新番][720P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【附百度网盘】【修罗字幕组】【约会大作战　第二季】【09】【720P】【中日双语字幕】")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【附百度网盘】【修罗字幕组】【约会大作战　第二季】【08】【720P】【中日双语字幕】")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【萌物百科字幕组】[约会大作战 第二季_Date A Live II][09][GB][1280×720][MP4](内附网盘地址）")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][08][繁體][720P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战 第二季_DATE A LIVE II][08][简体][720P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第09话 MP4 720p")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶俗×KNA】[约会大作战 2期][Date A Live ll][09][720P][简体][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
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
  public fun `353262-353260-353251-353008-352620-352618-352615-352603-352602-352553`() {
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[09][BIG5][720P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[09][GB][720P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第09話][1280x720][MP4_PC&PSV兼容][繁體]（翻譯、校對招募中）")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第08话 MKV 720p 简繁外挂")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][08][BIG5][四月新番][720P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][08][GB][四月新番][720P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][07][BIG5][四月新番][720P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][07][繁體][720P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战 第二季_DATE A LIVE II][07][简体][720P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][07][GB][四月新番][720P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `352551-352499-352490-352480-352461-352432-352431-352430-352420-351938`() {
    kotlin.run {
    val r = parse("【萌物百科字幕组】[约会大作战 第二季_Date A Live II][08][GB][1280×720][MP4](内附网盘地址）")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶俗×KNA】[约会大作战 2期][Date A Live ll][08][720P][简体][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第08话 MP4 720p")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第07话 MKV 720p 简繁外挂")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 08 // 720P-MP4-BIG5(繁)【內附校正招募漫畫】")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[08][BIG5][720P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[08][GB][720P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第08話][1280x720][MP4_PC&PSV兼容][繁體]（翻譯、校對招募中）")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("約會大作戰 第二季 08 英文字幕")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("ENG", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第07話][1280x720][MP4_PC&PSV兼容][繁體]（翻譯、校對招募中）")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `351732-351626-351604-351559-351530-351528-351524-351399-351398-351393`() {
    kotlin.run {
    val r =
        parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 07 // 720P-MP4-BIG5(繁)【內附校正招募漫畫】")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第07话 MP4 720p")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【萌物百科字幕组】[约会大作战 第二季_Date A Live II][07][GB][1280×720][MP4](内附网盘地址）")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶俗×KNA】[约会大作战 2期][Date A Live ll][07][720P][简体][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【附百度网盘】【修罗字幕组】【约会大作战　第二季】【06】【720P】【中日双语字幕】")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[07][BIG5][720P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[07][GB][720P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][06][繁體][720P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战 第二季_DATE A LIVE II][06][简体][720P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][05][繁體][720P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `351392-351389-351138-351086-351085-350793-350735-350689-350676-350672`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战 第二季_DATE A LIVE II][05][简体][720P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第06话 MKV 720p 简繁外挂")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【萌物百科字幕组】[约会大作战 第二季_Date A Live II][06][GB][1280×720][MP4](内附网盘地址）")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][06][BIG5][四月新番][720P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][06][GB][四月新番][720P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第06话 MP4 720p")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶俗×KNA】[约会大作战 2期][Date A Live ll][06][720P][简体][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 06 // 720P-MP4-BIG5(繁)【內附翻譯招募漫畫】")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[06][BIG5][720P][MP4][內詳]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[06][GB][720P][MP4][内详]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `350645-350643-350458-350399-350135-350133-350022-350020-350019-350018`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第06話][1280x720][MP4_PC&PSV兼容][繁體]（翻譯、校對招募中）")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("約會大作戰 第一季 PV")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【附百度网盘】【修罗字幕组】【约会大作战　第二季】【05】【720P】【中日双语字幕】")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第05话 MKV 720p 简繁外挂")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][05][BIG5][四月新番][720P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][05][GB][四月新番][720P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕組】約會大作戰Ⅱ Date a Live Ⅱ - 05[BIG5][720P][PSV&PC][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组】约会大作战Ⅱ Date a Live Ⅱ - 05[GB][720P][PSV&PC][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕組】約會大作戰Ⅱ Date a Live Ⅱ - 04[BIG5][720P][PSV&PC][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组】约会大作战Ⅱ Date a Live Ⅱ - 04[GB][720P][PSV&PC][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `350016-350015-349958-349877-349865-349810-349783-349772-349763-349762`() {
    kotlin.run {
    val r = parse("【幻之字幕組】約會大作戰Ⅱ Date a Live Ⅱ - 03_v2[BIG5][720P][PSV&PC][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组】约会大作战Ⅱ Date a Live Ⅱ - 03_v2[GB][720P][PSV&PC][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【萌物百科字幕组】[约会大作战 第二季_Date A Live II][05][GB][1280×720][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶俗×KNA】[约会大作战 2期][Date A Live ll][05][720P][简体][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第05话 MP4 720p")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 05 // 720P-MP4-BIG5(繁)")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【附百度网盘】【修罗字幕组】【约会大作战　第二季】【04】【720P】【中日双语字幕】")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[05][BIG5][720P][MP4][內詳]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[05][GB][720P][MP4][内详]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第05話][1280x720][MP4_PC&PSV兼容][繁體]（翻譯、校對招募中）")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `349681-349679-349546-349522-349521-349470-349195-349114-348910-348854`() {
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][04][BIG5][四月新番][720P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][04][GB][四月新番][720P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第04话 MKV 720p 简繁外挂")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][04][繁體][720P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战 第二季_DATE A LIVE II][04][简体][720P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【萌物百科字幕组】[约会大作战 第二季_Date A Live II][04][GB][1280×720][MP4](内附网盘地址）")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【附百度网盘】【修罗字幕组】【约会大作战　第二季】【03】【720P】【中日双语字幕】")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live II 约会大作战II 第04话 MP4 720p")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶俗×KNA】[约会大作战 2期][Date A Live ll][04][720P][简体][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 04 // 720P-MP4-BIG5(繁)")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `348802-348797-348796-348793-348466-348465-348422-348420-348334-347912`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live II 约会大作战II 第03话 MKV 720p 简繁外挂")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[04][BIG5][720P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[04][GB][720P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第04話][1280x720][MP4_PC&PSV兼容][繁體]（翻譯、校對招募中）")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕組】約會大作戰Ⅱ Date a Live Ⅱ - 03[BIG5][720P][PSV&PC][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组】约会大作战Ⅱ Date a Live Ⅱ - 03[GB][720P][PSV&PC][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][03][BIG5][四月新番][720P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][03][GB][四月新番][720P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【萌物百科字幕组】[约会大作战 第二季_Date A Live II][03][GB][1280×720][MP4](内附网盘地址）")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶俗×KNA】[约会大作战 2期][Date A Live ll][03][720P][简体][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347897-347883-347882-347853-347844-347823-347820-347819-347708-347628`() {
    kotlin.run {
    val r = parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 03 // 720P-MP4-BIG5(繁)【問卷內詳】")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][03][繁體][720P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战 第二季_DATE A LIVE II][03][简体][720P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[03][BIG5][720P][MP4][V2]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[03][GB][720P][MP4][V2]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[03][BIG5][720P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第03話][1280x720][MP4_PC&PSV兼容][繁體]（翻譯、校對招募中）")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[03][GB][720P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live II 约会大作战II 第02话 MKV 720p 简繁外挂")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE II 約會大作戰II][02][BIG5][PSP-480P]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347627-347562-347561-347284-347151-347113-347087-347086-347079-347072`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE II 約會大作戰II][02][BIG5][PSV-720P]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【附百度网盘】【修罗字幕组】【约会大作战　第二季】【01】【720P】【中日双语字幕】")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【附百度网盘】【修罗字幕组】【约会大作战　第二季】【02】【720P】【中日双语字幕】")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第02話][1280x720][MP4_PC&PSV兼容][繁體]（內詳）")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][02][BIG5][四月新番][720P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][02][GB][四月新番][720P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][02][繁體][720P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战 第二季_DATE A LIVE II][02][简体][720P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕組】約會大作戰Ⅱ Date a Live Ⅱ - 02[BIG5][720P][PSV&PC][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组】约会大作战Ⅱ Date a Live Ⅱ - 02[GB][720P][PSV&PC][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `347018-346887-346873-346852-346798-346774-346657-346295-346176-346128`() {
    kotlin.run {
    val r = parse("【萌物百科字幕组】[约会大作战 第二季_Date A Live II][02][GB][1280×720][MP4](内附网盘地址）")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶俗×KNA】[约会大作战 2期][Date A Live ll][02][720P][简体][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live II 约会大作战II 第02话 MP4 720p")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 02 // 720P-MP4-BIG5(繁)")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[02][BIG5][720P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[02][GB][720P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live II 约会大作战II 第01话 MKV 720p 简繁外挂")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][01][BIG5][四月新番][720P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[银光字幕组][约会大作战第二季DATE·A·LIVE][01][GB][四月新番][720P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE II 約會大作戰II][01][BIG5][PSP-480P]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `346127-345955-345953-345819-345812-345796-345773-345772-345766-345727`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE II 約會大作戰II][01][BIG5][PSV-720P]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕組】約會大作戰Ⅱ Date a Live Ⅱ - 01[BIG5][720P][PSV&PC][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组】约会大作战Ⅱ Date a Live Ⅱ - 01[GB][720P][PSV&PC][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶俗×KNA】[约会大作战 2期][Date A Live ll][01][720P][简体][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[TUcaptions] 2014春 // デート・ア・ライブII / 約會大作戰 第2季 // 01 // 720P-MP4-BIG5(繁)")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live II 约会大作战II 第01话 MP4 720p")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[萌物百科字幕组][约会大作战 第二季_Date A Live II][01][GB][1280×720][MP4](内附网盘地址）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战 第二季_DATE A LIVE II][01][简体][720P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰 第二季_DATE A LIVE II][01][繁體][720P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【惡魔島字幕組】★4月新番【約會大作戰 第二季_Date A Live II】[01][BIG5][720P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `345722-345715-343090-342780-342779-342778-342777-337409-336790-335879`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰 第二期_DATE_A_LIVE II】[第01話][1280x720][MP4_PC&PSV兼容][繁體]（翻譯、校對招募中）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【恶魔岛字幕组】★4月新番【约会大作战 第二季_Date A Live II】[01][GB][720P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第01～12+OAD話][BDRIP_720P][x264_AAC][繁體內嵌]")
    assertEquals("01..12+OAD話", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【千夏字幕＆ANK-Raws】【約會大作戰_DATE_A_LIVE】[VOL.06][BDRIP_1080P][x264-Hi10P_FLAC][繁體外掛]（內詳")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【千夏字幕＆ANK-Raws】【約會大作戰_DATE_A_LIVE】[VOL.05][BDRIP_1080P][x264-Hi10P_FLAC][繁體外掛]（內詳")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【千夏字幕＆ANK-Raws】【約會大作戰_DATE_A_LIVE】[VOL.04][BDRIP_1080P][x264-Hi10P_FLAC][繁體外掛]（內詳")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【千夏字幕＆ANK-Raws】【約會大作戰_DATE_A_LIVE】[VOL.01～06+OAD][BDRIP_1080P][x264-Hi10P_FLAC][繁體外掛]（內詳")
    assertEquals("01..06+OAD", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("約會大作戰 時崎狂三野艷舞蹈狂露底褲")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[TUcaptions] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 01~13End // BD1080P 10bit-MKV-BIG5(繁)【附特典】")
    assertEquals("01..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][13話_OVA][繁體][480P][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `335868-335867-335447-332129-332078-331843-330563-329968-329719-329654`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][13话_OVA][简体][720P][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][13話_OVA][繁體][720P][MP4]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("約會大作戰Ⅱ 先行ロングPV(2分鐘版)")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][13][BIG5][PSV&PSP][未放送話]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("約會大作戰 第二季 PV")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[TUcaptions] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 13(TV未放送) // 720P-MP4-BIG5(繁)")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 TV未放送第13话 MP4 720p")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕＆ANK-Raws】【約會大作戰_DATE_A_LIVE】[VOL.OVA][BDRIP_1080P][x264-Hi10P_FLAC][繁體外掛]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[异域-11番小队][约会大作战Date a Live][BDRIP][720P][X264_AAC]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★約會大作戰_DATE A LIVE 第13話(TV未放送) 720p BIG5 MP4")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `329630-329601-329504-329320-329308-329170-325935-325791-323712-323325`() {
    kotlin.run {
    val r = parse("【极影字幕社】★约会大作战_DATE A LIVE 第13话(TV未放送) 720p GB MP4")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【千夏字幕＆ANK-Raws】【約會大作戰_DATE_A_LIVE】[VOL.02～03][BDRIP_1080P][x264-Hi10P_FLAC][繁體外掛]（內詳")
    assertEquals("02..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第13话_TV未放送][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组】约会大作战 Date a Live - 13[GB][720P][PSV&PC][MP4](成員招募)")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕組】約會大作戰 Date a Live - 13[BIG5][720P][PSV&PC][MP4](成員招募)")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第13話_TV未放送][1280x720][MP4_PC&PSV兼容][繁體]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕＆ANK-Raws】【約會大作戰_DATE_A_LIVE】[VOL.02][BDRIP_1080P][x264-Hi10P_FLAC][繁體外掛]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 BDRip Vol.5 简繁外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 BDRip Vol.4 简繁外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 BDRip Vol.3 简繁外挂")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `323220-316264-315091-315092-315090-313005-312310-309228-309227-309215`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 BDRip Vol.1-2 简繁外挂")
    assertEquals("01..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【华盟字幕社&澄空学园】[约会大作战][1-12END] [BIG5][RMVB][848X480]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][全12話][繁體][480P][MP4](合集版本)")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][全12话][简体][720P][MP4](合集版本)")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][全12話][繁體][720P][MP4](合集版本)")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][01-12全][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("◆[漫游FREEWIND工作室] DATE A LIVE 約會大作戰 1080P BDRIP BD2 第3~4話+SP")
    assertEquals("03..04+SP", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][1-12][BIG5][PSP-480P][合集版本]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][1-12][BIG5][PSV-720P][合集版本]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第01～02話][BDRIP_720P][x264_AAC][繁體內嵌](戰勇內詳)")
    assertEquals("01..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `308635-308521-308395-308281-307934-307844-307689-307655-307647-307620`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][011v2-12v2][BIG5][1280x720][MP4]")
    assertEquals("11..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[TUcaptions] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 01v2~12v2 // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][12完][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("◆[漫游FREEWIND工作室] DATE A LIVE 約會大作戰 1080P BDRIP 第1~2話+SP")
    assertEquals("01..02+SP", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕＆ANK-Raws】【約會大作戰_DATE_A_LIVE】[VOL.01][BDRIP_1080P][x264-Hi10P_FLAC][繁體外掛]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][01-12][BIG5][1280x720][MP4]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第01-12话 MKV 720p 简繁外挂 合集")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][12][FIN][BIG5][PSV&PSP]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][11-12][BIG5][1024x576][MP4][招組員]")
    assertEquals("11..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第01-12话 MP4 720p 合集")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `307544-307439-307401-307316-307314-307141-307122-307027-307011-306992`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第01～12話_完][1440x810][x264-Hi10P_MP4][簡繁外掛]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第01-12話 完結合集 简繁外挂字幕 X264_AAC 720P MKV")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][09-10][BIG5][1280x720][MP4]")
    assertEquals("09..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live 01-12[GB][720P][PSV&PC][MP4]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live 01-12[BIG5][480P][PSP&PC][MP4]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 12[GB][720P][PSV&PC][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 12[BIG5][480P][PSP&PC][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][11][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第12v2話_完][1280x720][MP4_PC&PSV兼容][繁體]（換源補上二期預告）")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第12話 完結 BIG5繁體 720P MP4")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306991-306969-306965-306958-306957-306951-306928-306916-306911-306910`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第12话 完结 GB简体 720P MP4")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第11话 MKV 720p 简繁外挂")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第12话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][12完][繁體][480P][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][12完][简体][720P][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][12完][繁體][720P][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 12End // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE第12話 720p BIG5 MP4[第一季完]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第12话 MP4 720p 完")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第12话 720p GB MP4[第一季完]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306600-306599-306322-306300-306282-306255-306198-306170-306157-306156`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][11][BIG5][PSP-480P]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][11][BIG5][PSV-720P]<招募7月新番挖坑員>")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 11[GB][720P][PSV&PC][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 11[BIG5][480P][PSP&PC][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第11话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第12話_完][848x480][MP4_NICO先行][繁體]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][10][BIG5][1024x576][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][10][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第11話 BIG5繁體 720P MP4")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第11话 GB简体 720P MP4")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `306150-306138-306128-306115-306112-306111-306109-306108-306027-306019`() {
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][11][简体][720P][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][11][繁體][480P][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][11][繁體][720P][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE 第11話 720p BIG5 MP4")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第11话 720p GB MP4")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第11話][1280x720][MP4_PC&PSV兼容][繁體]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第11话 MP4 720p")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 11 // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第10话 MKV 720p 简繁外挂")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第10話][1440x810][x264-Hi10P_MP4][簡繁外掛]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `305867-305814-305813-305510-305478-305477-305443-305415-305410-305409`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][09][BIG5][1024x576][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][10][BIG5][PSP-480P]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][10][BIG5][PSV-720P] ( 7月新番招募組員")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第11話][848x480][MP4_NICO先行][繁體]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 10[BIG5][480P][PSP&PC][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 10[GB][720P][PSV&PC][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][09][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第09话 MKV 720p 简繁外挂 附EDPV")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第10話 BIG5繁體 720P MP4")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第10话 GB简体 720P MP4")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `305390-305372-305371-305366-305347-305346-305342-305340-305333-305332`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第10话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][10][简体][720P][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][10][繁體][480P][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][10][繁體][720P][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE 第10話 720p BIG5 MP4")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第10话 720p GB MP4")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第09話][1440x810][x264-Hi10P_MP4][簡繁外掛]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第10話][1280x720][MP4_PC&PSV兼容][繁體]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第10话 MP4 720p")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
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
  public fun `304836-304835-304790-304614-304612-304565-304540-304539-304532-304509`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][09][BIG5][PSP-480P]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][09][BIG5][PSV-720P] ( 招募時軸")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第10話][848x480][MP4_NICO先行][繁體]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 09[BIG5][480P][PSP&PC][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 09[GB][720P][PSV&PC][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][08][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第09話 BIG5繁體 720P MP4")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第09话 GB简体 720P MP4")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第09话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE 第09話 720p BIG5 MP4")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `304508-304482-304481-304473-304472-304467-304460-304309-304284-304171`() {
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第09话 720p GB MP4")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][09][简体][720P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][09][繁體][480P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][09][繁體][720P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第09話][1280x720][MP4_PC&PSV兼容][繁體]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第09话 MP4 720p")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 09 // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第08话 MKV 720p 简繁外挂")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][08][BIG5][1280x720][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第01～08話][1440x810][x264-Hi10P_MP4][簡繁外掛](內詳)")
    assertEquals("01..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `304139-304105-304072-304071-303711-303653-303649-303644-303621-303617`() {
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][08][BIG5][1024x576][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第08話 简繁外挂字幕 X264_AAC 720P MKV")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][08][BIG5][PSP-480P]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][08][BIG5][PSV-720P] ( 招募時軸")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第09話][848x480][MP4_NICO先行][繁體]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 08[BIG5][480P][PSP&PC][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 08[GB][720P][PSV&PC][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][07][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第07话 MKV 720p 简繁外挂")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第08話 BIG5繁體 720P MP4")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `303616-303612-303593-303568-303567-303555-303553-303546-303544-303543`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第08话 GB简体 720P MP4")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][06-07][BIG5][1280x720][MP4]")
    assertEquals("06..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第08话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][08][简体][720P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][08][繁體][480P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第08話][1280x720][MP4_PC&PSV兼容][繁體]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][08][繁體][720P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE 第08話 720p BIG5 MP4")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第08话 720p GB MP4")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第08话 MP4 720p")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `303541-303336-303273-303208-303185-303045-303044-302911-302803-302791`() {
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 08 // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][07][BIG5][1024x576][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][06][BIG5][1024x576][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第06话 MKV 720p 简繁外挂")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第07話 简繁外挂字幕 X264_AAC 720P MKV")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][07][BIG5][PSP-480P]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][07][BIG5][PSV-720P] ( 招募時軸")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第08話][848x480][MP4_NICO先行][繁體]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][06][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE 第07話 720p BIG5 MP4")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `302790-302783-302775-302758-302757-302753-302746-302737-302736-302730`() {
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第07话 720p GB MP4")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 07[BIG5][480P][PSP&PC][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 07[GB][720P][PSV&PC][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第07話 BIG5繁體 720P MP4")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第07话 GB简体 720P MP4")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第07话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第07話][1280x720][MP4_PC&PSV兼容][繁體]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][07][简体][720P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][07][繁體][480P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][07][繁體][720P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `302718-302717-302555-302386-302385-302347-302013-302012-301971-301969`() {
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第07话 MP4 720p")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 07 // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第06話 简繁外挂字幕 X264_AAC 720P MKV")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][06][BIG5][PSP-480P]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][06][BIG5][PSV-720P] ( 招募校對")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第07話][848x480][MP4_NICO先行][繁體](重要內詳)")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第06話 BIG5繁體 720P MP4")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第06话 GB简体 720P MP4")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 06[BIG5][480P][PSP&PC][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 06[GB][720P][PSV&PC][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301965-301964-301952-301938-301937-301917-301914-301904-301903-301899`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第06话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第05话 MKV 720p 简繁外挂")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][05][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][06][简体][720P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][06][繁體][480P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][06][繁體][720P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第06話][1280x720][MP4_PC&PSV兼容][繁體](招募片源錄製)")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 06 // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE 第06話 720p BIG5 MP4")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第06话 MP4 720p")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `301895-301629-301581-301501-301500-301395-301351-301350-301349-301200`() {
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第06话 720p GB MP4")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][05][BIG5][1280x720][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第05話 简繁外挂字幕 X264_AAC 720P MKV")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][05][BIG5][PSP-480P]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][05][BIG5][PSV-720P] ( 招募校對")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][04][BIG5][1280x720][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][05][BIG5][1024x576][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 05[BIG5][480P][PSP&PC][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 05[GB][720P][PSV&PC][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第04话 MKV 720p 简繁外挂")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `301173-301028-301027-301015-301002-301001-300997-300996-300995-300987`() {
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第06話][848x480][MP4_NICO先行][繁體](重要內詳)")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第05話 BIG5繁體 720P MP4")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第05话 GB简体 720P MP4")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第05话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][05][简体][720P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][05][繁體][480P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第05話][1280x720][MP4_PC&PSV兼容][繁體](招募片源錄製)")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][05][繁體][720P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][04][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][04][BIG5][1024x576][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300986-300976-300970-300969-300724-300651-300606-300389-300387-300320`() {
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE 第05話 720p BIG5 MP4")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社] Date A Live 约会大作战 第05话 MP4 720p")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第05话 720p GB MP4")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 05 // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第04話 简繁外挂字幕 X264_AAC 720P MKV")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第03話][1440x810][x264-Hi10P_MP4][簡繁外掛]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][03][BIG5][1280x720][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][04][BIG5][PSP-480P]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][04][BIG5][PSV-720P]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第05話][848x480][MP4_NICO先行][繁體](重要內詳)")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300251-300187-300161-300160-300129-300127-300102-300085-300084-300072`() {
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第04话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][03][BIG5][1024x576][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 04[GB][720P][PSV&PC][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 04[BIG5][480P][PSP&PC][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第04話 BIG5繁體 720P MP4")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第04话 GB简体 720P MP4")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live 约会大作战 第03话 MKV 720p 简繁外挂")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][04][简体][720P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][04][繁體][480P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][04][繁體][720P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `300071-300068-300062-300061-300060-300058-299846-299728-299727-299643`() {
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][03][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第04話][1280x720][MP4_PC&PSV兼容][繁體](招募片源錄製)")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE 第04話 720p BIG5 MP4")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 04 // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live 约会大作战 第04话 MP4 720p")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第04话 720p GB MP4")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第03話 简繁外挂字幕 X264_AAC 720P MKV")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 03[BIG5][480P][PSP&PC][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 03[GB][720P][PSV&PC][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 02[BIG5][480P][PSP&PC][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `299642-299354-299333-299301-299295-299275-299265-299264-299249-299194`() {
    kotlin.run {
    val r = parse("【幻之字幕组&猫蒲团汉化组】约会大作战_Date a Live - 02[GB][720P][PSV&PC][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][02][BIG5][1280x720][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第04話][848x480][MP4_NICO先行][繁體]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][03][BIG5][PSP-480P]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][03][BIG5][PSV-720P](雅安加油！")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第03话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][03][简体][720P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][03][繁體][480P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][03][繁體][720P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live 约会大作战 第02话 MKV 720p 简繁外挂")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `299189-299188-299176-299166-299154-299150-299148-299138-299055-298929`() {
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第03話 BIG5繁體 720P MP4")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第03话 GB简体 720P MP4")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第03話][1280x720][MP4_PC&PSV兼容][繁體](招募片源錄製)")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 03 // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE 第03話 720p BIG5 MP4")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第03话 720p GB MP4")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live 约会大作战 第03话 MP4 720p")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][01-02][繁簡外掛字幕][720P][AVC_Hi10P_AAC][MKV]")
    assertEquals("01..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][02][BIG5][1024x576][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第02話 简繁外挂字幕 X264_AAC 720P MKV")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `298722-298514-298512-298433-298432-298428-298328-298327-298326-298313`() {
    kotlin.run {
    val r = parse("【风之圣殿字幕组】【★四月新番】[约会大作战 DATE A LIVE][02][BIG5][1280X720][Mp4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][02][BIG5][PSP-480P]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][02][BIG5][PSV-720P](招募校對、後期工")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第02話 BIG5繁體 720P MP4")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第02话 GB简体 720P MP4")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第03話][848x480][MP4_NICO先行][繁體]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第02话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][02][简体][720P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][02][繁體][480P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][02][繁體][720P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `298309-298307-298304-298301-298293-298265-298121-298078-297985-297960`() {
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 01v2-02 // 720P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("01..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第02話][1280x720][MP4_PC&PSV兼容][繁體](招募片源錄製)")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰_DATE A LIVE 第02話 720p BIG5 MP4【字幕社招募片源录制】")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战_DATE A LIVE第02话 720p GB【字幕社招募片源录制】")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live 约会大作战 第02话 MP4 720p")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[澄空学园&华盟字幕社][4月新番] Date A Live 约会大作战 第01话 MKV 720p 简繁外挂")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][01][BIG5][1280x720][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第01話][1440x810][x264-Hi10P_MP4][簡繁外掛]（山口昇老師R.I.P）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[天の字幕組][4月新番][DATE A LIVE 約會大作戰][01][BIG5][1024x576][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第01話 简繁外挂字幕 X264_AAC 720P MKV")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `297773-297767-297699-297631-297554-297524-297509-297508-297503-297502`() {
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][01][BIG5][PSP-480P]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[NTR字幕組][4月新番][DATE A LIVE 約會大作戰][01][BIG5][PSV-720P](招募後期工")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕组】【约会大作战_DATE_A_LIVE】[第01话][1280x720][MP4_PC&PSV兼容][简体]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第02話][848x480][MP4_NICO先行][繁體]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第01話][1280x720][MP4_PC&PSV兼容][繁體]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[TUcaptions(THK)] 2013春 // デート・ア・ライブ / 約會大作戰 / DATE A LIVE // 01 // 720P-MP4-BIG5(繁)【新論壇正式開站!!】")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 約會大作戰】第01話 BIG5繁體 720P MP4")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【雪酷字幕組】★四月新番【DATE A LIVE 约会大作战】第01话 GB简体 720P")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][01][繁體][480P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【动漫国字幕组&DHR动研字幕组】[约会大作战_DATE A LIVE][01][簡体][720P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `297489-297484-297482-296759-296754-296737-296718-296665-167544`() {
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰 第01話[720p] BIG5 MP4")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战 第01话[720p] GB MP4")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【動漫國字幕組&DHR動研字幕組】[約會大作戰_DATE A LIVE][01][繁體][720P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第01話][848x480][MP4_NICO先行][繁體]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 約會大作戰 第01話[先行版][nico 854x480] BIG5 MP4")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 约会大作战 第01话[先行版][nico 854x480] GB MP4")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【千夏字幕組】【約會大作戰_DATE_A_LIVE】[第01話][先行_360P][MKV][繁簡內掛]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("360P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("デート·ア·ライブ_date a live_約會大作戰[01][nico 854x480 x264]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
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
