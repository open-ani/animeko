/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.watchtogether

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.TestUniversalMediaResolver
import me.him188.ani.app.domain.player.extension.AbstractPlayerExtensionTest
import me.him188.ani.app.domain.player.extension.EpisodePlayerExtensionFactory
import me.him188.ani.app.domain.player.extension.WatchTogetherPlayerExtension
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
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.test.TestMediampPlayer
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Two real client stacks sharing a delayed in-memory HTTP/SSE server and one virtual timeline. */
class WatchTogetherAlignmentEndToEndTest : AbstractPlayerExtensionTest() {
    @Test
    fun `playing host and member behind align without extra report`() = runTest {
        val room = createRoom(driftMillis = -1_200)
        align(room, "behind 1200 ms")
        assertEquals(1, room.member.player.seeks.size)
        assertTrue(room.member.player.correctivePauses > 0)
    }

    @Test
    fun `member ahead pauses to let the host catch up`() = runTest {
        val room = createRoom(driftMillis = 800)
        align(room, "ahead 800 ms")
        assertTrue(room.member.player.correctivePauses > 0)
        assertTrue(room.member.player.state.value.isPlaying)
    }

    @Test
    fun `paused host aligns exactly and member stays paused`() = runTest {
        val room = createRoom(driftMillis = -1_200, paused = true)
        align(room, "paused host")
        assertEquals(1, room.member.player.seeks.size)
        assertFalse(room.member.player.state.value.playWhenReady)
        assertEquals(0, room.member.player.correctivePauses)
    }

    @Test
    fun `slow seek measures latency and retries once`() = runTest {
        val room = createRoom(driftMillis = -1_200, seekLatencyMillis = 1_200)
        align(room, "1200 ms seek")
        assertEquals(2, room.member.player.seeks.size)
        assertTrue(room.member.player.seeks.all { it.completedAt - it.startedAt == 1_200L })
    }

    @Test
    fun `small host seek sends one report and fresh snapshot reaches member`() = runTest {
        val room = createRoom(driftMillis = -1_200)
        val reports = room.server.reportCount("host")
        val oldPlayback = assertNotNull(room.member.session.snapshot.value.playback)
        // A cached host seek has no data stall; SeekCompleted is its only immediate-report edge.
        room.host.player.seekTo(room.host.player.currentPositionMillis.value + 1_000)
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        assertEquals(reports + 1, room.server.reportCount("host"))
        println("E2E host small seek: immediateReports=${room.server.reportCount("host") - reports}")
        val playback = assertNotNull(room.member.session.snapshot.value.playback)
        assertTrue(playback.reportedAt > oldPlayback.reportedAt)
        assertTrue(playback.info.positionAt(room.server.now()) - oldPlayback.info.positionAt(room.server.now()) > 900)
        align(room, "host seek plus 1000 ms")
    }

    @Test
    fun `stale member clock refreshes with exactly one report`() = runTest {
        val room = createRoom(driftMillis = -1_200, paused = true)
        val reports = room.server.reportCount("member")
        advanceTimeBy(61_100)
        runCurrent()
        assertEquals(reports, room.server.reportCount("member"), "paused member must have no periodic reports")
        assertTrue(assertNotNull(room.member.session.serverClock.latestSampleAgeMillis()) > 60_000)
        align(room, "stale clock", expectedReports = 1)
        assertTrue(assertNotNull(room.member.session.serverClock.latestSampleAgeMillis()) < 1_000)
    }

    @Test
    fun `alignment stays within fifty milliseconds for thirty seconds without correction`() = runTest {
        val room = createRoom(driftMillis = -1_200)
        align(room, "before 30 second observation")
        val seekCount = room.member.player.seeks.size
        room.member.directives.clear()
        var maxResidual = 0L
        repeat(30_000) {
            advanceTimeBy(1)
            runCurrent()
            maxResidual = maxOf(maxResidual, abs(residual(room)))
            assertTrue(abs(residual(room)) <= 50, "drift at ${testScheduler.currentTime}: ${residual(room)} ms")
        }
        assertTrue(maxResidual <= 50)
        assertTrue(room.member.directives.filterIsInstance<PlaybackDirective.Sync>().isEmpty())
        assertEquals(seekCount, room.member.player.seeks.size)
        println("E2E 30 seconds: residual=${residual(room)} ms maxAbsResidual=$maxResidual ms Sync=0 extraSeek=0")
    }

    private suspend fun TestScope.align(room: Room, label: String, expectedReports: Int = 0) {
        val before = room.server.reportCount("member")
        val initialResidual = residual(room)
        room.member.manager.alignToHost()
        assertTrue(room.member.bridge.isAligning, "alignment request was not accepted")
        runCurrent()
        var elapsed = 0L
        while (room.member.bridge.isAligning && elapsed < 15_000) {
            advanceTimeBy(10)
            runCurrent()
            elapsed += 10
        }
        assertFalse(room.member.bridge.isAligning, "alignment timed out")
        assertEquals(WatchTogetherEffect.HostAlignmentFinished(true), room.member.alignmentResults.single())
        assertEquals(before + expectedReports, room.server.reportCount("member"))
        val difference = residual(room)
        println("E2E $label: initialResidual=$initialResidual ms residual=$difference ms seeks=${room.member.player.seeks.size} seekLatencies=${room.member.player.seeks.map { it.completedAt - it.startedAt }} ms clockOffset=${room.member.session.serverClock.currentOffsetMillis()} ms pauseWaits=${room.member.player.correctivePauses} extraReports=${room.server.reportCount("member") - before}")
        assertTrue(abs(difference) <= 50, "$label: member - host = $difference ms")
        assertEquals(1f, room.host.player.backend.nativePlaybackRate)
        assertEquals(1f, room.member.player.backend.nativePlaybackRate)
        assertTrue(room.member.directives.filterIsInstance<PlaybackDirective.Sync>().isEmpty(), "continuous correction interfered")
    }

    /** Both values are read without advancing the common virtual clock. */
    private fun residual(room: Room): Long =
        room.member.player.currentPositionMillis.value - room.host.player.currentPositionMillis.value

    private suspend fun TestScope.createRoom(
        driftMillis: Long,
        paused: Boolean = false,
        seekLatencyMillis: Long = 200,
    ): Room {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val server = SimulatedServer(this)
        val host = createClient(server, "host", clockOffsetMillis = 0)
        val member = createClient(server, "member", clockOffsetMillis = 3_000)
        host.player.jumpTo(60_000)
        member.player.jumpTo(60_000)
        runCurrent()
        host.manager.join("Room", "password").getOrThrow()
        advanceTimeBy(300)
        runCurrent()
        member.manager.join("Room", "password").getOrThrow()
        advanceTimeBy(500)
        runCurrent()
        // Normal join sync has settled before introducing the sub-threshold drift.
        if (!paused) {
            host.player.play()
            member.player.play()
            runCurrent()
            advanceTimeBy(500)
            runCurrent()
        }
        member.player.jumpTo(host.player.positionNow() + driftMillis)
        advanceTimeBy(1_200)
        runCurrent()
        member.player.seekLatencyMillis = seekLatencyMillis
        host.player.seeks.clear()
        member.player.seeks.clear()
        member.player.correctivePauses = 0
        member.directives.clear()
        assertEquals(paused, member.player.state.value.playWhenReady.not())
        assertTrue(abs(residual(Room(server, host, member)) - driftMillis) <= FRAME_MILLIS, "initial drift: ${residual(Room(server, host, member))}")
        assertTrue(abs(member.session.serverClock.currentOffsetMillis() + 3_000) <= 25)
        assertTrue(abs(member.manager.serverNowMillis() - server.now()) <= 25)
        return Room(server, host, member)
    }

    @OptIn(UnsafeEpisodeSessionApi::class)
    private suspend fun TestScope.createClient(server: SimulatedServer, id: String, clockOffsetMillis: Long): Client {
        val suite = EpisodePlayerTestSuite(this)
        val player = SimulatedPlayer(this, suite.player)
        val bridge = LocalPlaybackBridge()
        val manager = WatchTogetherManager(
            backgroundScope, server.client(id), MutableSettings(), LoggedIn, bridge, PlaybackAutomationGate(),
            localNowMillis = { server.now() + clockOffsetMillis },
        )
        suite.registerComponent<LocalPlaybackBridge> { bridge }
        suite.registerComponent<WatchTogetherManager> { manager }
        suite.registerComponent<MediaResolver> { TestUniversalMediaResolver }
        val state = EpisodeFetchSelectPlayState(
            subjectId, initialEpisodeId, player, backgroundScope,
            extensions = listOf(EpisodePlayerExtensionFactory { context, koin ->
                WatchTogetherPlayerExtension(context, koin, testScheduler.timeSource)
            }),
            suite.koin,
            mainDispatcher = EmptyCoroutineContext,
        )
        manager.start()
        state.onUIReady()
        runCurrent()
        val media = TestMediaList[0]
        suite.mediaSelectorTestBuilder.delayedMediaSource("e2e-$id").complete(listOf(media))
        suite.setMediaDuration(1_440_000)
        state.mediaSelectorFlow.filterNotNull().first().select(media)
        runCurrent()
        advanceTimeBy(100)
        runCurrent()
        player.pause()
        runCurrent()
        val client = Client(manager, bridge, player)
        backgroundScope.launch { bridge.directives.collect { client.directives += it } }
        backgroundScope.launch {
            manager.effects.collect { if (it is WatchTogetherEffect.HostAlignmentFinished) client.alignmentResults += it }
        }
        runCurrent()
        return client
    }

    private data class Room(val server: SimulatedServer, val host: Client, val member: Client)

    private class Client(
        val manager: WatchTogetherManager,
        val bridge: LocalPlaybackBridge,
        val player: SimulatedPlayer,
    ) {
        val session get() = assertIs<WatchTogetherState.InRoom>(manager.state.value).session
        val directives = mutableListOf<PlaybackDirective>()
        val alignmentResults = mutableListOf<WatchTogetherEffect.HostAlignmentFinished>()
    }

    /** Real mediamp state machine with frame-cadenced native facts and delayed native seek acknowledgements. */
    @OptIn(InternalForInheritanceMediampApi::class)
    private class SimulatedPlayer(
        private val scope: TestScope,
        val backend: TestMediampPlayer,
    ) : MediampPlayer by backend {
        data class Seek(val startedAt: Long, var completedAt: Long = -1)
        val seeks = mutableListOf<Seek>()
        var seekLatencyMillis = 0L
        var correctivePauses = 0
        private var position = 0L
        private var at = now()
        private var playing = false
        private var seeking = false

        init {
            backend.holdSeeks = true
            scope.backgroundScope.launch {
                while (isActive) {
                    advancePosition()
                    val untilNextFrame = if (playing && !seeking) FRAME_MILLIS - position % FRAME_MILLIS else FRAME_MILLIS
                    delay(untilNextFrame)
                    advancePosition()
                    if (!seeking) backend.injectPosition(position / FRAME_MILLIS * FRAME_MILLIS)
                }
            }
        }

        private fun now() = scope.testScheduler.currentTime
        private fun advancePosition() {
            val time = now()
            if (playing && !seeking) position += time - at
            at = time
        }

        override suspend fun setMediaData(data: MediaData, playWhenReady: Boolean, startPositionMillis: Long) {
            backend.setMediaData(data, playWhenReady, startPositionMillis)
            position = startPositionMillis
            at = now()
            playing = playWhenReady
        }

        fun positionNow(): Long {
            advancePosition()
            return position
        }

        fun jumpTo(value: Long) {
            position = value
            at = now()
            backend.injectPosition(position / FRAME_MILLIS * FRAME_MILLIS)
        }

        override fun play() {
            advancePosition()
            playing = true
            backend.play()
        }

        override fun pause() {
            advancePosition()
            if (playing && !seeking) correctivePauses++
            playing = false
            backend.pause()
        }

        override fun seekTo(positionMillis: Long) {
            advancePosition()
            seeking = true
            val seek = Seek(now())
            seeks += seek
            backend.seekTo(positionMillis)
            if (seekLatencyMillis > 0) backend.injectStall(true)
            scope.backgroundScope.launch {
                delay(seekLatencyMillis)
                position = positionMillis
                at = now()
                seeking = false
                seek.completedAt = now()
                backend.completeHeldSeek(stalledAtCompletion = false)
            }
        }
    }

    /** Server timestamps are captured after the uplink, before the delayed response. */
    private class SimulatedServer(
        private val scope: TestScope,
        private val uplinkMillis: Long = 40,
        private val downlinkMillis: Long = 60,
        private val jitterMillis: List<Long> = listOf(0L, 7L, 3L, 15L),
    ) {
        private val snapshot = MutableStateFlow(
            AniWatchTogetherRoomSnapshot(
                roomId = "room", roomName = "Room", version = 0, status = AniWatchTogetherRoomStatus.OPEN,
                hostUserId = "host", serverTime = now(), members = emptyList(),
            ),
        )
        private val reports = mutableMapOf<String, MutableList<AniReportWatchTogetherStateRequest>>()
        private var networkSequence = 0
        fun now() = 100_000 + scope.testScheduler.currentTime
        fun reportCount(id: String) = reports[id]?.size ?: 0

        // Deterministic queueing jitter preserves the 40/60 ms one-way asymmetry.
        private fun jitter() = jitterMillis[networkSequence++ % jitterMillis.size]

        fun client(id: String): WatchTogetherApiService = object : WatchTogetherApiService {
            override suspend fun join(roomName: String, password: String, following: Boolean): AniWatchTogetherJoinResponse {
                val jitter = jitter()
                delay(uplinkMillis + jitter)
                val response = AniWatchTogetherJoinResponse(
                    roomId = "room", created = id == "host", isHost = id == "host", sessionNonce = id,
                    serverTime = now(), snapshot = snapshot.value,
                )
                delay(downlinkMillis + jitter)
                return response
            }

            override suspend fun report(roomId: String, request: AniReportWatchTogetherStateRequest): AniWatchTogetherReportResponse {
                val jitter = jitter()
                reports.getOrPut(id) { mutableListOf() } += request
                delay(uplinkMillis + jitter)
                if (id == "host") {
                    snapshot.value = snapshot.value.copy(
                        version = snapshot.value.version + 1,
                        serverTime = now(),
                        playback = request.watching?.let { AniWatchTogetherPlayback(it, now()) },
                    )
                }
                val response = AniWatchTogetherReportResponse(
                    serverTime = now(), membership = AniWatchTogetherMembership.OK, version = snapshot.value.version,
                )
                delay(downlinkMillis + jitter)
                return response
            }

            override suspend fun leave(roomId: String, sessionNonce: String) {}

            override fun events(roomId: String, sessionNonce: String): Flow<WatchTogetherServerEvent> = channelFlow {
                delay(downlinkMillis + jitter())
                send(WatchTogetherServerEvent.Connected)
                launch {
                    while (isActive) {
                        delay(15_000)
                        send(WatchTogetherServerEvent.Ping)
                    }
                }
                snapshot.collect {
                    delay(downlinkMillis + jitter())
                    send(WatchTogetherServerEvent.Snapshot(it))
                }
            }
        }
    }

    private class MutableSettings : Settings<WatchTogetherSettings> {
        override val flow = MutableStateFlow(WatchTogetherSettings(enabled = true, followHost = true))
        override suspend fun set(value: WatchTogetherSettings) { flow.value = value }
    }

    private object LoggedIn : SessionStateProvider {
        override val stateFlow = MutableStateFlow<SessionState>(SessionState.Valid(bangumiConnected = false))
        override val eventFlow: Flow<SessionEvent> = emptyFlow()
    }

    private companion object {
        const val FRAME_MILLIS = 42L
    }
}
