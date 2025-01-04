/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.usecase.UseCase
import org.koin.core.Koin

interface SetDanmakuEnabledUseCase : UseCase {
    suspend operator fun invoke(enabled: Boolean)
}

class SetDanmakuEnabledUseCaseImpl(
    koin: Koin,
) : SetDanmakuEnabledUseCase {
    private val settingsRepository: SettingsRepository by koin.inject()

    override suspend fun invoke(enabled: Boolean) {
        settingsRepository.danmakuEnabled.set(enabled)
    }
}
