/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.components

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.tools.Progress
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_episode_watched_progress
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(TestOnly::class)
class CacheEpisodeRowTest {
    @Test
    fun `playback history is converted to clamped progress`() {
        val history = EpisodeHistory(
            episodeId = 1,
            positionMillis = 30_000,
            durationMillis = 60_000,
        )

        assertEquals(0.5f, history.toPlaybackProgress().getOrNull())
        assertEquals(1f, history.copy(positionMillis = 90_000).toPlaybackProgress().getOrNull())
    }

    @Test
    fun `invalid playback history has unspecified progress`() {
        val history = EpisodeHistory(
            episodeId = 1,
            positionMillis = 30_000,
            durationMillis = 60_000,
        )

        assertEquals(Progress.Unspecified, null.toPlaybackProgress())
        assertEquals(Progress.Unspecified, history.copy(positionMillis = 0).toPlaybackProgress())
        assertEquals(Progress.Unspecified, history.copy(durationMillis = null).toPlaybackProgress())
        assertEquals(Progress.Unspecified, history.copy(durationMillis = 0).toPlaybackProgress())
        assertEquals(Progress.Unspecified, history.copy(deletedAtMillis = 1).toPlaybackProgress())
    }

    @Test
    fun `completed cache shows playback progress`() = runAniComposeUiTest {
        val episode = createTestCacheEpisode(
            sort = 1,
            initialState = CacheEpisodePaused.COMPLETED,
            progress = 1f.toProgress(),
            downloadSpeed = FileSize.Unspecified,
            playbackProgress = 0.5f.toProgress(),
        )
        val watchedText = runBlocking {
            getString(Lang.cache_episode_watched_progress, "50.0%")
        }

        setContent {
            ProvideCompositionLocalsForPreview {
                CacheEpisodeRow(
                    episode = episode,
                    mediaSourceInfoProvider = null,
                    selectionMode = false,
                    selected = false,
                    onToggleSelected = {},
                    onEnterSelection = {},
                    onPlay = {},
                    onResume = {},
                    onPause = {},
                    onDelete = {},
                    onViewDetail = null,
                )
            }
        }

        onNodeWithText(watchedText).assertExists()
        onNodeWithTag(CacheEpisodeRowTestTags.playbackProgress(episode.cacheId))
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.5f, 0f..1f))
    }
}
