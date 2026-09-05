/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.settings

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.ui.foundation.AbstractViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * TV 设置子集薄 VM (atv-architecture.md §7.6, M3 精简版):
 * 播放三开关 + 弹幕总开关. 数据源/代理/主题等编辑入口提示到手机端.
 */
@Stable
class TvSettingsViewModel : AbstractViewModel(), KoinComponent {
    private val settingsRepository: SettingsRepository by inject()

    val danmakuEnabled: StateFlow<Boolean?> = settingsRepository.danmakuEnabled.flow
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), null)

    val videoConfig: StateFlow<VideoScaffoldConfig?> = settingsRepository.videoScaffoldConfig.flow
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleDanmakuEnabled() {
        backgroundScope.launch {
            settingsRepository.danmakuEnabled.update { !this }
        }
    }

    fun toggleAutoPlayNext() = updateVideoConfig { copy(autoPlayNext = !autoPlayNext) }
    fun toggleAutoSkipOpEd() = updateVideoConfig { copy(autoSkipOpEd = !autoSkipOpEd) }
    fun toggleAutoSwitchMediaOnError() =
        updateVideoConfig { copy(autoSwitchMediaOnPlayerError = !autoSwitchMediaOnPlayerError) }

    private fun updateVideoConfig(block: VideoScaffoldConfig.() -> VideoScaffoldConfig) {
        backgroundScope.launch {
            settingsRepository.videoScaffoldConfig.update(block)
        }
    }
}
