/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.preference.MediaCacheSettings
import me.him188.ani.app.data.repository.user.Settings
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopMediaSaveDirTest {
    @Test
    fun `first startup persists the default directory and preserves other settings`() = runBlocking {
        val settings = TestSettings(MediaCacheSettings(enabled = true, maxCountPerSubject = 5))
        val defaultDir = File("default-cache").absolutePath

        assertEquals(defaultDir, resolveDesktopMediaSaveDir(settings, defaultDir))
        assertEquals(MediaCacheSettings(enabled = true, maxCountPerSubject = 5, saveDir = defaultDir), settings.flow.value)
        assertEquals(1, settings.writes)
        assertEquals(defaultDir, resolveDesktopMediaSaveDir(settings, defaultDir))
        assertEquals(1, settings.writes)
    }

    @Test
    fun `unavailable directory survives repeated startups without rewriting settings`() = runBlocking {
        val root = createTempDirectory("ani-cache-dir-test").toFile()
        try {
            val configuredDir = root.resolve("unmounted-share/cache")
            val settings = TestSettings(MediaCacheSettings(saveDir = configuredDir.path))
            repeat(2) {
                assertEquals(configuredDir.absolutePath, resolveDesktopMediaSaveDir(settings, root.resolve("default").path))
                assertEquals(configuredDir.path, settings.flow.value.saveDir)
            }
            assertEquals(0, settings.writes)
            assertFalse(configuredDir.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `existing directory remains selected`() = runBlocking {
        val root = createTempDirectory("ani-cache-dir-test").toFile()
        try {
            val settings = TestSettings(MediaCacheSettings(saveDir = root.path))
            assertEquals(root.absolutePath, resolveDesktopMediaSaveDir(settings, root.resolve("default").path))
            assertEquals(0, settings.writes)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `forward slash paths use the same absolute representation as the torrent engine`() = runBlocking {
        val paths = if (File.separatorChar == '\\') {
            listOf("Z:/anitorrent", "//nas/share/anitorrent")
        } else {
            listOf("/mnt/share/anitorrent", "relative/cache")
        }
        for (configuredDir in paths) {
            val settings = TestSettings(MediaCacheSettings(saveDir = configuredDir))
            val baseDir = resolveDesktopMediaSaveDir(settings, "default-cache")
            val torrentDir = File(baseDir).resolve("anitorrent/torrent-hash").absolutePath

            assertEquals(File(configuredDir).absolutePath, baseDir)
            assertEquals(
                "${File.separator}anitorrent${File.separator}torrent-hash",
                torrentDir.substringAfter(baseDir),
            )
            assertEquals(configuredDir, settings.flow.value.saveDir)
            assertEquals(0, settings.writes)
        }
    }

    private class TestSettings(initial: MediaCacheSettings) : Settings<MediaCacheSettings> {
        override val flow = MutableStateFlow(initial)
        var writes = 0
            private set

        override suspend fun set(value: MediaCacheSettings) {
            writes++
            flow.value = value
        }
    }
}
