/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import me.him188.ani.app.platform.rememberPlatformWindow
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.utils.platform.annotations.TestOnly

@Composable
@TestOnly
@PublishedApi
internal actual inline fun ProvidePlatformCompositionLocalsForPreview(crossinline content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalPlatformWindow provides rememberPlatformWindow(),
        content = { content() }
    )
}
