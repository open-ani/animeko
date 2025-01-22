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
import me.him188.ani.app.ui.foundation.theme.DefaultSeedColor

@Serializable
enum class DarkMode {
    AUTO, LIGHT, DARK,
}

@Serializable
@Immutable
data class ThemeSettings(
    val darkMode: DarkMode = DarkMode.AUTO,
    val useDynamicTheme: Boolean = false, // only supported on Android with Build.VERSION.SDK_INT >= 31
    // TODO: Default "true" if supported (on Android, Build.VERSION.SDK_INT >= 31)
    val useBlackBackground: Boolean = false,
    val seedColorValue: ULong = DefaultSeedColor.value,
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    @Transient
    val seedColor: Color = Color(seedColorValue).let {
        // 4.4.0-alpha01 的默认是 Color.Unspecified, 4.4.0-alpha02 默认是 DEFAULT_SEED_COLOR. 所以要替换一下
        if (it == Color.Unspecified) DEFAULT_SEED_COLOR else it
    }

    companion object {
        @Stable
        val Default = ThemeSettings()
    }
}