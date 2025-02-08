/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.components

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import me.him188.ani.app.tools.Progress
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.framework.runComposeStateTest
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.Unspecified
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheEpisodeStateTest {
    class CalculateSizeTextTest {
        @Test
        fun `all unavailable`() {
            check(null, Unspecified, null)
        }

        @Test
        fun `progress unavailable - total size available`() =
            check("200.0 MB", 200.megaBytes, null)

        @Test
        fun `progress available - total size unavailable`() =
            check(null, Unspecified, 0.5f)

        @Test
        fun `all available`() =
            check("100.0 MB / 200.0 MB", 200.megaBytes, 0.5f)

        private fun check(expected: String?, total: FileSize, progress: Float?) {
            assertEquals(
                expected,
                CacheEpisodeState.calculateSizeText(
                    totalSize = total,
                    progress = progress,
                ),
            )
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("SameParameterValue")
    private fun cacheEpisode(
        sort: Int = 1,
        displayName: String = "翻转孤独",
        subjectId: Int = 1,
        episodeId: Int = 1,
        initialPaused: CacheEpisodePaused = when (sort % 2) {
            0 -> CacheEpisodePaused.PAUSED
            else -> CacheEpisodePaused.IN_PROGRESS
        },
        downloadSpeed: FileSize = 100.megaBytes,
        progress: Progress = 0.9f.toProgress(),
        totalSize: FileSize = 200.megaBytes,
    ): CacheEpisodeState {
        val state = mutableStateOf(initialPaused)
        return CacheEpisodeState(
            subjectId = subjectId,
            episodeId = episodeId,
            cacheId = "1",
            sort = EpisodeSort(sort),
            displayName = displayName,
            creationTime = 100,
            screenShots = stateOf(emptyList()),
            stats = stateOf(
                CacheEpisodeState.Stats(
                    downloadSpeed = downloadSpeed,
                    progress = progress,
                    totalSize = totalSize,
                ),
            ),
            state = state,
            onPause = { state.value = CacheEpisodePaused.PAUSED },
            onResume = { state.value = CacheEpisodePaused.IN_PROGRESS },
            onDelete = {},
            backgroundScope = GlobalScope,
        )
    }

    @Test
    fun `progress not available`() = runComposeStateTest {
        cacheEpisode(
            initialPaused = CacheEpisodePaused.IN_PROGRESS,
            downloadSpeed = 100.megaBytes,
            progress = Progress.Unspecified,
        ).run {
            assertEquals(false, isPaused)
            assertEquals(false, isFinished)
            assertEquals("200.0 MB", sizeText)
            assertEquals(null, progressText)
            assertEquals(Progress.Unspecified, progress)
            assertEquals(true, isProgressUnspecified)
        }
    }

    @Test
    fun `in progress and not finished`() = runComposeStateTest {
        cacheEpisode(
            initialPaused = CacheEpisodePaused.IN_PROGRESS,
            downloadSpeed = 100.megaBytes,
            progress = 0.1f.toProgress(),
        ).run {
            assertEquals(false, isPaused)
            assertEquals(false, isFinished)
            assertEquals("200.0 MB", sizeText)
            assertEquals("10.0%", progressText)
            assertEquals(0.1f, progress.getOrNull())
            assertEquals(false, isProgressUnspecified)
        }
    }

    @Test
    fun `in progress and finished`() = runComposeStateTest {
        cacheEpisode(
            initialPaused = CacheEpisodePaused.IN_PROGRESS,
            downloadSpeed = 100.megaBytes,
            progress = 1f.toProgress(),
        ).run {
            assertEquals(false, isPaused)
            assertEquals(true, isFinished)
            assertEquals("200.0 MB", sizeText)
            assertEquals(null, progressText)
            assertEquals(1f, progress.getOrNull())
            assertEquals(false, isProgressUnspecified)
        }
    }

    @Test
    fun `show speed if not finished`() = runComposeStateTest {
        cacheEpisode(
            initialPaused = CacheEpisodePaused.IN_PROGRESS,
            downloadSpeed = 100.megaBytes,
            progress = 0.1f.toProgress(),
        ).run {
            assertEquals("200.0 MB", sizeText)
            assertEquals("10.0%", progressText)
            assertEquals("100.0 MB/s", speedText)
        }
        cacheEpisode(
            initialPaused = CacheEpisodePaused.PAUSED,
            downloadSpeed = 100.megaBytes,
            progress = 0.1f.toProgress(),
        ).run {
            assertEquals("200.0 MB", sizeText)
            assertEquals("10.0%", progressText)
            assertEquals("100.0 MB/s", speedText)
        }
    }

    @Test
    fun `do not show speed if finished`() = runComposeStateTest {
        cacheEpisode(
            initialPaused = CacheEpisodePaused.IN_PROGRESS,
            downloadSpeed = 100.megaBytes,
            progress = 1f.toProgress(),
        ).run {
            assertEquals("200.0 MB", sizeText)
            assertEquals(null, progressText)
            assertEquals(null, speedText)
        }
        cacheEpisode(
            initialPaused = CacheEpisodePaused.PAUSED,
            downloadSpeed = 100.megaBytes,
            progress = 1f.toProgress(),
        ).run {
            assertEquals("200.0 MB", sizeText)
            assertEquals(null, progressText)
            assertEquals(null, speedText)
        }
        cacheEpisode(
            initialPaused = CacheEpisodePaused.PAUSED,
            downloadSpeed = 100.megaBytes,
            progress = 2f.toProgress(),
        ).run {
            assertEquals("200.0 MB", sizeText)
            assertEquals(null, progressText)
            assertEquals(null, speedText)
        }
    }
}
