/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exprovider

import android.view.View
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal actual fun BoxWithConstraintsScope.ExternalContentImpl(
    provider: ExternalContentProvider,
    contentId: String,
    expectedWidth: Int,
    expectedHeight: Int,
    modifier: Modifier,
) {
    AndroidView(
        factory = { context ->
            provider.viewProvider(context, contentId, expectedWidth, expectedHeight) as View
        },
        modifier = Modifier.fillMaxSize(),
        update = { _ -> },
    )
}