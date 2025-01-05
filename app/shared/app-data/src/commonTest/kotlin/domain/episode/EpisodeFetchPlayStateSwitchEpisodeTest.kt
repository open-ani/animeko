/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.test.TestScope
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.player.EpisodeHistories
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepositoryImpl
import me.him188.ani.app.domain.player.extension.AbstractPlayerExtensionTest
import me.him188.ani.app.domain.player.extension.RememberPlayProgressExtension
import me.him188.ani.utils.coroutines.childScope

class EpisodeFetchPlayStateSwitchEpisodeTest : AbstractPlayerExtensionTest() {
    private val repository = EpisodePlayHistoryRepositoryImpl(MemoryDataStore(EpisodeHistories.Empty))
    private fun TestScope.createCase() = run {
        val testScope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, testScope)
        suite.registerComponent<EpisodePlayHistoryRepository> { repository }

        val state = suite.createState(
            listOf(
                RememberPlayProgressExtension,
            ),
        )
        state.startBackgroundTasks()
        Triple(testScope, suite, state)
    }
//    
//    @Test
//    fun `switchEpisode then load`() = runTest {
//        val (testScope) = createCase()
//
////        assertEquals(emptyList(), repository.flow.first())
//        testScope.cancel()
//    }
}