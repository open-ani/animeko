/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.main

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.ui.foundation.AbstractViewModel

class TvMainViewModel(repository: UserRepository, private val savedState: SavedStateHandle) : AbstractViewModel() {
    private val content = savedState.getStateFlow("content", TvShellContent.Exploration)
    val uiState = combine(content, repository.selfInfoFlow.onStart { emit(null) }) { content, selfInfo -> TvMainUiState(content, selfInfo) }
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), TvMainUiState(content.value))

    fun onIntent(intent: TvMainIntent) {
        savedState["content"] = when (intent) {
            is TvMainIntent.SelectContent -> intent.content
            TvMainIntent.Back, TvMainIntent.LoggedIn -> TvShellContent.Exploration
        }
    }
}
