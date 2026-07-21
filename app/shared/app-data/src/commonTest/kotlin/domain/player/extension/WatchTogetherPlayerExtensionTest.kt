/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.preference.WatchTogetherSettings
import me.him188.ani.app.data.network.WatchTogetherApiService
import me.him188.ani.app.data.network.WatchTogetherServerEvent
import me.him188.ani.app.data.repository.user.Settings
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.watchtogether.LocalPlaybackBridge
import me.him188.ani.app.domain.watchtogether.PlaybackAutomationGate
import me.him188.ani.app.domain.watchtogether.WatchTogetherManager
import me.him188.ani.client.models.AniReportWatchTogetherStateRequest
import me.him188.ani.client.models.AniWatchTogetherJoinResponse
import me.him188.ani.client.models.AniWatchTogetherReportResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WatchTogetherPlayerExtensionTest : AbstractPlayerExtensionTest() {
    @Test
    fun `entering player reports loading episode before media is loaded`() = runTest {
        val suite = EpisodePlayerTestSuite(this)
        val bridge = LocalPlaybackBridge()
        val manager = WatchTogetherManager(
            scope = backgroundScope,
            api = UnusedWatchTogetherApiService,
            settings = MutableSettings(WatchTogetherSettings.Default),
            sessionStateProvider = LoggedInSessionStateProvider,
            playbackBridge = bridge,
            automationGate = PlaybackAutomationGate(),
            localNowMillis = { NOW_MILLIS },
        )
        suite.registerComponent<LocalPlaybackBridge> { bridge }
        suite.registerComponent<WatchTogetherManager> { manager }

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
        assertTrue(watching.buffering == true)
        assertEquals(1f, watching.playbackRate)
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

    private object UnusedWatchTogetherApiService : WatchTogetherApiService {
        override suspend fun join(roomName: String, password: String): AniWatchTogetherJoinResponse =
            error("Unexpected Watch Together API call")

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
