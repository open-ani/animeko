/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.subject.TestSubjectCollections
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.media.selector.MediaSelectorTestBuilder
import org.koin.core.Koin
import org.koin.dsl.module
import org.openani.mediamp.DummyMediampPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class EpisodeFetchPlayStateTest {
    private val subjectId = 1
    private val initialEpisodeId = 2

    fun EpisodePlayerTestSuite.createState(): EpisodeFetchPlayState {
        return EpisodeFetchPlayState(subjectId, initialEpisodeId, player, backgroundScope, koin)
    }

    @Test
    fun `can create state`() = runTest {
        val suite = EpisodePlayerTestSuite(backgroundScope)
        val state = suite.createState()
        assertEquals(subjectId, state.subjectId)
    }

    @Test
    fun `infoLoadErrorState initially null`() = runTest {
        val suite = EpisodePlayerTestSuite(backgroundScope)
        val state = suite.createState()
        assertEquals(null, state.infoLoadErrorState.value)
    }

    @Test
    fun `infoBundleFlow emits null first`() = runTest {
        val suite = EpisodePlayerTestSuite(backgroundScope)
        val state = suite.createState()
        assertEquals(null, state.infoBundleFlow.first()) // refreshes UI
    }

    @Test
    fun `infoBundleFlow load success`() = runTest {
        val suite = EpisodePlayerTestSuite(backgroundScope)
        val state = suite.createState()
        assertNotEquals(null, state.infoBundleFlow.drop(1).first())
        assertEquals(null, state.infoLoadErrorState.value)
    }

    @Test
    fun `infoBundleFlow load failure is captured in the background`() = runTest {
        val backgroundException = CompletableDeferred<Throwable>()
        val scope = CoroutineScope(
            SupervisorJob(backgroundScope.coroutineContext[Job]) + CoroutineExceptionHandler { _, throwable ->
                backgroundException.complete(throwable)
            },
        )
        val suite = EpisodePlayerTestSuite(scope)
        suite.registerComponent<GetSubjectEpisodeInfoBundleFlowUseCase> {
            GetSubjectEpisodeInfoBundleFlowUseCase { idsFlow ->
                idsFlow.map {
                    throw RepositoryNetworkException()
                }
            }
        }
        val state = suite.createState()
        val job = state.infoBundleFlow.drop(1).launchIn(scope) // will hang forever
        assertEquals(LoadError.NetworkError, state.infoLoadErrorState.onEach { println(it) }.filterNotNull().first())
        job.cancel()
        scope.cancel()
        assertIs<RepositoryNetworkException>(backgroundException.await(), "should be a network error")
    }


}

class EpisodePlayerTestSuite(
    val backgroundScope: CoroutineScope,
) {
    val player = DummyMediampPlayer()
    private val mediaSelectorTestBuilder = MediaSelectorTestBuilder()

    val koin = Koin()

    init {
        koin.loadModules(
            listOf(
                module {
                    single<GetSubjectEpisodeInfoBundleFlowUseCase> {
                        GetSubjectEpisodeInfoBundleFlowUseCase { idsFlow ->
                            idsFlow.map {
                                SubjectEpisodeInfoBundle(
                                    it.subjectId,
                                    it.episodeId,
                                    TestSubjectCollections[0].run {
                                        copy(subjectInfo = subjectInfo.copy(subjectId = subjectId))
                                    },
                                    TestSubjectCollections[0].episodes[0].run {
                                        copy(episodeInfo = episodeInfo.copy(episodeId = episodeId))
                                    },
                                )
                            }
                        }
                    }
                    single<CreateMediaFetchSelectBundleFlowUseCase> {
                        CreateMediaFetchSelectBundleFlowUseCase { _ ->
                            val mediaFetchSession =
                                mediaSelectorTestBuilder.createMediaFetchSession(mediaSelectorTestBuilder.createMediaFetcher())
                            flowOf(
                                MediaFetchSelectBundle(
                                    mediaFetchSession,
                                    mediaSelectorTestBuilder.createMediaSelector(mediaFetchSession),
                                ),
                            )
                        }
                    }
                },
            ),
        )
    }

    inline fun <reified T : Any> registerComponent(crossinline value: () -> T) {
        koin.loadModules(
            listOf(
                module {
                    single<T> { value() }
                },
            ),
        )
    }
}

class EpisodePlayerModules {

}
