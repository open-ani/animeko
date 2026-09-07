/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.subject.episode.video.loading.shouldShowVideoLoadingIndicator
import me.him188.ani.app.ui.watchtogether.WatchTogetherMemberPresence
import me.him188.ani.app.ui.watchtogether.toWatchTogetherMemberPresentation
import me.him188.ani.app.ui.watchtogether.toWatchTogetherPlaybackPresentation
import me.him188.ani.client.models.AniWatchTogetherMember
import me.him188.ani.client.models.AniWatchTogetherMemberState
import me.him188.ani.client.models.AniWatchTogetherWatchingInfo
import me.him188.ani.danmaku.ui.DanmakuConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvPlaybackSemanticsTest {
    @Test
    fun nextEpisodeRequiresAnExistingBroadcastNeighbor() {
        val first = TvStripEpisode(1, "1", "第一集", false, isKnownBroadcast = true)
        val second = TvStripEpisode(2, "2", "第二集", false)
        val state = TvEpisodeUiState(currentEpisodeId = 1, episodes = listOf(first, second))
        assertFalse(state.hasNextEpisode)
        assertTrue(state.copy(episodes = listOf(first, second.copy(isKnownBroadcast = true))).hasNextEpisode)
        assertFalse(state.copy(currentEpisodeId = 2).hasNextEpisode)
        assertFalse(state.copy(currentEpisodeId = 99).hasNextEpisode)
        assertFalse(state.copy(episodes = emptyList()).hasNextEpisode)
    }

    @Test
    fun loadingIndicatorRemainsAvailableAfterResolutionForBufferingAndPlayerErrors() {
        val loaded = VideoLoadingState.Succeed(false)
        assertFalse(shouldShowVideoLoadingIndicator(loaded, buffering = false, playerError = false))
        assertTrue(shouldShowVideoLoadingIndicator(loaded, buffering = true, playerError = false))
        assertTrue(shouldShowVideoLoadingIndicator(loaded, buffering = false, playerError = true))
        assertTrue(shouldShowVideoLoadingIndicator(VideoLoadingState.Initial, false, false))
        assertTrue(shouldShowVideoLoadingIndicator(VideoLoadingState.NetworkError, false, false))
    }

    @Test
    fun reducingDanmakuDensityPreservesSparseSettingsFromOtherDevices() {
        val original = DanmakuConfig.Default.copy(safeSeparation = 200.dp)
        val adjusted = original.adjustForTv(TvDanmakuProperty.Density, -1)
        assertTrue(adjusted.safeSeparation > original.safeSeparation)
        assertEquals(220.5f, adjusted.safeSeparation.value, .001f)
        assertTrue(adjusted.adjustForTv(TvDanmakuProperty.Density, 1).safeSeparation < adjusted.safeSeparation)
    }

    @Test
    fun danmakuControlsReachTheSharedExtremesIncludingOff() {
        var config = DanmakuConfig.Default
        repeat(80) {
            config = config.adjustForTv(TvDanmakuProperty.FontSize, 1)
                .adjustForTv(TvDanmakuProperty.Speed, 1)
                .adjustForTv(TvDanmakuProperty.Opacity, -1)
                .adjustForTv(TvDanmakuProperty.Area, -1)
        }
        assertEquals(54f, config.style.fontSize.value, .001f)
        assertEquals(264f, config.speed, .001f)
        assertEquals(0f, config.style.alpha)
        assertEquals(0f, config.displayArea)
        repeat(80) {
            config = config.adjustForTv(TvDanmakuProperty.FontSize, -1)
                .adjustForTv(TvDanmakuProperty.Speed, -1)
        }
        assertEquals(9f, config.style.fontSize.value, .001f)
        assertEquals(17.6f, config.speed, .001f)
    }

    @Test
    fun simpleSourcesKeepPendingAndFailedQueriesVisible() {
        fun group(state: MediaSourceFetchState) = TvSourceGroup("id", "source", "源", null, state, emptyList())
        assertTrue(group(MediaSourceFetchState.Idle).showInSimpleMode)
        assertTrue(group(MediaSourceFetchState.Working).showInSimpleMode)
        assertTrue(group(MediaSourceFetchState.Failed(IllegalStateException(), 0)).showInSimpleMode)
        assertTrue(group(MediaSourceFetchState.RateLimited(60_000, 0)).showInSimpleMode)
        assertFalse(group(MediaSourceFetchState.Disabled).showInSimpleMode)
        assertFalse(group(MediaSourceFetchState.Succeed(0)).showInSimpleMode)
    }

    @Test
    fun disconnectedMembersKeepPresenceEvenWithStaleWatchingData() {
        val member = AniWatchTogetherMember(
            userId = "self", nickname = "观众", isHost = false, following = true,
            state = AniWatchTogetherMemberState.DISCONNECTED, lastSeenAt = 10_000, watching = watching(),
        ).toWatchTogetherMemberPresentation(nowMillis = 190_000, selfUserId = "self")
        assertEquals(WatchTogetherMemberPresence.DISCONNECTED, member.state)
        assertEquals(3L, member.disconnectedMinutes)
        assertTrue(member.isSelf)
    }

    @Test
    fun bufferingAndLoadingAreRetainedAndDoNotAdvanceTheClock() {
        val playing = watching().toWatchTogetherPlaybackPresentation(12_000)
        assertEquals(22_000L, playing.positionMillis)
        val buffering = watching().copy(buffering = true).toWatchTogetherPlaybackPresentation(12_000)
        assertTrue(buffering.buffering)
        assertEquals(20_000L, buffering.positionMillis)
        val loading = watching().copy(loading = true).toWatchTogetherPlaybackPresentation(12_000)
        assertTrue(loading.loading)
        assertEquals(20_000L, loading.positionMillis)
    }

    private fun watching() = AniWatchTogetherWatchingInfo(
        subjectId = 1, episodeId = 2, subjectName = "番剧", episodeSort = "2", episodeName = "第二集",
        positionMillis = 20_000, positionAtMillis = 10_000, durationMillis = 60_000, paused = false,
    )
}
