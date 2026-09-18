/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.writeBytes
import me.him188.ani.utils.io.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PikPakResumeDataTest {
    private fun meta(vararg files: PikPakFileMeta) = PikPakTorrentMeta(
        uri = "magnet:?xt=urn:btih:157E0A57E1AF0E1CFD46258BA6C62938C21B6EE8",
        sourceKey = "157E0A57E1AF0E1CFD46258BA6C62938C21B6EE8",
        name = "pack",
        files = files.toList(),
    )

    private fun file(path: String, gcid: String, length: Long = 5L * 1024 * 1024) =
        PikPakFileMeta(index = 0, pathInTorrent = path, gcid = gcid, length = length)

    @Test
    fun `the gcid survives a round trip because it is the only lasting identity`() {
        val dir = SystemPaths.createTempDirectory("pikpak-meta")
        val resume = PikPakResumeData(dir)
        val original = meta(
            file("01.mkv", "A1B2C3D4E5F60718293A4B5C6D7E8F9001122334"),
            file("specials/SP01.mkv", "00112233445566778899AABBCCDDEEFF00112233"),
        )

        resume.write(original)

        assertEquals(original.copy(version = PikPakTorrentMeta.CURRENT_VERSION), resume.read())
    }

    @Test
    fun `a corrupt meta reads as absent so the caller re-resolves the magnet`() {
        val dir = SystemPaths.createTempDirectory("pikpak-meta-corrupt")
        dir.resolve(PikPakTorrentMeta.FILE_NAME).writeText("{ this is not json")

        assertNull(PikPakResumeData(dir).read())
    }

    @Test
    fun `a version 3 meta reads as absent so its file ids are never used`() {
        val dir = SystemPaths.createTempDirectory("pikpak-meta-v3")
        dir.resolve(PikPakTorrentMeta.FILE_NAME).writeText(
            """
            {
              "version": 3,
              "uri": "magnet:?xt=urn:btih:157E0A57E1AF0E1CFD46258BA6C62938C21B6EE8",
              "sourceKey": "157E0A57E1AF0E1CFD46258BA6C62938C21B6EE8",
              "name": "pack",
              "bucketId": "bucket-1",
              "files": [
                { "index": 0, "pathInTorrent": "01.mkv", "fileId": "cloud-1", "length": 4096 }
              ]
            }
            """.trimIndent(),
        )

        assertNull(PikPakResumeData(dir).read())
    }

    @Test
    fun `a meta without a version field reads as absent`() {
        val dir = SystemPaths.createTempDirectory("pikpak-meta-unversioned")
        dir.resolve(PikPakTorrentMeta.FILE_NAME).writeText(
            """
            {
              "uri": "magnet:?xt=urn:btih:157E0A57E1AF0E1CFD46258BA6C62938C21B6EE8",
              "sourceKey": "157E0A57E1AF0E1CFD46258BA6C62938C21B6EE8",
              "name": "pack",
              "files": []
            }
            """.trimIndent(),
        )

        assertNull(PikPakResumeData(dir).read())
    }

    @Test
    fun `a missing media file is normal but an over-long one forces a re-index`() {
        val dir = SystemPaths.createTempDirectory("pikpak-consistency")
        val meta = meta(file("01.mkv", "A1B2C3D4E5F60718293A4B5C6D7E8F9001122334", length = 4096))

        assertTrue(filesConsistent(dir, meta), "an absent file is not an inconsistency")

        dir.resolve("01.mkv").writeBytes(ByteArray(1024))
        assertTrue(filesConsistent(dir, meta), "a partial cache download is not an inconsistency")

        dir.resolve("01.mkv").writeBytes(ByteArray(8192))
        assertFalse(filesConsistent(dir, meta))
    }
}
