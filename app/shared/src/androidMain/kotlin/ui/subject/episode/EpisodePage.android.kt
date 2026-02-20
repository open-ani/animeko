/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.findActivity

@Composable
actual fun DisplayModeEffect(config: VideoScaffoldConfig) {
    val context = LocalContext.current
    DisposableEffect(context) {
        val modeId = context.getPreferredDisplayModeId()
        context.setPreferredDisplayMode(config.displayModeId)
        onDispose {
            context.setPreferredDisplayMode(modeId)
        }
    }
}

private fun Context.getPreferredDisplayModeId(): Int {
    val activity = this.findActivity() ?: return 0
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val params = activity.window.attributes
        return params.preferredDisplayModeId
    }
    return 0
}

private fun Context.setPreferredDisplayMode(modeId: Int) {
    val activity = this.findActivity() ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val params = activity.window.attributes
        if (modeId == 0) {
            params.preferredRefreshRate = 0f
            params.preferredDisplayModeId = 0
            activity.window.setAttributes(params)
        } else {
            if (display.supportedModes.orEmpty().any { it.modeId == modeId }) {
                params.preferredRefreshRate = display.supportedModes.first { it.modeId == modeId }.refreshRate
                params.preferredDisplayModeId = modeId
                activity.window.setAttributes(params)
            }
        }
    } else {
        // do nothing
    }
}
