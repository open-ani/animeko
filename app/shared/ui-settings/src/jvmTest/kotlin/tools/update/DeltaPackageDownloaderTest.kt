/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.toKtPath
import me.him188.ani.utils.ktor.asScopedHttpClient
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeltaPackageDownloaderTest {
    private val tempDir: File = Files.createTempDirectory("delta-package-downloader-test").toFile()
    private val saveDir: File = tempDir.resolve("updates").apply { mkdirs() }
    private val installRoot: File = tempDir.resolve("installed/Ani").apply { mkdirs() }

    /**
     * 发布包: big.bin 比 [DeltaPackageDownloader] 合并请求的间隔大, 把它两侧的 entry 隔成两个请求.
     */
    private val released = listOf(
        TestZipEntry("Ani/", null),
        TestZipEntry("Ani/app/", null),
        TestZipEntry("Ani/app/a.jar", randomBytes(300_000, 1)),
        TestZipEntry("Ani/app/b.jar", randomBytes(50_000, 2)),
        TestZipEntry("Ani/big.bin", randomBytes(2_000_000, 3)),
        TestZipEntry("Ani/app/c.jar", randomBytes(50_000, 4)),
        TestZipEntry("Ani/stored.dat", patternBytes(10_000, 5), stored = true),
        TestZipEntry("Ani/empty.txt", ByteArray(0)),
    )
    private val packageFile: File = writeTestZip(tempDir.resolve("release.zip"), released)
    private val packageBytes: ByteArray = packageFile.readBytes()
    private val manifest: DesktopPackageManifest = manifestOf(packageFile)
    private val packageSha1: String =
        MessageDigest.getInstance("SHA-1").digest(packageBytes).joinToString("") { "%02x".format(it) }

    private val packageUrl = "/pkg.zip"
    private val target: File get() = saveDir.resolve("pkg.zip")

    /** 每个发布包请求的 Range 头, 没有的为 `null`. */
    private val rangeRequests = mutableListOf<String?>()
    private var supportRange = true
    private var serveManifest = true
    private var corruptRanges = false
    private var rangeDelay: CompletableDeferred<Unit>? = null

    private fun entry(name: String): DesktopPackageEntry = manifest.entries.first { it.name == name }

    private fun rangeOf(vararg names: String): String {
        val first = entry(names.first())
        val last = entry(names.last())
        return "bytes=${first.dataOffset}-${last.dataOffset + last.compressedSize - 1}"
    }

    /** 已安装目录里放上 [released] 中除 [changed] 以外的文件, 即旧版本与新版本只差这些文件. */
    private fun installOldVersion(vararg changed: String) {
        writeInstalledFiles(installRoot, released.filter { it.name !in changed })
        for (name in changed) {
            // 旧版本里同名文件内容不同
            installRoot.resolve(name.substringAfter('/')).apply { parentFile.mkdirs() }.writeBytes(randomBytes(1000, 99))
        }
    }

    private fun ApplicationTestBuilder.setupServer() {
        routing {
            get(packageUrl) {
                val range = call.request.headers[HttpHeaders.Range]
                rangeRequests += range
                if (range == null || !supportRange) {
                    call.respondBytes(packageBytes, ContentType.Application.OctetStream)
                    return@get
                }
                rangeDelay?.await()
                val (start, end) = range.removePrefix("bytes=").split("-").map { it.toInt() }
                val slice = packageBytes.copyOfRange(start, end + 1)
                // 只破坏增量下载的第一个请求, 回退的整包下载要能成功
                if (corruptRanges && rangeRequests.size == 1) slice[slice.size / 2] = (slice[slice.size / 2] + 1).toByte()
                call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/${packageBytes.size}")
                call.response.header(HttpHeaders.AcceptRanges, "bytes")
                call.respondBytes(slice, ContentType.Application.OctetStream, HttpStatusCode.PartialContent)
            }
            get("$packageUrl.manifest.json") {
                if (serveManifest) call.respondText(manifest.toJson(), ContentType.Application.Json)
                else call.respond(HttpStatusCode.NotFound)
            }
            get("$packageUrl.sha1") {
                call.respondText("$packageSha1\n")
            }
        }
    }

    private fun ApplicationTestBuilder.downloader(output: PackageOutput = PackageOutput.Zip): DeltaPackageDownloader =
        DeltaPackageDownloader(
            createClient {
                expectSuccess = true
                install(HttpTimeout)
            }.asScopedHttpClient(),
            installRoot = installRoot,
            output = output,
        )

    private val stagingDir: File get() = saveDir.resolve("pkg.zip" + PackageOutput.Directory.SUFFIX)

    /** 拼出的目录与发布包内容一致, 顶层的 `Ani/` 已去掉. */
    private fun assertStagedPackage() {
        assertTrue(stagingDir.isDirectory, "staging directory should exist")
        assertFalse(File(stagingDir.path + ".part").exists(), "partial directory should be removed")
        for (entry in released) {
            val file = stagingDir.resolve(entry.name.substringAfter('/'))
            if (entry.content == null) {
                if (entry.name != "Ani/") assertTrue(file.isDirectory, entry.name)
            } else {
                assertEquals(entry.content.toList(), file.readBytes().toList(), entry.name)
            }
        }
    }

    private suspend fun DeltaPackageDownloader.downloadPackage() = download(
        alternativeUrls = listOf(packageUrl),
        filenameProvider = { it.substringAfterLast('/') },
        saveDir = saveDir.toKtPath().inSystem,
    )

    private fun assertAssembledPackage() {
        assertTrue(target.isFile, "assembled package should exist")
        assertFalse(saveDir.resolve("pkg.zip.part").exists(), "partial file should be removed")
        val expected = zipContents(packageFile).mapValues { it.value.toList() }
        assertEquals(expected, zipContents(target).mapValues { it.value.toList() })
    }

    @Test
    fun `fetches only changed entries and coalesces by gap`() = testApplication {
        setupServer()
        installOldVersion("Ani/app/b.jar", "Ani/app/c.jar")
        val downloader = downloader()

        assertNotNull(downloader.downloadPackage())

        val state = assertIs<FileDownloaderState.Succeed>(downloader.state.value)
        assertTrue(state.checked)
        assertEquals(1f, downloader.progress.first())
        // b 和 c 之间隔着 2 MB 的 big.bin, 分两个请求
        assertEquals(listOf<String?>(rangeOf("Ani/app/b.jar"), rangeOf("Ani/app/c.jar")), rangeRequests)
        assertAssembledPackage()
    }

    @Test
    fun `adjacent changed entries share one request`() = testApplication {
        setupServer()
        installOldVersion("Ani/app/a.jar", "Ani/app/b.jar")

        assertNotNull(downloader().downloadPackage())

        assertEquals(listOf<String?>(rangeOf("Ani/app/a.jar", "Ani/app/b.jar")), rangeRequests)
        assertAssembledPackage()
    }

    @Test
    fun `renamed file is reused by content`() = testApplication {
        setupServer()
        installOldVersion()
        installRoot.resolve("app/a.jar").renameTo(installRoot.resolve("app/a-old.jar"))

        assertNotNull(downloader().downloadPackage())

        assertEquals(emptyList<String?>(), rangeRequests)
        assertAssembledPackage()
    }

    @Test
    fun `downloads nothing when every file is installed`() = testApplication {
        setupServer()
        installOldVersion()

        val downloader = downloader()
        assertNotNull(downloader.downloadPackage())

        assertEquals(emptyList<String?>(), rangeRequests)
        assertIs<FileDownloaderState.Succeed>(downloader.state.value)
        assertAssembledPackage()
    }

    @Test
    fun `falls back to a full download when the server ignores Range`() = testApplication {
        setupServer()
        supportRange = false
        installOldVersion("Ani/app/b.jar")

        val downloader = downloader()
        assertNotNull(downloader.downloadPackage())

        val state = assertIs<FileDownloaderState.Succeed>(downloader.state.value)
        assertTrue(state.checked)
        assertEquals(1f, downloader.progress.first())
        assertEquals(packageBytes.toList(), target.readBytes().toList())
    }

    @Test
    fun `falls back to a full download when there is no manifest`() = testApplication {
        setupServer()
        serveManifest = false
        installOldVersion("Ani/app/b.jar")

        val downloader = downloader()
        assertNotNull(downloader.downloadPackage())

        assertIs<FileDownloaderState.Succeed>(downloader.state.value)
        assertEquals(packageBytes.toList(), target.readBytes().toList())
    }

    @Test
    fun `falls back to a full download when a fetched entry is corrupted`() = testApplication {
        setupServer()
        corruptRanges = true
        installOldVersion("Ani/app/b.jar")

        val downloader = downloader()
        assertNotNull(downloader.downloadPackage())

        assertIs<FileDownloaderState.Succeed>(downloader.state.value)
        assertFalse(saveDir.resolve("pkg.zip.part").exists())
        assertEquals(packageBytes.toList(), target.readBytes().toList())
    }

    @Test
    fun `reuses an assembled package that matches the manifest`() = testApplication {
        setupServer()
        installOldVersion("Ani/app/b.jar")
        assertNotNull(downloader().downloadPackage())
        rangeRequests.clear()
        val assembledBytes = target.readBytes()

        val downloader = downloader()
        assertNotNull(downloader.downloadPackage())

        assertEquals(emptyList<String?>(), rangeRequests)
        assertIs<FileDownloaderState.Succeed>(downloader.state.value)
        assertEquals(assembledBytes.toList(), target.readBytes().toList())
    }

    @Test
    fun `directory output stages the unpacked new version`() = testApplication {
        setupServer()
        installOldVersion("Ani/app/b.jar", "Ani/app/c.jar")
        val downloader = downloader(PackageOutput.Directory)

        val result = assertNotNull(downloader.downloadPackage())

        assertEquals(stagingDir.path, result.toString())
        assertIs<FileDownloaderState.Succeed>(downloader.state.value)
        assertEquals(listOf<String?>(rangeOf("Ani/app/b.jar"), rangeOf("Ani/app/c.jar")), rangeRequests)
        assertStagedPackage()
        assertFalse(target.exists(), "no zip is written in directory mode")
    }

    @Test
    fun `directory output reuses an existing staging directory`() = testApplication {
        setupServer()
        installOldVersion("Ani/app/b.jar")
        assertNotNull(downloader(PackageOutput.Directory).downloadPackage())
        rangeRequests.clear()

        val downloader = downloader(PackageOutput.Directory)
        assertNotNull(downloader.downloadPackage())

        assertEquals(emptyList<String?>(), rangeRequests)
        assertIs<FileDownloaderState.Succeed>(downloader.state.value)
        assertStagedPackage()
    }

    @Test
    fun `directory output rebuilds a staging directory that no longer matches`() = testApplication {
        setupServer()
        installOldVersion("Ani/app/b.jar")
        assertNotNull(downloader(PackageOutput.Directory).downloadPackage())
        // 暂存目录里的文件可能是安装目录文件的硬链接, 先删再写, 不能原地改写
        stagingDir.resolve("app/a.jar").apply {
            delete()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        rangeRequests.clear()

        assertNotNull(downloader(PackageOutput.Directory).downloadPackage())

        assertEquals(listOf<String?>(rangeOf("Ani/app/b.jar")), rangeRequests)
        assertStagedPackage()
    }

    @Test
    fun `directory output falls back to a zip when a fetched entry is corrupted`() = testApplication {
        setupServer()
        corruptRanges = true
        installOldVersion("Ani/app/b.jar")

        val downloader = downloader(PackageOutput.Directory)
        val result = assertNotNull(downloader.downloadPackage())

        assertEquals(target.path, result.toString())
        assertIs<FileDownloaderState.Succeed>(downloader.state.value)
        assertFalse(stagingDir.exists())
        assertFalse(File(stagingDir.path + ".part").exists())
        assertEquals(packageBytes.toList(), target.readBytes().toList())
    }

    @Test
    fun `cancellation leaves no partial file`() = testApplication {
        setupServer()
        installOldVersion("Ani/app/b.jar")
        val gate = CompletableDeferred<Unit>()
        rangeDelay = gate
        val downloader = downloader()

        coroutineScope {
            val job = launch { downloader.downloadPackage() }
            withTimeout(10_000) {
                while (rangeRequests.isEmpty()) delay(10)
            }
            job.cancel()
            job.join()
            gate.complete(Unit)
        }

        assertIs<FileDownloaderState.Cancelled>(downloader.state.value)
        assertFalse(target.exists())
        assertFalse(saveDir.resolve("pkg.zip.part").exists())
    }

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }
}
