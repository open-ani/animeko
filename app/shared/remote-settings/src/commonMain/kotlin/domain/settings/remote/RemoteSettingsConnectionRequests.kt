/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.settings.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.him188.ani.remote.settings.RemoteSettingsLink

/** In-memory handoff from platform deep links. Navigation entries contain no access key. */
object RemoteSettingsConnectionRequests {
    private val pending = MutableStateFlow<RemoteSettingsLink?>(null)
    val requests = pending.asStateFlow()

    /** Returns false when [uri] is not a valid remote settings link. */
    fun offer(uri: String): Boolean {
        pending.value =
            try {
                RemoteSettingsLink.parse(uri)
            } catch (_: Exception) {
                return false
            }
        return true
    }

    fun take(): RemoteSettingsLink? = pending.value?.also { pending.compareAndSet(it, null) }
}
