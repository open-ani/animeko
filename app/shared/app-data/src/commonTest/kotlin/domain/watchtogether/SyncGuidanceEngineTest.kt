package me.him188.ani.app.domain.watchtogether

import me.him188.ani.client.models.AniWatchTogetherPlayback
import me.him188.ani.client.models.AniWatchTogetherWatchingInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncGuidanceEngineTest {
    private val info = AniWatchTogetherWatchingInfo(
        subjectId = 1,
        episodeId = 2,
        subjectName = "Subject",
        episodeSort = "02",
        episodeName = "Episode",
        positionMillis = 10_000L,
        positionAtMillis = 1_000L,
        durationMillis = 100_000L,
        paused = false,
        playbackRate = 2.0f,
    )
    private val playback = AniWatchTogetherPlayback(info, 1_000L)

    @Test
    fun `computes navigation table`() {
        assertEquals(
            SyncAction.PushEpisode(1, 2, 14_000L),
            SyncGuidanceEngine.compute(playback, LocalPosition.NotInPlayer, 3_000L),
        )
        assertEquals(
            SyncAction.SeekOnly(14_000L, false),
            SyncGuidanceEngine.compute(playback, LocalPosition.InPlayer(1, 2), 3_000L),
        )
        assertEquals(
            SyncAction.SwitchEpisodeInPlace(2, 14_000L),
            SyncGuidanceEngine.compute(playback, LocalPosition.InPlayer(1, 3), 3_000L),
        )
        assertEquals(
            SyncAction.PopThenPushEpisode(1, 2, 14_000L),
            SyncGuidanceEngine.compute(playback, LocalPosition.InPlayer(9, 3), 3_000L),
        )
        assertEquals(null, SyncGuidanceEngine.compute(null, LocalPosition.NotInPlayer, 3_000L))
    }

    @Test
    fun `paused and future fixes do not advance`() {
        assertEquals(10_000L, info.copy(paused = true).positionAt(3_000L))
        assertEquals(10_000L, info.positionAt(500L))
    }
}
