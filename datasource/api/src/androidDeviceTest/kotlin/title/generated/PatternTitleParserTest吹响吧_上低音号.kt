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
 * 原名: `吹响吧_上低音号`
 * 数据源: `dmhy`
 *
 * 由 `test-codegen` 的 `GenerateTests.kt` 生成, 不要手动修改!
 * 如果你优化了解析器, 这些 test 可能会失败, 请检查是否它是因为以前解析错误而现在解析正确了. 
 * 如果是, 请更新测试数据: 执行 `GenerateTests.kt`.
 */
public class PatternTitleParserTest吹响吧_上低音号 : PatternBasedTitleParserTestSuite() {
  @Test
  public fun `670314-670313-670276-670275-670274-670253-670252-670215-670099-669963`() {
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第06話 BIG5 1080P MP4（字幕社招人內詳）")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第06話 BIG5 720P MP4（字幕社招人內詳）")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [07][WebRip][HEVC_AAC][简繁日内封][招募时轴]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [07][WebRip][HEVC_AAC][简日内嵌][招募时轴]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 吹響吧！上低音號 第三季 / Hibike! Euphonium 3 [07][WebRip][HEVC_AAC][繁日內嵌][招募時軸]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第06话 GB 1080P MP4（字幕社招人内详）")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第06话 GB 720P MP4（字幕社招人内详）")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[云光字幕组]吹响吧！上低音号 3 Hibike! Euphonium S3 [06][简体双语][1080p]招募翻译")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] 吹響吧！上低音號 第三季（僅限港澳台地區） - 07 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹響吧！上低音號 第三季 / Hibike! Euphonium 3][06][1080P][AVC AAC][繁日双语][WebRip]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669962-669928-669927-669910-669909-669902-669901-669843-669842-669841`() {
    kotlin.run {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹响吧！上低音号 第三季 / Hibike! Euphonium 3][06][1080P][AVC AAC][简日双语][WebRip]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第05話 BIG5 1080P MP4（字幕社招人內詳）")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第05話 BIG5 720P MP4（字幕社招人內詳）")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫猫字幕社][4月新番][吹响吧！上低音号 第三季][Hibike! Euphonium S3][06][1080P][MP4][简日双语]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫貓字幕社][4月新番][吹響吧！上低音號 第三季][Hibike! Euphonium S3][06][1080P][MP4][繁日雙語]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫貓字幕社][4月新番][吹響吧！上低音號 第三季][Hibike! Euphonium S3][05][1080P][MP4][繁日雙語]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫猫字幕社][4月新番][吹响吧！上低音号 第三季][Hibike! Euphonium S3][05][1080P][MP4][简日双语]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕組] 吹響吧！上低音號 3 / Hibike! Euphonium 3 [06][1080p][繁體內嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [06][1080p][简体内嵌]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [06][1080p][简繁内封]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `669835-669834-669692-669691-669690-669685-669684-669433-669432-669431`() {
    kotlin.run {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 - 06 [WebRip 2160P HEVC AAC][简繁日内封 ASSx3]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹响吧！上低音号 第三季 / Hibike! Euphonium 3 - 06 [WebRip 1080P HEVC AAC][简繁日内封 ASSx3]")
    assertEquals("03+06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 吹響吧！上低音號 第三季 / Hibike! Euphonium 3 [06][WebRip][HEVC_AAC][繁日內嵌][招募時軸]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [06][WebRip][HEVC_AAC][简日内嵌][招募时轴]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [06][WebRip][HEVC_AAC][简繁日内封][招募时轴]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹響吧！上低音號 第三季 / Hibike! Euphonium 3][05][1080P][AVC AAC][繁日双语][WebRip]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹响吧！上低音号 第三季 / Hibike! Euphonium 3][05][1080P][AVC AAC][简日双语][WebRip]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 吹响吧！上低音号 3 / Hibike! Euphonium 3 - 05 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[千夏字幕組][吹響吧！上低音號 3_Hibike! Euphonium 3][第05話][1080p_AVC][繁體]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[千夏字幕组][吹响吧！上低音号 3_Hibike! Euphonium 3][第05话][1080p_AVC][简体]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669404-669258-669239-669238-669188-669186-669165-669137-669135-668979`() {
    kotlin.run {
    val r =
        parse("[ANi] 吹響吧！上低音號 第三季（僅限港澳台地區） - 06 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[云光字幕组]吹响吧！上低音号 3 Hibike! Euphonium S3 [05][简体双语][1080p]招募翻译")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第05话 GB 1080P MP4（字幕社招人内详）")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第05话 GB 720P MP4（字幕社招人内详）")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫貓字幕社][4月新番][吹響吧！上低音號 第三季][Hibike! Euphonium S3][05][1080P][MP4][繁日雙語]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫猫字幕社][4月新番][吹响吧！上低音号 第三季][Hibike! Euphonium S3][05][1080P][MP4][简日双语]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕組] 吹響吧！上低音號 3 / Hibike! Euphonium 3 [05][1080p][繁體內嵌]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [05][1080p][简体内嵌]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [05][1080p][简繁内封]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [05][WebRip][HEVC_AAC][简繁日内封][招募时轴]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `668978-668971-668898-668897-668896-668891-668890-668882-668878-668743`() {
    kotlin.run {
    val r = parse("[北宇治字幕组] 吹響吧！上低音號 第三季 / Hibike! Euphonium 3 [05][WebRip][HEVC_AAC][繁日內嵌][招募時軸]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [05][WebRip][HEVC_AAC][简日内嵌][招募时轴]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[千夏字幕组][吹响吧！上低音号 3_Hibike! Euphonium 3][第04话][1080p_AVC][简体]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[千夏字幕組][吹響吧！上低音號 3_Hibike! Euphonium 3][第04話][1080p_AVC][繁體]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 吹响吧！上低音号 3 / Hibike! Euphonium 3 - 04 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第04話 BIG5 1080P MP4（字幕社招人內詳）")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第04話 BIG5 720P MP4（字幕社招人內詳）")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[云光字幕组]吹响吧！上低音号 3 Hibike! Euphonium S3 [04][简体双语][1080p]招募翻译")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] 吹響吧！上低音號 第三季（僅限港澳台地區） - 05 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 - 04 [WebRip 2160P HEVC AAC][简繁日内封 ASSx3]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `668742-668694-668693-668552-668550-668549-668548-668547-668465-668464`() {
    kotlin.run {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹响吧！上低音号 第三季 / Hibike! Euphonium 3 - 04 [WebRip 1080P HEVC AAC][简繁日内封 ASSx3]")
    assertEquals("03+04", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第04话 GB 1080P MP4（字幕社招人内详）")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第04话 GB 720P MP4（字幕社招人内详）")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕組] 吹響吧！上低音號 3 / Hibike! Euphonium 3 [04][1080p][繁體內嵌]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [04][1080p][简体内嵌]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫貓字幕社][4月新番][吹響吧！上低音號 第三季][Hibike! Euphonium S3][04][1080P][MP4][繁日雙語]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫猫字幕社][4月新番][吹响吧！上低音号 第三季][Hibike! Euphonium S3][04][1080P][MP4][简日双语]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [04][1080p][简繁内封]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [04][WebRip][HEVC_AAC][简繁日内封][招募时轴]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [04][WebRip][HEVC_AAC][简日内嵌][招募时轴]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `668463-668410-668409-668361-668337-668336-668334-668333-668332-668316`() {
    kotlin.run {
    val r = parse("[北宇治字幕组] 吹響吧！上低音號 第三季 / Hibike! Euphonium 3 [04][WebRip][HEVC_AAC][繁日內嵌][招募時軸]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第03話 BIG5 1080P MP4（字幕社招人內詳）")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第03話 BIG5 720P MP4（字幕社招人內詳）")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] 吹響吧！上低音號 第三季（僅限港澳台地區） - 04 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第03话 GB 1080P MP4（字幕社招人内详）")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第03话 GB 720P MP4（字幕社招人内详）")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[千夏字幕组][吹响吧！上低音号 3_Hibike! Euphonium 3][第03话][1080p_AVC][简体]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[千夏字幕組][吹響吧！上低音號 3_Hibike! Euphonium 3][第03話][1080p_AVC][繁體]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 吹响吧！上低音号 3 / Hibike! Euphonium 3 - 03 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 - 03 [WebRip 2160P HEVC AAC][简繁日内封 ASSx3]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `668315-668309-668308-668262-668203-668202-668161-668159-668158-668151`() {
    kotlin.run {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹响吧！上低音号 第三季 / Hibike! Euphonium 3 - 03 [WebRip 1080P HEVC AAC][简繁日内封 ASSx3]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹響吧！上低音號 第三季 / Hibike! Euphonium 3][03][1080P][AVC AAC][繁日双语][WebRip]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹响吧！上低音号 第三季 / Hibike! Euphonium 3][03][1080P][AVC AAC][简日双语][WebRip]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[云光字幕组]吹响吧！上低音号 3 Hibike! Euphonium S3 [03][简体双语][1080p]招募翻译")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第02話 BIG5 1080P MP4（字幕社招人內詳）")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第02話 BIG5 720P MP4（字幕社招人內詳）")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕組] 吹響吧！上低音號 3 / Hibike! Euphonium 3 [03][1080p][繁體內嵌]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [03][1080p][简体内嵌]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [03][1080p][简繁内封]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫貓字幕社][4月新番][吹響吧！上低音號 第三季][Hibike! Euphonium S3][03][1080P][MP4][繁日雙語]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `668150-668149-668148-668111-668110-667913-667912-667911-667884-667849`() {
    kotlin.run {
    val r = parse("[漫猫字幕社][4月新番][吹响吧！上低音号 第三季][Hibike! Euphonium S3][03][1080P][MP4][简日双语]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫貓字幕社][4月新番][吹響吧！上低音號 第三季][Hibike! Euphonium S3][02v2][1080P][MP4][繁日雙語]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫猫字幕社][4月新番][吹响吧！上低音号 第三季][Hibike! Euphonium S3][02v2][1080P][MP4][简日双语]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第02话 GB 1080P MP4（字幕社招人内详）")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第02话 GB 720P MP4（字幕社招人内详）")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [03v2][WebRip][HEVC_AAC][简繁日内封][招募时轴]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [03v2][WebRip][HEVC_AAC][简日内嵌][招募时轴]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 吹響吧！上低音號 第三季 / Hibike! Euphonium 3 [03v2][WebRip][HEVC_AAC][繁日內嵌][招募時軸]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹响吧！上低音号 第三季 / Hibike! Euphonium 3 - 02 [WebRip 1080P HEVC AAC][简繁日内封 ASSx3]")
    assertEquals("03+02", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 - 02 [WebRip 2160P HEVC AAC][简繁日内封 ASSx3]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `667844-667840-667775-667745-667693-667610-667609-667608-667520-667517`() {
    kotlin.run {
    val r =
        parse("[ANi] 吹響吧！上低音號 第三季（僅限港澳台地區） - 03 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[云光字幕组]吹响吧！上低音号 3 Hibike! Euphonium S3 [02][简体双语][1080p]招募翻译")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹响吧！上低音号 第三季 / Hibike! Euphonium 3][02][1080P][AVC AAC][简日双语][WebRip]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫猫字幕社][4月新番][吹响吧！上低音号 第三季][Hibike! Euphonium S3][02][1080P][MP4][简日双语]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫貓字幕社][4月新番][吹響吧！上低音號 第三季][Hibike! Euphonium S3][02][1080P][MP4][繁日雙語]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 吹响吧！上低音号 3 / Hibike! Euphonium 3 - 02 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[千夏字幕組][吹響吧！上低音號 3_Hibike! Euphonium 3][第02話][1080p_AVC][繁體]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[千夏字幕组][吹响吧！上低音号 3_Hibike! Euphonium 3][第02话][1080p_AVC][简体]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕組] 吹響吧！上低音號 3 / Hibike! Euphonium 3 [02][1080p][繁體內嵌]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [02][1080p][简体内嵌]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `667516-667411-667410-667378-667377-667313-667312-667298-667297-667290`() {
    kotlin.run {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [02][1080p][简繁内封]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第01話 BIG5 1080P MP4（字幕社招人內詳）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番 吹響吧！上低音號 第三季/Hibike! Euphonium S3 第01話 BIG5 720P MP4（字幕社招人內詳）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫貓字幕社][4月新番][吹響吧！上低音號 第三季][Hibike! Euphonium S3][01v2][1080P][MP4][繁日雙語]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫猫字幕社][4月新番][吹响吧！上低音号 第三季][Hibike! Euphonium S3][01v2][1080P][MP4][简日双语]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫貓字幕社][4月新番][吹響吧！上低音號 第三季][Hibike! Euphonium S3][01][1080P][MP4][繁日雙語]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[漫猫字幕社][4月新番][吹响吧！上低音号 第三季][Hibike! Euphonium S3][01][1080P][MP4][简日双语]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [02][WebRip][HEVC_AAC][简日内嵌][招募时轴]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 吹響吧！上低音號 第三季 / Hibike! Euphonium 3 [02][WebRip][HEVC_AAC][繁日內嵌][招募時軸]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第01话 GB 1080P MP4（字幕社招人内详）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `667289-667286-667228-667130-666843-666842-666841-666635-666634-666633`() {
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番 吹响吧！上低音号 第三季/Hibike! Euphonium S3 第01话 GB 720P MP4（字幕社招人内详）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [02][WebRip][HEVC_AAC][简繁日内封][招募时轴]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] 吹響吧！上低音號 第三季（僅限港澳台地區） - 02 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[云光字幕组]吹响吧！上低音号 3 Hibike! Euphonium S3 [01][简体双语][1080p]招募翻译")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 吹响吧！上低音号 3 / Hibike! Euphonium 3 - 01 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[千夏字幕组][吹响吧！上低音号 3_Hibike! Euphonium 3][第01话][1080p_AVC][简体]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[千夏字幕組][吹響吧！上低音號 3_Hibike! Euphonium 3][第01話][1080p_AVC][繁體]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕組] 吹響吧！上低音號 3 / Hibike! Euphonium 3 [01][1080p][繁體內嵌]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [01][1080p][简体内嵌]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[桜都字幕组] 吹响吧！上低音号 3 / Hibike! Euphonium 3 [01][1080p][简繁内封]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `666601-666600-666599-666598-666567-666566-666420-666337-666150-666027`() {
    kotlin.run {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub] 吹響吧！上低音號 第三季 / Hibike! Euphonium 3 - 01 [WebRip 2160P HEVC AAC][简繁日内封 ASSx2]【高压版】")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub] 吹響吧！上低音號 第三季 / Hibike! Euphonium 3 - 01 [WebRip 2160P HEVC AAC][简繁日内封 ASSx2]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("4K", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹响吧！上低音号 第三季 / Hibike! Euphonium 3 - 01 [WebRip 1080P HEVC AAC][简繁日内封 ASSx2]")
    assertEquals("03+01", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[萌樱字幕组&霜庭云花Sub][吹响吧！上低音号 第三季 / Hibike! Euphonium 3][01][1080P][AVC AAC][简日双语][WebRip]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [01v2][WebRip][HEVC_AAC][简繁日内封][招募时轴]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 吹響吧！上低音號 第三季 / Hibike! Euphonium 3 [01v2][WebRip][HEVC_AAC][繁日內嵌][招募時軸]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [01v2][WebRip][HEVC_AAC][简日内嵌][招募时轴]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] 吹響吧！上低音號 第三季（僅限港澳台地區） - 01 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [02][先行版][WebRip][HEVC_AAC][CHS_EN][招募时轴]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 吹响吧！上低音号 第三季 / Hibike! Euphonium 3 [01][先行版][WebRip][HEVC_AAC][CHS_EN][招募时轴]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `659668-651923-651922-651921-649884-648009-648008-647860-647859-597732`() {
    kotlin.run {
    val r =
        parse("[云光字幕组]特别篇 吹响吧！上低音号~合奏比赛~Hibike! Euphonium Special ~Episode of Ensemble Contest~ [简体双语][1080p]招募翻译")
    assertEquals("Special..Special", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[❀拨雪寻春❀] 吹響吧！上低音號～合奏比賽篇～ / Hibike! Euphonium Ensemble Contest Hen [BDRip][AVC-8bit 1080p][繁日内嵌]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[❀拨雪寻春❀] 吹响吧！上低音号～合奏比赛篇～ / Hibike! Euphonium Ensemble Contest Hen [BDRip][AVC-8bit 1080p][简日内嵌]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[❀拨雪寻春❀] 吹响吧！上低音号～合奏比赛篇～ / Hibike! Euphonium Ensemble Contest Hen [BDRip][HEVC-10bit 1080p][简繁日内封]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[桜都字幕組] 特別篇 吹響吧！上低音號 ~ 合奏比賽 ~ / Tokubetsu Hen Hibike! Euphonium: Ensemble Contest Hen [BDrip][AVC-8bit 1080p AAC][繁日內嵌]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕組][特別篇 吹響吧！上低音號 ~合奏競賽~_Tokubetsu Hen Hibike! Euphonium: Ensemble Contest Hen][劇場版][BDRip_1080p_AVC][繁日双语][招募新人]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组][特别篇 吹响吧！上低音号 ~合奏竞赛~_Tokubetsu Hen Hibike! Euphonium: Ensemble Contest Hen][剧场版][BDRip_1080p_AVC][简日双语][招募新人]")
    assertEquals("特别篇..特别篇", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 特別篇 吹響吧！上低音號 ~ 合奏比賽 ~ / Tokubetsu Hen Hibike! Euphonium ~ Ensemble Contest Hen ~ [BDRip v2][1080p][HEVC_FLAC][繁日內嵌]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[北宇治字幕组] 特别篇 吹响吧！上低音号 ~ 合奏比赛 ~ / Tokubetsu Hen Hibike! Euphonium ~ Ensemble Contest Hen ~ [BDRip v2][1080p][HEVC_FLAC][简日内嵌]")
    assertEquals("特别篇..特别篇", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("吹响吧！上低音号5周年纪念公演[EUPHO 5TH REGULAR CONCERT][BDRIP_1080p_x265_TrueHD_Atmos_TrueHD_2.0]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `560011-540923-536196-535781-535761-535752-532705-532580-518747-506290`() {
    kotlin.run {
    val r = parse("【DHR動研字幕組&茉語星夢】[吹響吧！上低音號～誓言的終章～_Chikai no Finale][劇場版][BDRip][繁體][720P][MP4]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&VCB-Studio] Gekijouban Hibike! Euphonium Chikai no Finale / 剧场版 吹响吧！上低音号～誓言的终章～ 10-bit 1080p HEVC BDRip [MOVIE]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("吹响吧！上低音号 全两季+Movie Hibike! Euphonium S1+S2+SP+Moviex4 [BD 1920x1080 HEVC-10bit OPUS][简繁内封字幕]")
    assertEquals("S1+S2+SP", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【千夏字幕组】【剧场版 吹响吧！上低音号 ~誓言的终曲~_Gekijouban Hibike! Euphonium: Chikai no Finale】[剧场版_v2][BDRip_1080p_AVC][简体]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【千夏字幕組】【劇場版 吹響吧！上低音號 ~誓言的終曲~_Gekijouban Hibike! Euphonium: Chikai no Finale】[劇場版_v2][BDRip_1080p_AVC][繁體]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[千夏字幕组&LoliHouse] 剧场版 吹响吧！上低音号 ~誓言的终曲~ / Gekijouban Hibike! Euphonium: Chikai no Finale [BDRip 1080p HEVC-10bit FLAC][简繁外挂字幕]（索引：誓言的终章）")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[U3-Web] 電影版 吹響吧！上低音號～誓言的終章～ / 劇場版 響け！ユーフォニアム～誓いのフィナーレ～ [2019][Movie][繁體中文內嵌字幕][BIG5][GP WEB-DL 1080p AVC AAC(2.0+5.1) MP4] (剧场版 吹响！悠风号 ～誓言的终章～(誓言的終曲) / Gekijouban Hibike! Euphonium : Chikai no Finale / Sound! Euphonium, the Movie -Our Promise: A Brand New Day-)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[U3-Web] 電影版 吹響吧！上低音號～誓言的終章～ / 劇場版 響け！ユーフォニアム～誓いのフィナーレ～ [2019][Movie][繁體中文內封字幕][BIG5][1080p HEVC-10bit AAC AC-3(DD5.1) MKV] (剧场版 吹响！悠风号 ～誓言的终章～(誓言的終曲) / Gekijouban Hibike! Euphonium : Chikai no Finale / Sound! Euphonium, the Movie -Our Promise: A Brand New Day-) [WEB-DL --> WebRip]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DHR動研字幕組&茉語星夢】[吹響吧！上低音號～莉茲與青鳥～_Liz to Aoi Tori][劇場版][BDRip][繁體][720P][MP4]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★[吹响吧！上低音号 Hibike! Euphonium][BDrip][1080P][HEVC]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `505589-505581-504907-484696-484695-484593-484106-478260-478169-476911`() {
    kotlin.run {
    val r =
        parse("[U3-Project] 吹響吧！上低音號～莉茲與青鳥～ / 利茲與青鳥 / Liz and the Blue Bird / Liz to Aoi Tori / リズと青い鳥 [MOVIE][BD AVC 1080p DTS-HDMA FLAC]【U3自購團招人 / 新增合購計畫 , 內詳】")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[U3-Project] 吹響吧！上低音號～莉茲與青鳥～ / Liz and the Blue Bird / Liz to Aoi Tori / リズと青い鳥 [台本付数量限定版][BDMV][1080p][MOVIE]【U3自購團招人 / 新增合購計畫 , 內詳】")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[VCB-Studio] Gekijouban Hibike! Euphonium: Todoketai Melody / 剧场版 吹响吧！上低音号~想要传达的旋律~ 10-bit 1080p HEVC BDRip [Movie Fin]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[届恋字幕组] 剧场版 吹响吧！上低音号 ～想要传达的旋律～ / Gekijouban Hibike! Euphonium Todoketai Melody [BDRip 1920x1080 AVC-8bit AAC][繁体内嵌字幕]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[届恋字幕组] 剧场版 吹响吧！上低音号 ～想要传达的旋律～ / Gekijouban Hibike! Euphonium Todoketai Melody [BDRip 1920x1080 AVC-8bit AAC][简体内嵌字幕]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Moozzi2] 劇場版 吹響吧！上低音號～想要傳達的旋律～ Hibike! Euphonium Todoketai Melody (BD 1920x1080 x264 Flac)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Eupho-Fans] 劇場版 吹響吧！上低音號～想要傳達的旋律～ / 劇場版 響け！ユーフォニアム～届けたいメロディ～ / Hibike! Euphonium Movie: Todoketai Melody [BDRip x264 1920x1080 AAC DTS PGS MKV]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★[吹响吧！上低音号二期 Hibike! Euphonium S2][BDrip][1080P][HEVC]")
    assertEquals("S2", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】[UHA-WINGS][吹响吧！上低音号 第2季/Hibike! Euphonium 2][BDSP][MKV 简日_繁日外挂]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【DHR動研&茉語星夢&千夏】[吹響吧！上低音號_Sound!Euphonium][S1+S2+Movie][繁體外掛字幕][BDRip][1080P][AVC_P10_FLAC_OPUSx2_SUP]")
    assertEquals("S1+S2+S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
  }

  @Test
  public fun `474771-472387-467213-467212-466528-466330-465556-464608-464607-463600`() {
    kotlin.run {
    val r =
        parse("【DHR動研&茉語星夢&千夏】[吹響吧！上低音號 第二季_Sound!Euphonium 2][全13話+SP][繁體外掛字幕][BDRip][1080P][AVC_P10_FLAC_OPUSx2_SUP] 單身?沒錢? 來看姬情百合片吧!")
    assertEquals("SP..SP", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][SP01-07全][繁體][720P][MP4]")
    assertEquals("SP01..SP07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[VCB-Studio] Hibike! Euphonium 2 / 吹响吧！上低音号 2 / 響け! ユーフォニアム 2 10-bit 1080p HEVC BDRip [Fin]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[VCB-Studio] Hibike! Euphonium 2 / 吹响吧！上低音号 2 / 響け! ユーフォニアム 2 8-bit 1080p HEVC BDRip [MP4 Ver.]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[UHA-WINGS＆ANK-Raws][吹响吧！上低音号 第二季][Hibike! Euphonium 02][01-13][MKV 简日_繁日双语外挂][BDrip 1920x1080 HEVC-YUV420P10 FLAC]")
    assertEquals("01..13", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[UHA-WINGS＆ANK-Raws][吹响吧！上低音号 第二季][Hibike! Euphonium 2][01-13][MKV 简日_繁日双语外挂][BDrip 1920x1080 HEVC-10bit]")
    assertEquals("01..13", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【浩天个人发布】吹响吧！上低音号第二季 BD720P内嵌简体字幕")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【Xrip】[吹響吧！上低音號 2][Sound!Euphonium 2][BDrip][Vol.01_07][1080P][x265_10bit_flac]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【Xrip】[吹響吧！上低音號 2][Sound!Euphonium 2][BDrip][Vol.01_07][1080P][x264_10bit_flac]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠风社】吹响吧！上低音号 2 SP7 [简][1080P][MP4]")
    assertEquals("SP07..SP07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `461337-461023-458613-456299-454096-451163-451096-450972-450888-450887`() {
    kotlin.run {
    val r =
        parse("[UHA-WINGS＆ANK-Raws][吹响吧！上低音号 第二季][Hibike! Euphonium 2][01-07][BDrip 1920x1080 HEVC-10bit][MKV 简日_繁日外挂]")
    assertEquals("01..07", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠风社】吹响吧！上低音号 2 SP6 [简][1080P][MP4]")
    assertEquals("SP06..SP06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠风社】吹响吧！上低音号 2 SP5 [简][1080P][MP4]")
    assertEquals("SP05..SP05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠风社】吹响吧！上低音号 2 SP1~4 [简][720P][MP4]")
    assertEquals("SP01..SP04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【悠哈璃羽字幕社】[合集][吹响吧！上低音号 第2季/Hibike! Euphonium 2][1-13END][1280x720][简繁合集][GB&BIG5]")
    assertEquals("01..13", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][01-13全][繁體][720P][MP4](合集版本)")
    assertEquals("01..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][13完][繁體][720P][MP4]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][12][繁體][720P][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][13END][1080P][BIG5][MP4]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][13END][1080P][GB][MP4]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `450666-450664-450432-450427-450426-450425-450424-450376-450375-450085`() {
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][13END][720P][GB][MP4]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][13END][720P][BIG5][MP4]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][11][繁體][720P][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][10][720P][GB][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][10][720P][BIG5][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][11][720P][BIG5][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][11][720P][GB][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][12][1080P][BIG5][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][12][1080P][GB][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][12][720P][GB][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `450082-449623-449583-449582-449577-449576-449575-449572-449463-449441`() {
    kotlin.run {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][12][720P][BIG5][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][10][繁體][720P][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][11][1080P][BIG5][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][11][1080P][GB][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][10][1080P][GB][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][10][1080P][BIG5][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][11][720P][GB][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][11][720P][BIG5][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][09][繁體][720P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][09][720P][BIG5][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `449439-449023-449019-448515-448514-448391-448384-448361-448360-448293`() {
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][09][720P][GB][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][10][720P][GB][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][10][720P][BIG5][MP4]z")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][09][1080P][GB][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][09][1080P][BIG5][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][09][720P][BIG5][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][09][720P][GB][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][08][720P][BIG5][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][08][720P][GB][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][07-08][繁體][720P][MP4]")
    assertEquals("07..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `447983-447982-447895-447893-447792-447786-447525-447524-447413-447407`() {
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][08][1080P][BIG5][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][08][1080P][GB][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][08][720P][BIG5][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][08][720P][GB][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][07][720P][BIG5][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][07][720P][GB][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][07][1080P][BIG5][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][07][1080P][GB][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][07][720P][BIG5][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][07][720P][GB][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `447289-447100-447099-446939-446938-446890-446886-446678-446626-446625`() {
    kotlin.run {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][06][繁體][720P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][06][720P][BIG5][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][06][720P][GB][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][06][1080P][BIG5][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][06][1080P][GB][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][06][720P][BIG5][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][06][720P][GB][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][05][繁體][720P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][05][720P][BIG5][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][05][720P][GB][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `446464-446463-446443-446442-446286-446248-446173-446123-446122-445927`() {
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][05][1080P][BIG5][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][05][1080P][GB][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][05][720P][BIG5][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][05][720P][GB][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][04][繁體][720P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][04][1080P][BIG5][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][04][1080P][GB][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][04][720P][BIG5][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][04][720P][GB][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][04][720P][GB][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `445925-445712-445674-445608-445607-445475-445366-445364-445317-444957`() {
    kotlin.run {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][04][720P][BIG5][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][03][繁體][720P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][03][1080P][BIG5][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][03][720P][BIGB][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][03][720P][GB][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][03][1080P][GB][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][03][720P][BIG5][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][03][720P][GB][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][02][繁體][720P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][02][720P][GB][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `444791-444790-444764-444762-444585-444487-444238-444209-444155-444127`() {
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][02][1080P][BIG5][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][02][1080P][GB][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][02][720P][GB][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][02][720P][BIG5][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號 第二季_Sound!Euphonium S2][01][繁體][720P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【悠哈璃羽字幕社】【10月新番】[吹响吧！上低音号 第2季/Hibike! Euphonium][01][720P][GB][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][01][1080P][BIG5][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][01][1080P][GB][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★10月新番[吹響吧！上低音號二期_Hibike! Euphonium S2][01][720P][BIG5][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★10月新番[吹响吧！上低音号二期_Hibike! Euphonium S2][01][720P][GB][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `442933-442833-442728-442727-442306-434464-433693-425284-422536-419873`() {
    kotlin.run {
    val r =
        parse("[LowPower-Raws]吹响吧！上低音号～欢迎加入北宇治高中管乐团～ Hibike! Euphonium Kitauji Koukou Suisougaku Bu e Youkoso - Movie (BD 1080P x264 FLAC)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】吹响吧！上低音号 剧场版 [繁体内嵌] [BDRip][MKV]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ReinForce] 劇場版 吹響吧！上低音號～歡迎加入北宇治高中管樂團～Gekijouban Hibike! Euphonium (BDRip 1920x1080 x264 FLAC) [字幕内附]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Ohys-Raws]劇場版 吹響吧！上低音號～歡迎加入北宇治高中管樂團～Gekijouban Hibike! Euphonium - Kitauji Koukou Suisougaku Bu e Youkoso (BD 1280x720 x264 AACx3) [字幕内附]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】吹响吧！上低音号 剧场版 [简体内嵌] [BDRip][MKV]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【DHR動研字幕組&茉語星夢&千夏字幕組】[吹響吧！上低音號～歡迎加入北宇治高中管樂團～_Hibike!Euphonium][Vol.1-Vol.7+OVA][繁體外掛字幕][BDRip][1080P][AVC_P10_FLAC_OPUSx2_SUP]")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【极影字幕社】吹响吧！上低音号-Hibike! Euphonium - Vol.1-[简体内嵌] [BDRip 1920x1080 x264 AC-3][MP4]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【浩天个人发布】吹响吧！上低音号 BD720P内嵌简体字幕")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【F宅】吹响吧！上低音号 SP特典合集 01-07 简 1080P MP4")
    assertEquals("01..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[异域-11番小队][吹响吧！上低音号 Hibike! Euphonium][01-14][BDRIP][1080P][X264_FLAC_AAC]")
    assertEquals("01..14", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `419515-419510-419458-419401-419400-419155-418769-418264-401721-399548`() {
    kotlin.run {
    val r = parse("【极影字幕社】★[吹响吧！上低音号Hibike! Euphonium ][番外篇][720P]BIG5")
    assertEquals("番外篇..番外篇", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【Xrip】[吹響吧！上低音號][Sound!Euphonium][BDrip][Vol.01_07][1080P][x264_10bit_flac]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★[吹响吧！上低音号Hibike! Euphonium ][番外篇][720P]GB")
    assertEquals("番外篇..番外篇", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[VCB-Studio] Hibike! Euphonium/吹响吧！上低音号 10bit 1080p AVC BDRip [Fin]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[VCB-Studio] Hibike! Euphonium/吹响吧！上低音号 8bit 1080p BDRip [MP4 ver]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[异域-11番小队][吹响！悠风号/吹响吧！上低音号 Hibike! Euphonium][01-14][BDRIP][720P][X264_10bit_AAC]")
    assertEquals("01..14", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【F宅】吹响吧！上低音号 番外篇 OVA 中日双语 1080P MP4")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【F宅】吹响吧！上低音号 番外篇 OVA 中日双语 720P MP4")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第01--13话][合集][720P]GB.MP4")
    assertEquals("01..13", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第01--13話][合集][720P]BIG5.MP4")
    assertEquals("01..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `399320-399295-399294-399292-398496-398494-398492-397689-397688-397683`() {
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第01--13話][合集][1080P][繁简内封][附字体].MKV")
    assertEquals("01..13", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第13話]END[1080P][繁简内封][附字体].MKV")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第13話]END[720P][BIG5].mp4")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第13话]END[720P][GB].mp4")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第12話][720P][BIG5].mp4")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第12话][720P][GB].mp4")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第12話][1080P][繁简内封][附字体].MKV")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第11話][1080P][繁简内封][附字体].MKV")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第11話][720P][BIG5].mp4")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第11话][720P][GB].mp4")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `396942-396941-396932-396164-396160-396156-395399-395398-395393-394612`() {
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第10話][1080P][繁简内封][附字体].MKV")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第10話][720P][BIG5].mp4")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第10话][720P][GB].mp4")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第09話][720P][BIG5].mp4")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第09話][1080P][繁简内封][附字体].MKV")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第09话][720P][GB].mp4")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第08話][1080P][繁简内封][附字体].MKV")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第08話][720P][BIG5].mp4")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第08话][720P][GB].mp4")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第07話][1080P][繁简内封][附字体].MKV")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `394611-394607-393818-393805-393785-393784-392969-392965-392955-392109`() {
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第07話][720P][BIG5].mp4")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第07话][720P][GB].mp4")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第06話V2版][720P][BIG5].mp4")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第06話][720P][BIG5].mp4")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第06话][720P][GB].mp4")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第06話][1080P][繁简内封][附字体].MKV")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第05話][1080P][繁简内封][附字体].MKV")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第05話][720P][BIG5].mp4")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第05话][720P][GB].mp4")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第04話][1080P][繁简内封][附字体].MKV")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `392108-392105-391310-391273-391272-391267-390515-390406-390404-390401`() {
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第04話][720P][BIG5].mp4")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第04话][720P][GB].mp4")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第03話v2版][720P][BIG5].mp4")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第03話][1080P][繁简内封][附字体].MKV")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第03話][720P][BIG5].mp4")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第03话][720P][GB].mp4")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第02話V2版][720P][BIG5].mp4")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番[吹響吧！上低音號Hibike! Euphonium ][第02話][720P][BIG5].mp4")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★04月新番[吹响吧！上低音号Hibike! Euphonium ][第02话][720P][GB].mp4")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★04月新番[吹響吧！上低音號Hibike! Euphonium ][第02話][1080P][繁简内封][附字体].MKV")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `389589-389588-389581`() {
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番[吹響吧！上低音號Hibike! Euphonium ][第01話][720P][BIG5].mp4")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【極影字幕社】★4月新番[吹響吧！上低音號Hibike! Euphonium ][第01話][1080P][繁简内封][附字体].MKV")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【极影字幕社】★4月新番[吹响吧！上低音号Hibike! Euphonium ][第01话][720P][GB].mp4")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }
}

// @formatter:on
