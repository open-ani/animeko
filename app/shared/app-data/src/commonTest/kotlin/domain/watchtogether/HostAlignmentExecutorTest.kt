package me.him188.ani.app.domain.watchtogether

import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.preference.WatchTogetherSettings
import me.him188.ani.app.data.network.WatchTogetherApiService
import me.him188.ani.app.data.network.WatchTogetherServerEvent
import me.him188.ani.app.data.repository.user.Settings
import me.him188.ani.app.domain.player.extension.HostAlignmentExecutor
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
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.test.TestMediampPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HostAlignmentExecutorTest {
    @Test
    fun `paused alignment waits for native completion and buffer without optimistic success`() = runTest {
        val case = createCase(paused = true)
        case.player.holdSeeks = true
        val job = start(case)
        runCurrent()
        assertEquals(10_000L, case.player.currentPositionMillis.value)
        assertTrue(job.isActive)
        case.player.completeHeldSeek(stalledAtCompletion = true)
        runCurrent()
        assertTrue(job.isActive)
        case.player.injectStall(false)
        runCurrent()
        job.join()
        assertFalse(case.player.state.value.playWhenReady)
        assertEquals(WatchTogetherEffect.HostAlignmentFinished(true), case.manager.effects.first())
        assertEquals(case.initialReports, case.api.reports.size)
    }

    @Test
    fun `masked completion waits for subsequent buffering before accepting paused seek`() = runTest {
        val case = createCase(paused = true)
        val job = start(case)
        runCurrent()
        advanceTimeBy(10)
        case.player.injectStall(true)
        advanceTimeBy(200)
        runCurrent()
        assertTrue(job.isActive)
        case.player.injectStall(false)
        advanceTimeBy(101)
        runCurrent()
        job.join()
        assertEquals(WatchTogetherEffect.HostAlignmentFinished(true), case.manager.effects.first())
        assertFalse(case.player.state.value.playWhenReady)
    }

    @Test
    fun `paused final residual accepts fifty milliseconds but rejects fifty one`() = runTest {
        for (residual in listOf(-51L, -50L, -30L, 30L, 50L, 51L)) {
            val case = createCase(paused = true)
            case.player.holdSeeks = true
            val job = start(case)
            runCurrent()
            case.session.updateSnapshot(case.session.snapshot.value.copy(
                version = 2,
                playback = AniWatchTogetherPlayback(case.host.copy(positionMillis = 10_000 - residual), now()),
            ))
            case.player.completeHeldSeek()
            job.join()
            assertEquals(WatchTogetherEffect.HostAlignmentFinished(HostAlignment.isAcceptableResidual(residual)), case.manager.effects.first())
            case.manager.leave()
            case.player.close()
            advanceTimeBy(HostAlignment.COOLDOWN_MILLIS)
        }
    }

    @Test
    fun `playing alignment pauses ahead then resumes without reporting`() = runTest {
        val case = createCase()
        val job = start(case)
        tick(case)
        advanceTimeBy(401)
        runCurrent()
        assertFalse(case.player.state.value.playWhenReady)
        assertTrue(case.bridge.isAligning)
        advanceTimeBy(1_000)
        runCurrent()
        job.join()
        assertTrue(case.player.state.value.isPlaying)
        assertEquals(WatchTogetherEffect.HostAlignmentFinished(true), case.manager.effects.first())
        assertEquals(case.initialReports, case.api.reports.size)
    }

    @Test
    fun `missing native position samples report failure instead of optimistic success`() = runTest {
        val case = createCase()
        val job = start(case)
        advanceTimeBy(401)
        runCurrent()
        job.join()
        assertEquals(WatchTogetherEffect.HostAlignmentFinished(false), case.manager.effects.first())
    }

    @Test
    fun `host clearing playback cancels corrective pause and restores play intent`() = runTest {
        val case = createCase()
        val job = start(case)
        tick(case)
        advanceTimeBy(401)
        runCurrent()
        assertFalse(case.player.state.value.playWhenReady)
        case.session.updateSnapshot(case.session.snapshot.value.copy(version = 2, playback = null))
        runCurrent()
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(case.player.state.value.playWhenReady)
        assertFalse(case.bridge.isAligning)
    }

    @Test
    fun `slower seek retries once and gives up when still behind`() = runTest {
        val case = createCase()
        case.player.holdSeeks = true
        val job = start(case)
        runCurrent()
        advanceTimeBy(1_200)
        case.player.completeHeldSeek()
        tick(case)
        advanceTimeBy(401)
        runCurrent()
        val secondTarget = case.player.currentPositionMillis.value
        assertTrue(secondTarget > 12_000, "retry must compensate the measured 1200 ms latency")
        advanceTimeBy(2_000)
        case.player.completeHeldSeek()
        advanceTimeBy(401)
        runCurrent()
        job.join()
        assertEquals(WatchTogetherEffect.HostAlignmentFinished(false), case.manager.effects.first())
        assertNull(case.bridge.alignmentRequest.value)
        assertEquals(case.initialReports, case.api.reports.size)
    }

    @Test
    fun `room departure host episode change local departure and media reload cancel alignment`() = runTest {
        repeat(4) { scenario ->
            val case = createCase()
            case.player.holdSeeks = true
            val job = start(case)
            runCurrent()
            when (scenario) {
                0 -> case.manager.leave()
                1 -> case.session.updateSnapshot(case.session.snapshot.value.copy(
                    version = 2,
                    playback = AniWatchTogetherPlayback(case.host.copy(episodeId = 3), now()),
                ))
                2 -> case.bridge.updateLocalWatching(null)
                3 -> case.player.setMediaData(UriMediaData("file://replacement.mp4"))
            }
            runCurrent()
            job.join()
            assertTrue(job.isCancelled)
            assertFalse(case.bridge.isAligning)
            case.manager.leave()
            case.player.close()
        }
    }

    private fun TestScope.now() = 1_000 + testScheduler.currentTime

    private fun TestScope.tick(case: Case) = backgroundScope.launch {
        var lastAt = now()
        var playing = case.player.state.value.isPlaying
        fun advanceClock() {
            val at = now()
            if (playing) case.player.injectPosition(case.player.nativePositionMillis + at - lastAt)
            lastAt = at
        }
        launch {
            case.player.state.collect {
                advanceClock()
                playing = it.isPlaying
            }
        }
        while (isActive) {
            delay(50)
            advanceClock()
        }
    }

    private fun TestScope.start(case: Case): Job {
        case.manager.alignToHost()
        val request = assertNotNull(case.bridge.alignmentRequest.value)
        return launch {
            try {
                case.executor.run(request, 1, 2)
            } finally {
                case.bridge.finishAlignment(request)
            }
        }
    }

    private suspend fun TestScope.createCase(paused: Boolean = false): Case {
        val host = AniWatchTogetherWatchingInfo(
            subjectId = 1, episodeId = 2, subjectName = "Subject", episodeSort = "1", episodeName = "Episode",
            positionMillis = 10_000, positionAtMillis = now(), durationMillis = 100_000,
            paused = paused, buffering = false, loading = false, playbackRate = 1f,
        )
        val api = Api(host)
        val bridge = LocalPlaybackBridge()
        bridge.updateLocalWatching(host)
        val manager = WatchTogetherManager(
            backgroundScope, api, MutableSettings(), LoggedIn, bridge, PlaybackAutomationGate(),
            localNowMillis = { now() },
        )
        manager.start()
        runCurrent()
        manager.join("Room", "password").getOrThrow()
        runCurrent()
        val session = assertIs<WatchTogetherState.InRoom>(manager.state.value).session
        val player = TestMediampPlayer(backgroundScope.coroutineContext)
        player.setMediaData(UriMediaData("file://test.mp4"), playWhenReady = !paused)
        return Case(host, api, bridge, manager, session, player,
            HostAlignmentExecutor(player, bridge, manager, testScheduler.timeSource), api.reports.size)
    }

    private data class Case(
        val host: AniWatchTogetherWatchingInfo,
        val api: Api,
        val bridge: LocalPlaybackBridge,
        val manager: WatchTogetherManager,
        val session: RoomSession,
        val player: TestMediampPlayer,
        val executor: HostAlignmentExecutor,
        val initialReports: Int,
    )

    private class MutableSettings : Settings<WatchTogetherSettings> {
        override val flow = MutableStateFlow(WatchTogetherSettings(enabled = true))
        override suspend fun set(value: WatchTogetherSettings) { flow.value = value }
    }

    private object LoggedIn : SessionStateProvider {
        override val stateFlow = MutableStateFlow<SessionState>(SessionState.Valid(bangumiConnected = false))
        override val eventFlow: Flow<SessionEvent> = emptyFlow()
    }

    private class Api(private val host: AniWatchTogetherWatchingInfo) : WatchTogetherApiService {
        val reports = mutableListOf<AniReportWatchTogetherStateRequest>()
        override suspend fun join(roomName: String, password: String, following: Boolean) = AniWatchTogetherJoinResponse(
            roomId = "room", created = false, isHost = false, sessionNonce = "nonce", serverTime = 1_000,
            snapshot = AniWatchTogetherRoomSnapshot(
                roomId = "room", roomName = roomName, version = 1, status = AniWatchTogetherRoomStatus.OPEN,
                hostUserId = "host", serverTime = 1_000, members = emptyList(),
                playback = AniWatchTogetherPlayback(host, 1_000),
            ),
        )
        override suspend fun report(roomId: String, request: AniReportWatchTogetherStateRequest): AniWatchTogetherReportResponse {
            reports += request
            return AniWatchTogetherReportResponse(serverTime = 1_000, membership = AniWatchTogetherMembership.OK, version = 1)
        }
        override suspend fun leave(roomId: String, sessionNonce: String) {}
        override fun events(roomId: String, sessionNonce: String): Flow<WatchTogetherServerEvent> = flow {
            emit(WatchTogetherServerEvent.Connected)
            awaitCancellation()
        }
    }
}
