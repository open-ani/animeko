/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.foundation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

object AniTvThemeDefaults {
    /** 与手机端 [me.him188.ani.app.data.models.preference.ThemeSettings] 默认种子色一致. */
    val SeedColor = Color(0xFF4F378B)
}

/**
 * TV 端主题: 复用 materialkolor 由种子色生成 m3 配色 (与手机同一算法, 品牌一致),
 * 逐字段映射到 tv-material ColorScheme. TV 固定深色 (atv-architecture.md D6).
 *
 * M0 阶段使用默认种子色; M3 起接入 SettingsRepository.themeSettings.
 */
@Composable
fun AniTvTheme(
    seedColor: Color = AniTvThemeDefaults.SeedColor,
    content: @Composable () -> Unit,
) {
    val m3ColorScheme = dynamicColorScheme(
        primary = seedColor,
        isDark = true,
        isAmoled = false,
        style = PaletteStyle.TonalSpot,
    )
    val tvColorScheme = remember(m3ColorScheme) { m3ColorScheme.toTvColorScheme() }
    MaterialTheme(
        colorScheme = tvColorScheme,
        content = content,
    )
}
