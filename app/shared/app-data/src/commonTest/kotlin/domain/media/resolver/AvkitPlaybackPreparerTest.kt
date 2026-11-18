/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.readBytes
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.writeBytes
import org.openani.mediamp.source.UriMediaData

class AvkitPlaybackPreparerTest {
    @Test
    fun `mkv is downloaded with playback headers and temporary files follow playback lifetime`() = runTest {
        val bytes = matroskaFixture()
        val directory = SystemPaths.createTempDirectory("matroska-test-")
        val client = HttpClient(MockEngine { request ->
            assertEquals("https://emby.test/original.mkv?api_key=token", request.url.toString())
            assertEquals("token", request.headers["X-Emby-Token"])
            assertEquals("VideoClient", request.headers[HttpHeaders.UserAgent])
            respond(bytes)
        })
        try {
            val data = UriMediaData(
                "https://emby.test/original.mkv?api_key=token",
                mapOf("X-Emby-Token" to "token", HttpHeaders.UserAgent to "VideoClient"),
            )
            val playback = AvkitPlaybackPreparer(
                client,
                fileUri = { "file://$it" },
                remux = { input, output, tracks ->
                    assertContentEquals(bytes, Path(input).inSystem.readBytes())
                    assertEquals("V_MPEGH/ISO/HEVC", tracks.videoCodecId)
                    Path(output).inSystem.writeBytes(byteArrayOf(1, 2, 3))
                },
                createDirectory = { directory },
            ).prepare(data)
            val result = playback.data as UriMediaData
            assertTrue(result.uri.startsWith("file://"))
            assertTrue(result.uri.endsWith("playback.mp4"))
            assertTrue(result.headers.isEmpty())
            assertSame(data.extraFiles, result.extraFiles)
            assertFalse(directory.resolve("source.mkv").exists())
            assertTrue(directory.resolve("playback.mp4").exists())
            playback.close()
            playback.close()
            assertFalse(directory.exists())
        } finally {
            client.close()
        }
    }

    @Test
    fun `extensionless Matroska is detected by a bounded range probe`() = runTest {
        val bytes = matroskaFixture()
        val ranges = mutableListOf<String?>()
        val client = HttpClient(MockEngine { request ->
            ranges += request.headers[HttpHeaders.Range]
            respond(bytes)
        })
        try {
            AvkitPlaybackPreparer(client, { "file://$it" }, { _, output, _ ->
                Path(output).inSystem.writeBytes(byteArrayOf(1))
            }).prepare(UriMediaData("https://emby.test/Videos/123/stream?static=true")).use {
                assertTrue((it.data as UriMediaData).uri.startsWith("file://"))
            }
            assertEquals(listOf("bytes=0-63", null), ranges)
        } finally {
            client.close()
        }
    }

    @Test
    fun `compatible mp4 is probed and HLS stays on its original URL without downloading`() = runTest {
        val bytes = mp4Fixture("hvc1")
        val client = HttpClient(MockEngine { request ->
            val range = checkNotNull(request.headers[HttpHeaders.Range]) { "Direct media must not be downloaded" }
            assertTrue(request.url.encodedPath.endsWith(".MP4"))
            val (start, end) = range.removePrefix("bytes=").split('-').map { it.toInt() }
            respond(bytes.copyOfRange(start, minOf(end + 1, bytes.size)), HttpStatusCode.PartialContent)
        })
        try {
            for (url in listOf("https://cdn.test/video.MP4?token=1", "https://cdn.test/index.m3u8")) {
                val data = UriMediaData(url)
                AvkitPlaybackPreparer(client, { it }, { _, _, _ -> error("Unexpected remux") })
                    .prepare(data).use { assertSame(data, it.data) }
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `hev1 MP4 is downloaded once and retagged for AVKit`() = runTest {
        val bytes = mp4Fixture("hev1")
        var downloads = 0
        val client = HttpClient(MockEngine { request ->
            assertEquals("token", request.headers["X-Emby-Token"])
            val range = request.headers[HttpHeaders.Range]
            if (range == null) { downloads++; respond(bytes) } else {
                val (start, end) = range.removePrefix("bytes=").split('-').map { it.toInt() }
                respond(bytes.copyOfRange(start, minOf(end + 1, bytes.size)), HttpStatusCode.PartialContent)
            }
        })
        try {
            AvkitPlaybackPreparer(client, { "file://$it" }, { _, output, tracks ->
                assertEquals("hvc1", mp4VideoTagFor(tracks.videoCodecId))
                Path(output).inSystem.writeBytes(byteArrayOf(1))
            }).prepare(UriMediaData("https://cdn.test/video.mp4", mapOf("X-Emby-Token" to "token"))).use {
                assertTrue((it.data as UriMediaData).uri.startsWith("file://"))
            }
            assertEquals(1, downloads)
        } finally { client.close() }
    }

    @Test
    fun `other containers are normalized and subtitle failure does not block the video`() = runTest {
        val client = HttpClient(MockEngine { respond("AVI fixture") })
        try {
            AvkitPlaybackPreparer(
                client, { "file://$it" }, { _, output, _ -> Path(output).inSystem.writeBytes(byteArrayOf(1)) },
                normalizeContainer = { _, output -> Path(output).inSystem.writeBytes(matroskaFixture()) },
                extractSubtitles = { _, _, _ -> error("Damaged subtitle") },
            ).prepare(UriMediaData("https://cdn.test/video.avi")).use {
                assertTrue((it.data as UriMediaData).uri.startsWith("file://"))
            }
        } finally { client.close() }
    }

    @Test
    fun `extensionless non Matroska stays on the original URL`() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine { requests++; respond("#EXTM3U\n#EXTINF:10,\na.ts") })
        try {
            val data = UriMediaData("https://cdn.test/stream")
            AvkitPlaybackPreparer(client, { it }, { _, _, _ -> error("Unexpected remux") })
                .prepare(data).use { assertSame(data, it.data) }
            assertEquals(1, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun `failed and cancelled remux delete all partial files`() = runTest {
        val client = HttpClient(MockEngine { respond(matroskaFixture()) })
        try {
            for (failure in listOf(IllegalStateException("remux failed"), CancellationException("cancelled"))) {
                val directory = SystemPaths.createTempDirectory("matroska-failure-")
                val preparer = AvkitPlaybackPreparer(client, { it }, { _, output, _ ->
                    Path(output).inSystem.writeBytes(byteArrayOf(1))
                    throw failure
                }, { directory })
                assertFailsWith<Exception> { preparer.prepare(UriMediaData("https://cdn.test/video.mkv")) }
                assertFalse(directory.exists())
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `failed download deletes its temporary directory`() = runTest {
        val directory = SystemPaths.createTempDirectory("matroska-download-")
        val client = HttpClient(MockEngine { respond("Forbidden", HttpStatusCode.Forbidden) }) {
            expectSuccess = true
        }
        try {
            val preparer = AvkitPlaybackPreparer(client, { it }, { _, _, _ -> error("Unexpected remux") }, { directory })
            assertFailsWith<Exception> { preparer.prepare(UriMediaData("https://cdn.test/video.mkv")) }
            assertFalse(directory.exists())
        } finally {
            client.close()
        }
    }

    private fun mp4Fixture(codec: String): ByteArray {
        fun integer(value: Int) = ByteArray(4) { (value shr ((3 - it) * 8)).toByte() }
        fun box(type: String, content: ByteArray) = integer(content.size + 8) + type.encodeToByteArray() + content
        var content = box("stsd", ByteArray(4) + integer(1) + box(codec, ByteArray(78)))
        for (type in listOf("stbl", "minf", "mdia", "trak", "moov")) content = box(type, content)
        return box("ftyp", "isom".encodeToByteArray()) + content
    }

    private fun matroskaFixture(): ByteArray {
        fun id(value: Long): ByteArray {
            val size = (value.toString(16).length + 1) / 2
            return ByteArray(size) { index -> (value shr ((size - index - 1) * 8)).toByte() }
        }

        fun size(value: Int): ByteArray {
            val length = (1..8).first { value < (1 shl (7 * it)) - 1 }
            val encoded = value or (1 shl (7 * length))
            return ByteArray(length) { index ->
                val shift = (length - index - 1) * 8
                ((encoded shr shift) and 0xff).toByte()
            }
        }

        fun element(tag: Long, value: ByteArray): ByteArray = id(tag) + size(value.size) + value
        fun number(tag: Long, value: Int): ByteArray = element(tag, byteArrayOf(value.toByte()))
        fun string(tag: Long, value: String): ByteArray = element(tag, value.encodeToByteArray())
        fun track(type: Int, codec: String, trackNumber: Int, language: String? = null, name: String? = null): ByteArray {
            val fields = number(0xd7, trackNumber) + number(0x83, type) + string(0x86, codec) +
                    (language?.let { string(0x22b59d, it) } ?: byteArrayOf()) +
                    (name?.let { string(0x536e, it) } ?: byteArrayOf())
            return element(0xae, fields)
        }

        val ebmlHeader = element(0x1a45dfa3, string(0x4282, "matroska"))
        val segment = id(0x18538067) + byteArrayOf(1, -1, -1, -1, -1, -1, -1, -1)
        val entries = track(1, "V_MPEGH/ISO/HEVC", 1) +
                track(2, "A_OPUS", 2) +
                track(17, "S_HDMV/PGS", 3, "chi") +
                track(17, "S_TEXT/ASS", 4, "chi", "简体") +
                track(17, "S_TEXT/UTF8", 5, "eng", "English")
        return ebmlHeader + segment + element(0x1654ae6b, entries) + ByteArray(150_000) { (it % 251).toByte() }
    }
}
