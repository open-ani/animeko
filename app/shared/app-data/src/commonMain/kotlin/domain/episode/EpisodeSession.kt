/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.utils.coroutines.childScope
import org.koin.core.Koin
import kotlin.coroutines.CoroutineContext

/**
 * A session of [MediaFetchSession] and [MediaSelector] for a subject episode, that is 'bound' to the lifecycle of the episodeId.
 *
 * When the episodeId changes, you should [close] this session and create a new one.
 *
 * @see EpisodeFetchSelectPlayState
 */
class EpisodeSession(
    subjectId: Int,
    val episodeId: Int,
    koin: Koin,
    parentCoroutineContext: CoroutineContext,
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(),
) : AutoCloseable {
    private val createMediaFetchSelectBundleFlowUseCase: CreateMediaFetchSelectBundleFlowUseCase by koin.inject()

    val sessionScope =
        parentCoroutineContext.childScope(CoroutineName("SubjectEpisodeFetchSelectSession")) // supervisor scope

    internal val sessionScopeTasksStarted = MutableStateFlow(false)

    /**
     * Loads subec
     */
    private val infoLoader = SubjectEpisodeInfoBundleLoader(
        subjectId,
        flowOf(episodeId), // single element, so infoBundleFlow may complete.
        koin,
    )

    /**
     * @see SubjectEpisodeInfoBundleLoader.infoBundleFlow
     */
    val infoBundleFlow: SharedFlow<SubjectEpisodeInfoBundle?> = infoLoader.infoBundleFlow
        .shareIn(sessionScope, started = sharingStarted, replay = 1)

    /**
     * @see SubjectEpisodeInfoBundleLoader.infoLoadErrorState
     */
    val infoLoadErrorStateFlow: StateFlow<LoadError?> get() = infoLoader.infoLoadErrorState


    /**
     * A flow of the bundle of [MediaFetchSession] and [MediaSelector].
     *
     * Flow re-emits (almost immediately) when [infoBundleFlow] emits.
     *
     * This flow does not produce errors.
     */
    val fetchSelectFlow = createMediaFetchSelectBundleFlowUseCase(infoBundleFlow)
        .shareIn(sessionScope, sharingStarted, replay = 1)
    // TODO: 2025/1/4 test fetchSelectFlow changes only when infoBundleFlow's value equality changes 

    val mediaSelectorFlow = fetchSelectFlow.map { it?.mediaSelector }
    val mediaFetchSessionFlow = fetchSelectFlow.map { it?.mediaFetchSession }

    /**
     * A cold flow that emits `true` when media sources are loading.
     *
     * - When all media sources have completed, this flow emits `false`.
     * - If there is an error when loading episode data, this flow emits `false`.
     */
    val mediaSourceLoadingFlow = fetchSelectFlow
        .transformLatest { bundle ->
            if (bundle == null) {
                emit(false)
                return@transformLatest
            }
            emitAll(
                bundle.mediaFetchSession.hasCompleted.map {
                    !it.allCompleted()
                },
            )
        }


    override fun close() {
        sessionScope.cancel()
    }
}
