package me.him188.ani.app.ui.exprovider

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun BoxWithConstraintsScope.ExternalContentImpl(
    provider: ExternalContentProvider,
    contentId: String,
    expectedWidth: Int,
    expectedHeight: Int,
    modifier: Modifier,
) {
}
