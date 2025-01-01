/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.annotation.UiThread
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import me.him188.ani.app.ui.foundation.LocalIsPreviewing
import kotlin.coroutines.CoroutineContext

interface TurnstileState {
    val url: String

    val tokenFlow: Flow<String>

    /**
     * Start requesting token.
     * Calling this method represents that you want to get tokens. [tokenFlow] may produce results any minute.
     */
    @UiThread
    fun reload()

    /**
     * Cancel requesting token.
     * [tokenFlow] will not produce any result after calling this method.
     */
    @UiThread
    fun cancel()

    companion object {
        /**
         * Callback URI for solving Cloudflare Turnstile at Bangumi.
         * You can intercept the request or register a system-wide URI handler.
         */
        const val CALLBACK_INTERCEPTION_PREFIX = "ani://bangumi-turnstile-callback"
    }
}

expect fun createTurnstileState(url: String): TurnstileState

fun TurnstileState(url: String): TurnstileState {
    return createTurnstileState(url)
}

fun createPreviewTurnstileState(): TurnstileState {
    return object : TurnstileState {
        override val url: String = ""
        override val tokenFlow: Flow<String> = emptyFlow()
        override fun reload() {}
        override fun cancel() {}
    }
}

@UiThread
suspend fun TurnstileState.reloadAndGetToken(
    tokenFlowContext: CoroutineContext = Dispatchers.Default
): String {
    reload()
    return tokenFlow.flowOn(tokenFlowContext).first()
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