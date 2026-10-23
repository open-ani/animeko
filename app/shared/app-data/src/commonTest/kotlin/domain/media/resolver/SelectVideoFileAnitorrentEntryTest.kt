/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.io.files.SystemPathSeparator
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.test.TestFactory
import me.him188.ani.test.dynamicTest
import me.him188.ani.test.permuted
import me.him188.ani.test.runDynamicTests
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SelectVideoFileAnitorrentEntryTest {
    private val json = Json { ignoreUnknownKeys = true }

    // 合集种子里的 scene 命名: 没有空格和括号, 集数只出现在 S02E02 里
    private val sceneSeasonPack = listOf(
        "Cyberpunk.Edgerunners.S02E01.Last.Chance.for.a.Slow.Dance.1080p.NF.WEB-DL.DUAL.DDP5.1.H.264-VARYG.mkv",
        "Cyberpunk.Edgerunners.S02E02.Give.Me.Another.Day.1080p.NF.WEB-DL.DUAL.DDP5.1.H.264-VARYG.mkv",
        "Cyberpunk.Edgerunners.S02E03.021.1080p.NF.WEB-DL.DUAL.DDP5.1.H.264-VARYG.mkv",
        "Cyberpunk.Edgerunners.S02E04.Someone.That.I.Used.To.Know.1080p.NF.WEB-DL.DUAL.DDP5.1.H.264-VARYG.mkv",
        "Cyberpunk.Edgerunners.S02E05.Fades.Pass.the.Distance.1080p.NF.WEB-DL.DUAL.DDP5.1.H.264-VARYG.mkv",
        "Cyberpunk.Edgerunners.S02E06.Everything.Hate.1080p.NF.WEB-DL.DUAL.DDP5.1.H.264-VARYG.mkv",
        "Cyberpunk.Edgerunners.S02E07.To.the.Depths.With.You.1080p.NF.WEB-DL.DUAL.DDP5.1.H.264-VARYG.mkv",
        "Cyberpunk.Edgerunners.S02E08.Serce.1080p.NF.WEB-DL.DUAL.DDP5.1.H.264-VARYG.mkv",
        "Cyberpunk.Edgerunners.S02E09.Strictly.Reserved.For.You.1080p.NF.WEB-DL.DUAL.DDP5.1.H.264-VARYG.mkv",
        "Cyberpunk.Edgerunners.S02E10.Then.Everyone.Went.Home.1080p.NF.WEB-DL.DUAL.DDP5.1.H.264-VARYG.mkv",
    ).map { "Cyberpunk.Edgerunners.S02.1080p.NF.WEB-DL.DUAL.DDP5.1.H.264-VARYG$SystemPathSeparator$it" }

    @Test
    fun `can select single`() {
        val selected = TorrentMediaResolver.selectVideoFileEntry(
            listOf("[Nekomoe kissaten&LoliHouse] Shuumatsu Train Doko e Iku - 04 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv"),
            { this },
            episodeTitles = listOf("终末列车去往何方?"),
            episodeSort = EpisodeSort(4),
            episodeEp = null,
        )
        assertNotNull(selected)
    }

    @Test
    fun `case from issue 813`() {
        val selected = TorrentMediaResolver.selectVideoFileEntry(
            listOf("[MingY] Senpai wa Otokonoko [06][1080p][CHS&JPN].mp4"),
            { this },
            episodeTitles = """
                - 前辈是男孩子
                - 先輩はおとこのこ
                - 学姐是男孩
                - 前辈是伪娘
                - Senpai wa Otokonoko
                - Senpai Is an Otokonoko
            """.trimIndent().split("\n").map { it.trim().removePrefix("- ") },
            episodeSort = EpisodeSort(6),
            episodeEp = EpisodeSort(6),
        )
        assertNotNull(selected)
    }

    @Test
    fun `can select from multiple by episodeSort`() {
        val selected = TorrentMediaResolver.selectVideoFileEntry(
            json.decodeFromString(
                ListSerializer(String.serializer()),
                """
                [
                    "[Nekomoe kissaten&LoliHouse] Shuumatsu Train Doko e Iku - 02 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv",
                    "[Nekomoe kissaten&LoliHouse] Shuumatsu Train Doko e Iku - 03 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv",
                    "[Nekomoe kissaten&LoliHouse] Shuumatsu Train Doko e Iku - 04 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv",
                    "[Nekomoe kissaten&LoliHouse] Shuumatsu Train Doko e Iku - 05 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv"
                ]
            """.trimIndent(),
            ),
            { this },
            episodeTitles = listOf("终末列车去往何方?"),
            episodeSort = EpisodeSort(4),
            episodeEp = null,
        )
        assertEquals(
            "[Nekomoe kissaten&LoliHouse] Shuumatsu Train Doko e Iku - 04 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv",
            selected,
        )
    }

    @Test
    fun `can select from multiple by episodeEp`() {
        val selected = TorrentMediaResolver.selectVideoFileEntry(
            json.decodeFromString(
                ListSerializer(String.serializer()),
                """
                [
                    "[Nekomoe kissaten&LoliHouse] Shuumatsu Train Doko e Iku - 02 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv",
                    "[Nekomoe kissaten&LoliHouse] Shuumatsu Train Doko e Iku - 03 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv",
                    "[Nekomoe kissaten&LoliHouse] Shuumatsu Train Doko e Iku - 04 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv",
                    "[Nekomoe kissaten&LoliHouse] Shuumatsu Train Doko e Iku - 05 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv"
                ]
            """.trimIndent(),
            ),
            { this },
            episodeTitles = listOf("终末列车去往何方?"),
            episodeSort = EpisodeSort(26),
            episodeEp = EpisodeSort(4),
        )
        assertEquals(
            "[Nekomoe kissaten&LoliHouse] Shuumatsu Train Doko e Iku - 04 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv",
            selected,
        )
    }

    @Test
    fun `select by sort than by ep`() {
        val selected = TorrentMediaResolver.selectVideoFileEntry(
            json.decodeFromString(
                ListSerializer(String.serializer()),
                """
                [
                    "[Nekomoe kissaten&LoliHouse] Shuumatsu Train Doko e Iku - 02 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv",
                    "[Nekomoe kissaten&LoliHouse] Shuumatsu Train Doko e Iku - 03 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv",
                    "[Nekomoe kissaten&LoliHouse] Shuumatsu Train Doko e Iku - 04 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv",
                    "[Nekomoe kissaten&LoliHouse] Shuumatsu Train Doko e Iku - 05 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv"
                ]
            """.trimIndent(),
            ),
            { this },
            episodeTitles = listOf("终末列车去往何方?"),
            episodeSort = EpisodeSort(4),
            episodeEp = EpisodeSort(2),
        )
        assertEquals(
            "[Nekomoe kissaten&LoliHouse] Shuumatsu Train Doko e Iku - 04 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv",
            selected,
        )
    }

    @Test
    fun `can select from multiple by single title match`() {
        val selected = TorrentMediaResolver.selectVideoFileEntry(
            json.decodeFromString(
                ListSerializer(String.serializer()),
                """
                [
                    "[Nekomoe kissaten&LoliHouse] Shuumatsu Train Doko e Iku - 02 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv",
                    "[Nekomoe kissaten&LoliHouse] Shuumatsu Train Doko e Iku - 03 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv",
                    "[Nekomoe kissaten&LoliHouse] Shuumatsu Train Doko e Iku - 04 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv",
                    "[Nekomoe kissaten&LoliHouse] Shuumatsu Train Doko e Iku - 05 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv"
                ]
            """.trimIndent(),
            ),
            { this },
            episodeTitles = listOf("Shuumatsu Train Doko e Iku - 03"),
            episodeSort = EpisodeSort(26),
            episodeEp = EpisodeSort(4),
        )
        assertEquals(
            "[Nekomoe kissaten&LoliHouse] Shuumatsu Train Doko e Iku - 03 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv",
            selected,
        )
    }

    @TestFactory
    fun `select normal over PV`() = runDynamicTests(
        listOf(
            "[DBD-Raws][未来日记][01][1080P][BDRip][HEVC-10bit][FLACx2].mkv",
            "[DBD-Raws][未来日记] PV [01][1080P][BDRip][HEVC-10bit][FLAC].mkv",
            "[DBD-Raws][未来日记] PV01 [1080P][BDRip][HEVC-10bit][FLAC].mkv",
        ).permuted().mapIndexed { index, list ->
            dynamicTest(index.toString()) {
                val selected = TorrentMediaResolver.selectVideoFileEntry(
                    list,
                    { this },
                    episodeTitles = listOf("未来日记"),
                    episodeSort = EpisodeSort(1),
                    episodeEp = null,
                )
                assertEquals(
                    "[DBD-Raws][未来日记][01][1080P][BDRip][HEVC-10bit][FLACx2].mkv",
                    selected,
                    message = list.toString(),
                )
            }
        },
    )

    @TestFactory
    fun `select SP over PV`() = runDynamicTests(
        listOf(
            "[DBD-Raws][未来日记][01][1080P][BDRip][HEVC-10bit][FLACx2].mkv",
            "[DBD-Raws][未来日记][PV][01][1080P][BDRip][HEVC-10bit][FLAC].mkv",
            "[DBD-Raws][未来日记][SP][1080P][BDRip][HEVC-10bit][FLAC].mkv",
            "[DBD-Raws][未来日记][NCOP1][1080P][BDRip][HEVC-10bit][FLAC].mkv",
        ).permuted().mapIndexed { index, list ->
            dynamicTest(index.toString()) {
                val selected = TorrentMediaResolver.selectVideoFileEntry(
                    list,
                    { this },
                    episodeTitles = listOf("未来日记"),
                    episodeSort = EpisodeSort("SP"),
                    episodeEp = null,
                )
                assertEquals(
                    "[DBD-Raws][未来日记][SP][1080P][BDRip][HEVC-10bit][FLAC].mkv",
                    selected,
                    message = list.toString(),
                )
            }
        },
    )

    @TestFactory
    fun `select S01E01-`() = runDynamicTests(
        // [桜都字幕组&7^ACG] 憧憬成为魔法少女/魔法少女にあこがれて/Mahou Shoujo ni Akogarete S01 | 01 [简繁字幕] BDrip 1080p x265 FLAC 2.0.mkv
        listOf(
            "Mahou Shoujo ni Akogarete S01E01-[1080p][BDRIP][x265.FLAC].mkv",
            "Mahou Shoujo ni Akogarete S01E02-[1080p][BDRIP][x265.FLAC].mkv",
            "Mahou Shoujo ni Akogarete S01E03-[1080p][BDRIP][x265.FLAC].mkv",
        ).permuted().mapIndexed { index, list ->
            dynamicTest(index.toString()) {
                val selected = TorrentMediaResolver.selectVideoFileEntry(
                    list,
                    { this },
                    episodeTitles = listOf("梦想成为魔法少女"),
                    episodeSort = EpisodeSort("01"),
                    episodeEp = null,
                )
                assertEquals(
                    "Mahou Shoujo ni Akogarete S01E01-[1080p][BDRIP][x265.FLAC].mkv",
                    selected,
                    message = list.toString(),
                )
            }
        },
    )

    @TestFactory
    fun `select S01E01- 2`() = runDynamicTests(
        // [桜都字幕组&7^ACG] 憧憬成为魔法少女/魔法少女にあこがれて/Mahou Shoujo ni Akogarete S01 | 01 [简繁字幕] BDrip 1080p x265 FLAC 2.0.mkv
        listOf(
            "Mahou Shoujo ni Akogarete S01E01-[1080p][BDRIP][x265.FLAC].mkv",
            "Mahou Shoujo ni Akogarete S01E02-[1080p][BDRIP][x265.FLAC].mkv",
            "Mahou Shoujo ni Akogarete S01E03-[1080p][BDRIP][x265.FLAC].mkv",
        ).permuted().mapIndexed { index, list ->
            dynamicTest(index.toString()) {
                val selected = TorrentMediaResolver.selectVideoFileEntry(
                    list,
                    { this },
                    episodeTitles = listOf("梦想成为魔法少女"),
                    episodeSort = EpisodeSort("02"),
                    episodeEp = null,
                )
                assertEquals(
                    "Mahou Shoujo ni Akogarete S01E02-[1080p][BDRIP][x265.FLAC].mkv",
                    selected,
                    message = list.toString(),
                )
            }
        },
    )

    @TestFactory
    fun `select SP 01 over PV`() = runDynamicTests(
        listOf(
            "[DBD-Raws][未来日记][01][1080P][BDRip][HEVC-10bit][FLACx2].mkv",
            "[DBD-Raws][未来日记][PV][01][1080P][BDRip][HEVC-10bit][FLAC].mkv",
            "[DBD-Raws][未来日记][SP][01][1080P][BDRip][HEVC-10bit][FLAC].mkv",
            "[DBD-Raws][未来日记][NCOP1][1080P][BDRip][HEVC-10bit][FLAC].mkv",
        ).permuted().mapIndexed { index, list ->
            dynamicTest(index.toString()) {
                val selected = TorrentMediaResolver.selectVideoFileEntry(
                    list,
                    { this },
                    episodeTitles = listOf("未来日记"),
                    episodeSort = EpisodeSort("SP"),
                    episodeEp = null,
                )
                assertEquals(
                    "[DBD-Raws][未来日记][SP][01][1080P][BDRip][HEVC-10bit][FLAC].mkv",
                    selected,
                    message = list.toString(),
                )
            }
        },
    )

    @TestFactory
    fun `select OVA over PV`() = runDynamicTests(
        listOf(
            "[DBD-Raws][未来日记][01][1080P][BDRip][HEVC-10bit][FLACx2].mkv",
            "[DBD-Raws][未来日记][PV][01][1080P][BDRip][HEVC-10bit][FLAC].mkv",
            "[DBD-Raws][未来日记][OVA][1080P][BDRip][HEVC-10bit][FLAC].mkv",
            "[DBD-Raws][未来日记][NCOP1][1080P][BDRip][HEVC-10bit][FLAC].mkv",
        ).permuted().mapIndexed { index, list ->
            dynamicTest(index.toString()) {
                val selected = TorrentMediaResolver.selectVideoFileEntry(
                    list,
                    { this },
                    episodeTitles = listOf("未来日记"),
                    episodeSort = EpisodeSort("OVA"),
                    episodeEp = null,
                )
                assertEquals(
                    "[DBD-Raws][未来日记][OVA][1080P][BDRip][HEVC-10bit][FLAC].mkv",
                    selected,
                    message = list.toString(),
                )
            }
        },
    )

    @TestFactory
    fun `select OVA12 over PV`() = runDynamicTests(
        listOf(
            "[DBD-Raws][未来日记][01][1080P][BDRip][HEVC-10bit][FLACx2].mkv",
            "[DBD-Raws][未来日记][PV][01][1080P][BDRip][HEVC-10bit][FLAC].mkv",
            "[DBD-Raws][未来日记][OVA12][1080P][BDRip][HEVC-10bit][FLAC].mkv",
            "[DBD-Raws][未来日记][NCOP1][1080P][BDRip][HEVC-10bit][FLAC].mkv",
        ).permuted().mapIndexed { index, list ->
            dynamicTest(index.toString()) {
                val selected = TorrentMediaResolver.selectVideoFileEntry(
                    list,
                    { this },
                    episodeTitles = listOf("未来日记"),
                    episodeSort = EpisodeSort("OVA"),
                    episodeEp = null,
                )
                assertEquals(
                    "[DBD-Raws][未来日记][OVA12][1080P][BDRip][HEVC-10bit][FLAC].mkv",
                    selected,
                    message = list.toString(),
                )
            }
        },
    )

    @TestFactory
    fun `select normal 01 over PV if no match`() = runDynamicTests(
        listOf(
            "[DBD-Raws][未来日记][01][1080P][BDRip][HEVC-10bit][FLACx2].mkv",
            "[DBD-Raws][未来日记][PV][01][1080P][BDRip][HEVC-10bit][FLAC].mkv",
            "[DBD-Raws][未来日记][SP][01][1080P][BDRip][HEVC-10bit][FLAC].mkv",
            "[DBD-Raws][未来日记][NCOP1][1080P][BDRip][HEVC-10bit][FLAC].mkv",
        ).permuted().mapIndexed { index, list ->
            dynamicTest(index.toString()) {
                val selected = TorrentMediaResolver.selectVideoFileEntry(
                    list,
                    { this },
                    episodeTitles = listOf("未来日记"),
                    episodeSort = EpisodeSort("08"),
                    episodeEp = null,
                )
                assertEquals(
                    "[DBD-Raws][未来日记][01][1080P][BDRip][HEVC-10bit][FLACx2].mkv",
                    selected,
                    message = list.toString(),
                )
            }
        },
    )

    @Test
    fun `select of full path`() {
        val selected = TorrentMediaResolver.selectVideoFileEntry(
            listOf(
                "[DBD制作组][4K_HDR][BanG Dream! Ave Mujica][01-13TV全集][2160P][WEB-DL][简日双语内嵌][AAC][MKV]$SystemPathSeparator[DBD-SUB][4K_HDR][BanG Dream! Ave Mujica][01][2160P][WEB-DL][H265-10bit][SCJP][AAC].mkv",
                "[DBD制作组][4K_HDR][BanG Dream! Ave Mujica][01-13TV全集][2160P][WEB-DL][简日双语内嵌][AAC][MKV]$SystemPathSeparator[DBD-SUB][4K_HDR][BanG Dream! Ave Mujica][02][2160P][WEB-DL][H265-10bit][SCJP][AAC].mkv",
                "[DBD制作组][4K_HDR][BanG Dream! Ave Mujica][01-13TV全集][2160P][WEB-DL][简日双语内嵌][AAC][MKV]$SystemPathSeparator[DBD-SUB][4K_HDR][BanG Dream! Ave Mujica][04][2160P][WEB-DL][H265-10bit][SCJP][AAC].mkv",
                "[DBD制作组][4K_HDR][BanG Dream! Ave Mujica][01-13TV全集][2160P][WEB-DL][简日双语内嵌][AAC][MKV]$SystemPathSeparator[DBD-SUB][4K_HDR][BanG Dream! Ave Mujica][05][2160P][WEB-DL][H265-10bit][SCJP][AAC].mkv",
                "[DBD制作组][4K_HDR][BanG Dream! Ave Mujica][01-13TV全集][2160P][WEB-DL][简日双语内嵌][AAC][MKV]$SystemPathSeparator[DBD-SUB][4K_HDR][BanG Dream! Ave Mujica][06][2160P][WEB-DL][H265-10bit][SCJP][AAC].mkv",
                "[DBD制作组][4K_HDR][BanG Dream! Ave Mujica][01-13TV全集][2160P][WEB-DL][简日双语内嵌][AAC][MKV]$SystemPathSeparator[DBD-SUB][4K_HDR][BanG Dream! Ave Mujica][07][2160P][WEB-DL][H265-10bit][SCJP][AAC].mkv",
                "[DBD制作组][4K_HDR][BanG Dream! Ave Mujica][01-13TV全集][2160P][WEB-DL][简日双语内嵌][AAC][MKV]$SystemPathSeparator[DBD-SUB][4K_HDR][BanG Dream! Ave Mujica][08][2160P][WEB-DL][H265-10bit][SCJP][AAC].mkv",
                "[DBD制作组][4K_HDR][BanG Dream! Ave Mujica][01-13TV全集][2160P][WEB-DL][简日双语内嵌][AAC][MKV]$SystemPathSeparator[DBD-SUB][4K_HDR][BanG Dream! Ave Mujica][09][2160P][WEB-DL][H265-10bit][SCJP][AAC].mkv",
                "[DBD制作组][4K_HDR][BanG Dream! Ave Mujica][01-13TV全集][2160P][WEB-DL][简日双语内嵌][AAC][MKV]$SystemPathSeparator[DBD-SUB][4K_HDR][BanG Dream! Ave Mujica][10][2160P][WEB-DL][H265-10bit][SCJP][AAC].mkv",
                "[DBD制作组][4K_HDR][BanG Dream! Ave Mujica][01-13TV全集][2160P][WEB-DL][简日双语内嵌][AAC][MKV]$SystemPathSeparator[DBD-SUB][4K_HDR][BanG Dream! Ave Mujica][11][2160P][WEB-DL][H265-10bit][SCJP][AAC].mkv",
                "[DBD制作组][4K_HDR][BanG Dream! Ave Mujica][01-13TV全集][2160P][WEB-DL][简日双语内嵌][AAC][MKV]$SystemPathSeparator[DBD-SUB][4K_HDR][BanG Dream! Ave Mujica][12][2160P][WEB-DL][H265-10bit][SCJP][AAC].mkv",
                "[DBD制作组][4K_HDR][BanG Dream! Ave Mujica][01-13TV全集][2160P][WEB-DL][简日双语内嵌][AAC][MKV]$SystemPathSeparator[DBD-SUB][4K_HDR][BanG Dream! Ave Mujica][13][2160P][WEB-DL][H265-10bit][SCJP][AAC].mkv",
                "[DBD制作组][4K_HDR][BanG Dream! Ave Mujica][01-13TV全集][2160P][WEB-DL][简日双语内嵌][AAC][MKV]$SystemPathSeparator[DBD-SUB][4K_HDR][Bang Dream! Ave Mujica][03][2160P][WEB-DL][H265-10bit][SCJP][AAC].mkv",
            ),
            { this },
            episodeTitles = listOf("纵使焚身成灰，我亦要爱你。"),
            episodeSort = EpisodeSort(11),
            episodeEp = EpisodeSort(11),
        )
        assertEquals(
            "[DBD制作组][4K_HDR][BanG Dream! Ave Mujica][01-13TV全集][2160P][WEB-DL][简日双语内嵌][AAC][MKV]$SystemPathSeparator[DBD-SUB][4K_HDR][BanG Dream! Ave Mujica][11][2160P][WEB-DL][H265-10bit][SCJP][AAC].mkv",
            selected,
        )
    }

    @Test
    fun `an incomplete listing does not fall back to the only video`() {
        val selected = TorrentMediaResolver.selectVideoFileEntry(
            listOf("[Sub] Season Pack - 01 [1080p].mkv"),
            { this },
            episodeTitles = listOf("第四集"),
            episodeSort = EpisodeSort(4),
            episodeEp = EpisodeSort(4),
            listingComplete = false,
        )
        assertNull(selected)
    }

    @Test
    fun `a complete listing still falls back to the only video`() {
        val selected = TorrentMediaResolver.selectVideoFileEntry(
            listOf("[Sub] Season Pack - 01 [1080p].mkv"),
            { this },
            episodeTitles = listOf("第四集"),
            episodeSort = EpisodeSort(4),
            episodeEp = EpisodeSort(4),
        )
        assertEquals("[Sub] Season Pack - 01 [1080p].mkv", selected)
    }

    @Test
    fun `an incomplete listing still matches by episode number`() {
        val selected = TorrentMediaResolver.selectVideoFileEntry(
            listOf("[Sub] Season Pack - 04 [1080p].mkv"),
            { this },
            episodeTitles = listOf("第四集"),
            episodeSort = EpisodeSort(4),
            episodeEp = EpisodeSort(4),
            listingComplete = false,
        )
        assertEquals("[Sub] Season Pack - 04 [1080p].mkv", selected)
    }

    @TestFactory
    fun `scene named season pack selects the file of each episode`() = runDynamicTests(
        (1..10).map { episode ->
            dynamicTest("episode $episode") {
                val selected = TorrentMediaResolver.selectVideoFileEntry(
                    sceneSeasonPack,
                    { this },
                    episodeTitles = listOf(""),
                    episodeSort = EpisodeSort(episode),
                    episodeEp = EpisodeSort(episode),
                )
                assertEquals(sceneSeasonPack[episode - 1], selected)
            }
        },
    )

    @TestFactory
    fun `fallback number match ignores 1080p and the season tag`() = runDynamicTests(
        listOf(
            EpisodeSort(2) to "Show.S02.Clip.02.1080p.mkv",
            EpisodeSort(8) to "Show.S02.Clip.08.1080p.mkv",
            EpisodeSort(10) to "Show.S02.Clip.10.1080p.mkv",
        ).map { (sort, expected) ->
            dynamicTest(sort.toString()) {
                val selected = TorrentMediaResolver.selectVideoFileEntry(
                    listOf("Show.S02.Intro.1080p.mkv", expected),
                    { this },
                    episodeTitles = listOf(""),
                    episodeSort = sort,
                    episodeEp = null,
                )
                assertEquals(expected, selected)
            }
        },
    )
}
