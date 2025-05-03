/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import android.os.Environment
import androidx.compose.runtime.ProvidableCompositionLocal
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.toKtPath
import java.io.File


actual typealias Context = android.content.Context

actual val LocalContext: ProvidableCompositionLocal<Context>
    get() = androidx.compose.ui.platform.LocalContext

class AndroidContextFiles(context: android.content.Context) : ContextFiles {
    override val cacheDir: SystemPath =
        (context.cacheDir ?: File("")).toKtPath().inSystem // can be null when previewing
    override val dataDir: SystemPath =
        (context.filesDir ?: File("")).toKtPath().inSystem // can be null when previewing

    val fallbackInternalBaseMediaCacheDir = dataDir.resolve("media-downloads")

    override val defaultBaseMediaCacheDir: SystemPath =
        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.toKtPath()?.inSystem
            ?: fallbackInternalBaseMediaCacheDir
}

internal actual val Context.filesImpl: ContextFiles
    get() = AndroidContextFiles(this)

