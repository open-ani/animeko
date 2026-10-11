/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.remote

import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.remote_settings_share_log
import org.jetbrains.compose.resources.getString
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.ui.settings.tabs.log.shareLogFile
import me.him188.ani.remote.settings.generated.models.LogSnapshot

@Composable
internal actual fun rememberShareRemoteLog(): suspend (LogSnapshot) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { log ->
            val file =
                withContext(Dispatchers.IO) {
                    context.cacheDir.resolve("remote-settings-logs/tv-app.log").apply {
                        parentFile?.mkdirs()
                        writeText(log.content)
                    }
                }
            val title = getString(Lang.remote_settings_share_log)
            withContext(Dispatchers.Main) { context.shareLogFile(file, title) }
        }
    }
}
