package me.him188.ani.app.domain.watchtogether

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.client.models.AniWatchTogetherRoomSnapshot
import me.him188.ani.client.models.AniWatchTogetherRoomStatus
import me.him188.ani.client.models.AniWatchTogetherWatchingInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocalPlaybackBridgeTest {
    private val watching = AniWatchTogetherWatchingInfo(
        subjectId = 1, episodeId = 2, subjectName = "Subject", episodeSort = "1", episodeName = "Episode",
        positionMillis = 10_000, positionAtMillis = 1_000, durationMillis = 100_000,
        paused = false, buffering = false, loading = false, playbackRate = 1f,
    )

    @Test
    fun `alignment suppresses discontinuities and cooldown rejects repeated requests`() = runTest {
        val bridge = LocalPlaybackBridge()
        val session = session()
        val owner = Any()
        val discontinuities = mutableListOf<Unit>()
        backgroundScope.launch { bridge.discontinuities.collect { discontinuities += it } }
        bridge.updateLocalWatching(owner, watching)
        runCurrent()
        discontinuities.clear()
        assertTrue(bridge.requestAlignment(session, 1_000))
        val request = assertNotNull(bridge.alignmentRequest.value)
        assertFalse(bridge.requestAlignment(session, 10_000))
        bridge.updateLocalWatching(owner, watching.copy(positionMillis = 40_000, paused = true))
        bridge.notifySeekCompleted(owner, 40_000, 1_000, isHost = false)
        bridge.updateLocalWatching(owner, watching.copy(positionMillis = 40_000))
        runCurrent()
        assertTrue(discontinuities.isEmpty())
        assertEquals(40_000L, bridge.localWatching.value?.positionMillis)
        bridge.finishAlignment(request)
        assertFalse(bridge.requestAlignment(session, 3_999))
        assertTrue(bridge.requestAlignment(session, 4_000))
        bridge.finishAlignment(request) // stale executor cannot release the newer request
        assertTrue(bridge.isAligning)
    }

    @Test
    fun `large jump without seek completion reports immediately`() = runTest {
        val bridge = LocalPlaybackBridge()
        val owner = Any()
        val reports = mutableListOf<Unit>()
        backgroundScope.launch { bridge.discontinuities.collect { reports += it } }
        bridge.updateLocalWatching(owner, watching)
        runCurrent()
        reports.clear()
        bridge.updateLocalWatching(owner, watching.copy(positionMillis = 40_000))
        runCurrent()
        assertEquals(1, reports.size)
    }

    @Test
    fun `large seek reports once regardless of reporter and completion ordering`() = runTest {
        for (reporterFirst in listOf(true, false)) {
            val bridge = LocalPlaybackBridge()
            val owner = Any()
            val reports = mutableListOf<Unit>()
            backgroundScope.launch { bridge.discontinuities.collect { reports += it } }
            bridge.updateLocalWatching(owner, watching)
            runCurrent()
            reports.clear()
            if (reporterFirst) bridge.updateLocalWatching(owner, watching.copy(positionMillis = 40_000))
            else bridge.notifySeekCompleted(owner, 40_000, 1_000, isHost = true)
            runCurrent()
            if (reporterFirst) bridge.notifySeekCompleted(owner, 40_000, 1_000, isHost = true)
            else bridge.updateLocalWatching(owner, watching.copy(positionMillis = 40_000))
            runCurrent()
            assertEquals(1, reports.size)
            assertEquals(40_000L, bridge.localWatching.value?.positionMillis)
        }
    }

    @Test
    fun `small seek reports once regardless of reporter and completion ordering`() = runTest {
        for (reporterFirst in listOf(true, false)) {
            val bridge = LocalPlaybackBridge()
            val owner = Any()
            val reports = mutableListOf<Unit>()
            backgroundScope.launch { bridge.discontinuities.collect { reports += it } }
            bridge.updateLocalWatching(owner, watching)
            runCurrent()
            reports.clear()
            val landed = watching.copy(positionMillis = 11_100, positionAtMillis = 1_100)
            if (reporterFirst) bridge.updateLocalWatching(owner, landed)
            else bridge.notifySeekCompleted(owner, 11_100, 1_100, isHost = true)
            runCurrent()
            if (reporterFirst) bridge.notifySeekCompleted(owner, 11_100, 1_100, isHost = true)
            else bridge.updateLocalWatching(owner, landed)
            runCurrent()
            assertEquals(1, reports.size)
            assertEquals(11_100L, bridge.localWatching.value?.positionMillis)
            bridge.notifySeekCompleted(Any(), 20_000, 1_200, isHost = true)
            runCurrent()
            assertEquals(1, reports.size)
        }
    }

    @Test
    fun `normal progress and completion within fifty milliseconds do not report`() = runTest {
        val bridge = LocalPlaybackBridge()
        val owner = Any()
        val reports = mutableListOf<Unit>()
        backgroundScope.launch { bridge.discontinuities.collect { reports += it } }
        bridge.updateLocalWatching(owner, watching)
        runCurrent()
        reports.clear()
        bridge.updateLocalWatching(owner, watching.copy(positionMillis = 11_000, positionAtMillis = 2_000))
        runCurrent()
        assertTrue(reports.isEmpty())
        bridge.notifySeekCompleted(owner, 11_150, 2_100, isHost = true)
        runCurrent()
        assertTrue(reports.isEmpty())
        assertEquals(2_100L, bridge.localWatching.value?.positionAtMillis)
    }

    private fun session() = RoomSession(
        roomId = "room", roomName = "Room", isHost = false, sessionNonce = "nonce",
        initialSnapshot = AniWatchTogetherRoomSnapshot(
            roomId = "room", roomName = "Room", version = 1, status = AniWatchTogetherRoomStatus.OPEN,
            hostUserId = "host", serverTime = 1_000, members = emptyList(),
        ),
        initialFollowing = true,
        serverClock = ServerClock { 1_000 },
    )
}
