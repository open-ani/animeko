/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.media

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.persistent.database.createTestAniDatabase
import me.him188.ani.app.data.persistent.database.dao.WebSearchSessionCacheEntity
import me.him188.ani.app.domain.mediasource.web.WebSearchEpisodeInfo
import me.him188.ani.app.domain.mediasource.web.WebSearchSubjectInfo
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * [SelectorMediaSourceEpisodeCacheRepository] 的行为测试, 使用真实的内存 Room 库.
 *
 * 覆盖: TTL 取小值与禁用、过期不可读与清理、按条目/数据源隔离与清除、同查询多页面共存、页面整体替换、后台刷新阈值判定.
 */
class SelectorMediaSourceEpisodeCacheRepositoryTest {
    private val mediaSourceId = "test-source"
    private val subjectName = "孤独摇滚"

    private fun runRepositoryTest(
        userTtl: Duration = 1.hours,
        refreshThreshold: Duration = Duration.ZERO,
        block: suspend (AniDatabase, SelectorMediaSourceEpisodeCacheRepository) -> Unit,
    ) = runBlocking {
        val database = createTestAniDatabase()
        try {
            block(
                database,
                SelectorMediaSourceEpisodeCacheRepository(
                    database.webSearchSessionCacheDao(),
                    userTtlFlow = flowOf(userTtl),
                    refreshThresholdFlow = flowOf(refreshThreshold),
                ),
            )
        } finally {
            database.close()
        }
    }

    private fun subjectInfo(url: String = "https://example.com/subject/1", name: String = subjectName) =
        WebSearchSubjectInfo(
            internalId = "1",
            name = name,
            fullUrl = url,
            partialUrl = "/subject/1",
            origin = null,
        )

    private fun episode(sort: Int, channel: String? = "线路1") = WebSearchEpisodeInfo(
        channel = channel,
        name = "第0${sort}集",
        episodeSortOrEp = EpisodeSort(sort),
        playUrl = "https://example.com/play/$sort",
    )

    private fun expiredRow(
        requesterSubjectId: Int?,
        mediaSourceId: String = this.mediaSourceId,
        subjectName: String = this.subjectName,
        expiresAt: Long,
    ) = WebSearchSessionCacheEntity(
        requesterSubjectId = requesterSubjectId,
        mediaSourceId = mediaSourceId,
        subjectName = subjectName,
        subjectPageName = subjectName,
        subjectInternalId = "1",
        subjectUrl = "https://example.com/subject/1",
        subjectPartialUrl = "/subject/1",
        channel = "线路1",
        episodeName = "第01集",
        episodeSortOrEp = EpisodeSort(1),
        playUrl = "https://example.com/play/1",
        cachedAt = expiresAt - 1000,
        expiresAt = expiresAt,
    )

    @Test
    fun `写入后可读回, 保持页面上的剧集顺序`() = runRepositoryTest { _, repository ->
        repository.addCache(
            1, mediaSourceId, subjectName, subjectInfo(),
            listOf(episode(1), episode(2), episode(3)),
            sourceCacheTtl = 1.hours,
        )

        val caches = repository.getCache(1, mediaSourceId, subjectName)
        assertEquals(1, caches.size)
        assertEquals(subjectName, caches.single().webSubjectInfo.name)
        assertEquals(
            listOf(EpisodeSort(1), EpisodeSort(2), EpisodeSort(3)),
            caches.single().webEpisodeInfos.map { it.episodeSortOrEp },
        )
        assertEquals("线路1", caches.single().webEpisodeInfos.first().channel)
    }

    @Test
    fun `TTL 取数据源配置与用户设置的较小者`() = runRepositoryTest(userTtl = 30.minutes) { database, repository ->
        repository.addCache(
            1, mediaSourceId, subjectName, subjectInfo(), listOf(episode(1)),
            sourceCacheTtl = 1.hours, // 大于用户设置, 应当取用户设置
        )
        val row = database.webSearchSessionCacheDao()
            .filterBySubjectName(1, mediaSourceId, subjectName, currentTimeMillis())
            .single()
        assertEquals(30.minutes.inWholeMilliseconds, row.expiresAt - row.cachedAt)
    }

    @Test
    fun `TTL 取数据源配置与用户设置的较小者 - 数据源更小`() = runRepositoryTest(userTtl = 1.hours) { database, repository ->
        repository.addCache(
            1, mediaSourceId, subjectName, subjectInfo(), listOf(episode(1)),
            sourceCacheTtl = 5.minutes,
        )
        val row = database.webSearchSessionCacheDao()
            .filterBySubjectName(1, mediaSourceId, subjectName, currentTimeMillis())
            .single()
        assertEquals(5.minutes.inWholeMilliseconds, row.expiresAt - row.cachedAt)
    }

    @Test
    fun `TTL 为 0 时不写入缓存`() = runRepositoryTest(userTtl = Duration.ZERO) { _, repository ->
        repository.addCache(
            1, mediaSourceId, subjectName, subjectInfo(), listOf(episode(1)),
            sourceCacheTtl = 1.hours,
        )
        assertTrue(repository.getCache(1, mediaSourceId, subjectName).isEmpty())
    }

    @Test
    fun `TTL 改为 0 后写入会清除该页面已有的缓存`() = runBlocking {
        val database = createTestAniDatabase()
        try {
            val dao = database.webSearchSessionCacheDao()
            SelectorMediaSourceEpisodeCacheRepository(
                dao, flowOf(1.hours),
                refreshThresholdFlow = flowOf(Duration.ZERO),
            )
                .addCache(1, mediaSourceId, subjectName, subjectInfo(), listOf(episode(1)), 1.hours)

            val disabled = SelectorMediaSourceEpisodeCacheRepository(
                dao, flowOf(Duration.ZERO),
                refreshThresholdFlow = flowOf(Duration.ZERO),
            )
            disabled.addCache(1, mediaSourceId, subjectName, subjectInfo(), listOf(episode(1)), 1.hours)

            assertTrue(disabled.getCache(1, mediaSourceId, subjectName).isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun `过期的行不会被读取`() = runRepositoryTest { database, repository ->
        database.webSearchSessionCacheDao()
            .insertAll(listOf(expiredRow(1, expiresAt = currentTimeMillis() - 1)))

        assertTrue(repository.getCache(1, mediaSourceId, subjectName).isEmpty())
    }

    @Test
    fun `purgeExpired 只删除已过期的行`() = runRepositoryTest { database, repository ->
        val dao = database.webSearchSessionCacheDao()
        dao.insertAll(
            listOf(
                expiredRow(1, mediaSourceId = "expired-source", expiresAt = currentTimeMillis() - 1),
            ),
        )
        repository.addCache(
            1, mediaSourceId, subjectName, subjectInfo(), listOf(episode(1)),
            sourceCacheTtl = 1.hours,
        )

        repository.purgeExpired()

        assertTrue(dao.filterBySubjectName(1, "expired-source", subjectName, 0).isEmpty())
        assertEquals(1, repository.getCache(1, mediaSourceId, subjectName).size)
    }

    @Test
    fun `不同条目的缓存互相隔离`() = runRepositoryTest { _, repository ->
        repository.addCache(1, mediaSourceId, subjectName, subjectInfo(), listOf(episode(1)), 1.hours)

        // 条目 2 使用相同的搜索名, 也读不到条目 1 的缓存
        assertTrue(repository.getCache(2, mediaSourceId, subjectName).isEmpty())

        repository.addCache(2, mediaSourceId, subjectName, subjectInfo(), listOf(episode(2)), 1.hours)
        assertEquals(
            listOf(EpisodeSort(1)),
            repository.getCache(1, mediaSourceId, subjectName).single().webEpisodeInfos.map { it.episodeSortOrEp },
        )
        assertEquals(
            listOf(EpisodeSort(2)),
            repository.getCache(2, mediaSourceId, subjectName).single().webEpisodeInfos.map { it.episodeSortOrEp },
        )
    }

    @Test
    fun `clearByRequestedSubject 只清除该条目的缓存`() = runRepositoryTest { _, repository ->
        repository.addCache(1, mediaSourceId, subjectName, subjectInfo(), listOf(episode(1)), 1.hours)
        repository.addCache(2, mediaSourceId, subjectName, subjectInfo(), listOf(episode(1)), 1.hours)

        repository.clearByRequestedSubject(1)

        assertTrue(repository.getCache(1, mediaSourceId, subjectName).isEmpty())
        assertEquals(1, repository.getCache(2, mediaSourceId, subjectName).size)
    }

    @Test
    fun `clearByRequestedSubjectAndSource 只清除该数据源的缓存`() = runRepositoryTest { _, repository ->
        repository.addCache(1, "source-a", subjectName, subjectInfo(), listOf(episode(1)), 1.hours)
        repository.addCache(1, "source-b", subjectName, subjectInfo(), listOf(episode(1)), 1.hours)

        repository.clearByRequestedSubjectAndSource(1, "source-a")

        assertTrue(repository.getCache(1, "source-a", subjectName).isEmpty())
        assertEquals(1, repository.getCache(1, "source-b", subjectName).size)
    }

    @Test
    fun `同一查询的多个条目页面互不覆盖`() = runRepositoryTest { _, repository ->
        repository.addCache(
            1, mediaSourceId, subjectName,
            subjectInfo(url = "https://example.com/subject/1", name = "孤独摇滚"),
            listOf(episode(1)), 1.hours,
        )
        repository.addCache(
            1, mediaSourceId, subjectName,
            subjectInfo(url = "https://example.com/subject/2", name = "孤独摇滚 第二季"),
            listOf(episode(2)), 1.hours,
        )

        val caches = repository.getCache(1, mediaSourceId, subjectName)
        assertEquals(2, caches.size)
        assertEquals(
            listOf("https://example.com/subject/1", "https://example.com/subject/2"),
            caches.map { it.webSubjectInfo.fullUrl },
        )
    }

    @Test
    fun `重复写入同一页面会移除页面上已不存在的剧集`() = runRepositoryTest { _, repository ->
        repository.addCache(
            1, mediaSourceId, subjectName, subjectInfo(),
            listOf(episode(1), episode(2), episode(3)), 1.hours,
        )
        repository.addCache(
            1, mediaSourceId, subjectName, subjectInfo(),
            listOf(episode(1), episode(2)), 1.hours,
        )

        assertEquals(
            listOf(EpisodeSort(1), EpisodeSort(2)),
            repository.getCache(1, mediaSourceId, subjectName).single().webEpisodeInfos.map { it.episodeSortOrEp },
        )
    }

    @Test
    fun `requesterSubjectId 为 null 时读写与清除一致`() = runRepositoryTest { _, repository ->
        repository.addCache(null, mediaSourceId, subjectName, subjectInfo(), listOf(episode(1)), 1.hours)

        // null 与具体 id 互不可见
        assertEquals(1, repository.getCache(null, mediaSourceId, subjectName).size)
        assertTrue(repository.getCache(1, mediaSourceId, subjectName).isEmpty())

        // 重复写入不会因唯一索引对 NULL 的特殊处理而产生重复行
        repository.addCache(null, mediaSourceId, subjectName, subjectInfo(), listOf(episode(1)), 1.hours)
        assertEquals(1, repository.getCache(null, mediaSourceId, subjectName).single().webEpisodeInfos.size)
    }

    @Test
    fun `无线路的剧集 channel 往返为 null`() = runRepositoryTest { _, repository ->
        repository.addCache(
            1, mediaSourceId, subjectName, subjectInfo(),
            listOf(episode(1, channel = null)), 1.hours,
        )

        assertEquals(
            null,
            repository.getCache(1, mediaSourceId, subjectName).single().webEpisodeInfos.single().channel,
        )
    }

    @Test
    fun `getCache 返回的剩余有效期与写入 TTL 一致`() = runRepositoryTest { _, repository ->
        repository.addCache(
            1, mediaSourceId, subjectName, subjectInfo(), listOf(episode(1)),
            sourceCacheTtl = 30.minutes,
        )

        val remaining = repository.getCache(1, mediaSourceId, subjectName).single().minDurationMillisToExpired
        assertTrue(remaining <= 30.minutes.inWholeMilliseconds)
        assertTrue(remaining > 29.minutes.inWholeMilliseconds)
    }

    @Test
    fun `shouldRefreshCache - 剩余有效期低于阈值时刷新`() =
        runRepositoryTest(userTtl = 1.hours, refreshThreshold = 30.minutes) { _, repository ->
            assertTrue(repository.shouldRefreshCache(10.minutes, sourceCacheTtl = 2.hours))
        }

    @Test
    fun `shouldRefreshCache - 剩余有效期高于阈值时不刷新`() =
        runRepositoryTest(userTtl = 1.hours, refreshThreshold = 30.minutes) { _, repository ->
            assertFalse(repository.shouldRefreshCache(50.minutes, sourceCacheTtl = 2.hours))
        }

    @Test
    fun `shouldRefreshCache - 阈值为 0 时关闭, 即使缓存已经过期`() =
        runRepositoryTest(userTtl = 1.hours, refreshThreshold = Duration.ZERO) { _, repository ->
            assertFalse(repository.shouldRefreshCache(10.minutes, sourceCacheTtl = 2.hours))
            // 行在读取与判断之间恰好过期时剩余时间为负, 也不能触发刷新
            assertFalse(repository.shouldRefreshCache((-1).minutes, sourceCacheTtl = 2.hours))
        }

    @Test
    fun `shouldRefreshCache - 生效 TTL 不大于阈值时关闭`() =
        runRepositoryTest(userTtl = 1.hours, refreshThreshold = 30.minutes) { _, repository ->
            // 生效 TTL = min(10min, 1h) = 10min <= 30min, 每次命中都会低于阈值, 视为关闭
            assertFalse(repository.shouldRefreshCache(5.minutes, sourceCacheTtl = 10.minutes))
            // 相等时同样关闭
            assertFalse(repository.shouldRefreshCache(5.minutes, sourceCacheTtl = 30.minutes))
        }

    @Test
    fun `shouldRefreshCache - 生效 TTL 取用户设置与数据源配置的较小者`() =
        runRepositoryTest(userTtl = 20.minutes, refreshThreshold = 30.minutes) { _, repository ->
            // 数据源配置 2h 很大, 但用户设置 20min <= 30min, 视为关闭
            assertFalse(repository.shouldRefreshCache(5.minutes, sourceCacheTtl = 2.hours))
        }
}
