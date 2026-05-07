/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import io.github.nihildigit.pikpak.DownloadLink
import io.github.nihildigit.pikpak.FileDetail
import me.him188.ani.torrent.offline.OfflineDownloadRejectedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * Unit tests for [buildResolvedMedia] — the `FileDetail → ResolvedMedia`
 * mapping. The logic is the "last mile" between PikPak metadata and the URL
 * mediamp actually opens; a regression here silently breaks playback.
 * Pure, no network — [FileDetail] is a plain `@Serializable data class`.
 */
class BuildResolvedMediaTest {

    private fun fileDetail(
        id: String = "f-123",
        name: String = "video.mp4",
        size: String = "12345",
        octetStreamUrl: String = "",
        octetStreamExpire: String = "",
        webContentLink: String = "",
    ) = FileDetail(
        id = id,
        name = name,
        size = size,
        webContentLink = webContentLink,
        links = FileDetail.Links(
            octetStream = DownloadLink(
                url = octetStreamUrl,
                expire = octetStreamExpire,
            ),
        ),
    )

    @Test
    fun `prefers octet-stream URL over webContentLink`() {
        val f = fileDetail(
            octetStreamUrl = "https://cdn.example.org/signed",
            webContentLink = "https://legacy.example.org/fallback",
        )
        assertEquals("https://cdn.example.org/signed", buildResolvedMedia(f).streamUrl)
    }

    @Test
    fun `falls back to webContentLink when octet-stream is missing`() {
        val f = fileDetail(
            octetStreamUrl = "",
            webContentLink = "https://legacy.example.org/fallback",
        )
        assertEquals("https://legacy.example.org/fallback", buildResolvedMedia(f).streamUrl)
    }

    @Test
    fun `throws rejected when both links are missing`() {
        val f = fileDetail(octetStreamUrl = "", webContentLink = "")
        val ex = assertFailsWith<OfflineDownloadRejectedException> { buildResolvedMedia(f) }
        assertNotNull(ex.message)
        // The error should surface enough context to debug (file id).
        assertEquals(true, ex.message!!.contains("f-123"))
    }

    @Test
    fun `parses valid ISO-8601 expire into Instant`() {
        val f = fileDetail(
            octetStreamUrl = "https://x/y",
            octetStreamExpire = "2030-01-01T00:00:00Z",
        )
        val resolved = buildResolvedMedia(f)
        assertEquals(Instant.parse("2030-01-01T00:00:00Z"), resolved.expiresAt)
    }

    @Test
    fun `empty expire yields null expiresAt`() {
        val f = fileDetail(octetStreamUrl = "https://x/y", octetStreamExpire = "")
        assertNull(buildResolvedMedia(f).expiresAt)
    }

    @Test
    fun `unparseable expire yields null expiresAt without throwing`() {
        val f = fileDetail(octetStreamUrl = "https://x/y", octetStreamExpire = "not-a-date")
        assertNull(buildResolvedMedia(f).expiresAt)
    }

    @Test
    fun `populates fileName and providerFileId when non-empty`() {
        val f = fileDetail(
            id = "file-42",
            name = "[Group] Episode 01.mkv",
            octetStreamUrl = "https://x/y",
        )
        val r = buildResolvedMedia(f)
        assertEquals("[Group] Episode 01.mkv", r.fileName)
        assertEquals("file-42", r.providerFileId)
    }

    @Test
    fun `empty name and id become null in ResolvedMedia`() {
        val f = fileDetail(id = "", name = "", octetStreamUrl = "https://x/y")
        val r = buildResolvedMedia(f)
        assertNull(r.fileName)
        assertNull(r.providerFileId)
    }

    @Test
    fun `size is parsed as Long when numeric`() {
        val f = fileDetail(size = "1073741824", octetStreamUrl = "https://x/y")
        assertEquals(1_073_741_824L, buildResolvedMedia(f).fileSize)
    }

    @Test
    fun `non-numeric size yields null fileSize`() {
        val f = fileDetail(size = "???", octetStreamUrl = "https://x/y")
        assertNull(buildResolvedMedia(f).fileSize)
    }
}
