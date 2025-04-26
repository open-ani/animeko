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
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.coroutines.retryWithBackoffDelay
import org.koin.core.Koin

data class SetEpisodeCollectionTypeRequest(
    val subjectId: Int,
    val episodeId: Int,
    val collectionType: UnifiedCollectionType
)

fun interface SetEpisodeCollectionTypeUseCase : UseCase {
    suspend operator fun invoke(
        subjectId: Int,
        episodeId: Int,
        collectionType: UnifiedCollectionType,
    )

    suspend fun invokeSafe(request: SetEpisodeCollectionTypeRequest): LoadError? {
        return LoadError.runAndWrapOrThrowCancellation {
            invoke(request.subjectId, request.episodeId, request.collectionType)
        }
    }
}

class SetEpisodeCollectionTypeUseCaseImpl(
    koin: Koin,
) : SetEpisodeCollectionTypeUseCase {
    private val episodeCollectionRepository: EpisodeCollectionRepository by koin.inject()
    override suspend fun invoke(subjectId: Int, episodeId: Int, collectionType: UnifiedCollectionType) {
        withContext(Dispatchers.Default) {
            suspend {
                // ClientRequestException
                // Client request(PATCH https://api.bgm.tv/v0/users/-/collections/235128/episodes) invalid: 400 . Text: "{"title":"Bad Request","details":{"path":"/v0/users/-/collections/235128/episodes","method":"PATCH"},"request_id":"****","description":"you need to add subject to your collection first"}
                episodeCollectionRepository.setEpisodeCollectionType(subjectId, episodeId, collectionType)
            }.asFlow().retryWithBackoffDelay(3).first()
        }
    }
}

