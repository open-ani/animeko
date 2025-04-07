/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import me.him188.ani.app.platform.IosContext
import me.him188.ani.app.platform.IosContextFiles
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.utils.io.SystemCacheDir
import me.him188.ani.utils.io.SystemSupportDir
import me.him188.ani.utils.platform.annotations.TestOnly

@Composable
@TestOnly
@PublishedApi
internal actual inline fun ProvidePlatformCompositionLocalsForPreview(crossinline content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContext provides remember {
            IosContext(
                IosContextFiles(
                    cacheDir = SystemCacheDir,
                    dataDir = SystemSupportDir,
                ),
            )
        },
    ) {
        content()
    }
}
