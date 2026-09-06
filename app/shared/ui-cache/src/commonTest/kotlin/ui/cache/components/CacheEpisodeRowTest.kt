/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.components

import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.tools.Progress
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
