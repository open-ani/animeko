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
     * TV: 退出播放页后保留播放会话 (播放器与整条"搜索数据源 → 选源 → 起播"的流水线),
     * 由侧边栏"正在播放"条目回去; 数据源在后台就绪时弹一次提示.
     *
     * 默认开: 它解决的是"等数据源要十几秒"这个真实痛点 —— 退出去干别的, 加载好了再回来.
     * 关掉则回到上游行为: 退出即销毁, 每次进来从头搜索. 想省内存 (保留的会话占着一个
     * 暂停中的解码器与缓冲区) 或觉得"退出了还占着资源"不放心的用户可以关.
     */
    val tvRetainPlaybackSession: Boolean = true,
    /**
     * TV: [tvRetainPlaybackSession] 的后台提示响哪一声 ([NoticeSoundKind.None] = 只弹 toast 不出声).
     *
     * 存在这里而不是 `VideoScaffoldConfig`: 它跟着上面那条开关走, 同一个功能的两个参数放一起.
     */
    val tvNoticeSound: NoticeSoundKind = NoticeSoundKind.Default,
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
    /**
     * TV: 界面整体缩放系数, 叠加在系统 density 之上 (1f = 跟随系统).
     *
     * 不少电视 / 盒子上报的 densityDpi 与实际面板不匹配 (常见于强制 4K UI、厂商魔改 ROM),
     * 导致界面整体偏大或偏小, 而这在系统设置里无从调整. 这里给用户一个纯客户端的补偿系数.
     *
     * 缩放的是 density 而非 fontScale: `sp -> px` 本身就要乘 density, 所以只改 density
     * 就能让文字和布局等比缩放; 两个都改会导致文字被缩放两次.
     */
    val uiScale: Float = 1f,
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    @Transient
    val seedColor: Color = Color(seedColorValue).let {
        // 4.4.0-alpha01 的默认是 Color.Unspecified, 4.4.0-alpha02 默认是 DEFAULT_SEED_COLOR. 所以要替换一下
        if (it == Color.Unspecified) DefaultSeedColor else it
    }

    /**
     * 已 clamp 的 [uiScale], 供渲染直接使用: 持久化的值可能来自旧版本或损坏的配置.
     *
     * clamp 用的是 [UI_SCALE_MIN] / [UI_SCALE_MAX] 这两个 `const` 而不是 [UI_SCALE_RANGE]:
     * `const` 在编译期就内联成字面量, 而 companion 里的 `val` 是运行期字段 —— 构造函数若去读它,
     * 就会和同一个 companion 里的 [Default] 抢初始化顺序 (`Default` 先初始化 → range 还是 null → NPE).
     */
    @Transient
    val effectiveUiScale: Float =
        if (uiScale.isFinite()) uiScale.coerceIn(UI_SCALE_MIN, UI_SCALE_MAX) else 1f

    companion object {
        @Stable
        val Default = ThemeSettings()

        /**
         * [uiScale] 的下界. 够小到能救回"全是巨型卡片"的机器.
         */
        const val UI_SCALE_MIN = 0.5f

        /**
         * [uiScale] 的上界.
         *
         * 2.5 是留了余量的 2.0: 最典型的故障是 4K 面板仍上报 1080p 的 densityDpi (320 而非 640),
         * 需要的补偿恰好是 2.0 —— 若把上界就设成 2.0, 这类设备只能顶着满档用, 想再大一点都没有余地.
         */
        const val UI_SCALE_MAX = 2.5f

        /** [uiScale] 的步进, 即一次方向键 / 一格刻度的变化量. */
        const val UI_SCALE_STEP = 0.1f

        /** [UI_SCALE_MIN]..[UI_SCALE_MAX], 供 Slider 之类需要 range 的调用方使用. */
        @Stable
        val UI_SCALE_RANGE = UI_SCALE_MIN..UI_SCALE_MAX
    }
}
