/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.onboarding

import kotlinx.coroutines.flow.StateFlow
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.user.SelfInfoStateProducer
import me.him188.ani.app.ui.user.SelfInfoUiState
import org.koin.core.component.KoinComponent

class OnboardingCompleteViewModel : AbstractViewModel(), KoinComponent {
    val state: StateFlow<SelfInfoUiState> = SelfInfoStateProducer().flow

    companion object {
        internal const val DEFAULT_AVATAR = "https://lain.bgm.tv/r/200/pic/user/l/icon.jpg"
    }
}