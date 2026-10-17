/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarInputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.readBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReproducibleJarTest {
    private val tempDir: Path = Files.createTempDirectory("reproducible-jar-test")

    private val manifest = "Manifest-Version: 1.0\n".toByteArray()
    private val classA = ByteArray(5000) { (it % 251).toByte() }
    private val classB = "class B".toByteArray()
    private val stored = "stored resource".toByteArray()

    private class Fixture(val name: String, val bytes: ByteArray, val method: Int = ZipEntry.DEFLATED)

    /** 内容相同, 但顺序、时间戳和 extra 字段都可以不同的 jar. */
    private fun writeJar(
        file: Path,
        order: List<Fixture>,
        time: Long,
        extra: ByteArray? = null,
        directoryEntry: Boolean = true,
    ): Path {
        ZipOutputStream(Files.newOutputStream(file)).use { out ->
            if (directoryEntry) {
                out.putNextEntry(ZipEntry("META-INF/").apply { this.time = time })
                out.closeEntry()
            }
            for (fixture in order) {
                val entry = ZipEntry(fixture.name).apply {
                    this.time = time
                    method = fixture.method
                    if (fixture.method == ZipEntry.STORED) {
                        size = fixture.bytes.size.toLong()
                        compressedSize = fixture.bytes.size.toLong()
                        crc = CRC32().apply { update(fixture.bytes) }.value
                    }
                    if (extra != null) this.extra = extra
                    if (fixture.name == "b/B.class") comment = "a comment"
                }
                out.putNextEntry(entry)
                out.write(fixture.bytes)
                out.closeEntry()
            }
        }
        return file
    }

    private fun fixtures() = listOf(
        Fixture("META-INF/MANIFEST.MF", manifest),
        Fixture("a/A.class", classA),
        Fixture("b/B.class", classB),
        Fixture("res/stored.txt", stored, ZipEntry.STORED),
    )

    private fun normalized(source: Path): Path {
        val target = tempDir.resolve(source.fileName.toString() + ".normalized")
        writeReproducibleJar(source, target)
        return target
    }

    private fun entries(jar: Path): List<ZipEntry> =
        ZipFile(jar.toFile()).use { it.entries().toList().map { entry -> ZipEntry(entry) } }

    private fun content(jar: Path): Map<String, ByteArray> = ZipFile(jar.toFile()).use { zip ->
        zip.entries().asSequence().filterNot { it.isDirectory }
            .associate { it.name to zip.getInputStream(it).use { input -> input.readBytes() } }
    }

    @Test
    fun `same content yields identical bytes regardless of order timestamps and extra fields`() {
        val oneHour = 60 * 60 * 1000L
        val first = writeJar(
            tempDir.resolve("first.jar"), fixtures(),
            time = 1_700_000_000_000L,
        )
        val second = writeJar(
            tempDir.resolve("second.jar"), fixtures().reversed(),
            time = 1_700_000_000_000L + oneHour,
            // 扩展时间戳字段 (0x5455), 真实的修改时间就藏在这里
            extra = byteArrayOf(0x55, 0x54, 5, 0, 1, 1, 2, 3, 4),
        )

        assertContentEquals(normalized(first).readBytes(), normalized(second).readBytes())
    }

    @Test
    fun `is idempotent`() {
        val jar = writeJar(tempDir.resolve("once.jar"), fixtures(), time = 1_700_000_000_000L)
        val once = normalized(jar)
        val twice = tempDir.resolve("twice.jar")
        writeReproducibleJar(once, twice)

        assertContentEquals(once.readBytes(), twice.readBytes())
    }

    @Test
    fun `keeps content methods and comments`() {
        val jar = writeJar(tempDir.resolve("content.jar"), fixtures().reversed(), time = 1_700_000_000_000L)
        val result = normalized(jar)

        val expected = content(jar)
        val actual = content(result)
        assertEquals(expected.keys, actual.keys)
        for ((name, bytes) in expected) {
            assertContentEquals(bytes, actual.getValue(name), name)
        }

        val byName = entries(result).associateBy { it.name }
        assertEquals(ZipEntry.STORED, byName.getValue("res/stored.txt").method)
        assertEquals(ZipEntry.DEFLATED, byName.getValue("a/A.class").method)
        assertEquals("a comment", byName.getValue("b/B.class").comment)
        assertEquals(true, byName.getValue("META-INF/").isDirectory)
    }

    @Test
    fun `manifest stays first and the rest is sorted by name`() {
        val jar = writeJar(tempDir.resolve("order.jar"), fixtures().reversed(), time = 1_700_000_000_000L)

        assertEquals(
            listOf("META-INF/", "META-INF/MANIFEST.MF", "a/A.class", "b/B.class", "res/stored.txt"),
            entries(normalized(jar)).map { it.name },
        )
    }

    @Test
    fun `manifest stays first without a META-INF directory entry`() {
        val jar = writeJar(
            tempDir.resolve("no-dir.jar"), fixtures().reversed(),
            time = 1_700_000_000_000L, directoryEntry = false,
        )

        assertEquals(
            listOf("META-INF/MANIFEST.MF", "a/A.class", "b/B.class", "res/stored.txt"),
            entries(normalized(jar)).map { it.name },
        )
    }

    @Test
    fun `entries carry the fixed timestamp and no extra field`() {
        val jar = writeJar(
            tempDir.resolve("time.jar"), fixtures(),
            time = System.currentTimeMillis(),
            extra = byteArrayOf(0x55, 0x54, 5, 0, 1, 1, 2, 3, 4),
        )

        for (entry in entries(normalized(jar))) {
            assertEquals(REPRODUCIBLE_JAR_ENTRY_TIME, entry.timeLocal, entry.name)
            assertNull(entry.extra, entry.name)
        }
    }

    @Test
    fun `rewritten jar is readable by ZipFile and JarInputStream`() {
        val jar = writeJar(tempDir.resolve("readable.jar"), fixtures().reversed(), time = 1_700_000_000_000L)
        val result = normalized(jar)

        JarInputStream(Files.newInputStream(result)).use { input ->
            assertEquals("1.0", input.manifest?.mainAttributes?.getValue("Manifest-Version"))
        }
        assertEquals(4, content(result).size)
    }

    @AfterTest
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }
}
