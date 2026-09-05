/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(UnsafeEpisodeSessionApi::class)

package me.him188.ani.app.domain.player.extension

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.episode.mediaFetchSessionFlow
import me.him188.ani.app.domain.episode.mediaSelectorFlow
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.TestUniversalMediaResolver
import me.him188.ani.app.domain.media.selector.MediaAutoSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorEventSavePreferenceUseCase
import me.him188.ani.app.domain.media.selector.eventHandling
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import me.him188.ani.utils.coroutines.childScope

/**
 * @see SaveMediaPreferenceExtension
 */
class SaveMediaPreferenceExtensionTest : AbstractPlayerExtensionTest() {
    private fun TestScope.createCase(
        saved: MutableList<Pair<Int, MediaPreference>>,
        setupSources: (suite: EpisodePlayerTestSuite) -> Unit = {},
    ): Triple<CoroutineScope, EpisodePlayerTestSuite, EpisodeFetchSelectPlayState> {
        val testScope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, testScope)
        suite.registerComponent<MediaResolver> {
            TestUniversalMediaResolver
        }
        suite.registerComponent<MediaSelectorEventSavePreferenceUseCase> {
            MediaSelectorEventSavePreferenceUseCase { mediaSelector, subjectId ->
                mediaSelector.eventHandling.run {
                    savePreferenceOnSelect { saved.add(subjectId to it) }
                }
            }
        }
        setupSources(suite)

        val state = suite.createState(
            listOf(
                SaveMediaPreferenceExtension,
            ),
        )
        state.onUIReady()
        advanceUntilIdle()
        return Triple(testScope, suite, state)
    }

    private fun createSingleLanguageMedia(mediaSourceId: String): DefaultMedia = createTestDefaultMedia(
        mediaId = "$mediaSourceId.1",
        mediaSourceId = mediaSourceId,
        originalTitle = "[XX字幕组] 孤独摇滚 ABC ABC ABC ABC ABC ABC ABC ABC ABC ABC",
        download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:1"),
        originalUrl = "https://example.com/1",
        publishedTime = 1,
        episodeRange = EpisodeRange.single(EpisodeSort(1)),
        properties = createTestMediaProperties(
            subtitleLanguageIds = listOf(SubtitleLanguage.ChineseSimplified.id),
            resolution = "1080P",
            alliance = "XX字幕组",
            size = 122.megaBytes,
            subtitleKind = SubtitleKind.CLOSED,
        ),
        kind = MediaSourceKind.WEB,
        location = MediaSourceLocation.Online,
    )

    @Test
    fun `SAVE-04 fetch 完成后手动 select 经 debounce 1s 恰保存一次且载荷来自所选 media`() = runTest {
        val saved = mutableListOf<Pair<Int, MediaPreference>>()
        lateinit var web1: CompletableDeferred<List<Media>>
        val (testScope, suite, state) = createCase(saved) { suite ->
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1")
        }

        val myMedia = createSingleLanguageMedia("web1")
        state.mediaFetchSessionFlow.filterNotNull().flatMapLatest { it.cumulativeResults }.launchIn(testScope)
        web1.complete(listOf(myMedia))
        advanceUntilIdle()

        val selector = state.mediaSelectorFlow.filterNotNull().first()
        assertTrue(selector.select(myMedia))
        runCurrent()

        advanceTimeBy(999)
        runCurrent()
        assertEquals(emptyList(), saved)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(
            listOf(
                subjectId to suite.mediaSelectorTestBuilder.savedUserPreference.value.copy(
                    alliance = "XX字幕组",
                    resolution = "1080P",
                    subtitleLanguageId = SubtitleLanguage.ChineseSimplified.id,
                    mediaSourceId = "web1",
                ),
            ),
            saved,
        )

        advanceUntilIdle()
        assertEquals(1, saved.size)

        testScope.cancel()
    }

    @Test
    fun `SAVE-04 SAVE-01 trySelectDefault 自动选择不触发保存`() = runTest {
        val saved = mutableListOf<Pair<Int, MediaPreference>>()
        lateinit var web1: CompletableDeferred<List<Media>>
        val (testScope, suite, state) = createCase(saved) { suite ->
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1")
        }

        val myMedia = createSingleLanguageMedia("web1")
        state.mediaFetchSessionFlow.filterNotNull().flatMapLatest { it.cumulativeResults }.launchIn(testScope)
        web1.complete(listOf(myMedia))
        advanceUntilIdle()

        val session = state.mediaFetchSessionFlow.filterNotNull().first()
        val selector = state.mediaSelectorFlow.filterNotNull().first()
        val selected = MediaAutoSelector(selector).select(session)
        assertEquals(myMedia, selected)

        // PINNED: SAVE-01 自动选择 (selectDefault, updatePreference=false) 不广播 onChangePreference, 不触发保存
        advanceTimeBy(1001)
        runCurrent()
        advanceUntilIdle()
        assertEquals(emptyList(), saved)

        testScope.cancel()
    }
}
