/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.flow.*
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.TestSubjectCollections
import me.him188.ani.app.domain.foundation.FlowLoadErrorObserver
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.foundation.catchLoadError
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.core.Koin

/**
 * A simple data bundle combining subject and episode collection info.
 *
 * Can be loaded using [SubjectEpisodeInfoBundleLoader].
 *
 * @see createTestSubjectEpisodeInfoBundle
 * @see SubjectEpisodeInfoBundleLoader
 */
data class SubjectEpisodeInfoBundle(
    val subjectId: Int,
    val episodeId: Int,
    val subjectCollectionInfo: SubjectCollectionInfo,
    val episodeCollectionInfo: EpisodeCollectionInfo,
) {
    /**
     * Convenience accessor for the [SubjectInfo] associated with [subjectId].
     */
    val subjectInfo: SubjectInfo get() = subjectCollectionInfo.subjectInfo

    /**
     * Convenience accessor for the [EpisodeInfo] associated with [episodeId].
     */
    val episodeInfo: EpisodeInfo get() = episodeCollectionInfo.episodeInfo
}

@TestOnly
fun createTestSubjectEpisodeInfoBundle(
    subjectId: Int,
    episodeId: Int,
): SubjectEpisodeInfoBundle {
    return SubjectEpisodeInfoBundle(
        subjectId,
        episodeId,
        TestSubjectCollections[0].run {
            copy(subjectInfo = subjectInfo.copy(subjectId = subjectId))
        },
        TestSubjectCollections[0].episodes[0].run {
            copy(episodeInfo = episodeInfo.copy(episodeId = episodeId))
        },
    )
}

/**
 * Loads [SubjectEpisodeInfoBundle] flows for a given [subjectId] and a dynamic [episodeIdFlow].
 *
 * This class uses a use case, [GetSubjectEpisodeInfoBundleFlowUseCase], to generate a flow of
 * [SubjectEpisodeInfoBundle] whenever the [episodeIdFlow] changes. It also exposes an error
 * state [infoLoadErrorState] that emits a [LoadError] if any exception occurs during data loading.
 *
 * @param subjectId The constant subject ID for which we are loading episode data.
 * @param episodeIdFlow A flow that emits the latest episode ID to load.
 *
 * @sample me.him188.ani.app.domain.getBundleFlow
 *
 * @see LoadError
 */
class SubjectEpisodeInfoBundleLoader(
    subjectId: Int,
    episodeIdFlow: Flow<Int>,
    koin: Koin,
) {
    /**
     * Underlying use case for fetching the flow of [SubjectEpisodeInfoBundle].
     */
    private val getSubjectEpisodeInfoBundleFlowUseCase: GetSubjectEpisodeInfoBundleFlowUseCase by koin.inject()

    private val flowLoadErrorObserver = FlowLoadErrorObserver()

    /**
     * A read-only flow of the last loading error, if any. Null when no error has occurred.
     */
    val infoLoadErrorState: StateFlow<LoadError?> = flowLoadErrorObserver.loadErrorState

    /**
     * A flow of [SubjectEpisodeInfoBundle] that updates each time [episodeIdFlow] emits a new value.
     * If an error occurs during loading, [infoLoadErrorState] will be updated with the corresponding
     * [LoadError]. This flow:
     *  - Emits `null` initially to clear any previous values.
     *  - Emits new [SubjectEpisodeInfoBundle] objects whenever the underlying use case provides them.
     *
     * This flow is intended to be shared in a view model or other long-lived scope.
     * Do not collect it multiple times concurrently.
     *
     * The flow may or may not complete. If the [episodeIdFlow] completes, this flow will also complete,
     * and it's guaranteed to complete normally without throwing an exception,
     * as all exceptions are caught and handled by [flowLoadErrorObserver].
     */
    val infoBundleFlow: Flow<SubjectEpisodeInfoBundle?> =
        episodeIdFlow.map { GetSubjectEpisodeInfoBundleFlowUseCase.SubjectIdAndEpisodeId(subjectId, it) }
            .transformLatest { request ->
                // Clear previous state or results
                emit(null)

                // Now fetch the new data, tracking errors
                emitAll(
                    getSubjectEpisodeInfoBundleFlowUseCase(flowOf(request))
                        .catchLoadError(flowLoadErrorObserver),
                )
            }

}
