/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.data.models.subject.SubjectSeriesInfo
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.subject.SubjectRelationsRepository
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

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
    private val subjectRelationsRepository: SubjectRelationsRepository by inject()

    override fun invoke(idsFlow: Flow<GetSubjectEpisodeInfoBundleFlowUseCase.SubjectIdAndEpisodeId>): Flow<SubjectEpisodeInfoBundle> {
        return idsFlow.flatMapLatest { (subjectId, episodeId) ->
            combine(
                subjectCollectionRepository.subjectCollectionFlow(subjectId),
                seriesInfoFlow(subjectId),
            ) { subject, seriesInfo ->
                val episodeCollectionInfo = (subject.episodes.find { it.episodeId == episodeId }
                    ?: throw NoSuchElementException("Episode $episodeId not found in subject $subjectId"))
                SubjectEpisodeInfoBundle(
                    subjectId, episodeId,
                    subject,
                    episodeCollectionInfo,
                    seriesInfo = seriesInfo ?: SubjectSeriesInfo.compute(subject),
                    subjectCompleted = EpisodeCollections.isSubjectCompleted(
                        subject.episodes.map { it.episodeInfo },
                        subject.recurrence,
                    ),
                )
            }
        }.flowOn(flowContext)
    }

    /**
     * 系列信息要加载主线上的其他条目才能识别分部. 只取第一个值: 系列信息变化会改变查询请求, 重建整个查询会话.
     * 超时或失败时为 `null`, 退回只用本条目自己的信息, 不让播放等太久.
     */
    private fun seriesInfoFlow(subjectId: Int): Flow<SubjectSeriesInfo?> = flow {
        val seriesInfo = try {
            withTimeoutOrNull(SERIES_INFO_TIMEOUT) {
                subjectRelationsRepository.subjectSeriesInfoFlow(subjectId).first()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load series info for subject $subjectId, falling back to the subject alone" }
            null
        }
        emit(seriesInfo)
    }

    private companion object {
        private val SERIES_INFO_TIMEOUT = 5.seconds
        private val logger = logger<GetSubjectEpisodeInfoBundleFlowUseCaseImpl>()
    }
}
