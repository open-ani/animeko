/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.main

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.him188.ani.app.ui.main.MainScreenSharedViewModel
import org.koin.core.Koin

class TvMainViewModel(koin: Koin) : MainScreenSharedViewModel(koin) {
    val uiState = accountState.map { TvMainUiState(it.selfInfo, isLoggedIn = it.isSessionValid) }
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), TvMainUiState(isLoggedIn = null))

    fun onIntent(intent: TvMainIntent) {
        when (intent) {
            TvMainIntent.Logout -> logout()
        }
    }
}

sealed interface TvMainIntent {
    data object Logout : TvMainIntent
}
