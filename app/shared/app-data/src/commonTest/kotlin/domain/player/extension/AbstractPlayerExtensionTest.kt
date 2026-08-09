/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import org.openani.mediamp.isMediaLoaded
import org.openani.mediamp.metadata.MediaProperties
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.test.TestMediampPlayer
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class AbstractPlayerExtensionTest {
    protected val subjectId = 1
    protected val initialEpisodeId = 2


    fun EpisodePlayerTestSuite.createState(
        extensions: List<EpisodePlayerExtensionFactory<*>> = listOf(),
    ): EpisodeFetchSelectPlayState {
        return EpisodeFetchSelectPlayState(
            subjectId,
            initialEpisodeId,
            player,
            backgroundScope,
            extensions = extensions,
            koin,
            mainDispatcher = EmptyCoroutineContext, // no switch
        )
    }

    /**
     * Sets the duration the (fake) media reports.
     *
     * - Before a media is opened, this configures [TestMediampPlayer.defaultMediaProperties], so the
     *   next open (e.g. triggered by selecting a media) reports this duration at its Ready point.
     * - If a media is already loaded, the updated properties are also injected into the current
     *   session, like a backend delivering a late properties update.
     *
     * Pass `null` for an unknown duration (v2 semantics for live/unknown media; replaces v1's `0`/`-1`).
     */
    fun EpisodePlayerTestSuite.setMediaDuration(durationMillis: Long?) {
        val properties = (player.mediaProperties.value ?: player.defaultMediaProperties)
            .copy(durationMillis = durationMillis)
        player.defaultMediaProperties = properties
        if (player.state.value.isMediaLoaded) {
            player.injectProperties(properties)
        }
    }


    @BeforeTest
    fun installDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun resetDispatcher() {
        Dispatchers.resetMain()
    }
}

/**
 * Loads a fake media directly into the player (bypassing the fetch-select pipeline), reporting
 * [durationMs] at the Ready point.
 *
 * Suspends until the open completes (the test scheduler is pumped automatically while suspended).
 * On return the player is [org.openani.mediamp.MediaStatus.Ready]; with [playWhenReady] it is
 * actually playing (fake transport is not stalled).
 *
 * Note: media loaded this way does NOT produce [EpisodeFetchSelectPlayState.MediaLoadedEvent] —
 * extensions gated on it stay disabled, which some tests rely on.
 */
suspend fun TestMediampPlayer.loadMedia(
    durationMs: Long?,
    playWhenReady: Boolean = false,
    uri: String = "file://test.mp4",
    startPositionMillis: Long = 0L,
) {
    defaultMediaProperties = defaultMediaProperties.copy(durationMillis = durationMs)
    setMediaData(UriMediaData(uri), playWhenReady = playWhenReady, startPositionMillis = startPositionMillis)
}
