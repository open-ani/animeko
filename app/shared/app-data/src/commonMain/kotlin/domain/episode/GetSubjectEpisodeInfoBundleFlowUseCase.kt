/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import me.him188.ani.app.data.models.subject.SubjectSeriesInfo
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.subject.SubjectRelationsRepository
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.utils.coroutines.flows.catching
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext

fun interface GetSubjectEpisodeInfoBundleFlowUseCase : UseCase {
    data class SubjectIdAndEpisodeId(
        val subjectId: Int,
        val episodeId: Int
    )

    operator fun invoke(idsFlow: Flow<SubjectIdAndEpisodeId>): Flow<SubjectEpisodeInfoBundle>
}

class GetSubjectEpisodeInfoBundleFlowUseCaseImpl(
    private val flowContext: CoroutineContext = Dispatchers.Default,
) : GetSubjectEpisodeInfoBundleFlowUseCase, KoinComponent {
    private val subjectCollectionRepository: SubjectCollectionRepository by inject()
    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val subjectRelationsRepository: SubjectRelationsRepository by inject()

    override fun invoke(idsFlow: Flow<GetSubjectEpisodeInfoBundleFlowUseCase.SubjectIdAndEpisodeId>): Flow<SubjectEpisodeInfoBundle> {
        return idsFlow.flatMapLatest { (subjectId, episodeId) ->
            combine(
                subjectCollectionRepository.subjectCollectionFlow(subjectId),
                episodeCollectionRepository.episodeCollectionInfoFlow(subjectId, episodeId),
                subjectRelationsRepository.subjectSeriesInfoFlow(subjectId).catching()
                    .onStart<Result<SubjectSeriesInfo>?> { emit(null) },
                // TODO: 2025/4/23 We should move this subjectCompleted calculation to Ani server.
                episodeCollectionRepository.subjectCompletedFlow(subjectId).catching()
                    .onStart<Result<Boolean>?> { emit(null) },
            ) { subject, episode, seriesInfo, subjectCompleted ->
                SubjectEpisodeInfoBundle(
                    subjectId, episodeId,
                    subject, episode,
                    seriesInfo = seriesInfo?.getOrNull(),
                    seriesInfoLoadError = seriesInfo?.exceptionOrNull()?.let { LoadError.fromException(it) },
                    subjectCompleted = subjectCompleted?.getOrNull(),
                    subjectCompletedLoadError = subjectCompleted?.exceptionOrNull()
                        ?.let { LoadError.fromException(it) },
                )
            }
        }.flowOn(flowContext)
    }
}