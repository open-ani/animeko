/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache.requester

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.SystemTemporaryDirectory
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectSeriesInfo
import me.him188.ani.app.domain.media.SOURCE_DMHY
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.cache.TestMediaCache
import me.him188.ani.app.domain.media.cache.storage.TestMediaCacheStorage
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.fetch.MediaFetcherConfig
import me.him188.ani.app.domain.media.fetch.MediaSourceMediaFetcher
import me.him188.ani.app.domain.media.fetch.awaitCompletion
import me.him188.ani.app.domain.media.selector.DefaultMediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.app.domain.media.selector.MediaSelectorFactory
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.media.selector.MediaSelectorSubtitlePreferences
import me.him188.ani.app.domain.mediasource.instance.createTestMediaSourceInstance
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.paging.SinglePagePagedSource
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.source.TestHttpMediaSource
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.api.topic.Resolution
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.SubtitleLanguage.ChineseSimplified
import me.him188.ani.datasources.api.topic.SubtitleLanguage.ChineseTraditional
import me.him188.ani.utils.io.resolve
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EpisodeCacheRequesterTest {
    private val nullFilePath = SystemTemporaryDirectory.resolve("null.tmp").toString()
    companion object {
        private val DEFAULT_PREFERENCE = MediaPreference.Empty.copy(
            fallbackResolutions = listOf(
                Resolution.R2160P,
                Resolution.R1440P,
                Resolution.R1080P,
                Resolution.R720P,
            ).map { it.id },
            fallbackSubtitleLanguageIds = listOf(
                ChineseSimplified,
                ChineseTraditional,
            ).map { it.id },
        )
    }

    private val mediaList: MutableStateFlow<MutableList<DefaultMedia>> = MutableStateFlow(
        TestMediaList.toMutableList(),
    )

    private fun addMedia(vararg media: DefaultMedia) {
        mediaList.value.addAll(media)
    }

    private val savedUserPreference = MutableStateFlow(DEFAULT_PREFERENCE)
    private val savedDefaultPreference = MutableStateFlow(DEFAULT_PREFERENCE)
    private val mediaSelectorSettings = MutableStateFlow(MediaSelectorSettings.Default)
    private val mediaSelectorContext = MutableStateFlow(
        MediaSelectorContext(
            subjectFinished = false,
            mediaSourcePrecedence = emptyList(),
            subtitlePreferences = MediaSelectorSubtitlePreferences.AllNormal,
            subjectSeriesInfo = SubjectSeriesInfo.Fallback,
            subjectInfo = SubjectInfo.Empty,
            episodeInfo = EpisodeInfo.Empty,
            mediaSourceTiers = MediaSelectorSourceTiers.Empty,
        ),
    )
    private val storage = TestMediaCacheStorage()
    private val storageFlow = MutableStateFlow(listOf(storage))

    private val requester =
        EpisodeCacheRequester(
            flowOf(
                MediaSourceMediaFetcher(
                    configProvider = { MediaFetcherConfig.Default },
                    mediaSources = listOf(
                        createTestMediaSourceInstance(
                            TestHttpMediaSource(
                                fetch = {
                                    SinglePagePagedSource {
                                        mediaList.value.map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                                    }
                                },
                            ),
                        ),
                    ),
                    flowContext = EmptyCoroutineContext,
                ),
            ),
            mediaSelectorFactory = object : MediaSelectorFactory {
                override fun create(
                    subjectId: Int,
                    episodeId: Int,
                    mediaList: Flow<List<Media>>,
                    flowCoroutineContext: CoroutineContext
                ): MediaSelector {
                    return DefaultMediaSelector(
                        mediaSelectorContextNotCached = mediaSelectorContext,
                        mediaListNotCached = mediaList,
                        savedUserPreference = savedUserPreference,
                        savedDefaultPreference = savedDefaultPreference,
                        enableCaching = false,
                        mediaSelectorSettings = mediaSelectorSettings,
                    )
                }
            },
            storagesLazy = storageFlow,
        )

    private fun createRequest(
        sort: EpisodeSort = EpisodeSort(0)
    ): EpisodeCacheRequest {
        return EpisodeCacheRequest(
            subjectInfo = SubjectInfo.Empty,
            episodeInfo = EpisodeInfo(0, EpisodeType.MainStory, sort = sort),
        )
    }

    ///////////////////////////////////////////////////////////////////////////
    // Basic states
    ///////////////////////////////////////////////////////////////////////////

    // Note: these tests test `requester.stage.value` and is different from other tests like SelectStorage::select

    @Test
    fun `initial stage idle`() = runTest {
        assertIs<CacheRequestStage.Idle>(requester.stage.value)
    }

    @Test
    fun `request changes state to SelectMedia`() = runTest {
        val request = createRequest()
        requester.request(request)
        assertIs<CacheRequestStage.SelectMedia>(requester.stage.value)
    }

    @Test
    fun `select unknown media`() = runTest {
        val request = createRequest()
        requester.request(request)
            .select(mediaList.value.first().copy()) // 注意, 这个时候实际上 MediaFetchSession 并未开始, 因为它是 lazy 的
        assertIs<CacheRequestStage.SelectStorage>(requester.stage.value)
    }

    @Test
    fun `select known media`() = runTest {
        val request = createRequest()
        requester.request(request).run {
            fetchSession.awaitCompletion()
            select(mediaList.value.first())
        }
        assertIs<CacheRequestStage.SelectStorage>(requester.stage.value)
    }

    @Test
    fun `select known storage`() = runTest {
        val request = createRequest()
        requester.request(request)
            .select(mediaList.value.first())
            .select(storage)
        assertIs<CacheRequestStage.Done>(requester.stage.value)
    }

    @Test
    fun `select unknown storage`() = runTest {
        val request = createRequest()
        requester.request(request)
            .select(mediaList.value.first())
            .select(TestMediaCacheStorage())
        assertIs<CacheRequestStage.Done>(requester.stage.value)
    }

    @Test
    fun `done has correct metadata`() = runTest {
        val request = createRequest().run {
            copy(
                subjectInfo.copy(subjectId = 12, name = "ひ", nameCn = "孤独摇滚"),
                episodeInfo.copy(sort = EpisodeSort(2), name = "第二集"), // 2 in 12
            )
        }

        val originalMedia = createDefaultMedia("$SOURCE_DMHY.1", EpisodeRange.range(EpisodeSort(1), EpisodeSort(12)))
        val done = requester.request(request)
            .select(originalMedia)
            .select(TestMediaCacheStorage())
        assertIs<CacheRequestStage.Done>(requester.stage.value)

        assertEquals(
            // note: compare with the MediaCacheMetadata above
            // We expect all info updated
            MediaCacheMetadata(
                subjectId = "12",
                episodeId = "0",
                subjectNameCN = "孤独摇滚",
                subjectNames = listOf("孤独摇滚", "ひ"),
                episodeSort = EpisodeSort(2), // using new
                episodeEp = null,
                episodeName = "第二集",
            ),
            done.metadata,
        )
    }

    ///////////////////////////////////////////////////////////////////////////
    // SelectMedia
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `SelectMedia select - cancel - and select same`() = runTest {
        val request = createRequest()
        requester.request(request).run {
            select(mediaList.value.first())
                .cancel()
                .select(mediaList.value.first())
        }
        assertIs<CacheRequestStage.SelectStorage>(requester.stage.value)
    }

    @Test
    fun `SelectMedia select - cancel - and select different`() = runTest {
        val request = createRequest()
        requester.request(request).run {
            select(mediaList.value.first())
                .cancel()
                .select(mediaList.value[1])
        }
        assertIs<CacheRequestStage.SelectStorage>(requester.stage.value)
    }

    ///////////////////////////////////////////////////////////////////////////
    // SelectMedia trySelect
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `SelectMedia tryAutoSelectByPreference`() = runTest {
        val request = createRequest()
        val selectMedia = requester.request(request)
        selectMedia
            .tryAutoSelectByPreference()
        assertIs<CacheRequestStage.SelectStorage>(requester.stage.value)
        assertEquals(true, selectMedia.attemptedTrySelect.value)
    }

    @Test
    fun `SelectMedia tryAutoSelectByCachedSeason selects none for empty input`() = runTest {
        val request = createRequest()
        assertNull(
            requester.request(request)
                .tryAutoSelectByCachedSeason(emptyList()),
        )
        val stage = requester.stage.value
        assertIs<CacheRequestStage.SelectMedia>(stage)
        assertEquals(true, stage.attemptedTrySelect.value)
    }

    @Test
    fun `SelectMedia tryAutoSelectByCachedSeason selects none for inputs matching single ep`() = runTest {
        val originalMedia = createTestDefaultMedia(
            mediaId = "$SOURCE_DMHY.1",
            mediaSourceId = SOURCE_DMHY,
            originalTitle = "[桜都字幕组] 孤独摇滚 ABC ABC ABC ABC ABC ABC ABC ABC ABC ABC",
            download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:1"),
            originalUrl = "https://example.com/1",
            publishedTime = 1,
            episodeRange = EpisodeRange.single(EpisodeSort(1)), // note here: single
            properties = createTestMediaProperties(
                subtitleLanguageIds = listOf(ChineseSimplified, ChineseTraditional).map { it.id },
                resolution = "1080P",
                alliance = "桜都字幕组",
                size = 122.megaBytes,
                subtitleKind = null,
            ),
            kind = MediaSourceKind.BitTorrent,
            location = MediaSourceLocation.Online,
        )
        val mediaCache = TestMediaCache(
            CachedMedia(
                originalMedia, "local",
                ResourceLocation.LocalFile(nullFilePath),
                MediaSourceLocation.Local,
                MediaSourceKind.LocalCache,
            ),
            MediaCacheMetadata(
                subjectId = "1",
                episodeId = "1",
                subjectNameCN = "孤独摇滚",
                subjectNames = listOf("孤独摇滚"),
                episodeSort = EpisodeSort(1),
                episodeEp = EpisodeSort(1),
                episodeName = "test",
            ),
        )

        val request = createRequest(sort = EpisodeSort(1))
        assertNull(
            requester.request(request)
                .tryAutoSelectByCachedSeason(listOf(mediaCache)),
        )

        val stage = requester.stage.value
        assertIs<CacheRequestStage.SelectMedia>(stage)
        assertEquals(true, stage.attemptedTrySelect.value)
    }

    @Test
    fun `SelectMedia tryAutoSelectByCachedSeason selects none for inputs without matching ep`() = runTest {
        val originalMedia = createTestDefaultMedia(
            mediaId = "$SOURCE_DMHY.1",
            mediaSourceId = SOURCE_DMHY,
            originalTitle = "[桜都字幕组] 孤独摇滚 ABC ABC ABC ABC ABC ABC ABC ABC ABC ABC",
            download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:1"),
            originalUrl = "https://example.com/1",
            publishedTime = 1,
            episodeRange = EpisodeRange.range(EpisodeSort(1), EpisodeSort(12)),
            properties = createTestMediaProperties(
                subtitleLanguageIds = listOf(ChineseSimplified, ChineseTraditional).map { it.id },
                resolution = "1080P",
                alliance = "桜都字幕组",
                size = 122.megaBytes,
                subtitleKind = null,
            ),
            kind = MediaSourceKind.BitTorrent,
            location = MediaSourceLocation.Online,
        )
        val mediaCache = TestMediaCache(
            CachedMedia(
                originalMedia, "local",
                ResourceLocation.LocalFile(nullFilePath),
                MediaSourceLocation.Local,
                MediaSourceKind.LocalCache,
            ),
            MediaCacheMetadata(
                subjectId = "1",
                episodeId = "1",
                subjectNameCN = "孤独摇滚",
                subjectNames = listOf("孤独摇滚"),
                episodeSort = EpisodeSort(1),
                episodeEp = EpisodeSort(1),
                episodeName = "test",
            ),
        )

        val request = createRequest(sort = EpisodeSort(0)) // 0 !in 12
        assertNull(
            requester.request(request)
                .tryAutoSelectByCachedSeason(listOf(mediaCache)),
        )

        val stage = requester.stage.value
        assertIs<CacheRequestStage.SelectMedia>(stage)
        assertEquals(true, stage.attemptedTrySelect.value)
    }

    @Suppress("SameParameterValue")
    private fun createDefaultMedia(id: String, epRange: EpisodeRange): DefaultMedia {
        return createTestDefaultMedia(
            mediaId = id,
            mediaSourceId = SOURCE_DMHY,
            originalTitle = "[桜都字幕组] 孤独摇滚 ABC ABC ABC ABC ABC ABC ABC ABC ABC ABC",
            download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:1"),
            originalUrl = "https://example.com/1",
            publishedTime = 1,
            episodeRange = epRange,
            properties = createTestMediaProperties(
                subtitleLanguageIds = listOf(ChineseSimplified, ChineseTraditional).map { it.id },
                resolution = "1080P",
                alliance = "桜都字幕组",
                size = 122.megaBytes,
                subtitleKind = null,
            ),
            kind = MediaSourceKind.BitTorrent,
            location = MediaSourceLocation.Online,
        )
    }

    private fun createMediaCache(
        originalMedia: Media,
        episodeSort: EpisodeSort,
        episodeEp: EpisodeSort?,
    ) = TestMediaCache(
        CachedMedia(
            originalMedia,
            "local",
            ResourceLocation.LocalFile(nullFilePath),
            MediaSourceLocation.Local,
            MediaSourceKind.LocalCache,
        ),
        MediaCacheMetadata(
            subjectId = "1",
            episodeId = "1",
            subjectNameCN = "孤独摇滚",
            subjectNames = listOf("孤独摇滚"),
            episodeSort = episodeSort,
            episodeEp = episodeEp,
            episodeName = "test",
        ),
    )

    @Test
    fun `SelectMedia tryAutoSelectByCachedSeason selects one for inputs with matching ep`() = runTest {
        val originalMedia = createDefaultMedia("$SOURCE_DMHY.1", EpisodeRange.range(EpisodeSort(1), EpisodeSort(12)))
        val mediaCache = createMediaCache(originalMedia, EpisodeSort(1), EpisodeSort(1))

        val request = createRequest().run {
            copy(
                subjectInfo.copy(subjectId = 12, name = "ひ", nameCn = "孤独摇滚"),
                episodeInfo.copy(sort = EpisodeSort(2), name = "第二集"), // 2 in 12
            )
        }
        val selectMedia = requester.request(request)
        val selectStorage = selectMedia
            .tryAutoSelectByCachedSeason(listOf(mediaCache))

        // 因为 storage 不包含 mediaCache
        assertIs<CacheRequestStage.SelectStorage>(selectStorage)

        val done = selectStorage.trySelectSingle()
        assertNotNull(done)

        assertEquals(originalMedia, done.media)
        assertEquals(
            // note: compare with the MediaCacheMetadata above
            // We expect all info updated
            MediaCacheMetadata(
                subjectId = "12",
                episodeId = "0",
                subjectNameCN = "孤独摇滚",
                subjectNames = listOf("孤独摇滚", "ひ"),
                episodeSort = EpisodeSort(2), // using new
                episodeEp = null,
                episodeName = "第二集",
            ),
            done.metadata,
        )

        assertEquals(true, selectMedia.attemptedTrySelect.value)
        assertEquals(true, selectStorage.attemptedTrySelect.value)
    }

    @Test
    fun `SelectMedia tryAutoSelectByCachedSeason goes Done if storage contains mediaCache`() = runTest {
        val originalMedia = createDefaultMedia("$SOURCE_DMHY.1", EpisodeRange.range(EpisodeSort(1), EpisodeSort(12)))
        val mediaCache = createMediaCache(originalMedia, EpisodeSort(1), EpisodeSort(1))
        storage.listFlow.value += mediaCache // note here

        val request = createRequest().run {
            copy(
                subjectInfo.copy(subjectId = 12, name = "ひ", nameCn = "孤独摇滚"),
                episodeInfo.copy(sort = EpisodeSort(2), name = "第二集"), // 2 in 12
            )
        }
        val selectMedia = requester.request(request)
        val done = selectMedia
            .tryAutoSelectByCachedSeason(listOf(mediaCache))
        assertIs<CacheRequestStage.Done>(done)

        assertEquals(originalMedia, done.media)
        assertEquals(
            // note: compare with the MediaCacheMetadata above
            // We expect all info updated
            MediaCacheMetadata(
                subjectId = "12",
                episodeId = "0",
                subjectNameCN = "孤独摇滚",
                subjectNames = listOf("孤独摇滚", "ひ"),
                episodeSort = EpisodeSort(2), // using new
                episodeEp = null,
                episodeName = "第二集",
            ),
            done.metadata,
        )

        assertEquals(true, selectMedia.attemptedTrySelect.value)
        // We should test SelectStorage for attemptedTrySelect but it is not that easy
    }

    ///////////////////////////////////////////////////////////////////////////
    // StaleStageException
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `select media two times`() = runTest {
        val request = createRequest()
        val selectMedia = requester.request(request)
        selectMedia.select(mediaList.value.first())
        assertFailsWith<StaleStageException> {
            selectMedia.select(mediaList.value.first())
        }
    }

    @Test
    fun `select storage two times`() = runTest {
        val request = createRequest()
        val state = requester.request(request).select(mediaList.value.first())
        state.select(storage)
        assertFailsWith<StaleStageException> {
            state.select(storage)
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // SelectStorage select
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `SelectStorage select selects one from options`() = runTest {
        val request = createRequest()
        val state = requester.request(request).select(mediaList.value.first())
        assertTrue { storage in state.storages }
        val done = state.select(storage) // storage is in options
        assertNotNull(done)
        assertSame(storage, done.storage)
    }

    @Test
    fun `SelectStorage select selects one even if not from options`() = runTest {
        val request = createRequest()
        val state = requester.request(request).select(mediaList.value.first())
        val storage = TestMediaCacheStorage() // new storage
        assertTrue { storage !in state.storages }
        val done = state.select(storage) // storage is not in options
        assertNotNull(done)
        assertSame(storage, done.storage)
    }

    @Test
    fun `SelectStorage select twice`() = runTest {
        val request = createRequest()
        val state = requester.request(request).select(mediaList.value.first())
        state.select(storage)
        assertFailsWith<StaleStageException> {
            state.select(storage)
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // SelectStorage trySelectByCache
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `SelectStorage trySelectByCache selects one if it is in storage`() = runTest {
        val request = createRequest()
        val originalMedia = createDefaultMedia("$SOURCE_DMHY.1", EpisodeRange.range(EpisodeSort(1), EpisodeSort(12)))
        val mediaCache = createMediaCache(originalMedia, EpisodeSort(1), EpisodeSort(1))
        storage.listFlow.value += mediaCache
        storageFlow.value += TestMediaCacheStorage()
        storageFlow.value += TestMediaCacheStorage()

        val state = requester.request(request).select(mediaList.value.first())
        val done = state.trySelectByCache(mediaCache)
        assertNotNull(done)
        assertSame(storage, done.storage)
        assertEquals(true, state.attemptedTrySelect.value)
    }

    @Test
    fun `SelectStorage storage options are frozen upon state transition`() = runTest {
        val request = createRequest()
        val state = requester.request(request).select(mediaList.value.first()) // storage options frozen since then

        storageFlow.value += TestMediaCacheStorage()
        storageFlow.value += TestMediaCacheStorage()

        assertEquals(1, state.storages.size)

        assertNotNull(state.trySelectSingle())
        assertEquals(true, state.attemptedTrySelect.value)
    }

    @Test
    fun `SelectStorage trySelectByCache selects one if it is in storage - shuffled`() = runTest {
        val request = createRequest()
        val originalMedia = createDefaultMedia("$SOURCE_DMHY.1", EpisodeRange.range(EpisodeSort(1), EpisodeSort(12)))
        val mediaCache = createMediaCache(originalMedia, EpisodeSort(1), EpisodeSort(1))

        val target: TestMediaCacheStorage
        storageFlow.value += TestMediaCacheStorage().apply {
            listFlow.value += mediaCache
            target = this
        }
        storageFlow.value += TestMediaCacheStorage()

        val state = requester.request(request).select(mediaList.value.first())

        val done = state.trySelectByCache(mediaCache)
        assertNotNull(done)
        assertSame(target, done.storage)
        assertEquals(true, state.attemptedTrySelect.value)
    }

    @Test
    fun `SelectStorage trySelectByCache selects none`() = runTest {
        val request = createRequest()
        val state = requester.request(request).select(mediaList.value.first())
        val originalMedia = createDefaultMedia("$SOURCE_DMHY.1", EpisodeRange.range(EpisodeSort(1), EpisodeSort(12)))
        val mediaCache = createMediaCache(originalMedia, EpisodeSort(1), EpisodeSort(1))
        storageFlow.value += TestMediaCacheStorage()
        storageFlow.value += TestMediaCacheStorage()

        val done = state.trySelectByCache(mediaCache)
        assertNull(done)
        assertEquals(true, state.attemptedTrySelect.value)
    }

    ///////////////////////////////////////////////////////////////////////////
    // SelectStorage trySelectSingle
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `SelectStorage trySelectSingle selects one when storageFlow is not empty`() = runTest {
        assertTrue { storageFlow.value.isNotEmpty() }
        val request = createRequest()
        val state = requester.request(request).select(mediaList.value.first())
        assertNotNull(state.trySelectSingle())
        assertEquals(true, state.attemptedTrySelect.value)
    }

    @Test
    fun `SelectStorage trySelectSingle selects none when storageFlow is empty`() = runTest {
        storageFlow.value = listOf()
        assertTrue { storageFlow.value.isEmpty() }
        val request = createRequest()
        val state = requester.request(request).select(mediaList.value.first())
        assertNull(state.trySelectSingle())
        assertEquals(true, state.attemptedTrySelect.value)
    }

    @Test
    fun `SelectStorage trySelectSingle selects none when storageFlow is not single`() = runTest {
        storageFlow.value = listOf(TestMediaCacheStorage(), TestMediaCacheStorage())
        assertTrue { storageFlow.value.size > 1 }
        val request = createRequest()
        val state = requester.request(request).select(mediaList.value.first())
        assertNull(state.trySelectSingle())
        assertEquals(true, state.attemptedTrySelect.value)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Cancel
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `cancel SelectMedia`() = runTest {
        val request = createRequest()
        requester.request(request)
            .cancel()
        assertIs<CacheRequestStage.Idle>(requester.stage.value)
    }

    @Test
    fun `cancel SelectMedia twice`() = runTest {
        val request = createRequest()
        requester.request(request).run {
            cancel()
            assertFailsWith<StaleStageException> {
                cancel()
            }
        }
    }

    @Test
    fun `cancel SelectStorage`() = runTest {
        val request = createRequest()
        requester.request(request)
            .select(mediaList.value.first())
            .cancel()
        assertIs<CacheRequestStage.SelectMedia>(requester.stage.value)
    }

    @Test
    fun `cancel SelectStorage twice`() = runTest {
        val request = createRequest()
        requester.request(request).select(mediaList.value.first()).run {
            cancel()
            assertFailsWith<StaleStageException> {
                cancel()
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // attemptedTrySelect
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `attemptedTrySelect is initially false for SelectMedia`() = runTest {
        val request = createRequest()
        val state = requester.request(request)
        assertEquals(false, state.attemptedTrySelect.value)
    }

    @Test
    fun `attemptedTrySelect is initially false for SelectStorage`() = runTest {
        val request = createRequest()
        val state = requester.request(request).select(mediaList.value.first())
        assertEquals(false, state.attemptedTrySelect.value)
    }

    // For `true` cases, see above
}
