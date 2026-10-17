/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.toKtPath
import me.him188.ani.utils.ktor.asScopedHttpClient
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * 用应用实际使用的 OkHttp 引擎和真实的本机 HTTP 服务器测增量下载的失败路径: Ktor 测试引擎在响应体读到一半抛异常时的行为
 * 与真实引擎不同, 只用测试引擎看不出来.
 */
class DeltaPackageDownloaderOkHttpTest {
    private val tempDir = createTempDirectory("delta-okhttp-test").toFile()
    private val installRoot = tempDir.resolve("install").apply { mkdirs() }
    private val saveDir = tempDir.resolve("save").apply { mkdirs() }

    private val released = listOf(
        TestZipEntry("Ani/", null),
        TestZipEntry("Ani/app/", null),
        TestZipEntry("Ani/app/a.jar", patternBytes(400_000, seed = 1)),
        // 要比 socket 和 channel 的缓冲区大得多: 响应体读到一半失败时, 剩下的部分能否被正确放弃取决于引擎.
        TestZipEntry("Ani/app/b.jar", randomBytes(16 * 1024 * 1024, seed = 2)),
        TestZipEntry("Ani/app/c.jar", patternBytes(200_000, seed = 3)),
    )
    private val packageFile = writeTestZip(tempDir.resolve("release.zip"), released)
    private val packageBytes = packageFile.readBytes()
    private val manifest = manifestOf(packageFile)
    private val packageSha1 = MessageDigest.getInstance("SHA-1").digest(packageBytes).joinToString("") { "%02x".format(it) }

    private val rangeRequests = ArrayList<String>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange -> handle(exchange) }
        start()
    }
    private val baseUrl = "http://127.0.0.1:${server.address.port}"

    private fun handle(exchange: HttpExchange) {
        try {
            val path = exchange.requestURI.path
            val body: ByteArray
            val status: Int
            when (path) {
                "/pkg.zip.manifest.json" -> {
                    body = manifest.toJson().toByteArray(); status = 200
                }

                "/pkg.zip.sha1" -> {
                    body = "$packageSha1\n".toByteArray(); status = 200
                }

                "/pkg.zip" -> {
                    val range = exchange.requestHeaders.getFirst("Range")
                    if (range == null) {
                        body = packageBytes; status = 200
                    } else {
                        synchronized(rangeRequests) { rangeRequests += range }
                        val (start, end) = range.removePrefix("bytes=").split("-").map { it.toInt() }
                        val slice = packageBytes.copyOfRange(start, end + 1)
                        // 只破坏增量下载的第一个请求 (每个字节取反, 解压一开始就失败), 回退的整包下载要能成功
                        if (synchronized(rangeRequests) { rangeRequests.size } == 1) {
                            for (i in slice.indices) slice[i] = (slice[i].toInt() xor 0xFF).toByte()
                        }
                        exchange.responseHeaders.set("Content-Range", "bytes $start-$end/${packageBytes.size}")
                        exchange.responseHeaders.set("Accept-Ranges", "bytes")
                        body = slice; status = 206
                    }
                }

                else -> {
                    body = ByteArray(0); status = 404
                }
            }
            exchange.sendResponseHeaders(status, if (body.isEmpty()) -1L else body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        } catch (_: Exception) {
            // 客户端中途断开是预期内的
        } finally {
            exchange.close()
        }
    }

    @AfterTest
    fun stop() {
        server.stop(0)
        tempDir.deleteRecursively()
    }

    @Test
    fun `directory output falls back to a zip when the first range is garbage`() = runBlocking {
        writeInstalledFiles(installRoot, released.filter { it.name != "Ani/app/b.jar" })
        val client = HttpClient(OkHttp) {
            expectSuccess = true
            install(HttpTimeout)
        }
        val downloader = DeltaPackageDownloader(
            client.asScopedHttpClient(),
            installRoot = installRoot,
            output = PackageOutput.Directory,
        )
        val result = withTimeout(60_000) {
            downloader.download(listOf("$baseUrl/pkg.zip"), saveDir = saveDir.toKtPath().inSystem)
        }
        assertNotNull(result)
        assertIs<FileDownloaderState.Succeed>(downloader.state.value)
        assertEquals(packageBytes.toList(), File(result.toString()).readBytes().toList())
        client.close()
    }
}
