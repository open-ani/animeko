/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.materialkolor.hct.Hct
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults

private val colorList = ArrayList<Color>(11)
    .apply {
        set(0, getHctColor(4))
        set(1, getHctColor(5))
        set(2, getHctColor(6))
        set(3, getHctColor(7))
        set(4, getHctColor(8))
        set(5, DefaultSeedColor)
        set(6, getHctColor(9))
        set(7, getHctColor(10))
        set(8, getHctColor(1))
        set(9, getHctColor(2))
        set(10, getHctColor(3))
    }

private fun getHctColor(base: Int): Color {
    return Color(Hct.from(base * 35.0, 40.0, 40.0).toInt())
}

/**
 * All available seed colors of color scheme for app.
 */
@Suppress("UnusedReceiverParameter")
val AniThemeDefaults.themeColorOptions: List<Color>
    get() = colorList