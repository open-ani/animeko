package me.him188.ani.app.domain.watchtogether

import me.him188.ani.client.models.AniWatchTogetherWatchingInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class HostAlignmentTest {
    private val host = AniWatchTogetherWatchingInfo(
        subjectId = 1,
        episodeId = 2,
        subjectName = "Subject",
        episodeSort = "1",
        episodeName = "Episode",
        positionMillis = 10_000L,
        positionAtMillis = 1_000L,
        durationMillis = 100_000L,
        paused = false,
        buffering = false,
        loading = false,
        playbackRate = 1f,
    )

    @Test
    fun `paused host seeks exactly without latency compensation`() {
        val paused = host.copy(paused = true)
        assertEquals(10_000L, HostAlignment.seekPosition(paused, 2_000, 500))
        assertEquals(HostAlignment.Action.Complete(0), HostAlignment.afterSeek(paused, 2_000, 10_000, 1))
    }

    @Test
    fun `playing host compensates seek latency and margin`() {
        assertEquals(11_650L, HostAlignment.seekPosition(host, 2_000, 500))
        assertEquals(12_350L, HostAlignment.seekPosition(host, 2_000, 1_200))
        assertEquals(100_000L, HostAlignment.seekPosition(host, 100_000, 1_200))
    }

    @Test
    fun `ahead waits and tolerance completes`() {
        assertEquals(HostAlignment.Action.Wait(150), HostAlignment.afterSeek(host, 2_000, 11_150, 1))
        assertIs<HostAlignment.Action.Complete>(HostAlignment.afterSeek(host, 2_000, 11_020, 1))
        assertIs<HostAlignment.Action.Complete>(HostAlignment.afterSeek(host, 2_000, 10_980, 1))
    }

    @Test
    fun `slow seek retries only once using measured latency`() {
        // A 1200 ms seek took longer than the initial 500 ms estimate plus margin.
        assertEquals(HostAlignment.Action.Retry, HostAlignment.afterSeek(host, 2_200, 10_650, 1))
        assertEquals(12_550L, HostAlignment.seekPosition(host, 2_200, 1_200))
        assertEquals(HostAlignment.Action.GiveUp(-550), HostAlignment.afterSeek(host, 2_200, 10_650, 2))
    }

    @Test
    fun `wait accounts for host playback rate without changing speed`() {
        assertEquals(HostAlignment.Action.Wait(100), HostAlignment.afterSeek(host.copy(playbackRate = 2f), 1_000, 10_200, 1))
    }

    @Test
    fun `final success accepts fifty milliseconds while decisions retain twenty millisecond tolerance`() {
        for (residual in listOf(-50L, -30L, 0L, 30L, 50L)) assertTrue(HostAlignment.isAcceptableResidual(residual))
        assertFalse(HostAlignment.isAcceptableResidual(-51))
        assertFalse(HostAlignment.isAcceptableResidual(51))
        assertIs<HostAlignment.Action.Wait>(HostAlignment.afterSeek(host, 1_000, 10_030, 1))
        assertEquals(HostAlignment.Action.Retry, HostAlignment.afterSeek(host, 1_000, 9_970, 1))
    }

    @Test
    fun `availability explains host loading buffering episode and cooldown`() {
        assertNull(HostAlignment.unavailableReason(host, host))
        assertEquals(HostAlignmentUnavailableReason.NO_HOST_CONTENT, HostAlignment.unavailableReason(null, host))
        assertEquals(HostAlignmentUnavailableReason.HOST_NOT_READY, HostAlignment.unavailableReason(host.copy(buffering = true), host))
        assertEquals(HostAlignmentUnavailableReason.HOST_NOT_READY, HostAlignment.unavailableReason(host.copy(loading = true), host))
        assertEquals(HostAlignmentUnavailableReason.DIFFERENT_EPISODE, HostAlignment.unavailableReason(host, host.copy(episodeId = 3)))
        assertEquals(HostAlignmentUnavailableReason.DIFFERENT_EPISODE, HostAlignment.unavailableReason(host, null))
        assertEquals(HostAlignmentUnavailableReason.ALIGNING, HostAlignment.unavailableReason(host, host, aligning = true))
        assertEquals(HostAlignmentUnavailableReason.COOLDOWN, HostAlignment.unavailableReason(host, host, cooldownRemaining = 1))
        assertNull(HostAlignment.unavailableReason(host, host, cooldownRemaining = 0))
    }
}
