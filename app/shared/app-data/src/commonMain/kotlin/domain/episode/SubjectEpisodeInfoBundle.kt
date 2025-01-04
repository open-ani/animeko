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
import me.him188.ani.app.domain.foundation.LoadError
import org.koin.core.Koin

/**
 * A simple data bundle combining subject and episode collection info.
 */
class SubjectEpisodeInfoBundle(
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

    /**
     * The internal mutable error state that tracks loading exceptions.
     */
    private val _infoLoadErrorState: MutableStateFlow<LoadError?> = MutableStateFlow(null)

    /**
     * A read-only flow of the last loading error, if any. Null when no error has occurred.
     */
    val infoLoadErrorState: StateFlow<LoadError?> = _infoLoadErrorState.asStateFlow()

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
     * The flow **never** completes exceptionally. Actually, it only completely and normally completes when [episodeIdFlow] compete.
     */
    val infoBundleFlow: Flow<SubjectEpisodeInfoBundle?> =
        episodeIdFlow.map { GetSubjectEpisodeInfoBundleFlowUseCase.SubjectIdAndEpisodeId(subjectId, it) }
            .transformLatest { request ->
                // Clear previous state or results
                emit(null)

                // Now fetch the new data, tracking errors
                emitAll(
                    getSubjectEpisodeInfoBundleFlowUseCase(flowOf(request))
                        .onEach {
                            // If no error is thrown, reset the error state
                            _infoLoadErrorState.value = null
                        }
                        .onCompletion { e ->
                            // If an exception occurs, store it for UI error handling
                            if (e != null) {
                                _infoLoadErrorState.value = LoadError.fromException(e)
                            }
                        },
                )
            }
}
