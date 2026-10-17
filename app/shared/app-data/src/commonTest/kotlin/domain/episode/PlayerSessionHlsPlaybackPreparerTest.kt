/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.preference.HlsAdFilteringMode
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.hls.HlsPlaybackOptions
import me.him188.ani.app.domain.media.hls.HlsPlaybackPreparer
import me.him188.ani.app.domain.media.hls.HlsPlaybackPreparerResult
import me.him188.ani.app.domain.media.hls.HlsPlaybackProxySession
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.mediasource.MediaSourceTags
import me.him188.ani.app.domain.mediasource.instance.GetMediaSourceInstancesUseCase
import me.him188.ani.app.domain.mediasource.instance.MediaSourceInstance
import me.him188.ani.app.domain.mediasource.instance.createTestMediaSourceInstance
import me.him188.ani.app.domain.settings.GetVideoScaffoldConfigUseCase
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.TestHttpMediaSource
import org.koin.core.Koin
import org.koin.dsl.module
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.test.TestMediampPlayer
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlayerSessionHlsPlaybackPreparerTest {
    private val episode = EpisodeMetadata(title = "EP1", ep = EpisodeSort(1), sort = EpisodeSort(1))

    @Test
    fun `does not prepare uri media data when setting is disabled`() = runTest {
        val preparer = RecordingHlsPlaybackPreparer()
        val playerSession = createPlayerSession(
            mode = HlsAdFilteringMode.OFF,
            preparer = preparer,
        )

        playerSession.loadMedia(TestMediaList.first(), episode)

        val data = assertIs<UriMediaData>(playerSession.player.mediaData.first())
        assertEquals("https://example.com/original.m3u8", data.uri)
        assertEquals(0, preparer.prepareCount)
    }

    @Test
    fun `prepares uri media data when setting is enabled`() = runTest {
        val preparer = RecordingHlsPlaybackPreparer()
        val playerSession = createPlayerSession(
            mode = HlsAdFilteringMode.ALL,
            preparer = preparer,
        )

        playerSession.loadMedia(TestMediaList.first(), episode)

        val data = assertIs<UriMediaData>(playerSession.player.mediaData.first())
        assertEquals("http://127.0.0.1:18080/proxied.m3u8", data.uri)
        assertEquals(1, preparer.prepareCount)
        assertFalse(preparer.sessions.single().closed)
    }

    @Test
    fun `opens proxied playlist at start position and prefetches there`() = runTest {
        val preparer = RecordingHlsPlaybackPreparer()
        val playerSession = createPlayerSession(
            mode = HlsAdFilteringMode.ALL,
            preparer = preparer,
        )

        playerSession.loadMedia(TestMediaList.first(), episode, startPositionMillis = 40_000)

        // 播放器打开的是去除广告后的播放列表, 起播位置与预缓存提示在同一条时间轴上
        val data = assertIs<UriMediaData>(playerSession.player.mediaData.first())
        assertEquals("http://127.0.0.1:18080/proxied.m3u8", data.uri)
        assertEquals(listOf<Long?>(40_000), preparer.startPositionHints)
        assertEquals(40_000, playerSession.player.currentPositionMillis.value)
    }

    @Test
    fun `starts from the beginning without prefetch hint by default`() = runTest {
        val preparer = RecordingHlsPlaybackPreparer()
        val playerSession = createPlayerSession(
            mode = HlsAdFilteringMode.ALL,
            preparer = preparer,
        )

        playerSession.loadMedia(TestMediaList.first(), episode)

        assertEquals(listOf<Long?>(null), preparer.startPositionHints)
        assertEquals(0, playerSession.player.currentPositionMillis.value)
    }

    @Test
    fun `opens at start position without hls preparation`() = runTest {
        val preparer = RecordingHlsPlaybackPreparer()
        val playerSession = createPlayerSession(
            mode = HlsAdFilteringMode.OFF,
            preparer = preparer,
        )

        playerSession.loadMedia(TestMediaList.first(), episode, startPositionMillis = 40_000)

        assertEquals(0, preparer.prepareCount)
        assertEquals(40_000, playerSession.player.currentPositionMillis.value)
    }

    @Test
    fun `smart mode filters media sources tagged by subscription`() = runTest {
        val preparer = RecordingHlsPlaybackPreparer()
        val playerSession = createPlayerSession(
            mode = HlsAdFilteringMode.SMART,
            preparer = preparer,
            sourceTags = listOf(MediaSourceTags.HLS_ADS),
        )

        playerSession.loadMedia(TestMediaList.first(), episode)

        assertTrue(preparer.options.single().filterSegments)
    }

    @Test
    fun `smart mode does not filter untagged media sources`() = runTest {
        val preparer = RecordingHlsPlaybackPreparer()
        val playerSession = createPlayerSession(
            mode = HlsAdFilteringMode.SMART,
            preparer = preparer,
        )

        playerSession.loadMedia(TestMediaList.first(), episode)

        val data = assertIs<UriMediaData>(playerSession.player.mediaData.first())
        assertEquals("https://example.com/original.m3u8", data.uri)
        assertEquals(0, preparer.prepareCount)
    }

    @Test
    fun `closes prepared proxy session on next load and player session close`() = runTest {
        val preparer = RecordingHlsPlaybackPreparer()
        val playerSession = createPlayerSession(
            mode = HlsAdFilteringMode.ALL,
            preparer = preparer,
        )

        playerSession.loadMedia(TestMediaList.first(), episode)
        val firstSession = preparer.sessions.single()

        playerSession.loadMedia(TestMediaList.first(), episode)
        val secondSession = preparer.sessions.last()

        assertTrue(firstSession.closed)
        assertFalse(secondSession.closed)

        playerSession.close()

        assertTrue(secondSession.closed)
    }

    @Test
    fun `closes prepared proxy session on stop playback`() = runTest {
        val preparer = RecordingHlsPlaybackPreparer()
        val playerSession = createPlayerSession(
            mode = HlsAdFilteringMode.ALL,
            preparer = preparer,
        )

        playerSession.loadMedia(TestMediaList.first(), episode)
        val session = preparer.sessions.single()

        playerSession.stopPlayback()

        assertTrue(session.closed)
    }

    private fun TestScope.createPlayerSession(
        mode: HlsAdFilteringMode,
        preparer: HlsPlaybackPreparer,
        sourceTags: List<String> = emptyList(),
    ): PlayerSession {
        val koin = Koin()
        koin.loadModules(
            listOf(
                module {
                    single<MediaResolver> {
                        StaticMediaResolver(
                            UriMediaData(
                                "https://example.com/original.m3u8",
                                mapOf("User-Agent" to "AnimekoTest"),
                            ),
                        )
                    }
                    single<GetVideoScaffoldConfigUseCase> {
                        GetVideoScaffoldConfigUseCase {
                            flowOf(
                                VideoScaffoldConfig.AllDisabled.copy(
                                    hlsAdFilteringMode = mode,
                                ),
                            )
                        }
                    }
                    single<GetMediaSourceInstancesUseCase> {
                        object : GetMediaSourceInstancesUseCase {
                            override fun invoke(): Flow<List<MediaSourceInstance>> = flowOf(
                                listOf(
                                    createTestMediaSourceInstance(
                                        TestHttpMediaSource(mediaSourceId = TestMediaList.first().mediaSourceId),
                                        config = MediaSourceConfig(tags = sourceTags),
                                    ),
                                ),
                            )
                        }
                    }
                    single<HlsPlaybackPreparer> { preparer }
                },
            ),
        )
        val player = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        return PlayerSession(player, koin, backgroundScope, mainDispatcher = EmptyCoroutineContext)
    }

    private class StaticMediaResolver(
        private val data: UriMediaData,
    ) : MediaResolver {
        override fun supports(media: Media): Boolean = true

        override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
            return object : MediaDataProvider<UriMediaData> {
                override val extraFiles: MediaExtraFiles = MediaExtraFiles.EMPTY

                override suspend fun open(scopeForCleanup: CoroutineScope): UriMediaData {
                    return data
                }
            }
        }
    }

    private class RecordingHlsPlaybackPreparer : HlsPlaybackPreparer {
        val sessions = mutableListOf<RecordingHlsPlaybackProxySession>()
        val options = mutableListOf<HlsPlaybackOptions>()
        val startPositionHints = mutableListOf<Long?>()
        val prepareCount: Int get() = options.size

        override suspend fun prepare(
            data: UriMediaData,
            options: HlsPlaybackOptions,
            startPositionHintMillis: Long?,
        ): HlsPlaybackPreparerResult {
            this.options += options
            startPositionHints += startPositionHintMillis
            val session = RecordingHlsPlaybackProxySession()
            sessions += session
            return HlsPlaybackPreparerResult(
                data = UriMediaData(
                    "http://127.0.0.1:18080/proxied.m3u8",
                    data.headers,
                    data.extraFiles,
                ),
                session = session,
            )
        }
    }

    private class RecordingHlsPlaybackProxySession : HlsPlaybackProxySession {
        var closed: Boolean = false
            private set

        override fun close() {
            closed = true
        }
    }
}
