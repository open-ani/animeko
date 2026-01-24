/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource

import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.domain.usecase.UseCase

fun interface GetPreferredWebMediaSourceUseCase : UseCase {
    operator fun invoke(subjectId: Int): Flow<String?>
}

fun interface SetPreferredWebMediaSourceUseCase : UseCase {
    /**
     * @param mediaSourceId `null` 表示移除偏好设置
     */
    suspend operator fun invoke(subjectId: Int, mediaSourceId: String?)
}

class GetPreferredWebMediaSourceUseCaseImpl(
    private val repository: EpisodePreferencesRepository,
) : GetPreferredWebMediaSourceUseCase {
    override operator fun invoke(subjectId: Int): Flow<String?> {
        return repository.getPreferredWebMediaSource(subjectId)
    }
}

class SetPreferredWebMediaSourceUseCaseImpl(
    private val repository: EpisodePreferencesRepository,
) : SetPreferredWebMediaSourceUseCase {
    override suspend fun invoke(subjectId: Int, mediaSourceId: String?) {
        if (mediaSourceId != null) {
            repository.setPreferredWebMediaSource(subjectId, mediaSourceId)
        } else {
            repository.removePreferredWebMediaSource(subjectId)
        }
    }
}