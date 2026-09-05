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
 * TV 端主题: 复用 materialkolor 由种子色生成 m3 配色 (与手机同一算法, 品牌一致).
 * TV 固定深色 (atv-architecture.md D6).
 *
 * 同时 provide material3 与 tv-material 两套 MaterialTheme:
 * 新基建 (对齐上游 PR 的自研焦点组件) 读 material3 的 colorScheme,
 * 存量 tv-material 组件读 tv-material 的.
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
    androidx.compose.material3.MaterialTheme(colorScheme = m3ColorScheme) {
        MaterialTheme(
            colorScheme = tvColorScheme,
            content = content,
        )
    }
}
