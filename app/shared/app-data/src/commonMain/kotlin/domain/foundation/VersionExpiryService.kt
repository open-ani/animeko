/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.foundation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Signals that the app version is expired and must be upgraded.
 */
interface VersionExpiryService {
    val state: StateFlow<VersionExpiryState?>
    fun onVersionExpired(latestVersion: String?)
}

data class VersionExpiryState(
    val latestVersion: String?,
)

class DefaultVersionExpiryService : VersionExpiryService {
    private val _state = MutableStateFlow<VersionExpiryState?>(null)
    override val state: StateFlow<VersionExpiryState?> = _state.asStateFlow()

    override fun onVersionExpired(latestVersion: String?) {
        _state.value = VersionExpiryState(latestVersion)
    }
}

