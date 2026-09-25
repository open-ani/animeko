/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import io.github.nihildigit.pikpak.InMemorySessionStore
import io.github.nihildigit.pikpak.MagnetResource
import io.github.nihildigit.pikpak.ResolvedFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.resolve
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MagnetResolveTest {
    private val magnet = "magnet:?xt=urn:btih:157e0a57e1af0e1cfd46258ba6c62938c21b6ee8&dn=pack"

    private fun downloader(
        root: SystemPath,
        resolve: suspend (uri: String) -> MagnetResource?,
    ) = PikPakTorrentDownloader(
        httpClient = HttpClient(MockEngine { error("this test resolves magnets through the injected resolver") }),
        credentials = MutableStateFlow(PikPakCredentials("nobody@example.com", "unused")),
        sessionStore = InMemorySessionStore(),
        rootDataDirectory = root,
        config = MutableStateFlow(PikPakEngineConfig()),
        parentCoroutineContext = Dispatchers.IO,
    ).also {
        it.magnetResolver = resolve
    }

    @Test
    fun `the listing is resolved once and answers canServe as well as the session`() = runBlocking {
        val calls = AtomicInteger(0)
        val root = SystemPaths.createTempDirectory("pikpak-resolve").resolve("pikpak")
        val downloader = downloader(root) {
            calls.incrementAndGet()
            MagnetResource("pack", listOf(ResolvedFile("01.mkv", 8192, "GCID-1")))
        }

        assertTrue(downloader.canServe(magnet))
        assertTrue(downloader.canServe(magnet))
        val session = downloader.startDownload(downloader.fetchTorrent(magnet))

        // 一次解析回答 canServe, 也交给随后的 createSession: 下载对话框正是这个顺序, 各问一次就要
        // 多付两次登录和查询. 列表描述的是 PikPak 的内容索引而非账号网盘, 不随登录者变化.
        assertEquals(1, calls.get(), "canServe 拿到的列表就是 createSession 需要的那份")
        assertEquals(listOf("01.mkv"), session.getFiles().map { it.pathInTorrent })

        session.close()
        downloader.close()
    }

    @Test
    fun `an unindexed magnet answers false and then fails as not indexed`() = runBlocking {
        val root = SystemPaths.createTempDirectory("pikpak-resolve-miss").resolve("pikpak")
        val downloader = downloader(root) { null }

        assertFalse(downloader.canServe(magnet))

        val failure = runCatching { downloader.startDownload(downloader.fetchTorrent(magnet)) }.exceptionOrNull()
        assertIs<PikPakNotIndexedException>(failure)

        downloader.close()
    }

    @Test
    fun `a torrent whose files are all unindexed is treated as a miss`() = runBlocking {
        val root = SystemPaths.createTempDirectory("pikpak-resolve-nogcid").resolve("pikpak")
        val downloader = downloader(root) {
            MagnetResource("pack", listOf(ResolvedFile("01.mkv", 8192, null)))
        }

        val failure = runCatching { downloader.startDownload(downloader.fetchTorrent(magnet)) }.exceptionOrNull()
        assertIs<PikPakNotIndexedException>(failure)

        downloader.close()
    }

    @Test
    fun `a partially indexed pack keeps its unindexed files in the listing`() = runBlocking {
        val root = SystemPaths.createTempDirectory("pikpak-resolve-partial").resolve("pikpak")
        val downloader = downloader(root) {
            MagnetResource(
                "pack",
                listOf(
                    ResolvedFile("01.mkv", 8192, "GCID-1"),
                    ResolvedFile("02.mkv", 8192, null),
                ),
            )
        }

        val session = downloader.startDownload(downloader.fetchTorrent(magnet))
        assertEquals(
            listOf("01.mkv", "02.mkv"),
            session.getFiles().map { it.pathInTorrent },
            "the unindexed episode must stay in the listing",
        )

        session.close()
        downloader.close()
    }

    @Test
    fun `a partially indexed pack is left to BT`() = runBlocking {
        val root = SystemPaths.createTempDirectory("pikpak-canserve-partial").resolve("pikpak")
        val downloader = downloader(root) {
            MagnetResource(
                "pack",
                listOf(
                    ResolvedFile("01.mkv", 8192, "GCID-1"),
                    ResolvedFile("02.mkv", 8192, null),
                ),
            )
        }

        assertFalse(downloader.canServe(magnet), "a pack missing one gcid must go to BT whole")

        downloader.close()
    }

    @Test
    fun `an unindexed non-video file does not push the pack to BT`() = runBlocking {
        val root = SystemPaths.createTempDirectory("pikpak-canserve-nonvideo").resolve("pikpak")
        val downloader = downloader(root) {
            MagnetResource(
                "pack",
                listOf(
                    ResolvedFile("01.mkv", 8192, "GCID-1"),
                    ResolvedFile("01.nfo", 1024, null),
                    ResolvedFile("sample/sample.txt", 512, null),
                ),
            )
        }

        assertTrue(downloader.canServe(magnet))

        downloader.close()
    }

    @Test
    fun `a torrent with no video at all is refused`() = runBlocking {
        val root = SystemPaths.createTempDirectory("pikpak-canserve-novideo").resolve("pikpak")
        val downloader = downloader(root) {
            MagnetResource("pack", listOf(ResolvedFile("readme.txt", 1024, "GCID-1")))
        }

        assertFalse(downloader.canServe(magnet))

        downloader.close()
    }
}
