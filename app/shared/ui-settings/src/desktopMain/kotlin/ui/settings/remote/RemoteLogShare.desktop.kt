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
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write
import me.him188.ani.remote.settings.generated.models.LogSnapshot

@Composable
internal actual fun rememberShareRemoteLog(): suspend (LogSnapshot) -> Unit = remember {
    { log ->
        FileKit.openFileSaver(suggestedName = "tv-app", extension = "log")
            ?.write(log.content.encodeToByteArray())
    }
}
