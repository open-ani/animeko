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
import me.him188.ani.app.ui.foundation.theme.DefaultSeedColor

private val colorList = ((4..10) + (1..3))
    .map { it * 35.0 }
    .map { Color(Hct.from(it, 40.0, 40.0).toInt()) }
    .toMutableList()
    .apply {
        add(5, DefaultSeedColor)
    }

val AniThemeDefaults.colorList: List<Color>
    get() = me.him188.ani.app.ui.theme.colorList