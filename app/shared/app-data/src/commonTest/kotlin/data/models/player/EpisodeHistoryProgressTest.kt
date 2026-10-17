/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.player

import kotlin.test.Test
import kotlin.test.assertEquals

class EpisodeHistoryProgressTest {
    @Test
    fun `progress is position over duration clamped to one`() {
        assertEquals(0.25f, EpisodeHistory(episodeId = 1, positionMillis = 250, durationMillis = 1000).playProgress)
        assertEquals(1f, EpisodeHistory(episodeId = 1, positionMillis = 1500, durationMillis = 1000).playProgress)
    }

    @Test
    fun `progress is null without a usable duration`() {
        assertEquals(null, EpisodeHistory(episodeId = 1, positionMillis = 250).playProgress)
        assertEquals(null, EpisodeHistory(episodeId = 1, positionMillis = 250, durationMillis = 0).playProgress)
    }

    @Test
    fun `map keeps only records with positive computable progress`() {
        val map = listOf(
            EpisodeHistory(episodeId = 1, positionMillis = 250, durationMillis = 1000),
            EpisodeHistory(episodeId = 2, positionMillis = 0, durationMillis = 1000),
            EpisodeHistory(episodeId = 3, positionMillis = 500),
        ).playProgressByEpisodeId()
        assertEquals(mapOf(1 to 0.25f), map)
    }
}
