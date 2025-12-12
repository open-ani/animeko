/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("NOTHING_TO_INLINE")

package me.him188.ani.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.window.WindowState
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.toKtPath
import java.io.File
import kotlin.contracts.contract

actual abstract class Context

@Stable
class DesktopContext(
    val windowState: WindowState,
    val dataDir: File,
    val cacheDir: File,
    val logsDir: File,
    val extraWindowProperties: ExtraWindowProperties,
) : Context() {
    val dataStoreDir = dataDir.resolve("datastore")
}

@Stable
class ExtraWindowProperties

actual val LocalContext: ProvidableCompositionLocal<Context> = compositionLocalOf {
    error("No Context provided")
}

object LocalDesktopContext {
    val current: DesktopContext
        @Composable
        inline get() {
            val context = LocalContext.current
            check(context is DesktopContext)
            return context
        }
}

@Stable
inline fun Context.checkIsDesktop(): DesktopContext {
    contract { returns() implies (this@checkIsDesktop is DesktopContext) }
    check(this is DesktopContext) { "Context must be DesktopContext, but had: $this" }
    return this
}

internal actual val Context.filesImpl: ContextFiles
    get() = object : ContextFiles {
        override val cacheDir: SystemPath = (this@filesImpl as DesktopContext).cacheDir.toKtPath().inSystem
        override val dataDir: SystemPath = (this@filesImpl as DesktopContext).dataDir.toKtPath().inSystem

        override val defaultMediaCacheBaseDir: SystemPath = dataDir.resolve("media-downloads")
    }
