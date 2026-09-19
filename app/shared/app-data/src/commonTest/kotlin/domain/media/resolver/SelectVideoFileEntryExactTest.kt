/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import me.him188.ani.datasources.api.EpisodeSort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * @see TorrentMediaResolver.Companion.selectVideoFileEntryExact
 */
class SelectVideoFileEntryExactTest {
    private val pack = (1..12).map { "[桜都字幕组] 测试动画 - ${it.toString().padStart(2, '0')} [1080p].mkv" } +
            "[桜都字幕组] 测试动画 - SP01 [1080p].mkv"

    private fun select(sort: EpisodeSort, ep: EpisodeSort? = sort, files: List<String> = pack) =
        TorrentMediaResolver.selectVideoFileEntryExact(
            files, { this }, listOf(""), episodeSort = sort, episodeEp = ep,
        )

    @Test
    fun `matches the file for the episode`() {
        assertEquals(pack[6], select(EpisodeSort(7)))
    }

    @Test
    fun `a special is never matched by number`() {
        // 编号相等也可能是巧合: 孤独摇滚包里的 S00E01 是 11.5 话, Bangumi 的 SP01 是 ABEMA 特番.
        assertNull(select(EpisodeSort("SP01"), ep = EpisodeSort(1)))
        val files = pack.dropLast(1) + "测试动画 S00E01-[1080p].mkv" + "测试动画 S00E02-[1080p].mkv"
        assertNull(select(EpisodeSort("SP02"), ep = EpisodeSort(2), files = files))
        assertNull(select(EpisodeSort("OP1"), ep = EpisodeSort(1), files = pack + "NCOP01.mkv"))
    }

    @Test
    fun `special without a file does not fall back to the main story`() {
        // 这是回归点: SP02 的 number 是 2, 宽松匹配会把它算进正片 02.
        assertNull(select(EpisodeSort("SP02"), ep = EpisodeSort(2)))
    }

    @Test
    fun `a special is matched by the episode title`() {
        val files = pack + "测试动画 特典 后藤双的姐姐观察日记 [1080p].mkv"
        val selected = TorrentMediaResolver.selectVideoFileEntryExact(
            files, { this }, listOf("后藤双的姐姐观察日记"), episodeSort = EpisodeSort("SP01"), episodeEp = EpisodeSort(1),
        )
        assertEquals(files.last(), selected)
    }

    @Test
    fun `a numberless special takes the only file of its kind`() {
        assertEquals(pack.last(), select(EpisodeSort("SP"), ep = null))
    }

    @Test
    fun `main story does not take a special file by number`() {
        val files = listOf("测试动画 S00E01-[1080p].mkv", "[桜都字幕组] 测试动画 - SP01 [1080p].mkv")
        assertNull(select(EpisodeSort(1), files = files))
    }

    @Test
    fun `number fallback needs the whole number`() {
        // 08 落在 [1080p] 里面, 不算; 8 写成 08 算.
        assertNull(select(EpisodeSort(8), files = listOf("测试动画 - 第七集 [1080p].mkv", "测试动画 - 第九集 [1080p].mkv")))
        assertEquals("测试动画 08 [1080p].mkv", select(EpisodeSort(8), files = listOf("测试动画 08 [1080p].mkv", "测试动画 09 [1080p].mkv")))
    }

    @Test
    fun `episode beyond the pack does not match`() {
        assertNull(select(EpisodeSort(20)))
    }

    @Test
    fun `the directory shared by every file is the torrent root and does not label`() {
        // anitorrent 的路径带根目录, 根目录名里的「映像特典」说的是整包内容, 不是每个文件的类别
        val root = "GALAXY ANGEL 1-26 DVD 1-7巻 映像特典"
        val files = listOf(
            "$root/[LITEN][R2JRAW][Galaxy_Angel][01][WMV9_MP3].mkv",
            "$root/[LITEN][R2JRAW][Galaxy_Angel][02][WMV9_MP3].mkv",
            "$root/SP/[LITEN][R2JRAW][Galaxy_Angel][Bonus][WMV9_MP3].mkv",
        )
        assertEquals(files[0], select(EpisodeSort(1), files = files))
        assertEquals(files[2], select(EpisodeSort("SP"), ep = null, files = files))
    }

    @Test
    fun `a first directory not shared by every file is a real subdirectory`() {
        // 路径不带根目录时 `specials/` 是子目录, 其下的文件不进正片池
        val files = listOf("specials/S00E01.mkv", "S01E01.mkv", "S01E02.mkv")
        assertEquals("S01E01.mkv", select(EpisodeSort(1), files = files))
        assertEquals("specials/S00E01.mkv", select(EpisodeSort("SP"), ep = null, files = files))
    }

    @Test
    fun `no video files gives null`() {
        assertNull(select(EpisodeSort(1), files = listOf("readme.txt")))
    }

    @Test
    fun `loose variant does not fall back to the first video of a pack`() {
        assertNull(
            TorrentMediaResolver.selectVideoFileEntry(
                pack, { this }, listOf(""),
                episodeSort = EpisodeSort(20), episodeEp = EpisodeSort(20),
            ),
        )
    }
}
