/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.test.Test
import kotlin.test.assertEquals


/**
 * @see me.him188.ani.app.domain.media.cache.MediaAutoCacheService
 */
class MediaAutoCacheServiceTest {
    private var id = 0
    private fun ep(
        id: Int = this.id++,
        airDate: PackedDate = PackedDate(0, 1, 1)
    ) = EpisodeInfo(
        episodeId = id, EpisodeType.MainStory,
        name = "Diana Houston",
        nameCn = "Nita O'Donnell",
        sort = EpisodeSort(1),
        comment = 5931,
        desc = "gubergren",
        airDate = airDate, // 一定已经开播
        ep = EpisodeSort(1),
    )

    @Test
    fun `no cache for watched`() = runTest {
        val res = DefaultMediaAutoCacheService.firstEpisodeToCache(
            listOf(
                EpisodeCollectionInfo(
                    ep(),
                    collectionType = UnifiedCollectionType.DONE,
                ),
            ),
            hasAlreadyCached = {
                false
            },
            recurrence = null,
        ).toList()

        assertEquals(0, res.size)
    }

    @Test
    fun `no cache for not broadcast`() = runTest {
        val res = DefaultMediaAutoCacheService.firstEpisodeToCache(
            listOf(
                EpisodeCollectionInfo(
                    ep(airDate = PackedDate(9999, 1, 1)),
                    collectionType = UnifiedCollectionType.WISH,
                ),
            ),
            hasAlreadyCached = {
                false
            },
            recurrence = null,
        ).toList()

        assertEquals(0, res.size)
    }

    @Test
    fun `no cache for already cached`() = runTest {
        val res = DefaultMediaAutoCacheService.firstEpisodeToCache(
            listOf(
                EpisodeCollectionInfo(
                    ep(),
                    collectionType = UnifiedCollectionType.WISH,
                ),
            ),
            hasAlreadyCached = {
                true
            },
            recurrence = null,
        ).toList()

        assertEquals(0, res.size)
    }

    @Test
    fun `cache unwatched`() = runTest {
        val res = DefaultMediaAutoCacheService.firstEpisodeToCache(
            listOf(
                EpisodeCollectionInfo(
                    ep(),
                    collectionType = UnifiedCollectionType.WISH,
                ),
            ),
            hasAlreadyCached = {
                false
            },
            recurrence = null,
        ).toList()

        assertEquals(1, res.size)
    }

    @Test
    fun `cache unwatched multiple`() = runTest {
        val res = DefaultMediaAutoCacheService.firstEpisodeToCache(
            listOf(
                EpisodeCollectionInfo(
                    ep(id = 0),
                    collectionType = UnifiedCollectionType.DONE,
                ),
                EpisodeCollectionInfo(
                    ep(id = 1),
                    collectionType = UnifiedCollectionType.WISH,
                ),
                EpisodeCollectionInfo(
                    ep(id = 2),
                    collectionType = UnifiedCollectionType.WISH,
                ),
                EpisodeCollectionInfo(
                    ep(id = 3),
                    collectionType = UnifiedCollectionType.WISH,
                ),
            ),
            hasAlreadyCached = {
                false
            },
            recurrence = null,
        ).toList()

        assertEquals(3, res.size)
        assertEquals(1, res.first().episodeInfo.episodeId)
    }

    @Test
    fun `no cache when already cached many`() = runTest {
        val res = DefaultMediaAutoCacheService.firstEpisodeToCache(
            listOf(
                EpisodeCollectionInfo(
                    ep(id = 0),
                    collectionType = UnifiedCollectionType.WISH,
                ),
                EpisodeCollectionInfo(
                    ep(id = 1),
                    collectionType = UnifiedCollectionType.WISH,
                ),
                EpisodeCollectionInfo(
                    ep(id = 2),
                    collectionType = UnifiedCollectionType.WISH,
                ),
                EpisodeCollectionInfo(
                    ep(id = 3),
                    collectionType = UnifiedCollectionType.WISH,
                ),
            ),
            hasAlreadyCached = {
                it.episodeInfo.episodeId <= 0
            },
            maxCount = 1,
            recurrence = null,
        ).toList()

        assertEquals(0, res.size)
    }

    @Test
    fun `cache until max count`() = runTest {
        val res = DefaultMediaAutoCacheService.firstEpisodeToCache(
            listOf(
                EpisodeCollectionInfo(
                    ep(id = 0),
                    collectionType = UnifiedCollectionType.WISH,
                ),
                EpisodeCollectionInfo(
                    ep(id = 1),
                    collectionType = UnifiedCollectionType.WISH,
                ),
                EpisodeCollectionInfo(
                    ep(id = 2),
                    collectionType = UnifiedCollectionType.WISH,
                ),
                EpisodeCollectionInfo(
                    ep(id = 3),
                    collectionType = UnifiedCollectionType.WISH,
                ),
            ),
            hasAlreadyCached = {
                it.episodeInfo.episodeId <= 1
            },
            maxCount = 3,
            recurrence = null,
        ).toList()

        assertEquals(listOf(2), res.map { it.episodeInfo.episodeId })
    }

    @Test
    fun `cache until max count when nothing cached`() = runTest {
        val res = DefaultMediaAutoCacheService.firstEpisodeToCache(
            listOf(
                EpisodeCollectionInfo(
                    ep(id = 0),
                    collectionType = UnifiedCollectionType.WISH,
                ),
                EpisodeCollectionInfo(
                    ep(id = 1),
                    collectionType = UnifiedCollectionType.WISH,
                ),
                EpisodeCollectionInfo(
                    ep(id = 2),
                    collectionType = UnifiedCollectionType.WISH,
                ),
                EpisodeCollectionInfo(
                    ep(id = 3),
                    collectionType = UnifiedCollectionType.WISH,
                ),
            ),
            hasAlreadyCached = {
                false
            },
            maxCount = 1,
            recurrence = null,
        ).toList()

        assertEquals(listOf(0), res.map { it.episodeInfo.episodeId })
    }
}
