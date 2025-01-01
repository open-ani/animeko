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
 * 原名: `未来日记`
 * 数据源: `dmhy`
 *
 * 由 `test-codegen` 的 `GenerateTests.kt` 生成, 不要手动修改!
 * 如果你优化了解析器, 这些 test 可能会失败, 请检查是否它是因为以前解析错误而现在解析正确了. 
 * 如果是, 请更新测试数据: 执行 `GenerateTests.kt`.
 */
public class PatternTitleParserTest未来日记 : PatternBasedTitleParserTestSuite() {
  @Test
  public fun `668596_V2_DBD_Raws_Mirai_Nikki_01_26TV_OVA_1080P_BDRip_HEVC_10bit_FLACx2_MKV`() {
    val r =
        parse("[V2][DBD-Raws][未来日记/Mirai Nikki/未来日記/みらいにっき][01-26TV全集+OVA][1080P][BDRip][HEVC-10bit][简繁外挂][FLACx2][MKV]")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `655726_DBD_Mirai_Nikki_01_26TV_OVA_JPN_Ver_1080P_BDRip_AVC_FLAC_MKV`() {
    val r =
        parse("[DBD製作組][未來日記/Mirai Nikki/未来日記/みらいにっき][01-26TV全集+OVA][日版/JPN.Ver][1080P][BDRip][AVC][繁體內嵌][FLAC][MKV]")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `655723_DBD_Mirai_Nikki_01_26TV_OVA_JPN_Ver_1080P_BDRip_AVC_FLAC_MKV`() {
    val r =
        parse("[DBD制作组][未来日记/Mirai Nikki/未来日記/みらいにっき][01-26TV全集+OVA][日版/JPN.Ver][1080P][BDRip][AVC][简体内嵌][FLAC][MKV]")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `655722_DBD_Mirai_Nikki_01_26TV_OVA_JPN_Ver_1080P_BDRip_HEVC_10bit_FLAC_MKV`() {
    val r =
        parse("[DBD制作组][未来日记/Mirai Nikki/未来日記/みらいにっき][01-26TV全集+OVA][日版/JPN.Ver][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV]")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `655383_DBD_Raws_Mirai_Nikki_01_26TV_OVA_JPN_Ver_1080P_BDRip_HEVC_10bit_FLAC_MKV`() {
    val r =
        parse("[DBD-Raws][未来日记/Mirai Nikki/未来日記/みらいにっき][01-26TV全集+OVA][日版/JPN.Ver][1080P][BDRip][HEVC-10bit][简繁日双语外挂][FLAC][MKV]")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `646425_Redial_Mirai_Nikki_Redial_OVA_BDRip_AVC_8bit_1080p`() {
    val r =
        parse("[❀拨雪寻春❀] 未來日記 Redial / 未来日記 リダイヤル / Mirai Nikki Redial [OVA][BDRip][AVC-8bit 1080p][繁日内嵌]")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `646424_Redial_Mirai_Nikki_Redial_OVA_BDRip_HEVC_10bit_1080p`() {
    val r =
        parse("[❀拨雪寻春❀] 未来日记 Redial / 未来日記 リダイヤル / Mirai Nikki Redial [OVA][BDRip][HEVC-10bit 1080p][简繁日内封]")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `646423_Redial_Mirai_Nikki_Redial_OVA_BDRip_AVC_8bit_1080p`() {
    val r =
        parse("[❀拨雪寻春❀] 未来日记 Redial / 未来日記 リダイヤル / Mirai Nikki Redial [OVA][BDRip][AVC-8bit 1080p][简日内嵌]")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EMBEDDED, r.subtitleKind)
  }

  @Test
  public fun `613473_VCB_Studio_Mirai_Nikki_10_bit_1080p_HEVC_BDRip_TV_OVA_Fin`() {
    val r = parse("[VCB-Studio] 未来日记 / Mirai Nikki / 未来日記 10-bit 1080p HEVC BDRip [TV + OVA Fin]")
    assertEquals("TV + OVA Fin..TV + OVA Fin", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `567796_DBD_Raws_Mirai_Nikki_01_26_OVA_1080P_BDRip_HEVC_10bit_FLAC_MKV`() {
    val r =
        parse("[DBD-Raws&四魂字幕组][未来日记/Mirai Nikki/みらいにっき][01-26全集+OVA+特典][1080P][BDRip][HEVC-10bit][简繁外挂字幕][FLAC][MKV]")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `539339_Snow_Raws_Mirai_Nikki_2011_2011_BD_1920x1080_HEVC_YUV420P10_FLAC`() {
    val r = parse("[Snow-Raws] 未来日记/Mirai Nikki 2011/未来日記 2011 (BD 1920x1080 HEVC-YUV420P10 FLAC)")
    assertEquals("2011..2011", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `532515_Mirai_Nikki_01_BD_1920x1080_HEVC_x265_10bit`() {
    val r = parse("未来日记 Mirai Nikki 01 [BD 1920x1080 HEVC x265 10bit][简繁内封字幕]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `532499_Mirai_Nikki_1_26_OVA_BD_1920x1080_HEVC_10bit_2011`() {
    val r = parse("未来日记 Mirai Nikki 1-26+OVA [BD 1920x1080 HEVC 10bit][简繁内封字幕][2011年]")
    assertEquals("01..26+OVA", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `531688_Redial_Mirai_Nikki_Redial_OVA_BDrip_BIG5_MP4_1920X1080`() {
    val r = parse("【幻櫻字幕組】【未來日記Redial Mirai Nikki Redial】【OVA】【BDrip】【BIG5_MP4】【1920X1080】")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `531687_Redial_Mirai_Nikki_Redial_OVA_BDrip_GB_MP4_1920X1080`() {
    val r = parse("【幻樱字幕组】【未来日记Redial Mirai Nikki Redial】【OVA】【BDrip】【GB_MP4】【1920X1080】")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `531686_Redial_Mirai_Nikki_Redial_OVA_BDrip_BIG5_MP4_1280X720`() {
    val r = parse("【幻櫻字幕組】【未來日記Redial Mirai Nikki Redial】【OVA】【BDrip】【BIG5_MP4】【1280X720】")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `531685_Redial_Mirai_Nikki_Redial_OVA_BDrip_GB_MP4_1280X720`() {
    val r = parse("【幻樱字幕组】【未来日记Redial Mirai Nikki Redial】【OVA】【BDrip】【GB_MP4】【1280X720】")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `447952_Moozzi2_Mirai_Nikki_BD_1920x1080_x264_FLACx2`() {
    val r = parse("[Moozzi2] 未來日記 Mirai Nikki (BD 1920x1080 x264 FLACx2)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `412839__11_Mirai_Nikki_1_26_OVA_BDRIP_720P_X264_10bit_AACx2`() {
    val r = parse("[异域-11番小队][未来日记 Mirai Nikki][1-26+OVA][BDRIP][720P][X264-10bit_AACx2]")
    assertEquals("01..26+OVA", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `360070_Xrip_Mirai_Nikki_BDrip_Vol_01_09_1080P_x264_10bit_flac`() {
    val r = parse("【Xrip】[未來日記][Mirai Nikki][BDrip][Vol.01_09][1080P][x264_10bit_flac]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `358768_Mirai_Nikki___9_Redial_OVA_BD_720p_AVC_AAC_mp4_encoded_by_SEED`() {
    val r =
        parse("未來日記 Mirai Nikki (無修正) - 全9卷+Redial OVA (乳) (BD 720p AVC AAC).mp4 [encoded by SEED] 有奶")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `319867_TUcaptions_2013_07_26_Mirai_Nikki_Redial_OVA_1080P_MKV_BIG5`() {
    val r =
        parse("[TUcaptions] 2013.07.26 // Mirai Nikki / 未來日記 Redial // OVA // 1080P-MKV-BIG5(繁)")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `319864_TUcaptions_2013_07_26_Mirai_Nikki_Redial_OVA_720P_MP4_BIG5`() {
    val r = parse("[TUcaptions] 2013.07.26 // Mirai Nikki / 未來日記 Redial // OVA // 720P-MP4-BIG5(繁)")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `318886_Kuu_Mirai_Nikki_Redial_v2_480p_D15521BB_mkv_OVA`() {
    val r = parse("[Kuu]Mirai Nikki Redial v2 [480p][D15521BB] mkv 未来日记 OVA [内封西班牙文字幕]")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `318885_GotWoot_Mirai_Nikki_Redial_DVD_576p_1F730058_mkv_OVA`() {
    val r = parse("[GotWoot] Mirai Nikki Redial [DVD 576p][1F730058] mkv 未来日记 OVA [内封英文]")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("ENG, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `318883_Mirai_nikki_Redial___OVA_mkv`() {
    val r = parse("Mirai nikki Redial - OVA mkv 未来日记")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `318882_Perry_Mirai_Nikki_Redial_OVA`() {
    val r = parse("[Perry] Mirai Nikki Redial OVA 未来日记 [内封西班牙文]")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `318873_kjnevicikal_Mirai_nikki_redial_OVA_720x480_AVC_AAC_eng_subs_ASS_OVA`() {
    val r =
        parse("[kjnevicikal] Mirai nikki redial OVA 720x480 AVC AAC + eng subs ASS 未来日记 OVA [外挂英文]")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("ENG, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `318870_HiNA_Betsuni_Mirai_Nikki_OVA_480p_DVD_A558F62A_mkv_OAD`() {
    val r = parse("[HiNA-Betsuni] Mirai Nikki OVA [480p DVD] [A558F62A].mkv 未来日记 OAD 第0集短篇[内封英文]")
    assertEquals("OAD..OAD", r.episodeRange.toString())
    assertEquals("ENG, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `318867_Final8_Mirai_Nikki___01_26_BD_10_bit_1920x1080_x264_FLAC`() {
    val r = parse("[Final8]Mirai Nikki - 01-26 (BD 10-bit 1920x1080 x264 FLAC) 未来日记 [内封英文]")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("ENG, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `318865_Mirai_Nikki_Future_Diary_Dual_Audio_1080p`() {
    val r = parse("Mirai Nikki (Future Diary) Dual-Audio 1080p 未来日记")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `318864_RCOS_Mirai_Nikki_BDRip_1920x1080_x264_Hi10P_FLAC_By_Arab_Bluray_com`() {
    val r =
        parse("[RCOS] Mirai Nikki BDRip 1920x1080 x264 Hi10P FLAC By Arab Bluray.com 未来日记 [内封阿拉伯文]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `318863_Mirai_Nikki_BDRip_1920x1080_x264_FLAC`() {
    val r = parse("Mirai Nikki 未来日记 [BDRip 1920x1080 x264 FLAC]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `317082_TUcaptions_2013_07_26_Mirai_Nikki_OVA_576P_MP4_BIG5__21_21`() {
    val r =
        parse("[TUcaptions] 2013.07.26 // Mirai Nikki / 未來日記 Redial// OVA // 576P-MP4-BIG5(繁)【社員招募中!!】")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `316447_Dymy_Mirai_Nikki_OVA_BIG5_1024X576_MP4_V2`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【OVA】【BIG5】【1024X576】【MP4】V2")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `314349_jhh2012_Mirai_Nikki_Redial_OVA_MKV_1024x576_DVDRIP_x264_FLAC`() {
    val r =
        parse("[jhh2012]【未來日記Mirai Nikki_Redial_數據傳輸】[OVA]【MKV】【簡繁內掛+外掛】[1024x576][DVDRIP_x264_FLAC]【附快傳】內詳")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `314347_jhh2012_Mirai_Nikki_Redial_OVA_480P_mp4_DVDRIP_AVC_AAC`() {
    val r = parse("[jhh2012]【未來日記Mirai Nikki_Redial】[OVA]【簡體】[480P][mp4][DVDRIP_AVC_AAC]【附快傳】")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `313906_OVA_Redial_Mirai_Nikki_Redial_DVDrip_GB_480P_RV10_MP4`() {
    val r = parse("【幻樱字幕组】【OVA】【未来日记Redial Mirai Nikki Redial】【DVDrip】【GB_480P】【RV10&MP4】")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `306714_OVA_Redial_Mirai_Nikki_Redial_GB_MP4_396P_nico`() {
    val r = parse("【幻樱字幕组】【OVA】【未来日记Redial Mirai Nikki Redial】【GB_MP4】【396P】【nico先行版】")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `306036_Mirai_Nikki_BDRIP_Vol_1_9_BIG5_GB_720P_MKV`() {
    val r = parse("【四魂制作组】[Mirai Nikki 未来日记][BDRIP][Vol.1-9][BIG5_GB][720P_MKV][全集]（附特典）")
    assertEquals("01..09", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `272715_Mirai_Nikki_BDRIP_Vol_1_9_BIG5_GB_1080P_MKV`() {
    val r = parse("【四魂制作组】[Mirai Nikki 未来日记][BDRIP][Vol.1-9][BIG5_GB][1080P_MKV][全集]（附特典）")
    assertEquals("01..09", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `270064__11_Mirai_nikki_BDRIP_1_26_SP_X264_AAC_720P_10bit`() {
    val r = parse("[异域-11番小队][未来日记Mirai_nikki][BDRIP][1-26+SP][X264_AAC][720P][10bit] 更新第10话字幕")
    assertEquals("01..26+SP", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `269329__11_Mirai_nikki_BDRIP_1_26_SP_X264_AAC_720P`() {
    val r = parse("[异域-11番小队][未来日记Mirai_nikki][BDRIP][1-26+SP][X264_AAC][720P] 更新第10话字幕")
    assertEquals("01..26+SP", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `267846_10_01_26_1280X720_MKV`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][01-26(全集)][1280X720][繁體][MKV]")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `267844_10_01_26_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][01-26(全集)][848X480][简体][RMVB]")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `267843_10_01_26_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][01-26(全集)][848X480][繁體][RMVB]")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `262990_Mirai_Nikki_BDRIP_Vol_7_BIG5_GB_1080P_MKV_OST`() {
    val r = parse("【四魂制作组】[Mirai Nikki 未来日记][BDRIP][Vol.7][BIG5_GB][1080P_MKV]（附OST特典）")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `262989_Mirai_Nikki_BDRIP_Vol_6_BIG5_GB_1080P_MKV_OST`() {
    val r = parse("【四魂制作组】[Mirai Nikki 未来日记][BDRIP][Vol.6][BIG5_GB][1080P_MKV]（附OST特典）")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `262988_Mirai_Nikki_BDRIP_Vol_5_BIG5_GB_1080P_MKV_OST`() {
    val r = parse("【四魂制作组】[Mirai Nikki 未来日记][BDRIP][Vol.5][BIG5_GB][1080P_MKV]（附OST特典）")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `262987_Mirai_Nikki_BDRIP_Vol_4_BIG5_GB_1080P_MKV_OST`() {
    val r = parse("【四魂制作组】[Mirai Nikki 未来日记][BDRIP][Vol.4][BIG5_GB][1080P_MKV]（附OST特典）")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `261984_ReinForce_Mirai_Nikki___Vol_6_BDRip_1920x1080_x264_FLAC`() {
    val r = parse("[ReinForce] 未来日记 Mirai Nikki - Vol.6 (BDRip 1920x1080 x264 FLAC)")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `260354_TSDM_Mirai_Nikki_1_24_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][1-24][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `260353_TSDM_Mirai_Nikki_1_24_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][1-24][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `259171_Mirai_Nikki_1_26FIN_720p_MKV`() {
    val r = parse("【诸神字幕組】[未来日记][Mirai Nikki][1~26FIN][720p][中日双语字幕][MKV]")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `252806_Mirai_Nikki_720P_MKV_ASS`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai Nikki 未来日记][720P][MKV+ASS]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `252310_TSDM_Mirai_Nikki_1_26FIN_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][1~26FIN][720p][简繁内挂][MKV]")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `252305_TSDM_Mirai_Nikki_26_END_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][26_END][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `252304_TSDM_Mirai_Nikki_26_END_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][26_END][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `252303_TSDM_Mirai_Nikki_26_END_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][26_END][720p][简繁内挂][MKV]")
    assertEquals("26..26", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `251657_10_Mirai_Nikki_01_26_GB_MP4`() {
    val r = parse("【华盟字幕社】[10月新番][Mirai_Nikki][未来日记][01-26][GB][MP4][全]")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251656_10_Mirai_Nikki_23_26_GB_MP4`() {
    val r = parse("【华盟字幕社】[10月新番][Mirai_Nikki][未来日记][23-26][GB][MP4][连载完毕]")
    assertEquals("23..26", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251526_10_01_26_END_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][01-26 END][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251525_10_26_END_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][26 END][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251508_Mirai_Nikki_01_26_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][01-26][BIG5_GB繁簡][720P][MKV]【全集】")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `251507_Mirai_Nikki_26_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][26][BIG5_GB繁簡][720P][MKV][完結]")
    assertEquals("26..26", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `251362_Dymy_Mirai_Nikki_1_26_BIG5_1280X720_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【1-26】【BIG5】【1280X720】【MKV】")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251352_Dymy_Mirai_Nikki_1_26_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【1-26】【BIG5】【1024X576】【RMVB】")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251329_Mirai_Nikki_01_26_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★ [未來日記][Mirai Nikki][01-26][完結合集][1024x576][繁體][RMVB]")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251328_Mirai_Nikki_01_26_1024x576_MP4`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][01-26][完结合集][1024x576][简体][MP4]")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251294_DHR_Sakura_Cafe_Mirai_nikki_01_26_848_480`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][01～26(完)][848 × 480][繁體](未修正合集)")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251286_DHR_Sakura_Cafe_Mirai_nikki_26_848_480`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][26(完)][848 × 480][繁體](人員招募中～)")
    assertEquals("26..26", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251277_SGS_01_26_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第01-26話 完結合集 BIG5繁體 480P MP4")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251276_SGS_01_26_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第01-26話 完結合集 BIG5繁體 720P MP4")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251217_Mirai_Nikki_26_END_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][26 END][GB][x264][848X480][mp4]")
    assertEquals("26..26", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251216_Mirai_Nikki_26_END_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][26 END][GB][x264][1280X720][mp4]")
    assertEquals("26..26", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251203_Dymy_Mirai_Nikki_26_BIG5_1280X720_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【26】【BIG5】【1280X720】【MKV】")
    assertEquals("26..26", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251178_Mirai_Nikki_26_1024x576_MP4`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][26][完结][1024x576][简体][MP4]")
    assertEquals("26..26", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251171_Mirai_Nikki_1_26_720P_MKV`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][1-26][合集][720P][繁简外挂][MKV]")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `251160_Mirai_Nikki_26_1280x720_MKV`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][01-26][完结合集][1280x720][简体][MKV]")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251150_Mirai_Nikki_26_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★ [未來日記][Mirai Nikki][26][1024x576][繁體][RMVB]")
    assertEquals("26..26", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251136_TSDM_Mirai_Nikki_25_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][25][720p][简繁内挂][MKV]")
    assertEquals("25..25", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `251120_SGS_26_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第26話 完 BIG5繁體 480P MP4")
    assertEquals("26..26", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251117_SGS_26_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第26話 完 BIG5繁體 720P MP4")
    assertEquals("26..26", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251098_Mirai_Nikki_01_26_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][01-26][BIG5繁體][720P][MP4]【全集】")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251094_10_26_END_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][26 END][848X480][简体][RMVB]")
    assertEquals("26..26", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251093_10_26_END_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][26 END][848X480][繁體][RMVB]")
    assertEquals("26..26", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251090_Mirai_Nikki_26_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][26][BIG5繁體][720P][MP4][完結]")
    assertEquals("26..26", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251084_Mirai_Nikki_01_26_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][01-26][BIG5繁體][1024X576][RMVB]【全集】")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251083_Mirai_Nikki_01_26_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][01-26][BIG5繁體][848X480][RMVB]【合集】")
    assertEquals("01..26", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251081_Mirai_Nikki_26_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][26][BIG5繁體][1024X576][RMVB][完結]")
    assertEquals("26..26", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251079_Mirai_Nikki_26_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][26][BIG5繁體][848X480][RMVB][完結]")
    assertEquals("26..26", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251078_10_Mirai_Nikki_26_GB_RV10_848X480_END`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 26【GB_RV10】【848X480】【END】")
    assertEquals("26..26", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `251012_Mirai_Nikki_25_720P_MKV`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][25][720P][繁简外挂][MKV]")
    assertEquals("25..25", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `250662_Mirai_Nikki_BDRIP_Vol_3_BIG5_GB_1080P_MKV_OST`() {
    val r = parse("【四魂制作组】[Mirai Nikki 未来日记][BDRIP][Vol.3][BIG5_GB][1080P_MKV]（附OST特典）")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `250661_Mirai_Nikki_BDRIP_Vol_2_BIG5_GB_1080P_MKV_OST`() {
    val r = parse("【四魂制作组】[Mirai Nikki 未来日记][BDRIP][Vol.2][BIG5_GB][1080P_MKV]（附OST特典）")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `250660_Mirai_Nikki_BDRIP_Vol_1_BIG5_GB_1080P_MKV_OST`() {
    val r = parse("【四魂制作组】[Mirai Nikki 未来日记][BDRIP][Vol.1][BIG5_GB][1080P_MKV]（附OST特典）")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `250626_DHR_Sakura_Cafe_Mirai_nikki_25_848_480`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][25][848 × 480][繁體](人員招募中～)")
    assertEquals("25..25", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `250570_10_25_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][25][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `250518_Mirai_Nikki_25_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][25][BIG5_GB繁簡][720P][MKV]")
    assertEquals("25..25", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `250498_Mirai_Nikki_25_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][25][GB][x264][848X480][mp4]")
    assertEquals("25..25", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `250497_Mirai_Nikki_25_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][25][GB][x264][1280X720][mp4]")
    assertEquals("25..25", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `250353_Drei_Raws_Mirai_Nikki_Vol_3_Vol_4_BDRip_1920x1080_AVC_Hi10p_FLACx2`() {
    val r = parse("[Drei-Raws][未来日记_Mirai_Nikki][Vol.3-Vol.4][BDRip][1920x1080][AVC_Hi10p_FLACx2]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `250344_TSDM_Mirai_Nikki_25_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][25][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `250343_TSDM_Mirai_Nikki_25_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][25][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `250302_Mirai_Nikki_25_1280x720_MKV`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][25][1280x720][简体][MKV]")
    assertEquals("25..25", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `250185_Mirai_Nikki_25_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★ [未來日記][Mirai Nikki][25][1024x576][繁體][RMVB]")
    assertEquals("25..25", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `250181_Mirai_Nikki_25_1024x576_MP4`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][25][1024x576][简体][MP4]")
    assertEquals("25..25", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `250144_SGS_25_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第25話 BIG5繁體 480P MP4")
    assertEquals("25..25", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `250143_SGS_25_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第25話 BIG5繁體 720P MP4")
    assertEquals("25..25", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `250122_10_25_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][25][848X480][简体][RMVB]")
    assertEquals("25..25", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `250121_10_25_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][25][848X480][繁體][RMVB]")
    assertEquals("25..25", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `250112_Mirai_Nikki_25_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][25][BIG5繁體][720P][MP4]")
    assertEquals("25..25", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `250109_Mirai_Nikki_25_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][25][BIG5繁體][1024X576][RMVB]")
    assertEquals("25..25", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `250108_Mirai_Nikki_25_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][25][BIG5繁體][848X480][RMVB]")
    assertEquals("25..25", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `250107_10_Mirai_Nikki_25_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 25【GB_RV10】【848X480】")
    assertEquals("25..25", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `249616_10_24_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][24][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `249558_Mirai_Nikki_24_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][24][BIG5_GB繁簡][720P][MKV]")
    assertEquals("24..24", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `249408_Mirai_Nikki_20_24_720P_MKV`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][20-24][720P][繁简外挂][MKV]")
    assertEquals("20..24", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `249395_DHR_Sakura_Cafe_Mirai_nikki_24_848_480`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][24][848 × 480][繁體](人員招募中～)")
    assertEquals("24..24", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `249374_Mirai_Nikki_24_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][24][GB][x264][848X480][mp4]")
    assertEquals("24..24", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `249373_Mirai_Nikki_24_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][24][GB][x264][1280X720][mp4]")
    assertEquals("24..24", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `249369_Dymy_Mirai_Nikki_24_BIG5_1280X720_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【24】【BIG5】【1280X720】【MKV】")
    assertEquals("24..24", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `249334_Dymy_Mirai_Nikki_24_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【24】【BIG5】【1024X576】【RMVB】")
    assertEquals("24..24", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `249316_DHR_Sakura_Cafe_Mirai_nikki_23_848_480`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][23][848 × 480][繁體](人員招募中～)")
    assertEquals("23..23", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `249300_Mirai_Nikk_24_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『24』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("24..24", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `249249_Mirai_Nikki_24_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★ [未來日記][Mirai Nikki][24][1024x576][繁體][RMVB]")
    assertEquals("24..24", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `249248_Mirai_Nikki_24_1024x576_MP4`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][24][1024x576][简体][MP4]")
    assertEquals("24..24", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `249247_Mirai_Nikki_24_1280x720_MKV`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][24][1280x720][简体][MKV]")
    assertEquals("24..24", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `249246_SGS_24_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第24話 BIG5繁體 480P MP4")
    assertEquals("24..24", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `249245_SGS_24_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第24話 BIG5繁體 720P MP4")
    assertEquals("24..24", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `249237_TSDM_Mirai_Nikki_23_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][24][720p][简繁内挂][MKV]")
    assertEquals("24..24", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `249227_Mirai_Nikki_24_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][24][BIG5繁體][720P][MP4]")
    assertEquals("24..24", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `249223_Mirai_Nikki_24_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][24][BIG5繁體][1024X576][RMVB]")
    assertEquals("24..24", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `249217_Mirai_Nikki_24_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][24][BIG5繁體][848X480][RMVB]")
    assertEquals("24..24", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `249216_TSDM_Mirai_Nikki_24_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][24][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `249214_TSDM_Mirai_Nikki_24_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][24][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `249213_10_24_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][24][848X480][简体][RMVB]")
    assertEquals("24..24", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `249212_10_24_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][24][848X480][繁體][RMVB]")
    assertEquals("24..24", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `249209_10_Mirai_Nikki_24_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 24【GB_RV10】【848X480】")
    assertEquals("24..24", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248890_10_23_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][23][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248826_Mirai_Nikki_23_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][23][BIG5_GB繁簡][720P][MKV]")
    assertEquals("23..23", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `248820_10_Mirai_Nikki_21v2_GB_MP4`() {
    val r = parse("【华盟字幕社】[10月新番][Mirai_Nikki][未来日记][21v2][GB][MP4]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248731_10_Mirai_Nikki_21_22_GB_MP4`() {
    val r = parse("【华盟字幕社】[10月新番][Mirai_Nikki][未来日记][21-22][GB][MP4]")
    assertEquals("21..22", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248658_Mirai_Nikki_22_1280x720_MKV`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][22][1280x720][简体][MKV]")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248587_Dymy_Mirai_Nikki_23_BIG5_1280X720_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【23】【BIG5】【1280X720】【MKV】")
    assertEquals("23..23", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248574_TSDM_Mirai_Nikki_23_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][23][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248573_TSDM_Mirai_Nikki_23_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][23][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248566_Dymy_Mirai_Nikki_23_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【23】【BIG5】【1024X576】【RMVB】")
    assertEquals("23..23", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248564_Dymy_Mirai_Nikki_22_BIG5_1280X720_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【22】【BIG5】【1280X720】【MKV】")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248551_Dymy_Mirai_Nikki_22_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【22】【BIG5】【1024X576】【RMVB】")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248480_TSDM_Mirai_Nikki_23_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][23][720p][简繁内挂][MKV]")
    assertEquals("23..23", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `248340_Mirai_Nikki_23_1280x720_MKV`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][23][1280x720][简体][MKV]")
    assertEquals("23..23", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248338_Mirai_Nikki_23_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★ [未來日記][Mirai Nikki][23][1024x576][繁體][RMVB]")
    assertEquals("23..23", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248337_Mirai_Nikki_23_1024x576_MP4`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][23][1024x576][简体][MP4]")
    assertEquals("23..23", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248322_Mirai_Nikk_23_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『23』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("23..23", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248319_Mirai_Nikki_23_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][23][GB][x264][1280X720][mp4]")
    assertEquals("23..23", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248318_Mirai_Nikki_23_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][23][GB][x264][848X480][mp4]")
    assertEquals("23..23", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248298_10_Mirai_Nikki_23_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 23【GB_RV10】【848X480】")
    assertEquals("23..23", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248262_10_23_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][23][848X480][简体][RMVB]")
    assertEquals("23..23", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248261_10_23_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][23][848X480][繁體][RMVB]")
    assertEquals("23..23", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248253_SGS_23_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第23話 BIG5繁體 480P MP4")
    assertEquals("23..23", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248252_SGS_23_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第23話 BIG5繁體 720P MP4")
    assertEquals("23..23", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248231_Mirai_Nikki_23_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][23][BIG5繁體][720P][MP4]")
    assertEquals("23..23", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248225_Mirai_Nikki_23_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][23][BIG5繁體][1024X576][RMVB]")
    assertEquals("23..23", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248218_Mirai_Nikki_23_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][23][BIG5繁體][848X480][RMVB]")
    assertEquals("23..23", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `248061_BBA_18_GB_1024x576_MP4_115`() {
    val r = parse("【BBA字幕组】【未来日记】[18][GB][1024x576][MP4]附115&招募中")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `247945_TSDM_Mirai_Nikki_22_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][22][720p][简繁内挂][MKV]")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `247779_DHR_Sakura_Cafe_Mirai_nikki_22_848_480`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][22][848 × 480][繁體](請組內人員進來看一下)")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `247776_TSDM_Mirai_Nikki_21_22_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][21-22][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `247775_TSDM_Mirai_Nikki_21_22_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][21-22][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `247767_Mirai_Nikki_22_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][22][BIG5_GB繁簡][720P][MKV]")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `247728_10_22_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][22][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `247391_Mirai_Nikki_22_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][22][GB][x264][1280X720][mp4]")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `247390_Mirai_Nikki_22_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][22][GB][x264][848X480][mp4]")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `247348_Mirai_Nikki_22_1024x576_MP4`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][22][1024x576][简体][MP4]")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `247347_Mirai_Nikki_22_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★ [未來日記][Mirai Nikki][22][1024x576][繁體][RMVB]")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `247313_Mirai_Nikk_22_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『22』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `247276_10_22_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][22][848X480][简体][RMVB]")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `247275_10_22_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][22][848X480][繁體][RMVB]")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `247267_SGS_22_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第22話 BIG5繁體 480P MP4")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `247266_SGS_22_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第22話 BIG5繁體 720P MP4")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `247252_Mirai_Nikki_22_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][22][BIG5繁體][720P][MP4]")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `247250_Mirai_Nikki_22_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][22][BIG5繁體][1024X576][RMVB]")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `247247_Mirai_Nikki_22_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][22][BIG5繁體][848X480][RMVB]")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `247237_10_Mirai_Nikki_22_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 22【GB_RV10】【848X480】")
    assertEquals("22..22", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `246822_Mirai_Nikki_21_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][21][BIG5_GB繁簡][720P][MKV]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `246803_DHR_Sakura_Cafe_Mirai_nikki_21_848_480`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][21][848 × 480][繁體](人員招募中～)")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `246792_10_21_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][21][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `246771_10_Mirai_Nikki_11_13_GB_MP4`() {
    val r = parse("【华盟字幕社】[10月新番][Mirai_Nikki][未来日记][11-13][GB][MP4]")
    assertEquals("11..13", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `246647_TSDM_Mirai_Nikki_21_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][21][720p][简繁内挂][MKV]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `246526_Mirai_Nikki_21_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][21][GB][x264][848X480][mp4]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `246525_Mirai_Nikki_21_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][21][GB][x264][1280X720][mp4]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `246524_Mirai_Nikki_20_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][20][GB][x264][1280X720][mp4]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `246476_Dymy_Mirai_Nikki_21_BIG5_1280X720_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【21】【BIG5】【1280X720】【MKV】")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `246472_Dymy_Mirai_Nikki_21_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【21】【BIG5】【1024X576】【RMVB】")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `246452_Mirai_Nikki_21_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★ [未來日記][Mirai Nikki][21][1024x576][繁體][RMVB]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `246451_Mirai_Nikki_21_1024x576_MP4`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][21][1024x576][简体][MP4]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `246450_Mirai_Nikki_21_1280x720_MKV`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][21][1280x720][简体][MKV]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `246421_Mirai_Nikk_21_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『21』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `246380_SGS_21_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第21話 BIG5繁體 480P MP4")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `246379_SGS_21_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第21話 BIG5繁體 720P MP4")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `246374_Mirai_Nikki_21_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][21][BIG5繁體][1024X576][RMVB]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `246373_10_21_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][21][848X480][繁體][RMVB]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `246372_10_21_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][21][848X480][简体][RMVB]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `246369_Mirai_Nikki_21_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][21][BIG5繁體][720P][MP4]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `246367_Mirai_Nikki_21_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][21][BIG5繁體][848X480][RMVB]")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `246363_10_Mirai_Nikki_21_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 21【GB_RV10】【848X480】")
    assertEquals("21..21", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `245315_w_BBA_17_GB_1024x576_MP4_115`() {
    val r = parse("内有重要通报=w=【BBA字幕组】【未来日记】[17][GB][1024x576][MP4]附115&招募中")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `245162_10_20_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][20][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `245136_Mirai_Nikki_20_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][20][BIG5_GB繁簡][720P][MKV]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `244908_10_Mirai_Nikki_19_20_GB_MP4`() {
    val r = parse("【华盟字幕社】[10月新番][Mirai_Nikki][未来日记][19-20][GB][MP4]")
    assertEquals("19..20", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `244874_DHR_Sakura_Cafe_Mirai_nikki_20_848_480`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][20][848 × 480][繁體](人員招募中～)")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `244720_Mirai_Nikki_20_1280x720_MKV`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][20][1280x720][简体][MKV]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `244598_Mirai_Nikk_20_1280x720_AAC`() {
    val r = parse("[萌月字幕组][未来日记][Mirai Nikk][20][1280x720 AAC]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `244575_Dymy_Mirai_Nikki_20_BIG5_1280X720_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【20】【BIG5】【1280X720】【MKV】")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `244564_Mirai_Nikki_20_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★ [未來日記][Mirai Nikki][20][1024x576][繁體][RMVB]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `244558_Dymy_Mirai_Nikki_20_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【20】【BIG5】【1024X576】【RMVB】")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `244557_Mirai_Nikki_20_1024x576_MP4`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][20][1024x576][简体][MP4]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `244548_SGS_20_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第20話 BIG5繁體 480P MP4")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `244547_SGS_20_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第20話 BIG5繁體 720P MP4 高清推廣")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `244502_TSDM_Mirai_Nikki_20_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][20][720p][简繁内挂][MKV]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `244479_TSDM_Mirai_Nikki_20_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][20][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `244478_TSDM_Mirai_Nikki_20_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][20][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `244477_Mirai_Nikki_20_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][20][BIG5繁體][720P][MP4]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `244476_10_20_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][20][848X480][繁體][RMVB]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `244475_10_20_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][20][848X480][简体][RMVB]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `244474_Mirai_Nikki_20_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][20][BIG5繁體][1024X576][RMVB]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `244472_Mirai_Nikki_20_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][20][BIG5繁體][848X480][RMVB]")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `244467_10_Mirai_Nikki_20_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 20【GB_RV10】【848X480】")
    assertEquals("20..20", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `244424_Mirai_Nikki_19_720P_MKV`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][19][720P][繁简外挂][MKV]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `244151_BBA_16_GB_1024x576_MP4_115`() {
    val r = parse("【BBA字幕组】【未来日记】[16][GB][1024x576][MP4]附115&招募中")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `244129_10_19_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][19][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `244038_Mirai_Nikki_19_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][19][BIG5_GB繁簡][720P][MKV]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `243918_Mirai_Nikki_19_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][19][BIG5繁體][720P][MP4]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `243917_Mirai_Nikki_19_BIG5_480P_576P_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][19][BIG5繁體][480P&576P][RMVB]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `243876_DHR_Sakura_Cafe_Mirai_nikki_19_848_480`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][19][848 × 480][繁體](人員招募中～)")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `243727_Mirai_Nikki_19_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][19][GB][x264][848X480][mp4]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `243724_Mirai_Nikki_19_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][19][GB][x264][1280X720][mp4]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `243642_Mirai_Nikk_19_1280x720_AAC`() {
    val r = parse("[萌月字幕组][未来日记][Mirai Nikk][19][1280x720 AAC]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `243626_Dymy_Mirai_Nikki_19_BIG5_1280X720_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【19】【BIG5】【1280X720】【MKV】")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `243617_Mirai_Nikki_19_1280x720_MKV`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][19][1280x720][简体][MKV]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `243612_Mirai_Nikki_19_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★ [未來日記][Mirai Nikki][19][1024x576][繁體][RMVB]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `243611_Mirai_Nikki_19_1024x576_MP4`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][19][1024x576][简体][MP4]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `243609_Dymy_Mirai_Nikki_19_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【19】【BIG5】【1024X576】【RMVB】")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `243598_SGS_19_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第19話 BIG5繁體 480P MP4")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `243579_TSDM_Mirai_Nikki_19_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][19][720p][简繁内挂][MKV]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `243575_TSDM_Mirai_Nikki_19_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][19][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `243572_SGS_19_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第19話 BIG5繁體 720P MP4 高清推廣")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `243568_10_19_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][19][848X480][繁體][RMVB]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `243567_10_19_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][19][848X480][简体][RMVB]")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `243566_10_Mirai_Nikki_19_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 19【GB_RV10】【848X480】")
    assertEquals("19..19", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `243269_10_Mirai_Nikki_15_18_GB_MP4`() {
    val r = parse("【华盟字幕社】[10月新番][Mirai_Nikki][未来日记][15-18][GB][MP4]")
    assertEquals("15..18", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `243217_DHR_Sakura_Cafe_Mirai_nikki_18_848_480`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][18][848 × 480][繁體]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `243156_Mirai_Nikki_18_720P_MKV`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][18][720P][繁简外挂][MKV]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `243147_10_18_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][18][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `243112_Mirai_Nikki_18_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][18][BIG5_GB繁簡][720P][MKV]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `243098_Mirai_Nikk_18_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『18』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242872_10_17_1280X720_MKV`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][17][1280X720][繁體][MKV]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242801_Mirai_Nikki_18_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][18][GB][x264][1280X720][mp4]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242800_Mirai_Nikki_18_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][18][GB][x264][848X480][mp4]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242772_Dymy_Mirai_Nikki_18_BIG5_1280X720_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【18】【BIG5】【1280X720】【MKV】")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242751_Mirai_Nikki_18_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★ [未來日記][Mirai Nikki][18][1024x576][繁體][RMVB]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242750_Mirai_Nikki_18_1024x576_MP4`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][18][1024x576][简体][MP4]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242744_Mirai_Nikki_18_1280x720_MKV`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][18][1280x720][简体][MKV]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242717_Dymy_Mirai_Nikki_18_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【18】【BIG5】【1024X576】【RMVB】")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242702_SGS_18_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第18話 BIG5繁體 480P MP4")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242701_SGS_18_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第18話 BIG5繁體 720P MP4 高清推廣")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242697_TSDM_Mirai_Nikki_18_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][18][720p][简繁内挂][MKV]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `242692_Mirai_Nikki_18_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][18][BIG5繁體][720P][MP4]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242690_10_18_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][18][848X480][繁體][RMVB]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242689_10_18_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][18][848X480][简体][RMVB]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242688_Mirai_Nikki_18_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][18][BIG5繁體][1024X576][RMVB]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242687_TSDM_Mirai_Nikki_18_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][18][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242686_Mirai_Nikki_18_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][18][BIG5繁體][848X480][RMVB]")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242683_TSDM_Mirai_Nikki_18_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][18][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242675_10_Mirai_Nikki_18_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 18【GB_RV10】【848X480】")
    assertEquals("18..18", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242468_BBA_15_GB_1024x576_MP4_115`() {
    val r = parse("内详+附特效【BBA字幕组】【未来日记】[15][GB][1024x576][MP4]附115&招募中")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242348_Mirai_Nikki_17_720P_MKV`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][17][720P][繁简外挂][MKV]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `242266_10_17_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][17][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242235_Mirai_Nikki_17_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][17][BIG5_GB繁簡][720P][MKV]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `242233_Drei_Raws_Mirai_Nikki_Vol_1_Vol_2_BDRip_1920x1080_AVC_Hi10p_FLACx2`() {
    val r = parse("[Drei-Raws][未来日记_Mirai_Nikki][Vol.1-Vol.2][BDRip][1920x1080][AVC_Hi10p_FLACx2]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `242163_10_14_16_1280X720_MKV`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][14-16][1280X720][繁體][MKV]")
    assertEquals("14..16", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241996_DHR_Sakura_Cafe_Mirai_nikki_17_848_480`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][17][848 × 480][繁體]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241956_Mirai_Nikki_17_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][17][GB][x264][1280X720][mp4]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241955_Mirai_Nikki_17_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][17][GB][x264][848X480][mp4]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241914_Dymy_Mirai_Nikki_17_BIG5_1280X720_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【17】【BIG5】【1280X720】【MKV】")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241874_Mirai_Nikk_17_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『17』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241810_Dymy_Mirai_Nikki_17_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【17】【BIG5】【1024X576】【RMVB】")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241763_Mirai_Nikki_17_1024x576_MP4`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][17][1024x576][简体][MP4]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241758_Mirai_Nikki_17_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★ [未來日記][Mirai Nikki][17][1024x576][繁體][RMVB]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241753_Mirai_Nikki_17_1280x720_MKV`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][17][1280x720][简体][MKV]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241728_TSDM_Mirai_Nikki_17_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][17][720p][简繁内挂][MKV]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `241727_10_17_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][17][848X480][繁體][RMVB]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241726_10_17_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][17][848X480][简体][RMVB]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241704_SGS_17_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第17話 BIG5繁體 480P MP4")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241703_SGS_17_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第17話 BIG5繁體 720P MP4 高清推廣")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241698_TSDM_Mirai_Nikki_17_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][17][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241693_Mirai_Nikki_17_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][17][BIG5繁體][720P][MP4]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241689_TSDM_Mirai_Nikki_17_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][17][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241686_Mirai_Nikki_17_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][17][BIG5繁體][1024X576][RMVB]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241684_Mirai_Nikki_17_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][17][BIG5繁體][848X480][RMVB]")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241682_10_Mirai_Nikki_17_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 17【GB_RV10】【848X480】")
    assertEquals("17..17", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241453_10_16_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][16][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241257_Mirai_Nikki_16_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][16][BIG5_GB繁簡][720P][MKV]")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `241235_Mirai_Nikki_16_720P_MKV`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][16][720P][繁简外挂][MKV]")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `241219_Mirai_Nikki_15_720P_MKV`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][15][720P][繁简外挂][MKV]")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `241063_Dymy_Mirai_Nikki_16_BIG5_1280X720_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【16】【BIG5】【1280X720】【MKV】")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `241042_Mirai_Nikk_16_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『16』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240938_DHR_Sakura_Cafe_Mirai_nikki_16_848_480_OP2`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][16][848 × 480][繁體](附上OP2的歌詞解析)")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240869_Dymy_Mirai_Nikki_16_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【16】【BIG5】【1024X576】【RMVB】")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240868_Mirai_Nikki_16_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][16][GB][x264][848X480][mp4]")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240867_Mirai_Nikki_16_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][16][GB][x264][1280X720][mp4]")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240819_TSDM_Mirai_Nikki_16_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][16][720p][简繁内挂][MKV]")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `240815_10_16_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][16][848X480][简体][RMVB]")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240814_10_16_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][16][848X480][繁體][RMVB]")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240799_Mirai_Nikki_16_1024x576_MP4`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][16][1024x576][简体][MP4]")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240795_Mirai_Nikki_16_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★ [未來日記][Mirai Nikki][16][1024x576][繁體][RMVB]")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240792_Mirai_Nikki_16_1280x720_MKV`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][16][1280x720][简体][MKV]")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240776_TSDM_Mirai_Nikki_16_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][16][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240773_TSDM_Mirai_Nikki_16_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][16][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240770_SGS_16_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第16話 BIG5繁體 480P MP4")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240769_Mirai_Nikki_16_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][16][BIG5繁體][720P][MP4]")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240768_SGS_16_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第16話 BIG5繁體 720P MP4 高清推廣")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240767_Mirai_Nikki_16_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][16][BIG5繁體][1024X576][RMVB]")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240766_Mirai_Nikki_16_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][16][BIG5繁體][848X480][RMVB]")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240762_10_Mirai_Nikki_16_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 16【GB_RV10】【848X480】")
    assertEquals("16..16", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240724_Mirai_Nikki_15_BIG5_480P_576P_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][15][BIG5繁體][480P&576P][RMVB]")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240723_Mirai_Nikki_15_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][15][BIG5繁體][720P][MP4]")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240722_Mirai_Nikki_14_15_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][14&15][BIG5_GB繁簡][720P][MKV]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `240695_10_15_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][15][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240436_Mirai_Nikki_15_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][15][GB][x264][848X480][mp4]")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240434_Mirai_Nikki_15_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][15][GB][x264][1280X720][mp4]")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240216_Dymy_Mirai_Nikki_15_BIG5_720P_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【15】【BIG5】【720P】【MKV】")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240205_DHR_Sakura_Cafe_Mirai_nikki_15_848_480_OP2`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][15][848 × 480][繁體](補上OP2的歌詞)")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240202_Dymy_Mirai_Nikki_15_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【15】【BIG5】【1024X576】【RMVB】")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240176_TSDM_Mirai_Nikki_15_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][15][720p][简繁内挂][MKV]")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `240155_TSDM_Mirai_Nikki_15_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][15][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240154_TSDM_Mirai_Nikki_15_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][15][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240086_Mirai_Nikk_15_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『15』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `240007_10_Mirai_Nikki_15_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 15【GB_RV10】【848X480】")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239975_Mirai_Nikki_15_1024x576_MP4`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][15][1024x576][简体][MP4]")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239974_Mirai_Nikki_15_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★ [未來日記][Mirai Nikki][15][1024x576][繁體][RMVB]")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239972_Mirai_Nikki_15_1280x720_MKV`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][15][1280x720][简体][MKV]")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239960_SGS_15_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第15話 BIG5繁體 480P MP4")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239959_SGS_15_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第15話 BIG5繁體 720P MP4 高清推廣")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239938_10_15_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][15][848X480][简体][RMVB]")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239937_10_15_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][15][848X480][繁體][RMVB]")
    assertEquals("15..15", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239769_Mirai_Nikki_13_720P_MKV`() {
    val r = parse("轉[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][13][720P][繁简外挂][MKV]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `239755_Mirai_Nikki_14_720P_MKV`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][14][720P][繁简外挂][MKV]")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `239529_10_14_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][14][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239471_BBA_14_GB_1024x576_MP4_115`() {
    val r = parse("【BBA字幕组】【未来日记】[14][GB][1024x576][MP4]附115&招募中")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239383_DHR_Sakura_Cafe_Mirai_nikki_14_848_480_OP2_TV_SIZE`() {
    val r =
        parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][14][848 × 480][繁體](附OP2的TV-SIZE 內詳)")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239299_10_Mirai_Nikki_14_GB_MP4`() {
    val r = parse("【华盟字幕社】[10月新番][Mirai_Nikki][未来日记][14][GB][MP4]")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239185_Mirai_Nikk_14_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『14』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239169_Mirai_Nikki_14_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][14][GB][x264][1280X720][mp4]")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239168_Mirai_Nikki_14_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][14][GB][x264][848X480][mp4]")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239108_Dymy_Mirai_Nikki_14_BIG5_720P_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【14】【BIG5】【720P】【MKV】")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239096_Dymy_Mirai_Nikki_14_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【14】【BIG5】【1024X576】【RMVB】")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239095_10_Mirai_Nikki_14_1280x720_MKV`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][14][1280x720][简体][MKV]")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239091_10_Mirai_Nikki_14_1024x576_MP4`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][14][1024x576][简体][MP4]")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239079_10_Mirai_Nikki_14_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★【10月新番】[未來日記][Mirai Nikki][14][1024x576][繁體][RMVB]")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239049_TSDM_Mirai_Nikki_14_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][14][720p][简繁内挂][MKV]")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `239045_Mirai_Nikki_14_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][14][BIG5繁體][720P][MP4]")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239040_Mirai_Nikki_14_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][14][BIG5繁體][848X480][RMVB]")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239039_Mirai_Nikki_14_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][14][BIG5繁體][1024X576][RMVB]")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239005_TSDM_Mirai_Nikki_14_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][14][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239004_TSDM_Mirai_Nikki_14_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][14][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239002_SGS_14_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第14話 BIG5繁體 480P MP4")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `239001_SGS_14_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第14話 BIG5繁體 720P MP4 高清推廣")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238996_10_14_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][14][848X480][简体][RMVB]")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238995_10_14_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][14][848X480][繁體][RMVB]")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238993_10_Mirai_Nikki_14_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 14【GB_RV10】【848X480】")
    assertEquals("14..14", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238964_10_12_13_1280X720_MKV`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][12-13][1280X720][繁體][MKV]")
    assertEquals("12..13", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238662_Mirai_Nikki_13_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][13][BIG5_GB繁簡][720P][MKV]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `238649_10_13_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][13][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238633_BBA_13_GB_1024x576_MP4`() {
    val r = parse("【BBA字幕组】【未来日记】[13][GB][1024x576][MP4]附115&招募中")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238577_Dymy_Mirai_Nikki_13_BIG5_720P_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【13】【BIG5】【720P】【MKV】")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238569_Dymy_Mirai_Nikki_13_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【13】【BIG5】【1024X576】【RMVB】")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238385_Mirai_Nikki_13_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][13][GB][x264][848X480][mp4]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238382_Mirai_Nikki_13_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][13][GB][x264][1280X720][mp4]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238360_Mirai_Nikk_13_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『13』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238344_Mirai_Nikki_13_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][13][BIG5繁體][848X480][RMVB]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238343_Mirai_Nikki_13_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][13][BIG5繁體][1024X576][RMVB]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238342_Mirai_Nikki_13_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][13][BIG5繁體][720P][MP4]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238324_DHR_Sakura_Cafe_Mirai_nikki_13_848_480`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][13][848 × 480][繁體]（人員招募中）")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238292_10_Mirai_Nikki_13_1024x576_MP4`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][13][1024x576][简体][MP4]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238289_10_Mirai_Nikki_13_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★【10月新番】[未來日記][Mirai Nikki][13][1024x576][繁體][RMVB]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238283_10_Mirai_Nikki_13_1280x720_MKV`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][13][1280x720][简体][MKV]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238266_TSDM_Mirai_Nikki_13_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][13][720p][简繁内挂][MKV]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `238238_TSDM_Mirai_Nikki_13_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][13][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238235_TSDM_Mirai_Nikki_13_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][13][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238230_10_13_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][13][848X480][简体][RMVB]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238228_SGS_13_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第13話 BIG5繁體 480P MP4")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238227_SGS_13_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第13話 BIG5繁體 720P MP4 高清推廣")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238226_10_13_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][13][848X480][繁體][RMVB]")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `238223_10_Mirai_Nikki_13_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 13【GB_RV10】【848X480】")
    assertEquals("13..13", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `237726_Dymy_ANK_Mirai_Nikki_BDrip_1080P_Vol_1_Hi10p`() {
    val r = parse("【Dymy字幕組&ANK壓片組】[Mirai_Nikki_未來日記][BDrip][1080P][Vol.1][Hi10p][簡繁外掛]")
    assertEquals("S?", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `237593_Mirai_Nikki_01_12_1024x576_MP4`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][01-12][1024x576][简体合集][MP4][招募压制]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `237591_Mirai_Nikki_01_12_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★ [未來日記][Mirai Nikki][01-12][1024x576][繁體合集][RMVB][招募压制]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `237589_Mirai_Nikki_01_12_1280x720_MKV`() {
    val r = parse("【异域字幕组】★ [未来日记][Mirai Nikki][01-12][1280x720][简体][MKV合集][招募压制]")
    assertEquals("01..12", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `237511_BBA_12_GB_1024x576_MP4`() {
    val r = parse("【BBA字幕组】【未来日记】[12][GB][1024x576][MP4]附115&招募中")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `237401_10_12_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][12][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `237366_Mirai_Nikki_12_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][12][BIG5_GB繁簡][720P][MKV]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `237282_Mirai_Nikki_12_720P_MKV`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][12][720P][繁简外挂][MKV]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `237204_Mirai_Nikki_12_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][12][GB][x264][848X480][mp4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `237203_Mirai_Nikki_12_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][12][GB][x264][1280X720][mp4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `237093_DHR_Sakura_Cafe_Mirai_nikki_12_848_480`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][12][848 × 480][繁體]（人員招募中）")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `237089_Mirai_Nikki_11_720P_MKV`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][11][720P][繁简外挂][MKV]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `237058_10_Mirai_Nikki_12_1024x576_MP4`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][12][1024x576][简体][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `237054_10_Mirai_Nikki_12_1280x720_MKV`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][12][1280x720][简体][MKV]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `237050_10_Mirai_Nikki_12_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★【10月新番】[未來日記][Mirai Nikki][12][1024x576][繁體][RMVB]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `237028_Dymy_Mirai_Nikki_12_BIG5_720P_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【12】【BIG5】【720P】【MKV】")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `237024_Dymy_Mirai_Nikki_12_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【12】【BIG5】【1024X576】【RMVB】")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `237012_SGS_12_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第12話 BIG5繁體 480P MP4")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `237011_TSDM_Mirai_Nikki_12_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][12][720p][简繁内挂][MKV]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `237003_SGS_12_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第12話 BIG5繁體 720P MP4 高清推廣")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236984_TSDM_Mirai_Nikki_12_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][12][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236983_10_Mirai_Nikki_12_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 12【GB_RV10】【848X480】")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236982_TSDM_Mirai_Nikki_12_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][12][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236979_Mirai_Nikki_12_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][12][BIG5繁體][1024X576][RMVB]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236978_10_12_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][12][848X480][繁體][RMVB]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236977_10_12_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][12][848X480][简体][RMVB]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236976_Mirai_Nikki_12_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][12][BIG5繁體][720P][MP4]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236971_Mirai_Nikki_12_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][12][BIG5繁體][848X480][RMVB]")
    assertEquals("12..12", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236779_10_11_1280X720_MKV`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][11][1280X720][繁體][MKV]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236736_BBA_11_GB_1024x576_MP4`() {
    val r = parse("【BBA字幕组】【未来日记】[11][GB][1024x576][MP4]附115&招募中")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236556_Mirai_Nikki_11_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][11][BIG5_GB繁簡][720P][MKV]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `236524_10_11_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][11][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236267_Mirai_Nikk_11_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『11』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236250_Mirai_Nikki_11_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][11][GB][x264][1280X720][mp4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236249_Mirai_Nikki_11_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][11][GB][x264][848X480][mp4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236248_DHR_Sakura_Cafe_Mirai_nikki_11_848_480`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][11][848 × 480][繁體]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236186_Dymy_Mirai_Nikki_11_BIG5_720P_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【11】【BIG5】【720P】【MKV】")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236181_Dymy_Mirai_Nikki_11_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【11】【BIG5】【1024X576】【RMVB】")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236177_TSDM_Mirai_Nikki_11_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][11][720p][简繁内挂][MKV]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `236176_TSDM_Mirai_Nikki_11_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][11][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236175_TSDM_Mirai_Nikki_11_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][11][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236169_SGS_11_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第11話 BIG5繁體 480P MP4")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236167_SGS_11_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第11話 BIG5繁體 720P MP4 高清推廣")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236162_10_Mirai_Nikki_11_1024x576_MP4`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][11][1024x576][简体][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236157_10_Mirai_Nikki_11_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★【10月新番】[未來日記][Mirai Nikki][11][1024x576][繁體][RMVB]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236155_10_Mirai_Nikki_11_1280x720_MKV`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][11][1280x720][简体][MKV]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236142_Mirai_Nikki_11_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][11][BIG5繁體][1024X576][RMVB]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236140_Mirai_Nikki_11_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][11][BIG5繁體][720P][MP4]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236139_10_11_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][11][848X480][繁體][RMVB]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236138_10_11_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][11][848X480][简体][RMVB]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236135_Mirai_Nikki_11_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][11][BIG5繁體][848X480][RMVB]")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236134_10_Mirai_Nikki_11_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 11【GB_RV10】【848X480】")
    assertEquals("11..11", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `236083_10_10_1280X720_MKV`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][10][1280X720][繁體][MKV]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235856_BBA_10_GB_1024x576_MP4_115`() {
    val r = parse("【BBA字幕组】【未来日记】[10][GB][1024x576][MP4]附115&招募中")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235765_Mirai_Nikki_10_720P_MKV`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][10][720P][繁简外挂][MKV]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `235716_Mirai_Nikki_10_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][10][BIG5_GB繁簡][720P][MKV]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `235681_10_Mirai_Nikki_10_GB_MP4`() {
    val r = parse("【华盟字幕社】[10月新番][Mirai_Nikki][未来日记][10][GB][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235634_10_10_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][10][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235450_Mirai_Nikki_10_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][10][GB][x264][848X480][mp4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235447_Mirai_Nikki_10_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][10][GB][x264][1280X720][mp4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235429_DHR_Sakura_Cafe_Mirai_nikki_10_848_480`() {
    val r =
        parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][10][848 × 480][繁體]（未來日記 之 我的九姊哪有這麼萌！）")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235420_Dymy_Mirai_Nikki_10_BIG5_720P_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【10】【BIG5】【720P】【MKV】")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235411_10_Mirai_Nikki_10v2_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 10v2【GB_RV10】【848X480】")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235408_Dymy_Mirai_Nikki_10_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【10】【BIG5】【1024X576】【RMVB】")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235407_Mirai_Nikk_10_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『10』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235361_TSDM_Mirai_Nikki_10_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][10][720p][简繁内挂][MKV]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `235359_TSDM_Mirai_Nikki_10_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][10][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235349_SGS_10_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第10話 BIG5繁體 480P MP4")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235348_SGS_10_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第10話 BIG5繁體 720P MP4 高清推廣")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235347_Mirai_Nikki_10_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][10][BIG5繁體][720P][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235346_10_Mirai_Nikki_10_1024x576_MP4`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][10][1024x576][简体][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235343_10_Mirai_Nikki_10_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★【10月新番】[未來日記][Mirai Nikki][10][1024x576][繁體][RMVB]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235342_Mirai_Nikki_10_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][10][BIG5繁體][1024X576][RMVB]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235341_10_Mirai_Nikki_10_1280x720_MKV`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][10][1280x720][简体][MKV]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235340_Mirai_Nikki_10_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][10][BIG5繁體][848X480][RMVB]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235336_TSDM_Mirai_Nikki_10_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][10][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235334_10_10_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][10][848X480][简体][RMVB]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235329_10_10_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][10][848X480][繁體][RMVB]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235261_10_09_1280X720_MKV`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][09][1280X720][繁體][MKV]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `235063_BBA_09_GB_1024x576_MP4_115`() {
    val r = parse("【BBA字幕组】【未来日记】[09][GB][1024x576][MP4]附115&招募中")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234957_DHR_Sakura_Cafe_Mirai_nikki_08_Hi10P_1080P`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][08][Hi10P_1080P][繁體]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234953_Mirai_Nikki_09_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][09][BIG5_GB繁簡][720P][MKV]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `234911_DHR_Sakura_Cafe_Mirai_nikki_07_Hi10P_1080P`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][07][Hi10P_1080P][繁體]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234851_10_09_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][09][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234831_TSDM_Mirai_Nikki_09_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][09][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234825_TSDM_Mirai_Nikki_09_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][09][720p][简繁内挂][MKV]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `234818_TSDM_Mirai_Nikki_09_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][09][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234809_Mirai_Nikki_09_720P_MKV`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][09][720P][繁简外挂][MKV]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `234783_DHR_Sakura_Cafe_Mirai_nikki_09_848_480`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][09][848 × 480][繁體]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234726_Dymy_Mirai_Nikki_09_BIG5_720P_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【09】【BIG5】【720P】【MKV】")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234720_Dymy_Mirai_Nikki_08_BIG5_720P_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【08】【BIG5】【720P】【MKV】")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234719_Dymy_Mirai_Nikki_09_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【09】【BIG5】【1024X576】【RMVB】")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234697_10_Mirai_Nikki_08_09_GB_MP4`() {
    val r = parse("【华盟字幕社】[10月新番][Mirai_Nikki][未来日记][08-09][GB][MP4]")
    assertEquals("08..09", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234627_Mirai_Nikki_09_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][09][GB][x264][1280X720][mp4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234616_Mirai_Nikk_09_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『09』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234504_10_Mirai_Nikki_09_1024x576_MP4`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][09][1024x576][简体][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234502_10_Mirai_Nikki_09_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★【10月新番】[未來日記][Mirai Nikki][09][1024x576][繁體][RMVB]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234501_SGS_09_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第09話 BIG5繁體 480P MP4")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234500_10_Mirai_Nikki_09_1280x720_MKV`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][09][1280x720][简体][MKV]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234497_SGS_09_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第09話 BIG5繁體 720P MP4 高清推廣")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234496_Mirai_Nikki_09_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][09][BIG5繁體][1024X576][RMVB]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234494_Mirai_Nikki_09_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][09][BIG5繁體][720P][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234491_Mirai_Nikki_09_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][09][BIG5繁體][848X480][RMVB]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234487_10_09_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][09][848X480][繁體][RMVB]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234486_10_09_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][09][848X480][简体][RMVB]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234484_10_Mirai_Nikki_09_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 09【GB_RV10】【848X480】")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234249_Mirai_Nikki_08_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][08][BIG5_GB繁簡][720P][MKV]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `234237_BBA_08_GB_1024x576_MP4_115`() {
    val r = parse("【BBA字幕组】【未来日记】[08][GB][1024x576][MP4]附115&招募中")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234225_10_08_1280X720_MKV`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][08][1280X720][繁體][MKV]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `234129_Mirai_Nikki_08_720P_MKV`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][08][720P][繁简外挂][MKV]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `234026_10_08_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][08][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233835_DHR_Sakura_Cafe_Mirai_nikki_08_848_480_FB_G`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][08][848 × 480][繁體]（千夏成立FB和G+分部囉！）")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233760_Mirai_Nikki_08_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][08][GB][x264][1280X720][mp4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233759_Mirai_Nikki_08_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][08][GB][x264][848X480][mp4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233697_Mirai_Nikk_08_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『08』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233675_10_Mirai_Nikki_08_1024x576_MP4`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][08][1024x576][简体][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233674_Dymy_Mirai_Nikki_08_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【08】【BIG5】【1024X576】【RMVB】")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233672_10_Mirai_Nikki_08_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★【10月新番】[未來日記][Mirai Nikki][08][1024x576][繁體][RMVB]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233669_10_Mirai_Nikki_08_1280x720_MKV`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][08][1280x720][简体][MKV]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233644_TSDM_Mirai_Nikki_08_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][08][720p][简繁内挂][MKV]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `233606_SGS_08_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第08話 BIG5繁體 480P MP4")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233605_SGS_08_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第08話 BIG5繁體 720P MP4 高清推廣")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233601_TSDM_Mirai_Nikki_08_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][08][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233600_Mirai_Nikki_08_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][08][BIG5繁體][720P][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233599_TSDM_Mirai_Nikki_08_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][08][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233597_10_08_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][08][848X480][繁體][RMVB]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233596_10_08_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][08][848X480][简体][RMVB]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233595_10_Mirai_Nikki_08_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 08【GB_RV10】【848X480】")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233594_Mirai_Nikki_08_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][08][BIG5繁體][1024X576][RMVB]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233590_Mirai_Nikki_08_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][08][BIG5繁體][848X480][RMVB]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233378_10_06_07_1280X720_MKV`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][06-07][1280X720][繁體][MKV]")
    assertEquals("06..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233346_BBA_07_GB_1024x576_MP4_115`() {
    val r = parse("【BBA字幕组】【未来日记】[07][GB][1024x576][MP4]附115&招募中")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233224_Mirai_Nikki_07_720P_MKV`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][07][720P][繁简外挂][MKV]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `233203_Mirai_Nikki_07_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][07][BIG5_GB繁簡][720P][MKV]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `233120_10_07_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][07][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233090_10_Mirai_Nikki_07_GB_MP4`() {
    val r = parse("【华盟字幕社】[10月新番][Mirai_Nikki][未来日记][07][GB][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `233027_Mirai_Nikk_07_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『07』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232875_DHR_Sakura_Cafe_Mirai_nikki_07_848_480_FB_G`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][07][848 × 480][繁體]（千夏成立FB和G+分部囉！）")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232874_DHR_Sakura_Cafe_Mirai_nikki_06_Hi10P_1080P`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][06][Hi10P_1080P][繁體]附网盘链接")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232872_10_Mirai_Nikki_07_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 07【GB_RV10】【848X480】")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232850_10_Mirai_Nikki_07v2_1024x576_MP4`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][07v2][1024x576][简体][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232844_Mirai_Nikki_07_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][07][GB][x264][1280X720][mp4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232843_Mirai_Nikki_07_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][07][GB][x264][848X480][mp4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232838_Dymy_Mirai_Nikki_07_BIG5_720P_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【07】【BIG5】【720P】【MKV】")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232818_Dymy_Mirai_Nikki_07_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【07】【BIG5】【1024X576】【RMVB】")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232779_SGS_07_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第07話 BIG5繁體 480P MP4")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232770_SGS_07_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第07話 BIG5繁體 720P MP4 高清推廣")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232768_10_Mirai_Nikki_07_1024x576_MP4`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][07][1024x576][简体][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232763_TSDM_Mirai_Nikki_07_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][07][720p][简繁内挂][MKV]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `232762_Mirai_Nikki_07_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][07][BIG5繁體][720P][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232761_10_Mirai_Nikki_07_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★【10月新番】[未來日記][Mirai Nikki][07][1024x576][繁體][RMVB]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232758_Mirai_Nikki_07_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][07][BIG5繁體][1024X576][RMVB]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232756_TSDM_Mirai_Nikki_07_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][07][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232755_Mirai_Nikki_07_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][07][BIG5繁體][848X480][RMVB]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232753_10_07_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][07][848X480][繁體][RMVB]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232752_10_07_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][07][848X480][简体][RMVB]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232750_TSDM_Mirai_Nikki_07_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][07][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232479_10_06_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][06][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232432_BBA_06_GB_1024x576_MP4_115`() {
    val r = parse("【BBA字幕组】【未来日记】[06][GB][1024x576][MP4]附115&招募中")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232258_Mirai_Nikki_06_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][06][BIG5_GB繁簡][720P][MKV]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `232178_Mirai_Nikki_06_720P_MKV`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][06][720P][繁简外挂][MKV]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `232147_10_Mirai_Nikki_06_GB_MP4`() {
    val r = parse("【华盟字幕社】[10月新番][Mirai_Nikki][未来日记][06][GB][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `232043_DHR_Sakura_Cafe_Mirai_nikki_05_Hi10P_1080P`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][05][Hi10P_1080P][繁體]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231970_Mirai_Nikki_05_GB_x264_1280X720_MKV`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][05]][GB][x264][1280X720][MKV]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231964_Mirai_Nikki_06_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][06][BIG5繁體][720P][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231963_Mirai_Nikki_06_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][06[BIG5繁體][1024X576][RMVB]")
    assertEquals("null", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231962_Mirai_Nikki_06_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][06][BIG5繁體][848X480][RMVB]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231946_DHR_Sakura_Cafe_Mirai_nikki_06_848_480_FB_G`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][06][848 × 480][繁體]（千夏成立FB和G+分部囉！）")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231932_Dymy_Mirai_Nikki_06_BIG5_720P_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【06】【BIG5】【720P】【MKV】")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231928_Dymy_Mirai_Nikki_06_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【06】【BIG5】【1024X576】【RMVB】")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231927_10_Mirai_Nikki_06_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 06【GB_RV10】【848X480】")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231926_Mirai_Nikki_06_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][06]][GB][x264][1280X720][mp4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231911_Mirai_Nikk_06_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『06』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231903_Mirai_Nikki_06_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][06]][GB][x264][848X480][mp4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231850_10_Mirai_Nikki_06_1024x576_MP4`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][06][1024x576][简体][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231845_TSDM_Mirai_Nikki_06_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][06][720p][简繁内挂][MKV]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `231844_10_Mirai_Nikki_06_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★【10月新番】[未來日記][Mirai Nikki][06][1024x576][繁體][RMVB]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231838_10_Mirai_Nikki_06_1280x720_MKV`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][06][1280x720][简体][MKV]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231819_SGS_06_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第06話 BIG5繁體 480P MP4")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231812_SGS_06_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第06話 BIG5繁體 720P MP4 高清推廣")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231808_TSDM_Mirai_Nikki_06_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][06][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231807_TSDM_Mirai_Nikki_06_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][06][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231804_10_06_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][06][848X480][繁體][RMVB]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231802_10_06_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][06][848X480][简体][RMVB]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231500_BBA_05_GB_1024x576_MP4_115`() {
    val r = parse("【BBA字幕组】【未来日记】[05][GB][1024x576][MP4]附115&招募中")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231499_BBA_04_v2_GB_1024x576_MP4_115`() {
    val r = parse("【BBA字幕组】【未来日记】[04][v2][GB][1024x576][MP4]附115&招募中")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231497_BBA_03_v2_GB_1024x576_MP4_115`() {
    val r = parse("【BBA字幕组】【未来日记】[03][v2][GB][1024x576][MP4]附115&招募中")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231496_BBA_02_v2_GB_1024x576_MP4_115`() {
    val r = parse("【BBA字幕组】【未来日记】[02][v2][GB][1024x576][MP4]附115&招募中")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231495_BBA_01_v2_GB_1024x576_MP4_115`() {
    val r = parse("【BBA字幕组】【未来日记】[01][v2][GB][1024x576][MP4]附115&招募中")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231308_Mirai_Nikki_05_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][05][BIG5_GB繁簡][720P][MKV]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `231279_10_05_1280X720_MKV`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][05][1280X720][繁體][MKV]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231252_10_05_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][05][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `231148_Mirai_Nikki_05_720P_MKV`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][05][720P][繁简外挂][MKV]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `231115_DHR_Sakura_Cafe_Mirai_nikki_05_848_480`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][05][848 × 480][繁體]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230957_Mirai_Nikk_05_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『05』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230932_10_Mirai_Nikki_05_GB_MP4`() {
    val r = parse("【华盟字幕社】[10月新番][Mirai_Nikki][未来日记][05][GB][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230917_Mirai_Nikki_05_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][05]][GB][x264][1280X720][mp4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230912_Dymy_Mirai_Nikki_05_BIG5_720P_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【05】【BIG5】【720P】【MKV】")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230888_Mirai_Nikki_05_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][05]][GB][x264][848X480][mp4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230881_Mirai_Nikki_04_720P_MKV`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][04][720P][繁简外挂][MKV]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `230880_Dymy_Mirai_Nikki_05_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【05】【BIG5】【1024X576】【RMVB】")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230867_10_Mirai_Nikki_05_1024x576_MP4`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][05][1024x576][简体][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230862_10_Mirai_Nikki_05_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★【10月新番】[未來日記][Mirai Nikki][05][1024x576][繁體][RMVB]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230859_10_Mirai_Nikki_05_1280x720_MKV`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][05][1280x720][简体][MKV]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230853_TSDM_Mirai_Nikki_05_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][05][720p][简繁内挂][MKV]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `230830_SGS_05_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第05話 BIG5繁體 480P MP4")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230824_SGS_05_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第05話 BIG5繁體 720P MP4 高清推廣")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230821_Mirai_Nikki_05_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][05][BIG5繁體][720P][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230819_TSDM_Mirai_Nikki_05_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][05][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230818_TSDM_Mirai_Nikki_05_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][05][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230816_Mirai_Nikki_05_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][05][BIG5繁體][1024X576][RMVB]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230814_10_Mirai_Nikki_05_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 05【GB_RV10】【848X480】")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230813_10_05_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][05][848X480][简体][RMVB]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230812_10_05_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][05][848X480][繁體][RMVB]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230809_Mirai_Nikki_05_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][05][BIG5繁體][848X480][RMVB]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230526_Mirai_Nikki_04_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][04][BIG5_GB繁簡][720P][MKV]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `230391_DHR_Sakura_Cafe_Mirai_nikki_04_Hi10P_1080P`() {
    val r =
        parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][04][Hi10P_1080P][繁體]（附眾神與人名關連的另一種版本）")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230368_Mirai_Nikki_04_GB_x264_1280X720_MKV`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][04]][GB][x264][1280X720][MKV]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230223_10_04_1280X720_MKV`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][04][1280X720][繁體][MKV]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230218_10_04_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][04][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230145_DHR_Sakura_Cafe_Mirai_nikki_04_848_480`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][04][848 × 480][繁體]（內附OP開頭分析）")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `230046_TSDM_Mirai_Nikki_04v2_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][04v2][720p][简繁内挂][MKV][修正字體]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `229989_10_Mirai_Nikki_04_GB_RV10_848X480_V2`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 04【GB_RV10】【848X480】V2(修正16'10\"错误)")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229949_Mirai_Nikki_04_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][04]][GB][x264][1280X720][mp4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229948_Mirai_Nikki_04_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][04]][GB][x264][848X480][mp4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229944_Mirai_Nikk_04_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『04』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229918_10_Mirai_Nikki_04_GB_MP4`() {
    val r = parse("【华盟字幕社】[10月新番][Mirai_Nikki][未来日记][04][GB][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229874_TSDM_Mirai_Nikki_04_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][04][720p][简繁内挂][MKV]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `229850_Dymy_Mirai_Nikki_04_BIG5_720P_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【04】【BIG5】【720P】【MKV】")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229844_Dymy_Mirai_Nikki_OVA_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【OVA】【BIG5】【1024X576】【RMVB】")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229841_Dymy_Mirai_Nikki_04_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【04】【BIG5】【1024X576】【RMVB】")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229828_10_Mirai_Nikki_04_1024x576_MP4`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][04][1024x576][简体][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229826_10_Mirai_Nikki_04_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★【10月新番】[未來日記][Mirai Nikki][04][1024x576][繁體][RMVB]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229824_10_Mirai_Nikki_04_1280x720_MKV`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][04][1280x720][简体][MKV]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229811_10_04_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][04][848X480][繁體][RMVB]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229809_10_04_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][04][848X480][简体][RMVB]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229807_Mirai_Nikki_04_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][04][BIG5繁體][1024X576][RMVB]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229804_SGS_04_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第04話 BIG5繁體 480P MP4")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229800_SGS_04_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第04話 BIG5繁體 720P MP4 高清推廣")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229790_TSDM_Mirai_Nikki_04_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][04][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229789_Mirai_Nikki_04_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][04][BIG5繁體][720P][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229788_TSDM_Mirai_Nikki_04_480P_BIG5_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][04][480P][BIG5繁體][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229785_Mirai_Nikki_04_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][04][BIG5繁體][848X480][RMVB]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229519_Mirai_Nikki_03_720P_MKV`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][03][720P][繁简外挂][MKV]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `229509_Mirai_Nikki_03_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][03]][GB][x264][1280X720][mp4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229417_DHR_Sakura_Cafe_Mirai_nikki_03_Hi10P_1080P`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][03][Hi10P_1080P][繁體]（內詳）")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229393_Mirai_Nikki_03_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][03][BIG5_GB繁簡][720P][MKV]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `229232_10_03_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][03][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `229223_10_03_1280X720_MKV`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][03][1280X720][繁體][MKV]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228912_10_Mirai_Nikki_03_GB_MP4`() {
    val r = parse("【华盟字幕社】[10月新番][Mirai_Nikki][未来日记][03][GB][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228911_Dymy_Mirai_Nikki_03_BIG5_720P_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【03】【BIG5】【720P】【MKV】")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228910_Dymy_Mirai_Nikki_03_V2_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【03_V2】【BIG5】【1024X576】【RMVB】")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228908_DHR_Sakura_Cafe_Mirai_nikki_03_848_480`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][03][848 × 480][繁體]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228889_Mirai_Nikk_03_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『03』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228877_Mirai_Nikki_03_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][03]][GB][x264][848X480][mp4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228876_Mirai_Nikki_02_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][02]][GB][x264][1280X720][mp4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228858_Dymy_Mirai_Nikki_03_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【03】【BIG5】【1024X576】【RMVB】")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228834_TSDM_Mirai_Nikki_03_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][03][720p][简繁内挂][MKV]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `228827_10_Mirai_Nikki_03_1024x576_MP4`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][03][1024x576][简体][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228825_10_Mirai_Nikki_03_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★【10月新番】[未來日記][Mirai Nikki][03][1024x576][繁體][RMVB]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228823_10_Mirai_Nikki_03_1280x720_MKV`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][03][1280x720][简体][MKV]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228812_SGS_03_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第03話 BIG5繁體 480P MP4")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228811_SGS_03_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第03話 BIG5繁體 720P MP4 高清推廣")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228806_Mirai_Nikki_03_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][03][BIG5繁體][720P][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228800_10_Mirai_Nikki_03_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 03【GB_RV10】【848X480】")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228798_Mirai_Nikki_03_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][03][BIG5繁體][1024X576][RMVB]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228797_10_03_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][03][848X480][简体][RMVB]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228796_10_03_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][03][848X480][繁體][RMVB]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228794_Mirai_Nikki_03_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][03][BIG5繁體][848X480][RMVB]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228556_10_02_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][02][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228453_DHR_Sakura_Cafe_Mirai_nikki_02_Hi10P_1080P`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][02][Hi10P_1080P][繁體]（內詳）")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228312_Mirai_Nikki_02_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][02][BIG5_GB繁簡][720P][MKV]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `228283_10_02_1280X720_MKV`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][02][1280X720][繁體][MKV]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228241_Mirai_Nikki_02_720P_MKV`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][02][720P][繁简外挂][MKV]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `228028_10_Mirai_Nikki_02_GB_MP4`() {
    val r = parse("【华盟字幕社】[10月新番][Mirai_Nikki][未来日记][02][GB][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228025_DHR_Sakura_Cafe_Mirai_nikki_02_848_480`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][02][848 × 480][繁體]（內詳）")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228006_Mirai_Nikki_02_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][02]][GB][x264][848X480][mp4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `228005_Mirai_Nikki_01_GB_x264_1280X720_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][01]][GB][x264][1280X720][mp4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227968_Mirai_Nikk_02V2_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『02V2』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227933_DHR_Sakura_Cafe_Mirai_nikki_01_1080P`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][01][Hi10P_1080P][繁體]（內詳）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227923_Mirai_Nikk_02_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『02』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227842_Dymy_Mirai_Nikki_02_BIG5_720P_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【02】【BIG5】【720P】【MKV】")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227831_TSDM_Mirai_Nikki_01v2_02_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][01v2~02][720p][简繁内挂][MKV]")
    assertEquals("01..02", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `227830_Mirai_Nikki_02_BIG5_848X480_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][02][BIG5繁體][848X480][RMVB]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227827_Dymy_Mirai_Nikki_01_BIG5_720P_MKV`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【01】【BIG5】【720P】【MKV】")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227825_Dymy_Mirai_Nikki_02_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【02】【BIG5】【1024X576】【RMVB】")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227817_10_Mirai_Nikki_02_1280x720_MKV`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][02][1280x720][简体][MKV]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227816_10_Mirai_Nikki_02_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★【10月新番】[未來日記][Mirai Nikki][02][1024x576][繁體][RMVB]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227815_10_Mirai_Nikki_02_1024x576_MP4`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][02][1024x576][简体][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227799_Mirai_Nikki_02_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][02][BIG5繁體][720P][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227794_SGS_02_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第02話 BIG5繁體 480P MP4")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227793_SGS_02_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第02話 BIG5繁體 720P MP4 高清推廣")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227788_TSDM_Mirai_Nikki_02_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][02][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227784_Mirai_Nikki_02_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][02][BIG5繁體][1024X576][RMVB]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227783_10_02_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][02][848X480][简体][RMVB]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227782_10_02_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][02][848X480][繁體][RMVB]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227780_10_Mirai_Nikki_02_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 02【GB_RV10】【848X480】")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227362_BBA_OVA_GB_720x480_MP4`() {
    val r = parse("[BBA字幕组][未来日记][OVA][GB][720x480][MP4]")
    assertEquals("OVA..OVA", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227353_10_01_PSP_480P_MP4`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][01][PSP_480P][繁體][MP4]")
    assertEquals("PSP_480P..PSP_480P", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227308_Mirai_Nikki_01_BIG5_GB_720P_MKV`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][01][BIG5_GB繁簡][720P][MKV]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `227292_10_01_1280X720_MKV`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][01][1280X720][繁體][MKV]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `227128_Mirai_Nikki_01_720P_MKV`() {
    val r = parse("[雪酷字幕组&曙光社][Mirai_Nikki 未来日记][01][720P][繁简外挂][MKV]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `227014_10_Mirai_Nikki_01_GB_MP4`() {
    val r = parse("【华盟字幕社】[10月新番][Mirai_Nikki][未来日记][01][GB][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `226971_Mirai_Nikki_01_GB_x264_848X480_mp4`() {
    val r = parse("【悠哈璃羽字幕社】[Mirai Nikki\\未来日记][01]][GB][x264][848X480][mp4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `226956_Mirai_Nikk_01_720P_mkv`() {
    val r = parse("【萌月字幕组】[未来日记][Mirai Nikk]『01』[十月新番]★[720P]☆[简体][mkv]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `226922_DHR_Sakura_Cafe_Mirai_nikki_01_848_480`() {
    val r = parse("★御宅千夏&DHR動研&Sakura Cafe★ [未來日記][Mirai_nikki][01][848 × 480][繁體]（內詳）")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `226918_01_Mirai_Nikki___01_CTC_1280x720_x264_AAC_mp4_RAW`() {
    val r = parse("未来日记01 Mirai Nikki - 01 (CTC 1280x720 x264 AAC)mp4 无字幕 RAW")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `226844_Dymy_Mirai_Nikki_01_BIG5_1024X576_RMVB`() {
    val r = parse("【Dymy字幕組】【Mirai_Nikki_未來日記】【01】【BIG5】【1024X576】【RMVB】")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `226825_TSDM_Mirai_Nikki_01_480P_GB_PC_PSP_MP4`() {
    val r = parse("【TSDM字幕组】[未来日记][Mirai Nikki][01][480P][GB简体][PC&PSP兼容MP4]")
    assertEquals("PC&PSP兼容MP4..PC&PSP兼容MP4", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `226822_10_Mirai_Nikki_01_1024x576_RMVB`() {
    val r = parse("【異域字幕組】★【10月新番】[未來日記][Mirai Nikki][01][1024x576][繁體][RMVB]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `226821_10_Mirai_Nikki_01_1024x576_MP4`() {
    val r = parse("【异域字幕組】★【10月新番】[未来日记][Mirai Nikki][01][1024x576][简体][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `226820_10_Mirai_Nikki_01_1280x720_MKV`() {
    val r = parse("【异域字幕组】★【10月新番】[未来日记][Mirai Nikki][01][1280x720][简体][MKV]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `226818_Mirai_Nikki_01_BIG5_720P_MP4`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][01][BIG5繁體][720P][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `226814_TSDM_Mirai_Nikki_01_720p_MKV`() {
    val r = parse("【TSDM字幕組】[未来日记][Mirai Nikki][01][720p][简繁内挂][MKV]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `226797_Mirai_Nikki_01_BIG5_1024X576_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][01][BIG5繁體][1024X576][RMVB]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `226790_Mirai_Nikki_01_GB_RV10_848X480`() {
    val r = parse("【幻樱字幕组】【10月新番】未来日记 Mirai Nikki 01【GB_RV10】【848X480】")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `226785_01_BIG5_480P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第01話 BIG5繁體 480P MP4")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `226784_01_BIG5_720P_MP4`() {
    val r = parse("【雪酷字幕組&SGS曙光社】★十月新番【未來日記】第01話 BIG5繁體 720P MP4 高清推廣內詳")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("720P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `226780_Mirai_Nikki_01_BIG5_RMVB`() {
    val r = parse("[四魂製作組][Mirai Nikki 未來日記][01][BIG5繁體][848X480][RMVB]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `226777_10_01_848X480_RMVB`() {
    val r = parse("【动漫国字幕组】★10月新番[未来日记][01][848X480][简体][RMVB]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `226773_10_01_848X480_RMVB`() {
    val r = parse("【動漫國字幕組】★10月新番[未來日記][01][848X480][繁體][RMVB]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `226766_01_H264_MP4`() {
    val r = parse("未来日记【第01话】无字幕 H264【MP4】(無字幕+片源)")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `226235_Mirai_Nikki_OAD_MKV`() {
    val r = parse("【华盟字幕社】[Mirai_Nikki][未来日记][OAD][MKV][简繁外挂]")
    assertEquals("OAD..OAD", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.EXTERNAL_DISCOVER, r.subtitleKind)
  }

  @Test
  public fun `226220_10_Mirai_Nikki_OAD_GB_480p_MP4`() {
    val r = parse("【华盟字幕社】[Mirai_Nikki][未来日记][OAD][GB][480p_MP4]")
    assertEquals("OAD..OAD", r.episodeRange.toString())
    assertEquals("CHS, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `200485_Mirai_Nikki_OAD_DVDRIP_GB_BIG5_MKV`() {
    val r = parse("[四魂制作组][Mirai Nikki 未来日记][OAD][DVDRIP][GB_BIG5][MKV]")
    assertEquals("OAD..OAD", r.episodeRange.toString())
    assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
  }

  @Test
  public fun `192030_12_OAD_848x480_RMVB`() {
    val r = parse("【動漫國字幕組】★12月[未來日記][OAD][848x480][繁體][RMVB]")
    assertEquals("OAD..OAD", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("480P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }

  @Test
  public fun `191983_Mirai_Nikki_OAD_BIG5_RMVB`() {
    val r = parse("[四魂制作组][Mirai Nikki 未来日记][OAD][BIG5繁體][RMVB]")
    assertEquals("OAD..OAD", r.episodeRange.toString())
    assertEquals("CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("null", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
  }
}

// @formatter:on
