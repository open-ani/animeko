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
 * 原名: `石纪元`
 * 数据源: `dmhy`
 *
 * 由 `test-codegen` 的 `GenerateTests.kt` 生成, 不要手动修改!
 * 如果你优化了解析器, 这些 test 可能会失败, 请检查是否它是因为以前解析错误而现在解析正确了. 
 * 如果是, 请更新测试数据: 执行 `GenerateTests.kt`.
 */
public class PatternTitleParserTest石纪元 : PatternBasedTitleParserTestSuite() {
  @Test
  public fun `690822-690627-690565-690253-690163-690007-689898-689075-688856-688817`() {
    kotlin.run {
    val r = parse("[猎户压制部] 石纪元 第四季 科学与未来 / Dr.STONE：Science Future [05] [1080p] [简日内嵌] [2025年1月番]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
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
    val r =
        parse("[芝士动物朋友] 石纪元 科学与未来 / Dr.STONE SCIENCE FUTURE [03][CR-WebRip][1080p][HEVC+AAC][简繁内封]（新石纪 第四季 S4）")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户压制部] 石纪元 第四季 科学与未来 / Dr.STONE：Science Future [03] [1080p] [简日内嵌] [2025年1月番]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
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
    val r = parse("[猎户压制部] 石纪元 第四季 科学与未来 / Dr.STONE：Science Future [01] [1080p] [简日内嵌] [2025年1月番]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[芝士动物朋友] 石纪元 科学与未来 / Dr.STONE SCIENCE FUTURE [02][CR-WebRip][1080p][HEVC+AAC][简繁内封]（新石纪 第四季 S4）")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][新石纪 Stone Wars/Dr. Stone Stone Wars][01-11TV全集][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV](石纪元 第二季/Dr. Stone S2/ドクターストーン Stone Wars)")
    assertEquals("01..11", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][新石纪 New World/Dr. Stone New World][01-22TV全集+SP][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV](石纪元 第三季/Dr. Stone S3/ドクターストーン New World)")
    assertEquals("01..22+SP", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[芝士动物朋友] 石纪元 科学与未来 / Dr.STONE SCIENCE FUTURE [01][CR-WebRip][1080p][HEVC+AAC][简繁内封]（新石纪 第四季 S4）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `679339-678927-678485-678132-677722-677229-674728-674226-673771-673207`() {
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 22 END [粵日雙語+內封繁體中文雙字幕] (WEB 1920x1080 AVC AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 21 [粵日雙語+內封繁體中文雙字幕] (WEB 1920x1080 AVC AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 20 [粵日雙語+內封繁體中文雙字幕] (WEB 1920x1080 AVC AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 19 [粵日雙語+內封繁體中文雙字幕] (WEB 1920x1080 AVC AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 18 [粵日雙語+內封繁體中文雙字幕] (WEB 1920x1080 AVC AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 17 [粵日雙語+內封繁體中文雙字幕] (WEB 1920x1080 AVC AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 16 [粵日雙語+內封繁體中文雙字幕] (WEB 1920x1080 AVC AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 15 [粵日雙語+內封繁體中文雙字幕] (WEB 1920x1080 AVC AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 14 [粵日雙語+內封繁體中文雙字幕] (WEB 1920x1080 AVC AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 13 [粵日雙語+內封繁體中文雙字幕] (WEB 1920x1080 AVC AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `672727-672230-671268-670745-670200-669435-668935-668405-667821-667820`() {
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 12 [粵日雙語+內封繁體中文雙字幕] (WEB 1920x1080 AVC AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 11 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 10 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 09 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 08 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 07 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 06 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 05 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 04 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 03 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `666366-665820-665454-659517-659248-658708-658151-657480-657082-656749`() {
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 02 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][代理商粵語]Dr.Stone New World 新石紀 / 石纪元 第三季 新世界 - 01 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][代理商粵語]Dr.Stone Ryuusui 新石紀 / 石纪元 龍水 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT Ani-One CHT)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[猎户压制部] 石纪元 第三季 下半 / Dr.STONE：New World [12-22] [WebRip] [1080p] [简日内嵌] [2023年10月番]")
    assertEquals("12..22", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户压制部] 石纪元 第三季 下半 / Dr.STONE：New World [22] [END] [1080p] [简日内嵌] [2023年10月番]")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户压制部] 石纪元 第三季 下半 / Dr.STONE：New World [21] [1080p] [简日内嵌] [2023年10月番]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户压制部] 石纪元 第三季 下半 / Dr.STONE：New World [20] [1080p] [简日内嵌] [2023年10月番]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户压制部] 石纪元 第三季 下半 / Dr.STONE：New World [19] [1080p] [简日内嵌] [2023年10月番]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户压制部] 石纪元 第三季 下半 / Dr.STONE：New World [18] [1080p] [简日内嵌] [2023年10月番]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户压制部] 石纪元 第三季 下半 / Dr.STONE：New World [17] [1080p] [简日内嵌] [2023年4月番]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
  }

  @Test
  public fun `656416-655803-655282-654897-654713-644072-643782-643763-643762-643158`() {
    kotlin.run {
    val r = parse("[猎户压制部] 石纪元 第三季 下半 / Dr.STONE：New World [16] [1080p] [简日内嵌] [2023年4月番]")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户压制部] 石纪元 第三季 下半 / Dr.STONE：New World [15] [1080p] [简日内嵌] [2023年4月番]")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户压制部] 石纪元 第三季 下半 / Dr.STONE：New World [14] [1080p] [简日内嵌] [2023年4月番]")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户压制部] 石纪元 第三季 下半 / Dr.STONE：New World [13] [1080p] [简日内嵌] [2023年4月番]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户压制部] 石纪元 第三季 下半 / Dr.STONE：New World [12] [1080p] [简日内嵌] [2023年4月番]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [01-11] [合集] [1080p] [简日内嵌] [2023年4月番]")
    assertEquals("01..11", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[豌豆字幕组&风之圣殿字幕组&LoliHouse] 新石纪 第三季 / 石纪元 第三季 / Dr.STONE S3 [01-11 合集][WebRip 1080p HEVC-10bit AAC][简繁外挂字幕][Fin]")
    assertEquals("01..11", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕组&风之圣殿字幕组】★04月新番[Dr.STONE 新石纪 / 石纪元 第三季][01-11][合集][简体][1080P][MP4]")
    assertEquals("01..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕組&風之聖殿字幕組】★04月新番[Dr.STONE 新石紀 / 石紀元 第三季][01-11][合集][繁體][1080P][MP4]")
    assertEquals("01..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [11] [1080p] [简繁日内封] [2023年4月番]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `643157-643093-643048-643047-642575-642574-642509-642463-642462-642014`() {
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [11] [1080p] [简日内嵌] [2023年4月番]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[豌豆字幕组&风之圣殿字幕组&LoliHouse] 新石纪 第三季 / 石纪元 第三季 / Dr.STONE S3 - 11 [WebRip 1080p HEVC-10bit AAC][简繁外挂字幕]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕组&风之圣殿字幕组】★04月新番[Dr.STONE 新石纪 / 石纪元 第三季][11][简体][1080P][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕組&風之聖殿字幕組】★04月新番[Dr.STONE 新石紀 / 石紀元 第三季][11][繁體][1080P][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [10] [1080p] [简繁日内封] [2023年4月番]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [10] [1080p] [简日内嵌] [2023年4月番]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[豌豆字幕组&风之圣殿字幕组&LoliHouse] 新石纪 第三季 / 石纪元 第三季 / Dr.STONE S3 - 10 [WebRip 1080p HEVC-10bit AAC][简繁外挂字幕]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕组&风之圣殿字幕组】★04月新番[Dr.STONE 新石纪 / 石纪元 第三季][10][简体][1080P][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕組&風之聖殿字幕組】★04月新番[Dr.STONE 新石紀 / 石紀元 第三季][10][繁體][1080P][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [09] [1080p] [简繁日内封] [2023年4月番]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `642013-641868-641865-641864-641499-641500-641365-641286-641285-640857`() {
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [09] [1080p] [简日内嵌] [2023年4月番]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[豌豆字幕组&风之圣殿字幕组&LoliHouse] 新石纪 第三季 / 石纪元 第三季 / Dr.STONE S3 - 09 [WebRip 1080p HEVC-10bit AAC][简繁外挂字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕组&风之圣殿字幕组】★04月新番[Dr.STONE 新石纪 / 石纪元 第三季][09][简体][1080P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕組&風之聖殿字幕組】★04月新番[Dr.STONE 新石紀 / 石紀元 第三季][09][繁體][1080P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [08] [1080p] [简繁日内封] [2023年4月番]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [08] [1080p] [简日内嵌] [2023年4月番]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[豌豆字幕组&风之圣殿字幕组&LoliHouse] 新石纪 第三季 / 石纪元 第三季 / Dr.STONE S3 - 08 [WebRip 1080p HEVC-10bit AAC][简繁外挂字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕组&风之圣殿字幕组】★04月新番[Dr.STONE 新石纪 / 石纪元 第三季][08][简体][1080P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕組&風之聖殿字幕組】★04月新番[Dr.STONE 新石紀 / 石紀元 第三季][08][繁體][1080P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [07] [1080p] [简繁日内封] [2023年4月番]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `640856-640661-640615-640614-640411-640410-640034-640007-640006-639600`() {
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [07] [1080p] [简日内嵌] [2023年4月番]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[豌豆字幕组&风之圣殿字幕组&LoliHouse] 新石纪 第三季 / 石纪元 第三季 / Dr.STONE S3 - 07 [WebRip 1080p HEVC-10bit AAC][简繁外挂字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕组&风之圣殿字幕组】★04月新番[Dr.STONE 新石纪 / 石纪元 第三季][07][简体][1080P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕組&風之聖殿字幕組】★04月新番[Dr.STONE 新石紀 / 石紀元 第三季][07][繁體][1080P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [06] [1080p] [简繁日内封] [2023年4月番]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [06] [1080p] [简日内嵌] [2023年4月番]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[豌豆字幕组&风之圣殿字幕组&LoliHouse] 新石纪 第三季 / 石纪元 第三季 / Dr.STONE S3 - 06 [WebRip 1080p HEVC-10bit AAC][简繁外挂字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕组&风之圣殿字幕组】★04月新番[Dr.STONE 新石纪 / 石纪元 第三季][06][简体][1080P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕組&風之聖殿字幕組】★04月新番[Dr.STONE 新石紀 / 石紀元 第三季][06][繁體][1080P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [05] [1080p] [简繁日内封] [2023年4月番]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `639599-639590-639589-639296-639285-639284-638641-638637-638636-638317`() {
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [05] [1080p] [简日内嵌] [2023年4月番]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [04] [1080p] [简繁日内封] [2023年4月番]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [04] [1080p] [简日内嵌] [2023年4月番]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[豌豆字幕组&风之圣殿字幕组&LoliHouse] 新石纪 第三季 / 石纪元 第三季 / Dr.STONE S3 - 05 [WebRip 1080p HEVC-10bit AAC][简繁外挂字幕]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕组&风之圣殿字幕组】★04月新番[Dr.STONE 新石纪 / 石纪元 第三季][05][简体][1080P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕組&風之聖殿字幕組】★04月新番[Dr.STONE 新石紀 / 石紀元 第三季][05][繁體][1080P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[豌豆字幕组&风之圣殿字幕组&LoliHouse] 新石纪 第三季 / 石纪元 第三季 / Dr.STONE S3 - 04 [WebRip 1080p HEVC-10bit AAC][简繁外挂字幕]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕组&风之圣殿字幕组】★04月新番[Dr.STONE 新石纪 / 石纪元 第三季][04][简体][1080P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕組&風之聖殿字幕組】★04月新番[Dr.STONE 新石紀 / 石紀元 第三季][04][繁體][1080P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [03] [1080p] [简繁日内封] [2023年4月番]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `638303-638032-637942-637941-637505-637504-637234-637208-637207-636740`() {
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [03] [1080p] [简日内嵌] [2023年4月番]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[豌豆字幕组&风之圣殿字幕组&LoliHouse] 新石纪 第三季 / 石纪元 第三季 / Dr.STONE S3 - 03 [WebRip 1080p HEVC-10bit AAC][简繁外挂字幕]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕组&风之圣殿字幕组】★04月新番[Dr.STONE 新石纪 / 石纪元 第三季][03][简体][1080P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕組&風之聖殿字幕組】★04月新番[Dr.STONE 新石紀 / 石紀元 第三季][03][繁體][1080P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [02] [1080p] [简繁日内封] [2023年4月番]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [02] [1080p] [简日内嵌] [2023年4月番]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[豌豆字幕组&风之圣殿字幕组&LoliHouse] 新石纪 第三季 / 石纪元 第三季 / Dr.STONE S3 - 02 [WebRip 1080p HEVC-10bit AAC][简繁外挂字幕]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕组&风之圣殿字幕组】★04月新番[Dr.STONE 新石纪 / 石纪元 第三季][02][简体][1080P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【豌豆字幕組&風之聖殿字幕組】★04月新番[Dr.STONE 新石紀 / 石紀元 第三季][02][繁體][1080P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("石纪元 新石纪 Dr.STONE S02")
    assertEquals("S2", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `636739-636738-636671-636670-636638-605298-605294-600372-599745-599318`() {
    kotlin.run {
    val r = parse("石纪元 新石纪 Dr.STONE S01")
    assertEquals("S1", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("石纪元 新石纪 Dr.STONE S01+S02")
    assertEquals("S1+S2", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [01] [1080p] [简繁日内封] [2023年4月番]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[猎户手抄部] 石纪元 第三季 Dr.STONE：New World [01] [1080p] [简日内嵌] [2023年4月番]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[豌豆字幕组&风之圣殿字幕组&LoliHouse] 新石纪 第三季 / 石纪元 第三季 / Dr.STONE S3 - 01 [WebRip 1080p HEVC-10bit AAC][简繁外挂字幕]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[NC-Raws] Ｄｒ．ＳＴＯＮＥ 石纪元 特别篇(龙水) / Dr. Stone: Ryuusui - 02 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[NC-Raws] Ｄｒ．ＳＴＯＮＥ 石纪元 特别篇(龙水) / Dr. Stone: Ryuusui - 01 (B-Global 1920x1080 HEVC AAC MKV)")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][ViuTV粵語]Dr.STONE 新石紀 / 石紀元 第二季 石之戰爭 / STONE WARS - 11 END [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT ViuTV CHT)")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][ViuTV粵語]Dr.STONE 新石紀 / 石紀元 第二季 石之戰爭 / STONE WARS - 10 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT ViuTV CHT)")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][ViuTV粵語]Dr.STONE新石紀 / 石紀元 [粵語+內封繁體中文雙字幕] (BD 1920x1080 x264 25fps AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `599179-598479-597847-597170-596508-596056-595481-595480-595218-594763`() {
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][ViuTV粵語]Dr.STONE 新石紀 / 石紀元 第二季 石之戰爭 / STONE WARS - 09 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT ViuTV CHT)")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][ViuTV粵語]Dr.STONE 新石紀 / 石紀元 第二季 石之戰爭 / STONE WARS - 08 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT ViuTV CHT)")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][ViuTV粵語]Dr.STONE 新石紀 / 石紀元 第二季 石之戰爭 / STONE WARS - 07 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT ViuTV CHT)")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][ViuTV粵語]Dr.STONE 新石紀 / 石紀元 第二季 石之戰爭 / STONE WARS - 06 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT ViuTV CHT)")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][ViuTV粵語]Dr.STONE 新石紀 / 石紀元 第二季 石之戰爭 / STONE WARS - 05 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT ViuTV CHT)")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][ViuTV粵語]Dr.STONE 新石紀 / 石紀元 第二季 石之戰爭 / STONE WARS - 04 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT ViuTV CHT)")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][ViuTV粵語]Dr.STONE 新石紀 / 石紀元 第二季 石之戰爭 / STONE WARS - 03 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT ViuTV CHT)")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][ViuTV粵語]Dr.STONE 新石紀 / 石紀元 第二季 石之戰爭 / STONE WARS - 02 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT ViuTV CHT)")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[DBD-Raws][石纪元/Dr.STONE][01-24TV全集+特典映像][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV]")
    assertEquals("01..24", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&壓制][ViuTV粵語]Dr.STONE 新石紀 / 石紀元 第二季 石之戰爭 / STONE WARS - 01 [粵日雙語+內封繁體中文雙字幕] (BD 1920x1080 x264 AACx2 PGS+SRT ViuTV CHT V2)")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHC, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `594003-593296-592849-592288-591726-590971-590440-589820-589467-588974`() {
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語] Dr.STONE 新石紀 / Dr.STONE 石紀元 / Dr.STONE - 24 END [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC 25fps AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("24..24", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語] Dr.STONE 新石紀 / Dr.STONE 石紀元 / Dr.STONE - 23 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC 25fps AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("23..23", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語] Dr.STONE 新石紀 / Dr.STONE 石紀元 / Dr.STONE - 22 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC 25fps AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語] Dr.STONE 新石紀 / Dr.STONE 石紀元 / Dr.STONE - 21 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC 25fps AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語] Dr.STONE 新石紀 / Dr.STONE 石紀元 / Dr.STONE - 20 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC 25fps AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語] Dr.STONE 新石紀 / Dr.STONE 石紀元 / Dr.STONE - 19 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC 25fps AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語] Dr.STONE 新石紀 / Dr.STONE 石紀元 / Dr.STONE - 18 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC 25fps AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語] Dr.STONE 新石紀 / Dr.STONE 石紀元 / Dr.STONE - 17 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC 25fps AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語]Dr.STONE新石紀 / Dr.STONE 石紀元 / Dr.STONE - 16 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC 25fps AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語]Dr.STONE新石紀 / Dr.STONE 石紀元 / Dr.STONE - 15 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC 25fps AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `588544-587869-587226-586898-586183-585881-584984-584338-583258-581717`() {
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語]Dr.STONE新石紀 / Dr.STONE 石紀元 / Dr.STONE - 14 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC 25fps AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語]Dr.STONE新石紀 / Dr.STONE 石紀元 / Dr.STONE - 13 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC 25fps AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語]Dr.STONE新石紀 / Dr.STONE 石紀元 / Dr.STONE - 12 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC 25fps AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語]Dr.STONE新石紀 / Dr.STONE 石紀元 / Dr.STONE - 11 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC 25fps AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語]Dr.STONE新石紀 / Dr.STONE 石紀元 / Dr.STONE - 10 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC 25fps AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語]Dr.STONE新石紀 / Dr.STONE 石紀元 / Dr.STONE - 09 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語]Dr.STONE新石紀 / Dr.STONE 石紀元 / Dr.STONE - 08 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語]Dr.STONE新石紀 / Dr.STONE 石紀元 / Dr.STONE - 07 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC AAC PGS+SRT ViuTV CAN CHT V2)")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語]Dr.STONE新石紀 / Dr.STONE 石紀元 / Dr.STONE - 06 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語]Dr.STONE新石紀 / Dr.STONE 石紀元 / Dr.STONE - 05 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
  }

  @Test
  public fun `581238-580464-579687-579103-565119-565118-565117-565116-565115-565114`() {
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語]Dr.STONE新石紀 / Dr.STONE 石紀元 / Dr.STONE - 04 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語]Dr.STONE新石紀 / Dr.STONE 石紀元 / Dr.STONE - 03 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語]Dr.STONE新石紀 / Dr.STONE 石紀元 / Dr.STONE - 02 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[jibaketa合成&音頻壓制][ViuTV粵語]Dr.STONE新石紀 / Dr.STONE 石紀元 / Dr.STONE - 01 [粵語+內封繁體中文雙字幕] (WEB 1920x1080 AVC AAC PGS+SRT ViuTV CAN CHT)")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHC, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【極影字幕社】 ★10月新番 石紀元第二季 Stone Wars Dr.Stone S2 Stone Wars 第01-11話 BIG5 1080P HEVC MP4（字幕社招人內詳）")
    assertEquals("01..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【極影字幕社】 ★10月新番 石紀元第二季 Stone Wars Dr.Stone S2 Stone Wars 第01-11話 BIG5 1080P AVC MP4（字幕社招人內詳）")
    assertEquals("01..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【極影字幕社】 ★10月新番 石紀元第二季 Stone Wars Dr.Stone S2 Stone Wars 第01-11話 BIG5 720P AVC MP4（字幕社招人內詳）")
    assertEquals("01..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【极影字幕社】 ★01月新番 石纪元第二季 Stone Wars Dr.Stone S2 Stone Wars 第01-11话 GB 1080P HEVC MP4（字幕社招人内详）")
    assertEquals("01..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【极影字幕社】 ★01月新番 石纪元第二季 Stone Wars Dr.Stone S2 Stone Wars 第01-11话 GB 1080P AVC MP4（字幕社招人内详）")
    assertEquals("01..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【极影字幕社】 ★01月新番 石纪元第二季 Stone Wars Dr.Stone S2 Stone Wars 第01-11话 GB 720P AVC MP4（字幕社招人内详）")
    assertEquals("01..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `564972-564548-564547-563922-563921-563737-563164-563163-563035-562887`() {
    kotlin.run {
    val r = parse("[c.c動漫][1月新番][新石紀 / 石紀元 第二季][01-11][合集][BIG5][1080P][MP4]")
    assertEquals("01..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【極影字幕社】 ★10月新番 石紀元第二季 Stone Wars Dr.Stone S2 Stone Wars 第11話 BIG5 1080P HEVC MP4（字幕社招人內詳）")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【极影字幕社】 ★01月新番 石纪元第二季 Stone Wars Dr.Stone S2 Stone Wars 第11话 GB 1080P HEVC MP4（字幕社招人内详）")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【極影字幕社】 ★10月新番 石紀元第二季 Stone Wars Dr.Stone S2 Stone Wars 第10話 BIG5 1080P HEVC MP4（字幕社招人內詳）")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【极影字幕社】 ★01月新番 石纪元第二季 Stone Wars Dr.Stone S2 Stone Wars 第10话 GB 1080P HEVC MP4（字幕社招人内详）")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c动漫][1月新番][新石纪 / 石纪元 第二季][11][GB][HEVC][1080P][MP4][END]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【極影字幕社】 ★10月新番 石紀元第二季 Stone Wars Dr.Stone S2 Stone Wars 第09話 BIG5 1080P HEVC MP4（字幕社招人內詳）")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【极影字幕社】 ★01月新番 石纪元第二季 Stone Wars Dr.Stone S2 Stone Wars 第09话 GB 1080P HEVC MP4（字幕社招人内详）")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c动漫][1月新番][新石纪 / 石纪元 第二季][10][GB][HEVC][1080P][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【極影字幕社】 ★10月新番 石紀元第二季 Stone Wars Dr.Stone S2 Stone Wars 第08話 BIG5 1080P HEVC MP4（字幕社招人內詳）")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `562886-562753-561998-561997-561616-561615-561083-560294-560085-560084`() {
    kotlin.run {
    val r =
        parse("【极影字幕社】 ★01月新番 石纪元第二季 Stone Wars Dr.Stone S2 Stone Wars 第08话 GB 1080P HEVC MP4（字幕社招人内详）")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c动漫][1月新番][新石纪 / 石纪元 第二季][08-09][GB][HEVC][1080P][MP4]")
    assertEquals("08..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【極影字幕社】 ★10月新番 石紀元第二季 Stone Wars Dr.Stone S2 Stone Wars 第07話 BIG5 1080P HEVC MP4（字幕社招人內詳）")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【极影字幕社】 ★01月新番 石纪元第二季 Stone Wars Dr.Stone S2 Stone Wars 第07话 GB 1080P HEVC MP4（字幕社招人内详）")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【極影字幕社】 ★10月新番 石紀元第二季 Stone Wars Dr.Stone S2 Stone Wars 第06話 BIG5 1080P HEVC MP4（字幕社招人內詳）")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【极影字幕社】 ★01月新番 石纪元第二季 Stone Wars Dr.Stone S2 Stone Wars 第06话 GB 1080P HEVC MP4（字幕社招人内详）")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c动漫][1月新番][新石纪 / 石纪元 第二季][07][GB][HEVC][1080P][MP4][暫停更新公告]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c动漫][1月新番][新石纪 / 石纪元 第二季][06][GB][HEVC][1080P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【極影字幕社】 ★10月新番 石紀元第二季 Stone Wars Dr.Stone S2 Stone Wars 第05話 BIG5 1080P HEVC MP4（字幕社招人內詳）")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【极影字幕社】 ★01月新番 石纪元第二季 Stone Wars Dr.Stone S2 Stone Wars 第05话 GB 1080P HEVC MP4（字幕社招人内详）")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `559707-559674-559673-559009-558951-558950-558358-558030-558029-557644`() {
    kotlin.run {
    val r = parse("[c.c动漫][1月新番][新石纪 / 石纪元 第二季][05][GB][HEVC][1080P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【極影字幕社】 ★10月新番 石紀元第二季 Stone Wars Dr.Stone S2 Stone Wars 第04話 BIG5 1080P HEVC MP4（字幕社招人內詳）")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【极影字幕社】 ★01月新番 石纪元第二季 Stone Wars Dr.Stone S2 Stone Wars 第04话 GB 1080P HEVC MP4（字幕社招人内详）")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c动漫][1月新番][新石纪 / 石纪元 第二季][04][GB][HEVC][1080P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【極影字幕社】 ★10月新番 石紀元第二季 Stone Wars Dr.Stone S2 Stone Wars 第03話 BIG5 1080P HEVC MP4（字幕社招人內詳）")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【极影字幕社】 ★01月新番 石纪元第二季 Stone Wars Dr.Stone S2 Stone Wars 第03话 GB 1080P HEVC MP4（字幕社招人内详）")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c动漫][1月新番][新石纪 / 石纪元 第二季][03][GB][HEVC][1080P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【極影字幕社】 ★10月新番 石紀元第二季 Stone Wars Dr.Stone S2 Stone Wars 第02話 BIG5 1080P HEVC MP4（字幕社招人內詳）")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【极影字幕社】 ★01月新番 石纪元第二季 Stone Wars Dr.Stone S2 Stone Wars 第02话 GB 1080P HEVC MP4（字幕社招人内详）")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c动漫][1月新番][新石纪 / 石纪元 第二季][02][GB][HEVC][1080P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `557507-557505-557001-531015-530498-530004-529474-528954-528484-527990`() {
    kotlin.run {
    val r =
        parse("【極影字幕社】 ★10月新番 石紀元第二季 Stone Wars Dr.Stone S2 Stone Wars 第01話 BIG5 1080P HEVC MP4（字幕社招人內詳）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("【极影字幕社】 ★01月新番 石纪元第二季 Stone Wars Dr.Stone S2 Stone Wars 第01话 GB 1080P MP4（字幕社招人内详）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c动漫][1月新番][新石纪 / 石纪元 第二季][01][GB][HEVC][1080P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c動漫][7月新番][新石紀 / 石紀元][Dr.STONE][24][BIG5][1080P][MP4][END][網盤]")
    assertEquals("24..24", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c動漫][7月新番][新石紀 / 石紀元][Dr.STONE][23][BIG5][1080P][MP4][網盤]")
    assertEquals("23..23", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c動漫][7月新番][新石紀 / 石紀元][Dr.STONE][22][BIG5][1080P][MP4][網盤]")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c動漫][7月新番][新石紀 / 石紀元][Dr.STONE][21][BIG5][1080P][MP4][網盤]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c動漫][7月新番][新石紀 / 石紀元][Dr.STONE][20][BIG5][1080P][MP4][網盤]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c動漫][7月新番][新石紀 / 石紀元][Dr.STONE][19][BIG5][1080P][MP4][網盤]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c動漫][7月新番][新石紀 / 石紀元][Dr.STONE][18][BIG5][1080P][MP4][網盤]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `527515-526972`() {
    kotlin.run {
    val r = parse("[c.c動漫][7月新番][新石紀 / 石紀元][Dr.STONE][17][BIG5][1080P][MP4][網盤]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("[c.c動漫][7月新番][新石紀 / 石紀元][Dr.STONE][16][BIG5][1080P][MP4][網盤]")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }
}

// @formatter:on
