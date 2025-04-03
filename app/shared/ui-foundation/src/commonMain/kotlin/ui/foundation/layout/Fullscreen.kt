/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.layout

import androidx.compose.runtime.Composable
import me.him188.ani.app.platform.Context


/**
 * Request to set the fullscreen, landscape mode, hiding status bars and navigation bars.
 *
 * If [fullscreen] is `true`, the app will enter fullscreen mode.
 * Otherwise, the app go back to the users' preferred mode.
 *
 * Note that when [fullscreen] is `false`, the system bars will be visible,
 * but the app may be still in landscape mode if the user's system is in landscape mode.
 */
expect suspend fun Context.setRequestFullScreen(window: PlatformWindowMP, fullscreen: Boolean)

expect fun Context.setSystemBarVisible(window: PlatformWindowMP, visible: Boolean)

@Suppress("NOTHING_TO_INLINE", "KotlinRedundantDiagnosticSuppress")
@Composable
inline fun isSystemInFullscreen(): Boolean = LocalPlatformWindow.current.isUndecoratedFullscreen
