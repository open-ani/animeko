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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.him188.ani.app.data.models.preference.WatchTogetherSettings
import me.him188.ani.app.data.network.WatchTogetherApiService
import me.him188.ani.app.data.network.WatchTogetherServerEvent
import me.him188.ani.app.data.repository.user.Settings
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.episode.mediaSelectorFlow
import me.him188.ani.app.domain.episode.player
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.TestUniversalMediaResolver
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.watchtogether.LocalPlaybackBridge
import me.him188.ani.app.domain.watchtogether.PlaybackAutomationGate
import me.him188.ani.app.domain.watchtogether.WatchTogetherManager
import me.him188.ani.client.models.AniReportWatchTogetherStateRequest
import me.him188.ani.client.models.AniWatchTogetherJoinResponse
import me.him188.ani.client.models.AniWatchTogetherMembership
import me.him188.ani.client.models.AniWatchTogetherReportResponse
import me.him188.ani.client.models.AniWatchTogetherRoomSnapshot
import me.him188.ani.client.models.AniWatchTogetherRoomStatus
import org.openani.mediamp.PlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WatchTogetherPlayerExtensionTest : AbstractPlayerExtensionTest() {
    @Test
    fun `entering player reports loading episode before media is loaded`() = runTest {
        val (suite, bridge) = createCase()

        val state = suite.createState(listOf(WatchTogetherPlayerExtension))
        state.onUIReady()
        runCurrent()

        val watching = assertNotNull(bridge.localWatching.value)
        assertEquals(subjectId, watching.subjectId)
        assertEquals(initialEpisodeId, watching.episodeId)
        assertEquals(0L, watching.positionMillis)
        assertEquals(NOW_MILLIS, watching.positionAtMillis)
        assertEquals(0L, watching.durationMillis)
        assertTrue(watching.paused)
        assertTrue(watching.loading == true)
        assertEquals(false, watching.buffering)
        assertEquals(1f, watching.playbackRate)
    }

    @Test
    fun `reloading media does not broadcast transient fixes`() = runTest {
        val (suite, bridge) = createCase()
        val state = suite.createState(listOf(WatchTogetherPlayerExtension))
        state.onUIReady()
        runCurrent()

        loadSelectedMedia(suite, state)

        suite.player.currentPositionMillis.value = 30_000L
        suite.player.playbackState.value = PlaybackState.PLAYING
        advanceTimeBy(1_100)
        runCurrent()
        val playing = assertNotNull(bridge.localWatching.value)
        assertEquals(30_000L, playing.positionMillis)
        assertFalse(playing.paused)

        // Simulate an in-episode source switch: the player drops back to READY and the
        // position transiently resets to 0. No fix may be produced from this state.
        suite.player.playbackState.value = PlaybackState.READY
        suite.player.currentPositionMillis.value = 0L
        advanceTimeBy(5_000)
        runCurrent()
        val duringReload = assertNotNull(bridge.localWatching.value)
        assertEquals(30_000L, duringReload.positionMillis)
        assertFalse(duringReload.paused)

        // Once playback stabilizes again, fixes resume from the real position.
        suite.player.currentPositionMillis.value = 31_000L
        suite.player.playbackState.value = PlaybackState.PLAYING
        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(31_000L, assertNotNull(bridge.localWatching.value).positionMillis)
    }

    @Test
    fun `everyone pauses after the first load while in a room`() = runTest {
        val api = InRoomWatchTogetherApiService()
        val (suite, bridge, manager) = createCase(
            api = api,
            settings = WatchTogetherSettings(enabled = true),
        )
        manager.start()
        runCurrent()
        assertTrue(manager.join("Room", "password").isSuccess)
        runCurrent()

        val state = suite.createState(listOf(WatchTogetherPlayerExtension))
        state.onUIReady()
        runCurrent()
        loadSelectedMedia(suite, state)

        // The player auto-plays right after loading; in a room the first play is held back so
        // the host can start everyone together.
        assertEquals(PlaybackState.PAUSED, suite.player.playbackState.value)
        val heldFix = assertNotNull(bridge.localWatching.value)
        assertTrue(heldFix.paused)
        assertEquals(false, heldFix.loading)
        // The transient auto-play between load and hold must never reach the room.
        assertTrue(
            api.reports.mapNotNull { it.watching }.none { it.paused == false },
            "pre-hold PLAYING leaked into a report: ${api.reports}",
        )

        // The host's explicit play is not held back again and reports immediately.
        suite.player.playbackState.value = PlaybackState.PLAYING
        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(PlaybackState.PLAYING, suite.player.playbackState.value)
        val playingFix = assertNotNull(bridge.localWatching.value)
        assertEquals(false, playingFix.paused)
        assertTrue(api.reports.mapNotNull { it.watching }.any { it.paused == false })
    }

    private fun TestScope.createCase(
        api: WatchTogetherApiService = UnusedWatchTogetherApiService,
        settings: WatchTogetherSettings = WatchTogetherSettings.Default,
    ): Triple<EpisodePlayerTestSuite, LocalPlaybackBridge, WatchTogetherManager> {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val suite = EpisodePlayerTestSuite(this)
        val bridge = LocalPlaybackBridge()
        val manager = WatchTogetherManager(
            scope = backgroundScope,
            api = api,
            settings = MutableSettings(settings),
            sessionStateProvider = LoggedInSessionStateProvider,
            playbackBridge = bridge,
            automationGate = PlaybackAutomationGate(),
            localNowMillis = { NOW_MILLIS },
        )
        suite.registerComponent<LocalPlaybackBridge> { bridge }
        suite.registerComponent<WatchTogetherManager> { manager }
        suite.registerComponent<MediaResolver> { TestUniversalMediaResolver }
        return Triple(suite, bridge, manager)
    }

    @OptIn(UnsafeEpisodeSessionApi::class)
    private suspend fun TestScope.loadSelectedMedia(
        suite: EpisodePlayerTestSuite,
        state: EpisodeFetchSelectPlayState,
        durationMillis: Long = 100_000L,
    ) {
        val media = TestMediaList[0]
        val source = suite.mediaSelectorTestBuilder.delayedMediaSource("watch-together-0")
        source.complete(listOf(media))
        state.mediaSelectorFlow.filterNotNull().first().select(media)
        suite.setMediaDuration(durationMillis)
        // Not advanceUntilIdle: once media loads, the reporter's sample ticker keeps the
        // scheduler busy forever. Bounded advancement is enough to finish the media load.
        runCurrent()
        advanceTimeBy(100)
        runCurrent()
    }

    private class MutableSettings<T>(initial: T) : Settings<T> {
        private val state = MutableStateFlow(initial)
        override val flow: Flow<T> = state

        override suspend fun set(value: T) {
            state.value = value
        }
    }

    private object LoggedInSessionStateProvider : SessionStateProvider {
        override val stateFlow: Flow<SessionState> = flowOf(SessionState.Valid(bangumiConnected = false))
        override val eventFlow: Flow<SessionEvent> = emptyFlow()
    }

    private class InRoomWatchTogetherApiService : WatchTogetherApiService {
        val reports = mutableListOf<AniReportWatchTogetherStateRequest>()

        override suspend fun join(
            roomName: String,
            password: String,
            following: Boolean,
        ): AniWatchTogetherJoinResponse = AniWatchTogetherJoinResponse(
            roomId = "room-id",
            created = true,
            isHost = true,
            sessionNonce = "nonce",
            serverTime = NOW_MILLIS,
            snapshot = AniWatchTogetherRoomSnapshot(
                roomId = "room-id",
                roomName = roomName,
                version = 1L,
                status = AniWatchTogetherRoomStatus.OPEN,
                hostUserId = "host-id",
                serverTime = NOW_MILLIS,
                members = emptyList(),
            ),
        )

        override suspend fun report(
            roomId: String,
            request: AniReportWatchTogetherStateRequest,
        ): AniWatchTogetherReportResponse {
            reports += request
            return AniWatchTogetherReportResponse(
                serverTime = NOW_MILLIS,
                membership = AniWatchTogetherMembership.OK,
            )
        }

        override suspend fun leave(roomId: String, sessionNonce: String) {
        }

        override fun events(roomId: String, sessionNonce: String): Flow<WatchTogetherServerEvent> = flow {
            emit(WatchTogetherServerEvent.Connected)
            awaitCancellation()
        }
    }

    private object UnusedWatchTogetherApiService : WatchTogetherApiService {
        override suspend fun join(
            roomName: String,
            password: String,
            following: Boolean,
        ): AniWatchTogetherJoinResponse = error("Unexpected Watch Together API call")

        override suspend fun report(
            roomId: String,
            request: AniReportWatchTogetherStateRequest,
        ): AniWatchTogetherReportResponse = error("Unexpected Watch Together API call")

        override suspend fun leave(roomId: String, sessionNonce: String) {
            error("Unexpected Watch Together API call")
        }

        override fun events(roomId: String, sessionNonce: String): Flow<WatchTogetherServerEvent> = emptyFlow()
    }

    private companion object {
        const val NOW_MILLIS = 1_234L
    }
}
