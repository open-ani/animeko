/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * "玻璃"底色的默认墨色浓度. 对齐 M3 state-layer 惯例 (8%-12% 的淡层).
 */
const val GLASS_CONTAINER_ALPHA = 0.10f

/**
 * 半透明的"玻璃"容器底色: 以 onSurface 为墨色、向主题动态色 (surfaceTint, 封面取色的
 * primary) 偏移少许 —— 比纯灰更融入取色主题.
 *
 * 用它而不是不透明的 `surfaceContainer*`: 底下压着背景图 (TV 详情页的 backdrop) 时,
 * 实心底色会把图整块盖掉. 墨色随主题自动反转 —— 暗色主题是白色半透明, 浅色主题是深色半透明,
 * 配合不透明的 onSurface 内容色, 任意主题下都读得清.
 */
@Composable
fun glassContainerColor(alpha: Float = GLASS_CONTAINER_ALPHA): Color = lerp(
    MaterialTheme.colorScheme.onSurface,
    MaterialTheme.colorScheme.surfaceTint,
    0.35f,
).copy(alpha = alpha)

@Composable
fun Color.looming(): Color {
    return copy(alpha = 0.90f)
}

@Composable
fun Color.slightlyWeaken(): Color {
    return copy(alpha = 0.618f)
}

@Composable
fun Color.weaken(): Color {
    return copy(alpha = 0.5f)
}

@Composable
fun Color.stronglyWeaken(): Color {
    return copy(alpha = 1 - 0.618f)
}

@Composable
fun Color.disabledWeaken(): Color {
    return copy(alpha = 0.12f)
}

/**
 * 在 HSV 空间上调整本色: 色相恒定, 只缩放饱和度与明度.
 *
 * 用来做"同一个颜色的深浅两档" —— 上面那几个 `weaken` 是降 alpha, 在深色主题里反而更暗;
 * 掺白 (lerp 到白) 又会连色相感一起冲淡. 两者都读不出"同一件事的两个状态".
 */
@Stable
fun Color.adjustHsv(saturationFactor: Float = 1f, valueFactor: Float = 1f): Color {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == red -> 60f * (((green - blue) / delta) % 6f)
        max == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val saturation = if (max == 0f) 0f else delta / max
    return Color.hsv(
        hue = hue,
        saturation = (saturation * saturationFactor).coerceIn(0f, 1f),
        value = (max * valueFactor).coerceIn(0f, 1f),
        alpha = alpha,
    )
}

//@Stable
//fun aniDarkColorTheme(): ColorScheme {
//    PaletteTokens.run {
//        return darkColorScheme()
//    }
//}
//
//@Stable
//fun aniLightColorTheme(): ColorScheme = lightColorScheme(
//)
