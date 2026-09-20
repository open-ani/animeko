/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.player

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BufferingMediaCacheProgressProviderTest {
    private val buffered = MutableStateFlow(-1L)
    private val duration = MutableStateFlow<Long?>(null)
    private val provider = BufferingMediaCacheProgressProvider(buffered, duration)

    @Test
    fun `empty when buffered position unknown`() = runTest {
        duration.value = 100_000L
        provider.flow.test {
            assertEquals(MediaCacheProgressInfo.Empty, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `empty when duration unknown`() = runTest {
        buffered.value = 10_000L
        provider.flow.test {
            assertEquals(MediaCacheProgressInfo.Empty, awaitItem())
            duration.value = 0L
            expectNoEvents()
        }
    }

    @Test
    fun `done chunk covers buffered ratio`() = runTest {
        duration.value = 100_000L
        buffered.value = 25_000L
        provider.flow.test {
            val info = awaitItem()
            assertEquals(listOf(ChunkState.DONE, ChunkState.NONE), info.chunkStates)
            assertEquals(0.25f, info.chunkWeights[0])
            assertEquals(0.75f, info.chunkWeights[1])
        }
    }

    @Test
    fun `emits when buffered position advances`() = runTest {
        duration.value = 100_000L
        buffered.value = 0L
        provider.flow.test {
            assertEquals(0f, awaitItem().chunkWeights[0])
            buffered.value = 50_000L
            assertEquals(0.5f, awaitItem().chunkWeights[0])
            buffered.value = 100_000L
            assertEquals(1f, awaitItem().chunkWeights[0])
            expectNoEvents()
        }
    }

    @Test
    fun `sub-permille changes are not emitted`() = runTest {
        duration.value = 100_000L
        buffered.value = 50_000L
        provider.flow.test {
            assertEquals(0.5f, awaitItem().chunkWeights[0])
            buffered.value = 50_040L
            expectNoEvents()
            buffered.value = 50_100L
            assertEquals(0.501f, awaitItem().chunkWeights[0])
        }
    }

    @Test
    fun `buffered beyond duration is clamped`() = runTest {
        duration.value = 100_000L
        buffered.value = 120_000L
        provider.flow.test {
            val info = awaitItem()
            assertEquals(1f, info.chunkWeights[0])
            assertEquals(0f, info.chunkWeights[1])
        }
    }

    @Test
    fun `returns to empty when position becomes unknown`() = runTest {
        duration.value = 100_000L
        buffered.value = 50_000L
        provider.flow.test {
            awaitItem()
            buffered.value = -1L
            assertEquals(MediaCacheProgressInfo.Empty, awaitItem())
        }
    }
}
