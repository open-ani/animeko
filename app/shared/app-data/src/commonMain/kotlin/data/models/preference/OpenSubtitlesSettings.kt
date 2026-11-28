/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class OpenSubtitlesSettings(
    val enabled: Boolean = true,
    val apiKey: String = "",
    val username: String = "",
    val password: String = "",
    val languages: String = "zh-cn,zh-tw,en,ja",
) {
    val hasAccount: Boolean get() = username.isNotBlank() && password.isNotEmpty()
    val isConfigured: Boolean get() = enabled && (apiKey.isNotBlank() || hasAccount)

    override fun toString(): String = "OpenSubtitlesSettings(enabled=$enabled, configured=$isConfigured)"

    companion object {
        val Default = OpenSubtitlesSettings()
    }
}
