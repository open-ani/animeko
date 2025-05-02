/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.resolve
import kotlin.contracts.contract

actual val LocalContext: ProvidableCompositionLocal<Context> = compositionLocalOf {
    error("No Context provided")
}

actual abstract class Context

class IosContext(
    val files: IosContextFiles
) : Context() {
}

fun Context.asIosContext(): IosContext {
    contract { returns() implies (this@asIosContext is IosContext) }
    return this as IosContext
}

internal actual val Context.filesImpl: ContextFiles get() = this.asIosContext().files

class IosContextFiles(
    override val cacheDir: SystemPath,
    override val dataDir: SystemPath
) : ContextFiles {
    val datastoreDir = dataDir.resolve("datastores")
    val logsDir = dataDir.resolve("logs")
    override val defaultMediaCacheDir: SystemPath = dataDir
}
