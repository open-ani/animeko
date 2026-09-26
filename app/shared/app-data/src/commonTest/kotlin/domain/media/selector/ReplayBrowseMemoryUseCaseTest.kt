/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import androidx.datastore.core.DataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.data.repository.media.ManualBrowseMemories
import me.him188.ani.app.data.repository.media.ManualBrowseMemory
import me.him188.ani.app.data.repository.media.ManualBrowseMemoryRepositoryImpl
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnBeforeSelect
import me.him188.ani.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnSelect
import me.him188.ani.app.domain.media.selector.testFramework.collectEvents
import me.him188.ani.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import me.him188.ani.app.domain.mediasource.instance.createTestMediaSourceInstance
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.BrowseChannel
import me.him188.ani.datasources.api.source.BrowseEpisode
import me.him188.ani.datasources.api.source.BrowseSubject
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.source.TestHttpMediaSource
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ReplayBrowseMemoryUseCaseTest {
    /**
     * 支持浏览的假源: 固定线路列表, 记录 browseSubject 调用次数, createMedia 按传入的集号生成 WEB 资源.
     */
    private class FakeBrowsableSource(
        mediaSourceId: String = SOURCE_ID,
        override val supportsBrowsing: Boolean = true,
    ) : TestHttpMediaSource(mediaSourceId = mediaSourceId, kind = MediaSourceKind.WEB) {
        var channels: List<BrowseChannel> = listOf(channel(CHANNEL_1, numberedSorts(12)))
        var browseDelay: Duration = Duration.ZERO
        var browseError: Throwable? = null
        var browseCalls = 0
            private set
        val createdMedia = mutableListOf<Media>()

        /**
         * 条目页请求期间发生的外部动作 (用户「播放并记住」/ 手动选择), 在返回结果前执行.
         */
        var onBrowse: suspend () -> Unit = {}

        override suspend fun browseSubject(subject: BrowseSubject): List<BrowseChannel> {
            browseCalls++
            browseError?.let { throw it }
            if (browseDelay > Duration.ZERO) delay(browseDelay)
            onBrowse()
            return channels
        }

        override fun createMedia(
            subject: BrowseSubject,
            channelName: String?,
            episode: BrowseEpisode,
            episodeSort: EpisodeSort?,
        ): Media = createTestDefaultMedia(
            mediaId = "$mediaSourceId.${episode.url}",
            mediaSourceId = mediaSourceId,
            originalUrl = episode.url,
            download = ResourceLocation.WebVideo(episode.url),
            originalTitle = "${subject.name} ${episode.name}",
            publishedTime = 0,
            properties = createTestMediaProperties(
                alliance = channelName ?: "",
                subtitleLanguageIds = listOf(SubtitleLanguage.ChineseSimplified.id),
            ),
            episodeRange = episodeSort?.let { EpisodeRange.single(it) },
            location = MediaSourceLocation.Online,
            kind = MediaSourceKind.WEB,
        ).also { createdMedia += it }
    }

    private class TestClock(private var current: Instant = Instant.fromEpochSeconds(1_700_000_000)) : Clock {
        override fun now(): Instant = current
        fun advance(duration: Duration) {
            current += duration
        }
    }

    /**
     * 写入必失败的 DataStore, 模拟磁盘错误.
     */
    private class FailingWriteStore(initial: ManualBrowseMemories) : DataStore<ManualBrowseMemories> {
        override val data: Flow<ManualBrowseMemories> = flowOf(initial)
        override suspend fun updateData(
            transform: suspend (t: ManualBrowseMemories) -> ManualBrowseMemories,
        ): ManualBrowseMemories = throw IllegalStateException("disk full")
    }

    private class Fixture(
        val source: FakeBrowsableSource = FakeBrowsableSource(),
        store: DataStore<ManualBrowseMemories> = MemoryDataStore(ManualBrowseMemories.Empty),
        val clock: TestClock = TestClock(),
        instanceEnabled: Boolean = true,
    ) {
        val repository = ManualBrowseMemoryRepositoryImpl(store)
        val useCase = ReplayBrowseMemoryUseCaseImpl(
            repository,
            flowOf(listOf(createTestMediaSourceInstance(source, isEnabled = instanceEnabled))),
            clock,
        )
    }

    private suspend fun fixture(
        memory: ManualBrowseMemory? = memory(),
        source: FakeBrowsableSource = FakeBrowsableSource(),
        instanceEnabled: Boolean = true,
    ): Fixture = Fixture(source = source, instanceEnabled = instanceEnabled).also {
        if (memory != null) it.repository.set(SUBJECT_ID, memory)
    }

    // region 集号命中

    @Test
    fun `sort hit selects formally and updates memory`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val fixture = fixture(memory = memory(episodeIndex = 2, episodeSort = EpisodeSort(3), playedAsSort = EpisodeSort(3)))

        lateinit var result: ReplayResult
        val collected = selector.collectEvents {
            // 跳集看第 7 话: 位置守卫不满足, 但集号能命中
            result = fixture.useCase(SUBJECT_ID, episodeInfo(7), selector)
        }

        assertEquals(ReplayResult.SELECTED_BY_SORT, result)
        val media = fixture.source.createdMedia.single()
        assertSame(media, selector.selected.value)
        assertEquals(EpisodeRange.single(EpisodeSort(7)), media.episodeRange)
        assertEquals(1, collected.onChangePreference.size, "正式选择写偏好")
        assertSame(media, collected.onSelect.single().event.media)
        assertEquals(
            memory(episodeIndex = 6, episodeSort = EpisodeSort(7), playedAsSort = EpisodeSort(7)),
            fixture.repository.get(SUBJECT_ID),
        )
    }

    @Test
    fun `ep hit when the site renumbers a sequel from 1`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚 第二季")
        val fixture = fixture()

        // 条目服务的集号是 13, 站点从 01 重新编号: 按 ep = 1 命中第一项, 资源仍标为第 13 话
        val result = fixture.useCase(SUBJECT_ID, episodeInfo(sort = 13, ep = 1), selector)

        assertEquals(ReplayResult.SELECTED_BY_SORT, result)
        val media = fixture.source.createdMedia.single()
        assertSame(media, selector.selected.value)
        assertEquals(EpisodeRange.single(EpisodeSort(13)), media.episodeRange)
        assertEquals(
            memory(episodeIndex = 0, episodeSort = EpisodeSort(1), playedAsSort = EpisodeSort(13)),
            fixture.repository.get(SUBJECT_ID),
        )
    }

    @Test
    fun `unparsable sorts never match even when the target is unparsable too`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val source = FakeBrowsableSource().apply {
            channels = listOf(channel(CHANNEL_1, listOf(EpisodeSort("第一话"), EpisodeSort("第二话"), EpisodeSort("第三话"))))
        }
        val fixture = fixture(source = source, memory = memory(episodeIndex = 0, episodeSort = null, playedAsSort = EpisodeSort(1)))

        val result = fixture.useCase(SUBJECT_ID, EpisodeInfo.Empty.copy(episodeId = 1, sort = EpisodeSort("第三话")), selector)

        assertEquals(ReplayResult.NOT_FOUND, result)
        assertNull(selector.selected.value)
        assertTrue(source.createdMedia.isEmpty())
    }

    // endregion

    // region 按位置

    @Test
    fun `position hit selects temporarily and chains memory`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val source = FakeBrowsableSource().apply { channels = listOf(channel(CHANNEL_1, unparsableSorts(12))) }
        val fixture = fixture(source = source, memory = memory(episodeIndex = 2, episodeSort = null, playedAsSort = EpisodeSort(3)))

        lateinit var result: ReplayResult
        val collected = selector.collectEvents {
            result = fixture.useCase(SUBJECT_ID, episodeInfo(4), selector)
        }

        assertEquals(ReplayResult.SELECTED_BY_POSITION, result)
        val media = source.createdMedia.single()
        assertSame(media, selector.selected.value)
        assertEquals(EpisodeRange.single(EpisodeSort(4)), media.episodeRange, "按位置命中的资源也标为目标集")
        assertEquals("https://example.com/play/$CHANNEL_1/3", media.originalUrl, "第 k+1 项")
        collected.assertOrder(OnBeforeSelect::class, OnSelect::class)
        assertEquals(
            memory(episodeIndex = 3, episodeSort = null, playedAsSort = EpisodeSort(4)),
            fixture.repository.get(SUBJECT_ID),
        )

        // 再看下一集: 链式推进
        selector.unselect()
        assertEquals(ReplayResult.SELECTED_BY_POSITION, fixture.useCase(SUBJECT_ID, episodeInfo(5), selector))
        assertEquals("https://example.com/play/$CHANNEL_1/4", assertNotNull(selector.selected.value).originalUrl)
        assertEquals(
            memory(episodeIndex = 4, episodeSort = null, playedAsSort = EpisodeSort(5)),
            fixture.repository.get(SUBJECT_ID),
        )
    }

    @Test
    fun `position guard rejects jumping ahead`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val source = FakeBrowsableSource().apply { channels = listOf(channel(CHANNEL_1, unparsableSorts(12))) }
        val memory = memory(episodeIndex = 2, episodeSort = null, playedAsSort = EpisodeSort(3))
        val fixture = fixture(source = source, memory = memory)

        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(8), selector))

        assertNull(selector.selected.value)
        assertTrue(source.createdMedia.isEmpty())
        assertEquals(1, source.browseCalls)
        assertEquals(memory, fixture.repository.get(SUBJECT_ID), "记忆不变")
    }

    @Test
    fun `position guard rejects going backwards`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val source = FakeBrowsableSource().apply { channels = listOf(channel(CHANNEL_1, unparsableSorts(12))) }
        val fixture = fixture(source = source, memory = memory(episodeIndex = 4, episodeSort = null, playedAsSort = EpisodeSort(5)))

        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))

        assertNull(selector.selected.value)
        assertTrue(source.createdMedia.isEmpty())
    }

    @Test
    fun `position guard requires normal sorts on both sides`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val source = FakeBrowsableSource().apply { channels = listOf(channel(CHANNEL_1, unparsableSorts(12))) }
        val fixture = fixture(source = source, memory = memory(episodeIndex = 2, episodeSort = null, playedAsSort = EpisodeSort(1, EpisodeType.SP)))

        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(2), selector))

        assertNull(selector.selected.value)
    }

    @Test
    fun `missing next episode is not found and not throttled`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val source = FakeBrowsableSource().apply { channels = listOf(channel(CHANNEL_1, unparsableSorts(3))) }
        val memory = memory(episodeIndex = 2, episodeSort = null, playedAsSort = EpisodeSort(3))
        val fixture = fixture(source = source, memory = memory)

        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))
        assertNull(selector.selected.value)
        assertEquals(1, source.browseCalls)
        assertEquals(memory, fixture.repository.get(SUBJECT_ID))

        // 记忆结构有效, 只是这一集不存在: 下次仍会请求 (站点可能已更新)
        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))
        assertEquals(2, source.browseCalls)
    }

    // endregion

    // region 线路

    @Test
    fun `channel index fallback by name records the actual index`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val source = FakeBrowsableSource().apply {
            // 站点在前面插入了一条新线路, 记住的下标 0 指向了别的线路
            channels = listOf(channel("线路0", numberedSorts(12)), channel(CHANNEL_1, numberedSorts(12)))
        }
        val fixture = fixture(source = source, memory = memory(channelIndex = 0, channelName = CHANNEL_1))

        assertEquals(ReplayResult.SELECTED_BY_SORT, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))

        assertEquals("https://example.com/play/$CHANNEL_1/3", assertNotNull(selector.selected.value).originalUrl)
        assertEquals(
            memory(channelIndex = 1, channelName = CHANNEL_1, episodeIndex = 3, episodeSort = EpisodeSort(4), playedAsSort = EpisodeSort(4)),
            fixture.repository.get(SUBJECT_ID),
        )
    }

    @Test
    fun `channel index is preferred over name when both match`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val source = FakeBrowsableSource().apply {
            // 两条线路归一化为同一标识, 按下标取第二条
            channels = listOf(channel(CHANNEL_1, numberedSorts(12), urlPrefix = "a"), channel(CHANNEL_1, numberedSorts(12), urlPrefix = "b"))
        }
        val fixture = fixture(source = source, memory = memory(channelIndex = 1, channelName = CHANNEL_1))

        assertEquals(ReplayResult.SELECTED_BY_SORT, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))

        assertEquals("https://example.com/play/b/3", assertNotNull(selector.selected.value).originalUrl)
    }

    @Test
    fun `site without channel concept matches the null named channel`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val source = FakeBrowsableSource().apply { channels = listOf(channel(null, numberedSorts(12))) }
        val fixture = fixture(source = source, memory = memory(channelIndex = 0, channelName = null))

        assertEquals(ReplayResult.SELECTED_BY_SORT, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))

        assertEquals("", assertNotNull(selector.selected.value).properties.alliance)
    }

    @Test
    fun `channel missing removes the memory`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val source = FakeBrowsableSource().apply { channels = listOf(channel("线路9", numberedSorts(12))) }
        val fixture = fixture(source = source, memory = memory(channelIndex = 0, channelName = CHANNEL_1))

        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))

        assertNull(selector.selected.value)
        assertTrue(source.createdMedia.isEmpty())
        assertNull(fixture.repository.get(SUBJECT_ID), "线路列表非空但找不到记住的线路: 记忆已结构性失效")

        // 没有记忆了, 不再请求条目页
        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))
        assertEquals(1, source.browseCalls)
    }

    @Test
    fun `channel missing keeps a memory rewritten while browsing`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val source = FakeBrowsableSource().apply { channels = listOf(channel("线路9", numberedSorts(12))) }
        val fixture = fixture(source = source, memory = memory(channelIndex = 0, channelName = CHANNEL_1))
        // 条目页请求慢, 用户等不及打开手动查找并「播放并记住」了新线路
        val rewritten = memory(channelIndex = 2, channelName = "线路9", episodeIndex = 3, episodeSort = EpisodeSort(4), playedAsSort = EpisodeSort(4))
        source.onBrowse = { fixture.repository.set(SUBJECT_ID, rewritten) }

        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))

        assertEquals(rewritten, fixture.repository.get(SUBJECT_ID), "只删本次读到的旧记忆, 用户刚写入的新记忆保留")
    }

    @Test
    fun `channel missing keeps the memory when the user selected while browsing`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val source = FakeBrowsableSource().apply { channels = listOf(channel("线路9", numberedSorts(12))) }
        val memory = memory(channelIndex = 0, channelName = CHANNEL_1)
        val fixture = fixture(source = source, memory = memory)
        val current = media(kind = MediaSourceKind.WEB)
        mediaApi.addMedia(current)
        source.onBrowse = { assertTrue(selector.select(current)) }

        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))

        assertSame(current, selector.selected.value)
        assertEquals(memory, fixture.repository.get(SUBJECT_ID), "用户已手动选择时不删记忆")
    }

    @Test
    fun `sort hit does not overwrite a memory rewritten while browsing`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val source = FakeBrowsableSource()
        val fixture = fixture(source = source, memory = memory(episodeIndex = 2, episodeSort = EpisodeSort(3), playedAsSort = EpisodeSort(3)))
        val rewritten = memory(channelIndex = 1, channelName = "线路2", episodeIndex = 0, episodeSort = EpisodeSort(1), playedAsSort = EpisodeSort(4))
        source.onBrowse = { fixture.repository.set(SUBJECT_ID, rewritten) }

        assertEquals(ReplayResult.SELECTED_BY_SORT, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))

        assertSame(source.createdMedia.single(), selector.selected.value, "回放时还没有选择, 仍然选中")
        assertEquals(rewritten, fixture.repository.get(SUBJECT_ID), "记忆更新是 CAS, 不覆盖用户刚写入的新记忆")
    }

    // endregion

    // region 结构性未命中缓存

    @Test
    fun `empty browse result is throttled for ten minutes`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val source = FakeBrowsableSource().apply { channels = emptyList() }
        val memory = memory()
        val fixture = fixture(source = source, memory = memory)

        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))
        assertEquals(1, source.browseCalls)
        assertEquals(memory, fixture.repository.get(SUBJECT_ID), "持久化记忆保留")

        fixture.clock.advance(9.minutes)
        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(5), selector))
        assertEquals(1, source.browseCalls, "10 分钟内同一记忆不再请求")

        fixture.clock.advance(2.minutes)
        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(5), selector))
        assertEquals(2, source.browseCalls, "过期后重新请求")

        // 用户重新「播放并记住」得到新记忆: 缓存只对相等的记忆生效
        fixture.repository.set(SUBJECT_ID, memory.copy(subject = BrowseSubject("孤独摇滚 (新)", "https://example.com/subject/2")))
        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(5), selector))
        assertEquals(3, source.browseCalls)
    }

    // endregion

    // region 失败与跳过

    @Test
    fun `browse error is swallowed and not throttled`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val source = FakeBrowsableSource().apply { browseError = RepositoryNetworkException("offline") }
        val memory = memory()
        val fixture = fixture(source = source, memory = memory)

        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))
        assertNull(selector.selected.value)
        assertEquals(memory, fixture.repository.get(SUBJECT_ID))

        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))
        assertEquals(2, source.browseCalls, "网络错误不记入结构性未命中缓存")
    }

    @Test
    fun `browse timeout is not found and not throttled`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val source = FakeBrowsableSource().apply { browseDelay = ReplayBrowseMemoryUseCaseImpl.REPLAY_TIMEOUT + 5.seconds }
        val fixture = fixture(source = source)

        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))
        assertNull(selector.selected.value)

        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))
        assertEquals(2, source.browseCalls)
    }

    @Test
    fun `existing selection skips browsing`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val fixture = fixture()
        val current = media(kind = MediaSourceKind.WEB)
        mediaApi.addMedia(current)
        assertTrue(selector.select(current))

        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))

        assertEquals(0, fixture.source.browseCalls)
        assertSame(current, selector.selected.value)
    }

    @Test
    fun `no memory skips browsing`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val fixture = fixture(memory = null)

        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))

        assertEquals(0, fixture.source.browseCalls)
        assertNull(selector.selected.value)
    }

    @Test
    fun `disabled source is skipped and memory kept`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val memory = memory()
        val fixture = fixture(memory = memory, instanceEnabled = false)

        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))

        assertEquals(0, fixture.source.browseCalls)
        assertEquals(memory, fixture.repository.get(SUBJECT_ID))
    }

    @Test
    fun `source without browsing support is skipped`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val fixture = fixture(source = FakeBrowsableSource(supportsBrowsing = false))

        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))

        assertEquals(0, fixture.source.browseCalls)
    }

    @Test
    fun `memory of another source is skipped`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val fixture = fixture(memory = memory(mediaSourceId = "web-other"))

        assertEquals(ReplayResult.NOT_FOUND, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))

        assertEquals(0, fixture.source.browseCalls)
    }

    @Test
    fun `memory write failure keeps the selection and the result`() = runSimpleMediaSelectorTestSuite {
        initSubject("孤独摇滚")
        val source = FakeBrowsableSource()
        val fixture = Fixture(
            source = source,
            store = FailingWriteStore(ManualBrowseMemories(mapOf(SUBJECT_ID to memory()))),
        )

        assertEquals(ReplayResult.SELECTED_BY_SORT, fixture.useCase(SUBJECT_ID, episodeInfo(4), selector))

        assertSame(source.createdMedia.single(), selector.selected.value)
    }

    // endregion

    private companion object {
        const val SUBJECT_ID = 42
        const val SOURCE_ID = "web-a"
        const val CHANNEL_1 = "线路1"

        val SUBJECT = BrowseSubject(name = "孤独摇滚", url = "https://example.com/subject/1")

        fun memory(
            mediaSourceId: String = SOURCE_ID,
            channelIndex: Int = 0,
            channelName: String? = CHANNEL_1,
            episodeIndex: Int = 2,
            episodeSort: EpisodeSort? = EpisodeSort(3),
            playedAsSort: EpisodeSort = EpisodeSort(3),
        ) = ManualBrowseMemory(
            mediaSourceId = mediaSourceId,
            subject = SUBJECT,
            channelIndex = channelIndex,
            channelName = channelName,
            episodeIndex = episodeIndex,
            episodeSort = episodeSort,
            playedAsSort = playedAsSort,
        )

        fun episodeInfo(sort: Int, ep: Int? = sort) = EpisodeInfo.Empty.copy(
            episodeId = 1,
            sort = EpisodeSort(sort),
            ep = ep?.let { EpisodeSort(it) },
        )

        /** 站点解析出 1..[count] 的集号. */
        fun numberedSorts(count: Int): List<EpisodeSort?> = (1..count).map { EpisodeSort(it) }

        /** 站点解析不出集号 (例如「第三话」), 只能按位置回放. */
        fun unparsableSorts(count: Int): List<EpisodeSort?> = List(count) { null }

        fun channel(name: String?, sorts: List<EpisodeSort?>, urlPrefix: String = name ?: "default") = BrowseChannel(
            name = name,
            episodes = sorts.mapIndexed { index, sort ->
                BrowseEpisode(
                    name = "第${index + 1}集",
                    url = "https://example.com/play/$urlPrefix/$index",
                    episodeSort = sort,
                )
            },
        )
    }
}
