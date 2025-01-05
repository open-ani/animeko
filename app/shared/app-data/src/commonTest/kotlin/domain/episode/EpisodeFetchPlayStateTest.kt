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
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.player.extension.AbstractPlayerExtensionTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EpisodeFetchPlayStateTest : AbstractPlayerExtensionTest() {
//    fun TestScope.createState(
//        extensions: List<EpisodePlayerExtensionFactory<*>> = listOf(
//            RememberPlayProgressExtension,
//            MarkAsWatchedExtension,
//            SwitchNextEpisodeExtension.Factory(
//                getNextEpisode = { currentEpisodeId ->
//                    listOf(1)
//                },
//            ),
//            SwitchMediaOnPlayerErrorExtension,
//            AutoSelectExtension,
//            SaveMediaPreferenceExtension,
//        ),
//    ): EpisodeFetchPlayState {
//        val testScope = this.childScope()
//        val suite = createSuite(testScope)
//
//        // Register mocked or stubbed components.
//        suite.registerComponent<GetVideoScaffoldConfigUseCase> {
//            GetVideoScaffoldConfigUseCase {
//                flowOf(
//                    VideoScaffoldConfig.AllDisabled.copy(
//                        autoMarkDone = true,
//                        hideSelectorOnSelect = true,
//                        autoPlayNext = true,
//                        autoSkipOpEd = true,
//                    ),
//                )
//            }
//        }
//        suite.registerComponent<GetEpisodeCollectionTypeUseCase> {
//            getEpisodeCollectionType
//        }
//        suite.registerComponent<SetEpisodeCollectionTypeUseCase> {
//            setEpisodeCollectionType
//        }
//
//        val state = suite.createState(
//            extensions = listOf(extensionFactory),
//        )
//        state.startBackgroundTasks()
//        return Triple(testScope, suite, state)
//    }

    @Test
    fun `can create state`() = runTest {
        val suite = createSuite()
        val state = suite.createState()
        assertEquals(subjectId, state.subjectId)
    }

    private fun TestScope.createSuite() = EpisodePlayerTestSuite(this, backgroundScope)
}

