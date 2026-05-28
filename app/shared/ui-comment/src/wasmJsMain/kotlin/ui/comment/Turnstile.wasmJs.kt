package me.him188.ani.app.ui.comment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.him188.ani.app.domain.comment.TurnstileState

actual fun createTurnstileState(url: String): TurnstileState = object : TurnstileState {
    override val url: String = url
    override val tokenFlow: Flow<String> = emptyFlow()
    override val webErrorFlow: Flow<TurnstileState.Error> = emptyFlow()
    override fun reload() {}
    override fun cancel() {}
}

@Composable
actual fun ActualTurnstile(state: TurnstileState, constraints: Constraints, modifier: Modifier) {
    Column(modifier.fillMaxWidth().height(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Turnstile is not available in the browser build")
    }
}
