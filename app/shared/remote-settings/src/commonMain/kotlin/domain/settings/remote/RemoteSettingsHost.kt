/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.settings.remote

import kotlinx.coroutines.flow.StateFlow
import me.him188.ani.remote.settings.RemoteSettingsLink

interface RemoteSettingsHost {
    val state: StateFlow<RemoteSettingsHostState>
}

sealed interface RemoteSettingsHostState {
    /** The server is listening and [link] is what the QR code encodes. */
    data class Ready(val link: RemoteSettingsLink) : RemoteSettingsHostState

    data object Starting : RemoteSettingsHostState

    data object PermissionRequired : RemoteSettingsHostState

    data object NoNetwork : RemoteSettingsHostState

    data object Unavailable : RemoteSettingsHostState
}
