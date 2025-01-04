/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.domain.usecase.UseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

fun interface MediaSelectorEventSavePreferenceUseCase : UseCase {
    suspend operator fun invoke(mediaSelector: MediaSelector, subjectId: Int)
}

object MediaSelectorEventSavePreferenceUseCaseImpl : MediaSelectorEventSavePreferenceUseCase, KoinComponent {
    private val episodePreferencesRepository: EpisodePreferencesRepository by inject()

    override suspend fun invoke(mediaSelector: MediaSelector, subjectId: Int) {
        mediaSelector.eventHandling.run {
            savePreferenceOnSelect {
                episodePreferencesRepository.setMediaPreference(subjectId, it)
            }
        }
    }
}