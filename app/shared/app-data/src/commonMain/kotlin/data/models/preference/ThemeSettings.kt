/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

val DEFAULT_SEED_COLOR = Color(0xFF6200EE)

@Serializable
enum class DarkMode {
    AUTO, LIGHT, DARK,
}

@Serializable
@Immutable
data class ThemeSettings(
    val darkMode: DarkMode = DarkMode.AUTO,
    val useDynamicTheme: Boolean = false, // default "true" on Android && Build.VERSION.SDK_INT >= 31
    val useBlackBackground: Boolean = false,
    val seedColorValue: ULong = DEFAULT_SEED_COLOR.value,
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    @Transient
    val seedColor: Color = Color(seedColorValue)
    companion object {
        @Stable
        val Default = ThemeSettings()
    }
}