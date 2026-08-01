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
import me.him188.ani.app.ui.theme.DefaultSeedColor

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
    val alwaysDarkInEpisodePage: Boolean = false,
    val useDynamicSubjectPageTheme: Boolean = false,
    val seedColorValue: ULong = DefaultSeedColor.value,
    val enableAnimatedGradientSubjectPage: Boolean = false,
    val enableFrostedGlassEffect: Boolean = false,
    /** TV: 探索页使用沉浸式布局 (Hero 轮播); 关闭则回退上游原布局 (低端机可关以降低开销). */
    val tvImmersiveExploration: Boolean = true,
    /** TV: 条目详情页使用沉浸式布局 (Hero 首屏); 关闭则回退上游通用多栏布局. */
    val tvImmersiveDetails: Boolean = true,
    /** TV: 新番时间表使用日期胶囊 + 海报网格布局; 关闭则回退上游 15 天并排的纵向列表. */
    val tvImmersiveSchedule: Boolean = true,
    /**
     * TV: 完整视觉效果 (**默认关**), 即不为低端设备让步的那一档.
     *
     * 一个开关打包全部"好看但费机器"的取舍, 因为需要其中一项的设备通常三项都扛得住:
     * - 过渡动画: 跨分类切换的卡片滑动 (关 = 渐隐渐现);
     * - 常驻装饰动画: 加载占位脉动、hero 长标题无限跑马灯 (关 = 静态 / 滚固定次数即停);
     * - 图片档位: "继续观看"hero 背景剧照用 TMDB 原图 (关 = w1280).
     *
     * 默认关: 实测这三项分别贡献了换分类的掉帧、页面永远进不了静止态的常驻底噪、
     * 每次换卡 8-33MB 的位图解码 —— 而收益在 10-foot 观看距离上本就不明显.
     * 高性能盒子的用户在设置里一键开回完整档.
     */
    val tvFullVisualEffects: Boolean = false,
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    @Transient
    val seedColor: Color = Color(seedColorValue).let {
        // 4.4.0-alpha01 的默认是 Color.Unspecified, 4.4.0-alpha02 默认是 DEFAULT_SEED_COLOR. 所以要替换一下
        if (it == Color.Unspecified) DefaultSeedColor else it
    }

    companion object {
        @Stable
        val Default = ThemeSettings()
    }
}
