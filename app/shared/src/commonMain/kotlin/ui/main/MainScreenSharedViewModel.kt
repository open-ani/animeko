/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.main

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.user.SelfInfoStateProducer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MainScreenSharedViewModel : AbstractViewModel(), KoinComponent {
    private val settingsRepository by inject<SettingsRepository>()

    val selfInfo = SelfInfoStateProducer(koin = getKoin()).flow

    private val needReLoginAfter500 = settingsRepository.oneshotActionConfig.flow
        .map { it.needReLoginAfter500 }
        .shareInBackground(replay = 1)

    private val _navigateToBgmLoginEvent = MutableSharedFlow<Any>(extraBufferCapacity = 1)
    val navigateToBgmLoginEvent: Flow<Any> = _navigateToBgmLoginEvent

    @Suppress("DEPRECATION")
    suspend fun checkNeedReLogin() {
        if (needReLoginAfter500.first()) {
            val migrationResult = SessionManager.migrateBangumiToken(koin = getKoin())
            if (migrationResult is SessionManager.MigrationResult.NeedReLogin) {
                // 迁移失败, 直接跳转到 BGM 登录
                _navigateToBgmLoginEvent.emit(Any())
            }
            settingsRepository.oneshotActionConfig.update { copy(needReLoginAfter500 = false) }
        }
    }
}