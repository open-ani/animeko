/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.player.extension.EpisodePlayerExtensionFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class EpisodeFetchPlayStateTest {
    private val subjectId = 1
    private val initialEpisodeId = 2

    private fun EpisodePlayerTestSuite.createState(
        extensions: List<EpisodePlayerExtensionFactory<*>> = listOf(),
    ): EpisodeFetchPlayState {
        return EpisodeFetchPlayState(
            subjectId,
            initialEpisodeId,
            player,
            backgroundScope,
            extensions = extensions,
            koin
        )
    }

    @Test
    fun `can create state`() = runTest {
        val suite = EpisodePlayerTestSuite(backgroundScope)
        val state = suite.createState()
        assertEquals(subjectId, state.subjectId)
    }
}

