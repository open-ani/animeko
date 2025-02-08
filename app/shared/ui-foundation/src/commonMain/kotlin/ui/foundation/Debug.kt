/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.preference.DebugSettings
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

// todo: shit
interface DebugSettingsViewModel {
    @TestOnly
    var isAppInDebugModeOverride: Boolean
    val isAppInDebugMode: Boolean
    val showControllerAlwaysOnRequesters: Boolean

    val debugSettings: State<DebugSettings>
    fun updateDebugSettings(settings: DebugSettings)
}

// todo: shit
class DebugSettingsViewModelImpl : DebugSettingsViewModel, AbstractViewModel(), KoinComponent {
    private val settingsRepository by inject<SettingsRepository>()
    override val debugSettings by lazy { settingsRepository.debugSettings.flow.produceState(DebugSettings(_placeHolder = -1)) }

    override fun updateDebugSettings(settings: DebugSettings) {
        launchInBackground {
            settingsRepository.debugSettings.set(settings)
        }
    }

    @TestOnly
    override var isAppInDebugModeOverride by mutableStateOf(false)

    @OptIn(TestOnly::class)
    override val isAppInDebugMode: Boolean by derivedStateOf {
        isAppInDebugModeOverride || debugSettings.value.enabled
    }
    override val showControllerAlwaysOnRequesters: Boolean
            by settingsRepository.debugSettings.flow.map { it.showControllerAlwaysOnRequesters }.produceState(false)
}

// todo: shit
class PreviewDebugSettingsViewModel : DebugSettingsViewModel {
    @TestOnly
    override var isAppInDebugModeOverride: Boolean = true

    @OptIn(TestOnly::class)
    override val isAppInDebugMode: Boolean
        get() = isAppInDebugModeOverride
    override val showControllerAlwaysOnRequesters: Boolean
        get() = true

    override val debugSettings: MutableState<DebugSettings> = mutableStateOf(DebugSettings(_placeHolder = -1))

    override fun updateDebugSettings(settings: DebugSettings) {
        debugSettings.value = settings
    }
}

@Composable
fun isInDebugMode(): Boolean {
    val vm = rememberDebugSettingsViewModel()
    return vm.isAppInDebugMode
}

// todo: shit
@Composable
fun rememberDebugSettingsViewModel(): DebugSettingsViewModel {
    return if (LocalIsPreviewing.current) {
        remember { PreviewDebugSettingsViewModel() }
    } else {
        viewModel<DebugSettingsViewModelImpl> { DebugSettingsViewModelImpl() }
    }
}