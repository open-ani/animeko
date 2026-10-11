/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.remote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import me.him188.ani.app.ui.settings.tabs.log.shareFile
import me.him188.ani.remote.settings.generated.models.LogSnapshot
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.writeText

@Composable
internal actual fun rememberShareRemoteLog(): suspend (LogSnapshot) -> Unit {
    val controller = LocalUIViewController.current
    return remember(controller) {
        { log ->
            val path = Path(SystemTemporaryDirectory, "tv-app.log")
            path.inSystem.writeText(log.content)
            withContext(Dispatchers.Main) { shareFile(path.toString(), controller) }
        }
    }
}
