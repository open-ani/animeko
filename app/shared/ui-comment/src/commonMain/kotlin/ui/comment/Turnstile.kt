/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.him188.ani.app.domain.comment.TurnstileState
import me.him188.ani.app.ui.foundation.LocalIsPreviewing
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview

expect fun createTurnstileState(url: String): TurnstileState

fun TurnstileState(url: String): TurnstileState {
    return createTurnstileState(url)
}

fun createPreviewTurnstileState(): TurnstileState {
    return object : TurnstileState {
        override val url: String = ""
        override val tokenFlow: Flow<String> = emptyFlow()
        override val webErrorFlow: Flow<TurnstileState.Error> = emptyFlow()
        override fun reload() {}
        override fun cancel() {}
    }
}

@Composable
fun Turnstile(
    state: TurnstileState,
    modifier: Modifier = Modifier,
) {
    val previewing = LocalIsPreviewing.current
    BoxWithConstraints(modifier) {
        if (previewing) {
            Column(
                modifier = Modifier.fillMaxWidth().height(96.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("PreviewTurnstile")
            }
        } else {
            ActualTurnstile(state, constraints, Modifier)
        }
    }
}

@Composable
expect fun ActualTurnstile(
    state: TurnstileState,
    constraints: Constraints,
    modifier: Modifier = Modifier,
)

@Preview
@Composable
private fun PreviewTurnstile() {
    ProvideCompositionLocalsForPreview {
        Turnstile(state = createPreviewTurnstileState())
    }
}
