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
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import me.him188.ani.app.platform.DesktopContext
import me.him188.ani.app.platform.ExtraWindowProperties
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.PlatformWindow
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.currentPlatform
import java.io.File

@Composable
@TestOnly
@PublishedApi
internal actual inline fun ProvidePlatformCompositionLocalsForPreview(crossinline content: @Composable () -> Unit) {
    val windowState = remember { WindowState(size = DpSize(1920.dp, 1080.dp)) }
    CompositionLocalProvider(
        LocalContext provides remember {
            DesktopContext(
                windowState,
                File("."),
                File("."),
                File("./logs"),
                ExtraWindowProperties(),
            )
        },
        LocalPlatformWindow provides remember(windowState) {
            PlatformWindow(0L, null, windowState, currentPlatform())
        },
    ) {
        content()
    }
}
