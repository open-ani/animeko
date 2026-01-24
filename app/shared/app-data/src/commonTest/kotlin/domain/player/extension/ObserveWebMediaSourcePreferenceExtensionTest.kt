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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.episode.mediaFetchSessionFlow
import me.him188.ani.app.domain.episode.mediaSelectorFlow
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.TestUniversalMediaResolver
import me.him188.ani.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import me.him188.ani.app.domain.mediasource.SetPreferredWebMediaSourceUseCase
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.coroutines.childScope
import kotlin.contracts.contract
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * @see ObserveWebMediaSourcePreferenceExtension
 */
class ObserveWebMediaSourcePreferenceExtensionTest : AbstractPlayerExtensionTest() {
    private val preferredWebMediaSource = MutableStateFlow<String?>(null)
    private val setPreferenceCalls = mutableListOf<Pair<Int, String>>()

    data class Context(
        val scope: CoroutineScope,
        val suite: EpisodePlayerTestSuite,
        val state: EpisodeFetchSelectPlayState,
    )

    private fun TestScope.createCase(
        config: (scope: CoroutineScope, suite: EpisodePlayerTestSuite) -> Unit = { _, _ -> },
    ): Context {
        contract {
            callsInPlace(config, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
        }
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val testScope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, testScope)

        suite.registerComponent<MediaResolver> {
            TestUniversalMediaResolver
        }
        suite.registerComponent<GetPreferredWebMediaSourceUseCase> {
            GetPreferredWebMediaSourceUseCase { preferredWebMediaSource }
        }
        suite.registerComponent<SetPreferredWebMediaSourceUseCase> {
            SetPreferredWebMediaSourceUseCase { subjectId, mediaSourceId ->
                if (mediaSourceId != null) {
                    setPreferenceCalls.add(subjectId to mediaSourceId)
                } else {
                    setPreferenceCalls.removeAll { it.first == subjectId }
                }
                preferredWebMediaSource.value = mediaSourceId
            }
        }

        // Reset state
        preferredWebMediaSource.value = null
        setPreferenceCalls.clear()

        config(testScope, suite)

        val state = suite.createState(
            listOf(
                ObserveWebMediaSourcePreferenceExtension,
            ),
        )
        state.onUIReady()
        return Context(testScope, suite, state)
    }

    private fun startMediaFetcher(
        state: EpisodeFetchSelectPlayState,
        testScope: CoroutineScope
    ) {
        // MediaFetcher is lazy. We perform fetching in testScope (i.e. foreground). `advanceUntilIdle` will wait for the fetching to complete.
        state.mediaFetchSessionFlow.filterNotNull().flatMapLatest { it.cumulativeResults }.launchIn(testScope)
    }

    @Test
    fun `selecting web media updates preference`() = runTest {
        val web1: CompletableDeferred<List<Media>>
        val (testScope, suite, state) = createCase { _, suite ->
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1", kind = MediaSourceKind.WEB)
        }

        startMediaFetcher(state, testScope)

        val media = suite.mediaSelectorTestBuilder.createMedia("web1", kind = MediaSourceKind.WEB)
        web1.complete(listOf(media))
        advanceUntilIdle()

        // Select the web media
        state.mediaSelectorFlow.filterNotNull().first().select(media)
        advanceUntilIdle()

        // Verify preference was set
        assertEquals(1, setPreferenceCalls.size)
        assertEquals(subjectId to "web1", setPreferenceCalls.first())

        testScope.cancel()
    }

    @Test
    fun `selecting same web source does not update preference again`() = runTest {
        val web1: CompletableDeferred<List<Media>>
        val (testScope, suite, state) = createCase { _, suite ->
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1", kind = MediaSourceKind.WEB)
        }

        // Pre-set the preference
        preferredWebMediaSource.value = "web1"

        startMediaFetcher(state, testScope)

        val media = suite.mediaSelectorTestBuilder.createMedia("web1", kind = MediaSourceKind.WEB)
        web1.complete(listOf(media))
        advanceUntilIdle()

        // Select the web media
        state.mediaSelectorFlow.filterNotNull().first().select(media)
        advanceUntilIdle()

        // Preference should not be updated since it's already the same
        assertEquals(0, setPreferenceCalls.size)

        testScope.cancel()
    }

    @Test
    fun `selecting different web source updates preference`() = runTest {
        val web2: CompletableDeferred<List<Media>>
        val (testScope, suite, state) = createCase { _, suite ->
            web2 = suite.mediaSelectorTestBuilder.delayedMediaSource("web2", kind = MediaSourceKind.WEB)
        }

        // Pre-set the preference to a different source
        preferredWebMediaSource.value = "web1"

        startMediaFetcher(state, testScope)

        val media = suite.mediaSelectorTestBuilder.createMedia("web2", kind = MediaSourceKind.WEB)
        web2.complete(listOf(media))
        advanceUntilIdle()

        // Select the different web media
        state.mediaSelectorFlow.filterNotNull().first().select(media)
        advanceUntilIdle()

        // Preference should be updated to the new source
        assertEquals(1, setPreferenceCalls.size)
        assertEquals(subjectId to "web2", setPreferenceCalls.first())

        testScope.cancel()
    }

    @Test
    fun `selecting non-web media does not update preference`() = runTest {
        val bt1: CompletableDeferred<List<Media>>
        val (testScope, suite, state) = createCase { _, suite ->
            bt1 = suite.mediaSelectorTestBuilder.delayedMediaSource("bt1", kind = MediaSourceKind.WEB)
        }

        startMediaFetcher(state, testScope)

        val media = suite.mediaSelectorTestBuilder.createMedia("bt1", kind = MediaSourceKind.BitTorrent)
        bt1.complete(listOf(media))
        advanceUntilIdle()

        // Select the BT media
        state.mediaSelectorFlow.filterNotNull().first().select(media)
        advanceUntilIdle()

        // No preference should be set for non-web media
        assertEquals(0, setPreferenceCalls.size)

        testScope.cancel()
    }

    @Test
    fun `remove preference if preferred source fails`() = runTest {
        val web1: CompletableDeferred<List<Media>>
        val web2: CompletableDeferred<List<Media>>
        val context = createCase { _, suite ->
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1", kind = MediaSourceKind.WEB)
            web2 = suite.mediaSelectorTestBuilder.delayedMediaSource("web2", kind = MediaSourceKind.WEB)
        }
        val (testScope, suite, state) = context

        // Pre-set the preference
        preferredWebMediaSource.value = "web1"
        setPreferenceCalls.add(subjectId to "web1")

        startMediaFetcher(state, testScope)
        web1.completeExceptionally(IllegalStateException("constant failure"))
        advanceUntilIdle()

        // Source web1 failed, so preference should be removed
        assertEquals(0, setPreferenceCalls.size)

        val media = suite.mediaSelectorTestBuilder.createMedia("web2", kind = MediaSourceKind.WEB)
        web2.complete(listOf(media))
        advanceUntilIdle()

        // Select the different web media
        state.mediaSelectorFlow.filterNotNull().first().select(media)
        advanceUntilIdle()

        // Preference should be updated to the new source
        assertEquals(1, setPreferenceCalls.size)
        assertEquals(subjectId to "web2", setPreferenceCalls.first())

        testScope.cancel()
    }
}
