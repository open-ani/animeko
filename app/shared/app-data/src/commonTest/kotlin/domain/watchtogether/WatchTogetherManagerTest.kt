/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.watchtogether

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.preference.RememberedRoomSession
import me.him188.ani.app.data.models.preference.WatchTogetherSettings
import me.him188.ani.app.data.network.WatchTogetherApiService
import me.him188.ani.app.data.network.WatchTogetherJoinException
import me.him188.ani.app.data.network.WatchTogetherJoinFailure
import me.him188.ani.app.data.network.WatchTogetherServerEvent
import me.him188.ani.app.data.repository.user.Settings
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.client.models.AniReportWatchTogetherStateRequest
import me.him188.ani.client.models.AniWatchTogetherJoinResponse
import me.him188.ani.client.models.AniWatchTogetherMembership
import me.him188.ani.client.models.AniWatchTogetherPlayback
import me.him188.ani.client.models.AniWatchTogetherReportResponse
import me.him188.ani.client.models.AniWatchTogetherRoomSnapshot
import me.him188.ani.client.models.AniWatchTogetherRoomStatus
import me.him188.ani.client.models.AniWatchTogetherWatchingInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchTogetherManagerTest {
    @Test
    fun `join follow foreground lifecycle and leave keep one source of truth`() = runTest {
        val settings = MutableSettings(WatchTogetherSettings(enabled = true, followHost = true))
        val api = FakeApi(isHost = false)
        val gate = PlaybackAutomationGate()
        val manager = WatchTogetherManager(
            scope = backgroundScope,
            api = api,
            settings = settings,
            sessionStateProvider = FakeSessionStateProvider(),
            playbackBridge = LocalPlaybackBridge(),
            automationGate = gate,
            reconnectJitterMillis = { 1L },
        )

        manager.start()
        runCurrent()
        assertIs<WatchTogetherState.Idle>(manager.state.value)

        assertTrue(manager.join("Friday", "secret").isSuccess)
        runCurrent()
        val inRoom = assertIs<WatchTogetherState.InRoom>(manager.state.value)
        assertEquals("Friday", inRoom.session.roomName)
        assertEquals(true, api.lastJoinFollowing)
        assertTrue(gate.suppressed.value)
        assertNotNull(settings.state.value.rememberedSession)
        assertEquals(1, api.eventConnections)

        manager.setFollowing(false)
        runCurrent()
        assertFalse(inRoom.session.following.value)
        assertFalse(gate.suppressed.value)
        assertFalse(settings.state.value.followHost)
        assertFalse(api.reports.last().following)

        manager.setAppForeground(false)
        runCurrent()
        assertEquals(1, api.eventCompletions)
        manager.setAppForeground(true)
        runCurrent()
        assertEquals(2, api.eventConnections)

        manager.leave()
        runCurrent()
        assertIs<WatchTogetherState.Idle>(manager.state.value)
        assertNull(settings.state.value.rememberedSession)
        assertFalse(gate.suppressed.value)
        assertEquals(1, api.leaveCalls)
    }

    @Test
    fun `replacement bye exits without auto rejoin`() = runTest {
        val settings = MutableSettings(WatchTogetherSettings(enabled = true))
        val api = FakeApi(isHost = false, byeReason = "REPLACED")
        val manager = WatchTogetherManager(
            scope = backgroundScope,
            api = api,
            settings = settings,
            sessionStateProvider = FakeSessionStateProvider(),
            playbackBridge = LocalPlaybackBridge(),
            automationGate = PlaybackAutomationGate(),
            reconnectJitterMillis = { 1L },
        )

        manager.start()
        runCurrent()
        assertTrue(manager.join("Friday", "secret").isSuccess)
        runCurrent()

        assertIs<WatchTogetherState.Idle>(manager.state.value)
        assertNull(settings.state.value.rememberedSession)
        assertEquals(
            WatchTogetherEffect.RoomEnded(WatchTogetherRoomEndReason.SESSION_REPLACED),
            manager.effects.first(),
        )
        assertEquals(1, api.eventConnections)
    }

    @Test
    fun `auto join stops and clears a room that has closed`() = runTest {
        val settings = MutableSettings(
            WatchTogetherSettings(
                enabled = true,
                rememberedSession = RememberedRoomSession("Friday", "secret", joinedAt = 1L),
            ),
        )
        val api = FakeApi(
            isHost = false,
            joinFailure = WatchTogetherJoinFailure.ROOM_CLOSED,
        )
        val manager = WatchTogetherManager(
            scope = backgroundScope,
            api = api,
            settings = settings,
            sessionStateProvider = FakeSessionStateProvider(),
            playbackBridge = LocalPlaybackBridge(),
            automationGate = PlaybackAutomationGate(),
            reconnectJitterMillis = { 1L },
        )

        manager.start()
        runCurrent()

        assertIs<WatchTogetherState.Idle>(manager.state.value)
        assertNull(settings.state.value.rememberedSession)
        assertEquals(1, api.joinCalls)
        assertEquals(
            WatchTogetherEffect.RoomEnded(WatchTogetherRoomEndReason.ROOM_CLOSED),
            manager.effects.first(),
        )
    }

    @Test
    fun `follower deviating in-player is pulled back to the host episode`() = runTest {
        val settings = MutableSettings(WatchTogetherSettings(enabled = true, followHost = true))
        val bridge = LocalPlaybackBridge()
        val api = FakeApi(
            isHost = false,
            snapshotPlayback = AniWatchTogetherPlayback(
                info = watchingInfo(subjectId = 10, episodeId = 100),
                reportedAt = 1_000L,
            ),
        )
        val manager = WatchTogetherManager(
            scope = backgroundScope,
            api = api,
            settings = settings,
            sessionStateProvider = FakeSessionStateProvider(),
            playbackBridge = bridge,
            automationGate = PlaybackAutomationGate(),
            reconnectJitterMillis = { 1L },
        )
        manager.start()
        runCurrent()
        assertTrue(manager.join("Friday", "secret").isSuccess)
        runCurrent()

        val directives = mutableListOf<PlaybackDirective>()
        backgroundScope.launch { bridge.directives.collect { directives += it } }
        runCurrent()

        // Playing the host's episode: nothing to correct.
        bridge.updateLocalWatching(watchingInfo(subjectId = 10, episodeId = 100))
        runCurrent()
        assertTrue(directives.filterIsInstance<PlaybackDirective.SwitchEpisode>().isEmpty())

        // An in-player switch bypasses navigation; the manager must pull the follower back.
        bridge.updateLocalWatching(watchingInfo(subjectId = 10, episodeId = 101))
        runCurrent()
        assertEquals(
            100,
            directives.filterIsInstance<PlaybackDirective.SwitchEpisode>().single().episodeId,
        )
    }

    @Test
    fun `paused host stops periodic fixes while sse is connected`() = runTest {
        val settings = MutableSettings(WatchTogetherSettings(enabled = true))
        val bridge = LocalPlaybackBridge()
        val api = FakeApi(isHost = true)
        val manager = WatchTogetherManager(
            scope = backgroundScope,
            api = api,
            settings = settings,
            sessionStateProvider = FakeSessionStateProvider(),
            playbackBridge = bridge,
            automationGate = PlaybackAutomationGate(),
            localNowMillis = { currentTime },
            reconnectJitterMillis = { 1L },
        )
        manager.start()
        runCurrent()
        assertTrue(manager.join("Friday", "secret").isSuccess)
        runCurrent()

        bridge.updateLocalWatching(watchingInfo(subjectId = 1, episodeId = 2))
        runCurrent()
        val playingBaseline = api.reports.size
        advanceTimeBy(11_000)
        runCurrent()
        assertTrue(api.reports.size > playingBaseline, "playing host must keep sending periodic fixes")

        bridge.updateLocalWatching(watchingInfo(subjectId = 1, episodeId = 2, paused = true))
        runCurrent()
        val afterPauseEdge = api.reports.size
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(afterPauseEdge, api.reports.size, "frozen paused fix must not be re-sent periodically")

        bridge.updateLocalWatching(
            watchingInfo(subjectId = 1, episodeId = 2, paused = false, positionMillis = 60_000L),
        )
        runCurrent()
        assertTrue(api.reports.size > afterPauseEdge, "resume edge must report immediately")
    }

    private fun watchingInfo(
        subjectId: Int,
        episodeId: Int,
        paused: Boolean = false,
        positionMillis: Long = 0L,
    ) = AniWatchTogetherWatchingInfo(
        subjectId = subjectId,
        episodeId = episodeId,
        subjectName = "Subject",
        episodeSort = "01",
        episodeName = "Episode",
        positionMillis = positionMillis,
        positionAtMillis = 1_000L,
        durationMillis = 1_440_000L,
        paused = paused,
    )

    private class MutableSettings<T>(initial: T) : Settings<T> {
        val state = MutableStateFlow(initial)
        override val flow: Flow<T> = state

        override suspend fun set(value: T) {
            state.value = value
        }
    }

    private class FakeSessionStateProvider : SessionStateProvider {
        override val stateFlow: Flow<SessionState> = MutableStateFlow(SessionState.Valid(bangumiConnected = false))
        override val eventFlow: Flow<SessionEvent> = emptyFlow()
    }

    private class FakeApi(
        private val isHost: Boolean,
        private val byeReason: String? = null,
        private val joinFailure: WatchTogetherJoinFailure? = null,
        private val snapshotPlayback: AniWatchTogetherPlayback? = null,
    ) : WatchTogetherApiService {
        var joinCalls = 0
        var lastJoinFollowing: Boolean? = null
        var eventConnections = 0
        var eventCompletions = 0
        var leaveCalls = 0
        val reports = mutableListOf<AniReportWatchTogetherStateRequest>()

        override suspend fun join(
            roomName: String,
            password: String,
            following: Boolean,
        ): AniWatchTogetherJoinResponse {
            joinCalls++
            lastJoinFollowing = following
            joinFailure?.let { throw WatchTogetherJoinException(it) }
            return AniWatchTogetherJoinResponse(
                roomId = "room-id",
                created = isHost,
                isHost = isHost,
                sessionNonce = "nonce",
                serverTime = 1_000L,
                snapshot = snapshot(roomName),
            )
        }

        override suspend fun report(
            roomId: String,
            request: AniReportWatchTogetherStateRequest,
        ): AniWatchTogetherReportResponse {
            reports += request
            return AniWatchTogetherReportResponse(
                serverTime = 1_000L,
                membership = AniWatchTogetherMembership.OK,
            )
        }

        override suspend fun leave(roomId: String, sessionNonce: String) {
            leaveCalls++
        }

        override fun events(roomId: String, sessionNonce: String): Flow<WatchTogetherServerEvent> = flow {
            eventConnections++
            try {
                emit(WatchTogetherServerEvent.Connected)
                if (byeReason != null) {
                    emit(WatchTogetherServerEvent.Bye(byeReason))
                } else {
                    awaitCancellation()
                }
            } finally {
                eventCompletions++
            }
        }

        private fun snapshot(roomName: String) = AniWatchTogetherRoomSnapshot(
            roomId = "room-id",
            roomName = roomName,
            version = 1L,
            status = AniWatchTogetherRoomStatus.OPEN,
            hostUserId = "host-id",
            serverTime = 1_000L,
            playback = snapshotPlayback,
            members = emptyList(),
        )
    }
}
