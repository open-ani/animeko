/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.settings

import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.usecase.UseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

fun interface GetMediaSelectorSettingsFlowUseCase : UseCase {
    operator fun invoke(): Flow<MediaSelectorSettings>
}

object GetMediaSelectorSettingsFlowUseCaseImpl : GetMediaSelectorSettingsFlowUseCase, KoinComponent {
    private val settingsRepository: SettingsRepository by inject()
    override fun invoke(): Flow<MediaSelectorSettings> = settingsRepository.mediaSelectorSettings.flow
}

