/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import me.him188.ani.tv.ui.foundation.TvNavigationEffect
import me.him188.ani.tv.ui.foundation.TvNavigationEvent

@Composable
fun TvLoginRoute(
    viewModel: TvLoginViewModel,
    onNavigate: (TvNavigationEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    TvNavigationEffect(viewModel.navigationEvents, onNavigate)
    TvLoginScreen(state, viewModel::onIntent, modifier)
}
