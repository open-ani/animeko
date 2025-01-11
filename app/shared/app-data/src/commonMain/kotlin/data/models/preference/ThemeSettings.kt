/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

const val DEFAULT_SEED_COLOR = -9397516 // Color(0xFF6750A4).toArgb()

@Serializable
enum class DarkMode {
    LIGHT, DARK, AUTO,
}

@Serializable
@Immutable
data class ThemeSettings(
    val darkMode: DarkMode = DarkMode.AUTO,
    val useDynamicTheme: Boolean = false, // default "true" on Android && Build.VERSION.SDK_INT >= 31
    val isAmoled: Boolean = false,
    val seedColor: Int = DEFAULT_SEED_COLOR,
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    companion object {
        @Stable
        val Default = ThemeSettings()
    }
}