/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exprovider

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.LocalContext

/**
 * Displays external content identified by [contentId].
 *
 * On Android, this renders a native [android.view.View].
 * On iOS and JVM (Desktop), this is a no-op.
 *
 * @param contentId identifier for the content provider
 * @param modifier the [Modifier] to apply to this composable
 */
@Composable
fun ExternalContent(
    contentId: String,
    modifier: Modifier = Modifier,
) {
    val provider = LocalExternalContentProvider.current ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    DisposableEffect(provider) {
        scope.launch { provider.initialize(context, contentId) }
        onDispose { provider.dispose(contentId) }
    }

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx().toInt() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx().toInt() }

        ExternalContentImpl(provider, contentId, widthPx, heightPx, Modifier.fillMaxSize())
    }
}

@Composable
internal expect fun BoxWithConstraintsScope.ExternalContentImpl(
    provider: ExternalContentProvider,
    contentId: String,
    expectedWidth: Int,
    expectedHeight: Int,
    modifier: Modifier = Modifier,
)