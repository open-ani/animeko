package me.him188.ani.app.ui.foundation.lists

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun LazyListVerticalScrollbar(state: LazyListState, modifier: Modifier) {
    LazyListVerticalScrollIndicator(state, modifier)
}
