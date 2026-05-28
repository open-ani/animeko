/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import kotlinx.io.files.Path
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.resolve

actual val LocalContext: ProvidableCompositionLocal<Context> = compositionLocalOf {
    WebContext()
}

actual abstract class Context

class WebContext : Context()

internal actual val Context.filesImpl: ContextFiles get() = WebContextFiles

private object WebContextFiles : ContextFiles {
    override val cacheDir: SystemPath = Path("/cache").inSystem
    override val dataDir: SystemPath = Path("/data").inSystem
    override val defaultMediaCacheBaseDir: SystemPath = dataDir.resolve("media-downloads")
}
