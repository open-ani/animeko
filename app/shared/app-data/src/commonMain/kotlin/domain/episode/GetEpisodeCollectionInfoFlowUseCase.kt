/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.domain.usecase.UseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext

fun interface GetEpisodeCollectionInfoFlowUseCase : UseCase {
    operator fun invoke(subjectId: Int, episodeId: Int): Flow<EpisodeCollectionInfo>
}

class GetEpisodeCollectionInfoFlowUseCaseImpl(
    private val flowContext: CoroutineContext = Dispatchers.Default,
) : GetEpisodeCollectionInfoFlowUseCase, KoinComponent {
    private val repository: EpisodeCollectionRepository by inject()
    override fun invoke(subjectId: Int, episodeId: Int): Flow<EpisodeCollectionInfo> {
        return repository.episodeCollectionInfoFlow(subjectId, episodeId).flowOn(flowContext)
    }
}
