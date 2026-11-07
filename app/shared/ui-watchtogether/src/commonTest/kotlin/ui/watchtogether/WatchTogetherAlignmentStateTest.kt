package me.him188.ani.app.ui.watchtogether

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.watchtogether.HostAlignmentUnavailableReason
import me.him188.ani.client.models.AniWatchTogetherWatchingInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchTogetherAlignmentStateTest {
    private val watching = AniWatchTogetherWatchingInfo(
        subjectId = 1, episodeId = 2, subjectName = "Subject", episodeSort = "1", episodeName = "Episode",
        positionMillis = 10_000, positionAtMillis = 1_000, durationMillis = 100_000,
        paused = true, buffering = false, loading = false, playbackRate = 1f,
    )

    @Test
    fun `paused unchanged playback becomes available exactly when cooldown expires`() = runTest {
        val host = MutableStateFlow<AniWatchTogetherWatchingInfo?>(watching)
        val local = MutableStateFlow<AniWatchTogetherWatchingInfo?>(watching)
        val until = MutableStateFlow(3_000L)
        val reasons = mutableListOf<HostAlignmentUnavailableReason?>()
        backgroundScope.launch {
            watchTogetherAlignmentAvailabilityFlow(host, local, MutableStateFlow(false), until) {
                testScheduler.currentTime
            }.collect { reasons += it }
        }
        runCurrent()
        assertEquals(HostAlignmentUnavailableReason.COOLDOWN, reasons.last())
        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(HostAlignmentUnavailableReason.COOLDOWN, reasons.last())
        advanceTimeBy(1)
        runCurrent()
        assertNull(reasons.last())
        // Updating the deadline cancels the pending expiry of the previous deadline.
        until.value = 6_000
        runCurrent()
        advanceTimeBy(1_000)
        until.value = 8_000
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(HostAlignmentUnavailableReason.COOLDOWN, reasons.last())
        advanceTimeBy(2_000)
        runCurrent()
        assertNull(reasons.last())
    }

    @Test
    fun `host snapshot changes recompute availability while local playback stays paused`() = runTest {
        val host = MutableStateFlow<AniWatchTogetherWatchingInfo?>(watching)
        val reasons = mutableListOf<HostAlignmentUnavailableReason?>()
        backgroundScope.launch {
            watchTogetherAlignmentAvailabilityFlow(host, MutableStateFlow(watching), MutableStateFlow(false), MutableStateFlow(0L)) {
                testScheduler.currentTime
            }.collect { reasons += it }
        }
        runCurrent()
        assertNull(reasons.last())
        host.value = watching.copy(buffering = true)
        runCurrent()
        assertEquals(HostAlignmentUnavailableReason.HOST_NOT_READY, reasons.last())
        host.value = watching.copy(loading = true)
        runCurrent()
        assertEquals(HostAlignmentUnavailableReason.HOST_NOT_READY, reasons.last())
        host.value = watching.copy(episodeId = 3)
        runCurrent()
        assertEquals(HostAlignmentUnavailableReason.DIFFERENT_EPISODE, reasons.last())
        host.value = null
        runCurrent()
        assertEquals(HostAlignmentUnavailableReason.NO_HOST_CONTENT, reasons.last())
        host.value = watching
        runCurrent()
        assertNull(reasons.last())
    }
}
