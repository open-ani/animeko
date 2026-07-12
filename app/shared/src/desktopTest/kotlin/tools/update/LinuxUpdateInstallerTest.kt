/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

class LinuxUpdateInstallerTest {
    @Test
    fun `converts package URLs to zsync URLs`() {
        assertEquals(
            listOf(
                "https://example.com/ani.appimage.zsync",
                "https://example.com/already.appimage.zsync",
            ),
            listOf(
                "https://example.com/ani.appimage",
                "https://example.com/already.appimage.zsync",
            ).toAppImageZsyncUrls(),
        )
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    fun `update script retries sources and exits without relaunching app`() {
        val tempDir = createTempDirectory("linux-update-installer-test").toFile()
        val updateLog = tempDir.resolve("updates.log")
        val launchMarker = tempDir.resolve("launched")

        val appImage = tempDir.resolve("Animeko.AppImage").apply {
            writeText(
                """
                    #!/usr/bin/env bash
                    touch "${launchMarker.absolutePath}"
                """.trimIndent(),
            )
            assertTrue(setExecutable(true))
        }
        val updater = tempDir.resolve(LINUX_APPIMAGE_UPDATE_TOOL).apply {
            writeText(
                """
                    #!/usr/bin/env bash
                    echo "${'$'}3" >> "${updateLog.absolutePath}"
                    if [[ "${'$'}3" == "zsync|https://fallback.example/update.zsync" ]]; then
                      mv "${'$'}4" "${'$'}4.zs-old"
                      printf 'updated' > "${'$'}4"
                      exit 0
                    fi
                    exit 1
                """.trimIndent(),
            )
            assertTrue(setExecutable(true))
        }
        val script = tempDir.resolve("update.sh").apply {
            writeText(LINUX_APPIMAGE_UPDATE_SCRIPT)
            assertTrue(setExecutable(true))
        }

        val process = ProcessBuilder(
            script.absolutePath,
            appImage.absolutePath,
            updater.absolutePath,
            "zsync|https://primary.example/update.zsync",
            "zsync|https://fallback.example/update.zsync",
        ).start()

        assertTrue(process.waitFor(10, TimeUnit.SECONDS))
        assertEquals(0, process.exitValue())
        assertEquals(
            listOf(
                "zsync|https://primary.example/update.zsync",
                "zsync|https://fallback.example/update.zsync",
            ),
            updateLog.readLines(),
        )
        assertFalse(launchMarker.exists())
        assertEquals("updated", appImage.readText())
        assertTrue(tempDir.listFiles().orEmpty().none { it.name.startsWith(".Animeko.AppImage.update.") })
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    fun `failed update leaves original AppImage untouched`() {
        val tempDir = createTempDirectory("linux-update-installer-test").toFile()
        val appImage = tempDir.resolve("Animeko.AppImage").apply {
            writeText("original")
            assertTrue(setExecutable(true))
        }
        val updater = tempDir.resolve(LINUX_APPIMAGE_UPDATE_TOOL).apply {
            writeText(
                """
                    #!/usr/bin/env bash
                    mv "${'$'}4" "${'$'}4.zs-old"
                    printf 'incomplete' > "${'$'}4"
                    exit 1
                """.trimIndent(),
            )
            assertTrue(setExecutable(true))
        }
        val script = tempDir.resolve("update.sh").apply {
            writeText(LINUX_APPIMAGE_UPDATE_SCRIPT)
            assertTrue(setExecutable(true))
        }

        val process = ProcessBuilder(
            script.absolutePath,
            appImage.absolutePath,
            updater.absolutePath,
            "zsync|https://example.com/update.zsync",
        ).start()

        assertTrue(process.waitFor(10, TimeUnit.SECONDS))
        assertEquals(1, process.exitValue())
        assertEquals("original", appImage.readText())
        assertTrue(tempDir.listFiles().orEmpty().none { it.name.startsWith(".Animeko.AppImage.update.") })
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    fun `cancelled update leaves original AppImage untouched`() {
        val tempDir = createTempDirectory("linux-update-installer-test").toFile()
        val appImage = tempDir.resolve("Animeko.AppImage").apply {
            writeText("original")
            assertTrue(setExecutable(true))
        }
        val updateStarted = tempDir.resolve("update-started")
        val updater = tempDir.resolve(LINUX_APPIMAGE_UPDATE_TOOL).apply {
            writeText(
                """
                    #!/usr/bin/env bash
                    mv "${'$'}4" "${'$'}4.zs-old"
                    touch "${updateStarted.absolutePath}"
                    sleep 30
                    printf 'updated' > "${'$'}4"
                """.trimIndent(),
            )
            assertTrue(setExecutable(true))
        }
        val script = tempDir.resolve("update.sh").apply {
            writeText(LINUX_APPIMAGE_UPDATE_SCRIPT)
            assertTrue(setExecutable(true))
        }

        val process = ProcessBuilder(
            script.absolutePath,
            appImage.absolutePath,
            updater.absolutePath,
            "zsync|https://example.com/update.zsync",
        ).start()
        val startDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!updateStarted.exists() && System.nanoTime() < startDeadline) {
            Thread.sleep(10)
        }
        assertTrue(updateStarted.exists())

        process.descendants().use { descendants -> descendants.forEach { it.destroy() } }
        process.destroy()

        assertTrue(process.waitFor(10, TimeUnit.SECONDS))
        assertEquals("original", appImage.readText())
        assertTrue(tempDir.listFiles().orEmpty().none { it.name.startsWith(".Animeko.AppImage.update.") })
    }
}
