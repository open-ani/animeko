/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.engine.DummyMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.ui.framework.runComposeStateTest
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheManagementViewModelTest {
    @Test
    fun `overall stats should sum storage stats`() = runComposeStateTest {
        val storage1 = TestStorage(
            MediaStats(
                uploaded = 300L.bytes,
                downloaded = 1200L.bytes,
                uploadSpeed = 10L.bytes,
                downloadSpeed = 50L.bytes,
            ),
        )
        val storage2 = TestStorage(
            MediaStats(
                uploaded = 100L.bytes,
                downloaded = 800L.bytes,
                uploadSpeed = 5L.bytes,
                downloadSpeed = 20L.bytes,
            ),
        )

        val stats = MutableStateFlow(listOf<MediaCacheStorage>(storage1, storage2))
            .overallStatsFlow()
            .take(1)
            .toList()
            .single()

        assertEquals(2000L.bytes, stats.downloaded)
        assertEquals(400L.bytes, stats.uploaded)
        assertEquals(70L.bytes, stats.downloadSpeed)
        assertEquals(15L.bytes, stats.uploadSpeed)
    }

    @Test
    fun `overall stats should update when storage stats change`() = runComposeStateTest {
        val storage1Stats = MutableStateFlow(
            MediaStats(
                uploaded = 300L.bytes,
                downloaded = 1200L.bytes,
                uploadSpeed = 10L.bytes,
                downloadSpeed = 50L.bytes,
            ),
        )
        val storage2Stats = MutableStateFlow(
            MediaStats(
                uploaded = 100L.bytes,
                downloaded = 800L.bytes,
                uploadSpeed = 5L.bytes,
                downloadSpeed = 20L.bytes,
            ),
        )
        val storages = MutableStateFlow(
            listOf<MediaCacheStorage>(TestStorage(storage1Stats), TestStorage(storage2Stats)),
        )

        val emissions = mutableListOf<MediaStats>()
        val job = launch {
            storages.overallStatsFlow().take(2).toList(emissions)
        }

        testScheduler.runCurrent()
        storages.value = listOf(TestStorage(storage1Stats))
        testScheduler.runCurrent()
        job.join()

        assertEquals(2, emissions.size)
        assertEquals(2000L.bytes, emissions[0].downloaded)
        assertEquals(1200L.bytes, emissions[1].downloaded)
        assertEquals(400L.bytes, emissions[0].uploaded)
        assertEquals(300L.bytes, emissions[1].uploaded)
    }
}

private class TestStorage(
    override val stats: Flow<MediaStats>,
) : MediaCacheStorage {
    constructor(initialStats: MediaStats) : this(MutableStateFlow(initialStats))

    override val mediaSourceId: String = "test"
    override val cacheMediaSource: MediaSource
        get() = error("unused")
    override val engine = DummyMediaCacheEngine(mediaSourceId)
    override val listFlow = MutableStateFlow<List<MediaCache>>(emptyList())

    override suspend fun restorePersistedCaches() = Unit

    override suspend fun cache(
        media: me.him188.ani.datasources.api.Media,
        metadata: MediaCacheMetadata,
        episodeMetadata: me.him188.ani.app.domain.media.resolver.EpisodeMetadata,
        resume: Boolean,
    ): MediaCache = error("unused")

    override suspend fun delete(cache: MediaCache): Boolean = false

    override suspend fun deleteFirst(predicate: (MediaCache) -> Boolean): Boolean = false

    override fun close() = Unit
}
