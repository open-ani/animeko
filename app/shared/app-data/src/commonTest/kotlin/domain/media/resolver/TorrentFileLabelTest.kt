/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import me.him188.ani.app.domain.media.resolver.TorrentFileLabel.Kind
import me.him188.ani.datasources.api.EpisodeSort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 样例全部取自 2026-09 对 nyaa 2008 至 2026 年整季包的核对 (3269 个种子, 129209 个文件名,
 * 615 个发布组).
 */
class TorrentFileLabelTest {
    private fun label(name: String) = TorrentFileLabel.of(name)

    @Test
    fun `main story files carry no label`() {
        assertNull(label("Steins;Gate 2011 S01E23-[1080p][BDRIP][x265.OPUS].mkv"))
        assertNull(label("[VCB-Studio] Bakuon!! [01][Ma10p_1080p][x265_flac].mkv"))
        assertNull(label("[DBD-Raws][Ultraman Omega][01][1080P][BDRip][HEVC-10bit][FLAC].mkv"))
        assertNull(label("[Moozzi2] Shigatsu wa Kimi no Uso - 22 END (BD 1920x1080 x265-10Bit FLAC_QAAC).mkv"))
        assertNull(label("[Beatrice-Raws] Tengen Toppa Gurren Lagann 01 [BDRip 1920x1080 HEVC FLAC].mkv"))
        assertNull(label("[UCCUSS] BOCCHI THE ROCK! ぼっち・ざ・ろっく! 第01話 「#01 転がるぼっち」 (BD 1920x1080p AVC FLAC).mkv"))
        assertNull(label("[SubsPlease] Oshi no Ko S3 - 01v2 (1080p) [2619E3AE].mkv"))
    }

    @Test
    fun `season zero and SP labels are SP with their number`() {
        assertEquals(TorrentFileLabel(Kind.SP, 2), label("specials/Mushoku Tensei Isekai Ittara Honki Dasu S00E02-[1080p][BDRIP][AV1.OPUS].mkv"))
        assertEquals(TorrentFileLabel(Kind.SP, 15), label("SP/[VCB-Studio]Ore No Imoto II[BDRip][Hi10p_1080p][x264_flac][SP15].mkv"))
        assertEquals(TorrentFileLabel(Kind.SP, 1), label("[VCB-Studio] Bakuon!! [SP01-2][Ma10p_1080p][x265_flac].mkv"))
        assertEquals(TorrentFileLabel(Kind.SP, null), label("[DBD-Raws][未来日记][SP][1080P][BDRip][HEVC-10bit][FLAC].mkv"))
        assertEquals(TorrentFileLabel(Kind.SP, 1), label("[桜都字幕组] 测试动画 - SP01 [1080p].mkv"))
    }

    @Test
    fun `OAD and OVA are SP`() {
        assertEquals(TorrentFileLabel(Kind.SP, 2), label("[VCB-Studio] Shingeki no Kyojin [OAD02(3.25)][Ma10p_1080p][x265_flac].mkv"))
        assertEquals(TorrentFileLabel(Kind.SP, 1), label("[VCB-Studio] Minami-ke Betsubara [OAD01][Ma10p_720p][x265_ac3].mkv"))
        assertEquals(Kind.SP, label("[Moozzi2] Shigatsu wa Kimi no Uso OVA - Moments (BD 1920x1080 x265-10Bit FLAC_QAAC).mkv")?.kind)
        assertEquals(TorrentFileLabel(Kind.SP, 3), label("[DBD-Raws][Ultraman Omega][Special Recap][03][1080P][BDRip][HEVC-10bit][FLAC].mkv"))
        assertEquals(TorrentFileLabel(Kind.SP, 6), label("特典映像/[DBD-Raws][Ultraman Omega][Tokuten][06][1080P].mkv"))
    }

    @Test
    fun `creditless openings and endings`() {
        assertEquals(TorrentFileLabel(Kind.OP, null), label("NCOP.mkv"))
        assertEquals(TorrentFileLabel(Kind.ED, 2), label("NCED2.mkv"))
        assertEquals(TorrentFileLabel(Kind.OP, 3), label("NCOP/[VCB-Studio]Ore No Imoto II[BDRip][Hi10p_1080p][x264_flac][NCOP03].mkv"))
        assertEquals(TorrentFileLabel(Kind.ED, 12), label("SPs/[VCB-Studio] Komi-san [NCED_EP12][Ma10p_1080p][x265_flac].mkv"))
        assertEquals(TorrentFileLabel(Kind.ED, 25), label("NCOP&NCED/[DBD-Raws][Ultraman Omega][NCED25][1080P].mkv"))
        assertEquals(TorrentFileLabel(Kind.ED, 1), label("SP/[ANK-Raws] Maria†Holic (Creditless ED_1) (BDrip 1920x1080 x264 FLAC).mkv"))
        // OPv3 的 v3 是版本号
        assertEquals(TorrentFileLabel(Kind.OP, null), label("NC/[Beatrice-Raws] Tengen Toppa Gurren Lagann (Creditless OPv3) [BDRip].mkv"))
    }

    @Test
    fun `an SP serial in front of a creditless label is not an SP`() {
        // jsum 与 Moozzi2 的 SP 序号只是花絮流水号, 类别看后面的词.
        assertEquals(Kind.OP, label("[Boogiepop wa Warawanai][Vol.01][SP01][NCOP][BDRIP][1080P][H264_FLAC].mkv")?.kind)
        assertEquals(Kind.PV, label("[Boogiepop wa Warawanai][Vol.02][SP01][PV #01][BDRIP][1080P][H264_FLAC].mkv")?.kind)
        assertEquals(Kind.OP, label("EXTRA/[Moozzi2] Shigatsu wa Kimi no Uso [SP01] NCOP - 02 (BD 1920x1080 x265-10Bit FLAC_QAAC).mkv")?.kind)
        assertEquals(Kind.OTHER, label("EXTRA/[Moozzi2] Shigatsu wa Kimi no Uso [SP00] Menu - 01 (BD 1920x1080 x265-10Bit FLAC_QAAC).mkv")?.kind)
    }

    @Test
    fun `previews and menus`() {
        assertEquals(TorrentFileLabel(Kind.PV, 8), label("PV/[DBD-Raws][Ultraman Omega][PV][08][1080P].mkv"))
        assertEquals(TorrentFileLabel(Kind.PV, 2), label("SPs/[Nekomoe kissaten&VCB-Studio] Sono Bisque Doll [Preview02][Ma10p_1080p][x265_flac].mkv"))
        assertEquals(Kind.PV, label("[2.43 Seiin Koukou Danshi Volley-bu][BD-BOX 02][Disc 02][SP02][EP11 Web Yokoku][BDRIP][1080P].mkv")?.kind)
        assertEquals(Kind.OTHER, label("menu/[DBD-Raws][Ultraman Omega][menu][B1][SP1][1080P].mkv")?.kind)
        assertEquals(Kind.OTHER, label("SP/S01 BD-BOX MENU/MENU 01.mkv")?.kind)
        assertEquals(Kind.OTHER, label("[VCB-Studio] Bakuon!! [Menu01-1][Ma10p_1080p][x265_flac].mkv")?.kind)
        assertEquals(Kind.OTHER, label("人物访谈/[DBD-Raws][Ultraman Omega][Interview][05][1080P].mkv")?.kind)
        assertEquals(Kind.OTHER, label("[VCB-Studio] Bakuon!! [IV03][Ma10p_1080p][x265_flac].mkv")?.kind)
    }

    @Test
    fun `labels next to underscores and digits`() {
        // 下划线是词字符, \b 在这三个名字里都不成立
        assertEquals(Kind.PV, label("[BD-raw][My-HiME][BOX_Vol.6_10][Character_PV1][AE1D2948].m2ts")?.kind)
        assertEquals(Kind.PV, label("Koutetsujou no Kabaneri - Vol.01 PV_02 (BD 1280x720 AVC AAC).mp4")?.kind)
        assertEquals(Kind.OP, label("Haikyuu!! S3/(Hi10)_Haikyuu!!_S3_-_OP_(BD_720p)_(Sergey-Commie)_(6816FC45).mkv")?.kind)
        // 标签与序号之间同样没有词边界
        assertEquals(Kind.PV, label("[Xrip][Fate_Apocrypha][BDrip][CM1][1080P][x264_10bit_flac].mkv")?.kind)
        assertEquals(TorrentFileLabel(Kind.ED, null), label("[ReinForce] Fate／Extra ~Last Encore~ EDv1 (BDRip 1920x1080 x264 FLAC).mkv"))
    }

    @Test
    fun `hex in a crc32 suffix is not a label`() {
        assertNull(label("Haikyuu!! S2/(Hi10)_Haikyuu!!_S2_-_11_(BD_720p)_(Sergey-Commie)_(ED6EBF92).mkv"))
        assertNull(label("[Renascent] High School! Kimengumi (1985) - 84 [DVD] [ED87C8CF].mkv"))
    }

    @Test
    fun `words that only look like labels`() {
        // IV 不带序号时是罗马数字第四季; Especial 是西语的 special
        assertNull(label("[Shiniori-Raws] Overlord IV - 01 (BD 1280x720 x265 10bit AAC).mp4"))
        assertNull(label("[DollarsFansub] Mirai Nikki - Murumuru-sensei Especial - 03 (BD 1080p x264 AAC).mkv"))
    }

    @Test
    fun `the directory decides when the file name says nothing`() {
        assertEquals(
            Kind.OTHER,
            label("[NAOKI-Raws] 機動新世紀ガンダムX BD-BOX (BDRip x264 DTS-HDMA Chap)/Menu/DISC1 (BDRip x264 DTS-HDMA).mkv")?.kind,
        )
        assertEquals(Kind.PV, label("[ReinForce] Koi to Uso (BDRip 1920x1080 x264 FLAC)/Extra/PV/CV1.mkv")?.kind)
        assertEquals(
            Kind.SP,
            label("[HYSUB]Golden Kamuy[01~24+OAD+SP][GB_MP4][1280X720]/SP/[HYSUB]Golden Kamuy Douga Gekijou[01][GB_MP4][1280X720].mp4")?.kind,
        )
    }

    @Test
    fun `every directory level decides, including the first`() {
        // 路径不带种子根目录时第一段就是真正的子目录. 带根目录的那种由 TorrentMediaResolver 在拿到
        // 整份清单后剥掉, 不是这里的事.
        assertEquals(Kind.SP, label("specials/S01E01.mkv")?.kind)
        // 带加号的目录是内容清单, 不是类别
        assertNull(label("Title - TV + OVA + SP/[Group] Title - 01.mkv"))
    }

    @Test
    fun `bonus words beyond the NC family`() {
        assertEquals(Kind.OP, label("Extras/Clean Opening.mkv")?.kind)
        assertEquals(TorrentFileLabel(Kind.ED, 2), label("[SMC] Sailor Moon Stars Textless Ending 2[D102184F].mkv"))
        assertEquals(Kind.OP, label("EXTRA/[Moozzi2] Nagasarete Airantou [SP01] Non Telop Opening - 01 (BD 1920x1080 x.264 Flac).mkv")?.kind)
        assertEquals(Kind.PV, label("EXTRA/[Moozzi2] Nagasarete Airantou [SP03] Program Promotion Video - 01 (BD 1920x1080 x.264 Flac).mkv")?.kind)
        assertEquals(Kind.PV, label("Fate Zero BD-Box_SPOT_1 (BD 1920x1080 x264 FLAC).mkv")?.kind)
    }

    @Test
    fun `bangumi episode types map to kinds`() {
        assertEquals(Kind.SP, TorrentFileLabel.kindOf(EpisodeSort("SP01")))
        assertEquals(Kind.SP, TorrentFileLabel.kindOf(EpisodeSort("OVA")))
        assertEquals(Kind.OP, TorrentFileLabel.kindOf(EpisodeSort("OP1")))
        assertNull(TorrentFileLabel.kindOf(EpisodeSort(1)))
    }
}
