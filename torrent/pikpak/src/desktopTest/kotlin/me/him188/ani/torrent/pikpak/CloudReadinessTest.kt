/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class CloudReadinessTest {
    private val length = PikPakFileEntry.PIECE_SIZE * 2

    private var prepareCalls = 0

    private fun entry() = PikPakFileEntry(
        index = 0,
        length = length,
        saveDirectory = SystemPaths.createTempDirectory("pikpak-readiness"),
        relativePath = RELATIVE_PATH,
        torrentId = "readiness-source-key",
        parentCoroutineContext = Dispatchers.IO_,
        meta = PikPakFileMeta(index = 0, pathInTorrent = RELATIVE_PATH, gcid = "GCID-01", length = length),
        source = FakeRangeSource(ByteArray(length.toInt())),
        prepareSource = { prepareCalls++ },
        concurrency = 1,
        scheduler = DownloadScheduler(),
        onHandleCountChanged = {},
    )

    @Test
    fun `an entry that is already ready does not mint a second link`() {
        runBlocking {
            val entry = entry()
            try {
                entry.ensureCloudReady()
                entry.ensureCloudReady()
                // Playback, the progress-bar preview and the fetcher all enter through
                // ensureCloudReady, and minting costs a round trip against a signed URL, so a
                // second answer would be paid for on every one of them.
                assertEquals(1, prepareCalls, "the entry re-prepared after it already had a link")
            } finally {
                entry.close()
            }
        }
    }

    private companion object {
        const val RELATIVE_PATH = "01.mkv"
    }
}
