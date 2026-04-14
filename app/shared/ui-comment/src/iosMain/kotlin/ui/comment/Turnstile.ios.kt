/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.him188.ani.app.domain.comment.TurnstileState

actual fun createTurnstileState(url: String): TurnstileState {
    return object : TurnstileState {
        override val url: String = url
        override val tokenFlow: Flow<String> = emptyFlow()
        override val webErrorFlow: Flow<TurnstileState.Error> = emptyFlow()
        override fun reload() {}
        override fun cancel() {}
    }
}

@Composable
actual fun ActualTurnstile(
    state: TurnstileState,
    constraints: Constraints,
    modifier: Modifier,
) {
    // No-op: iOS Turnstile not yet implemented
}
