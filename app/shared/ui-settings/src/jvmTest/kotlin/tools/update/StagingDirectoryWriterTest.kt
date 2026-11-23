/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StagingDirectoryWriterTest {
    private val tempDir = createTempDirectory("staging-writer-test").toFile()

    private fun packageWith(content: ByteArray): Pair<File, DesktopPackageEntry> {
        val zip = writeTestZip(tempDir.resolve("package.zip"), listOf(TestZipEntry("Ani/app/data.bin", content)))
        val entry = manifestOf(zip).entries.single { it.name == "Ani/app/data.bin" }
        return zip to entry
    }

    private fun rawData(zip: File, entry: DesktopPackageEntry): ByteArray =
        zip.readBytes().copyOfRange(entry.dataOffset.toInt(), (entry.dataOffset + entry.compressedSize).toInt())

    /**
     * 在另一个线程里写 [data], 返回抛出的异常; 超过 [timeoutMillis] 没结束视为挂住.
     */
    private fun writeRaw(entry: DesktopPackageEntry, data: ByteArray, timeoutMillis: Long = 10_000): Throwable? {
        val failure = AtomicReference<Throwable?>()
        val worker = thread(isDaemon = true) {
            runCatching {
                runBlocking {
                    StagingDirectoryWriter(tempDir.resolve("staging").apply { mkdirs() })
                        .writeRawEntry(entry) { out -> out.write(data) }
                }
            }.onFailure { failure.set(it) }
        }
        worker.join(timeoutMillis)
        assertFalse(worker.isAlive, "writeRawEntry did not finish within ${timeoutMillis}ms")
        return failure.get()
    }

    @Test
    fun `writes a deflated entry and verifies it`() {
        val (zip, entry) = packageWith(patternBytes(300_000, seed = 1))
        val failure = writeRaw(entry, rawData(zip, entry))
        assertTrue(failure == null, "Unexpected failure: $failure")
        assertTrue(tempDir.resolve("staging/app/data.bin").readBytes().contentEquals(patternBytes(300_000, seed = 1)))
    }

    @Test
    fun `rejects compressed data with every byte flipped without hanging`() {
        val (zip, entry) = packageWith(patternBytes(300_000, seed = 2))
        val flipped = rawData(zip, entry).map { (it.toInt() xor 0xFF).toByte() }.toByteArray()
        assertNotNull(writeRaw(entry, flipped))
    }

    @Test
    fun `rejects compressed data with one byte changed without hanging`() {
        val (zip, entry) = packageWith(randomBytes(300_000, seed = 3))
        val damaged = rawData(zip, entry).also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        assertNotNull(writeRaw(entry, damaged))
    }

    @Test
    fun `rejects compressed data that is cut short`() {
        val (zip, entry) = packageWith(patternBytes(300_000, seed = 4))
        val cut = rawData(zip, entry).copyOf(rawData(zip, entry).size / 2)
        val failure = writeRaw(entry, cut)
        assertIs<IllegalStateException>(failure)
    }

    private fun DesktopPackageEntry.withMode(mode: Int) = DesktopPackageEntry(
        name = name,
        method = method,
        crc32 = crc32,
        compressedSize = compressedSize,
        size = size,
        dataOffset = dataOffset,
        dosTime = dosTime,
        externalAttributes = mode.toLong() shl 16,
        sha256 = sha256,
    )

    private fun File.fileKey(): Any = Files.readAttributes(toPath(), BasicFileAttributes::class.java).fileKey()

    @Test
    fun `keeps the top-level directory when asked`() {
        val (zip, entry) = packageWith(patternBytes(1000, seed = 5))
        val staging = tempDir.resolve("staging").apply { mkdirs() }
        runBlocking {
            StagingDirectoryWriter(staging, stripTopLevel = false).writeRawEntry(entry) { it.write(rawData(zip, entry)) }
        }
        assertTrue(staging.resolve("Ani/app/data.bin").isFile)
        assertFalse(staging.resolve("app").exists())
    }

    @Test
    @EnabledOnOs(OS.MAC, OS.LINUX)
    fun `restores unix permissions of a fetched entry`() {
        val (zip, entry) = packageWith(patternBytes(1000, seed = 6))
        val staging = tempDir.resolve("staging").apply { mkdirs() }
        val executable = entry.withMode("755".toInt(8))
        runBlocking {
            StagingDirectoryWriter(staging).writeRawEntry(executable) { it.write(rawData(zip, entry)) }
        }
        val file = staging.resolve("app/data.bin")
        assertEquals("755".toInt(8), posixModeOf(file))
        assertTrue(file.canExecute())
    }

    @Test
    @EnabledOnOs(OS.MAC, OS.LINUX)
    fun `links a local file only when its permissions match`() {
        val content = patternBytes(1000, seed = 7)
        val (_, entry) = packageWith(content)
        val installed = tempDir.resolve("installed/data.bin").apply {
            parentFile.mkdirs()
            writeBytes(content)
        }
        applyPosixMode(installed, "644".toInt(8))

        val linked = tempDir.resolve("linked").apply { mkdirs() }
        StagingDirectoryWriter(linked).writeLocalEntry(entry.withMode("644".toInt(8)), installed)
        assertEquals(installed.fileKey(), linked.resolve("app/data.bin").fileKey(), "same permissions: hard link")

        val copied = tempDir.resolve("copied").apply { mkdirs() }
        StagingDirectoryWriter(copied).writeLocalEntry(entry.withMode("755".toInt(8)), installed)
        val copy = copied.resolve("app/data.bin")
        assertNotEquals(installed.fileKey(), copy.fileKey(), "different permissions: copy")
        assertEquals("755".toInt(8), posixModeOf(copy))
        assertEquals("644".toInt(8), posixModeOf(installed), "the installed file must not change")
    }

    @Test
    @EnabledOnOs(OS.MAC, OS.LINUX)
    fun `verify rejects wrong permissions`() {
        val (zip, entry) = packageWith(patternBytes(1000, seed = 8))
        val executable = entry.withMode("755".toInt(8))
        val manifest = DesktopPackageManifest(
            formatVersion = DesktopPackageManifest.SUPPORTED_FORMAT_VERSION,
            packageSize = zip.length(),
            entries = listOf(executable),
        )
        val staging = tempDir.resolve("staging").apply { mkdirs() }
        runBlocking {
            StagingDirectoryWriter(staging).writeRawEntry(executable) { it.write(rawData(zip, entry)) }
        }
        StagingDirectoryWriter.verify(staging, manifest)
        applyPosixMode(staging.resolve("app/data.bin"), "644".toInt(8))
        assertFailsWith<IllegalStateException> { StagingDirectoryWriter.verify(staging, manifest) }
    }
}
