/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 界面行为开关: 描述当前设备/交互方式下界面该怎么表现, 由应用入口提供一次, 界面代码只读取.
 *
 * 存在的意义是让共享界面代码不必知道自己跑在什么设备上. 例如遥控器设备上不显示屏幕内返回按钮,
 * 共享代码读 [showBackNavigationButton] 即可, 不需要判断平台 —— 判断只发生在提供者一侧
 * ([Default] 与各设备预设).
 *
 * 只放**数据**: 布尔开关、比例、颜色策略. 不要放可组合项或者设备专属逻辑, 那些应该由对应设备
 * 自己的界面模块提供实现.
 */
@Immutable
data class AniUiBehavior(
    /**
     * 界面是否靠焦点导航, 即没有指针 (遥控器 / 纯键盘).
     *
     * 这是一整类交互差异的来源, 由各站点自行解释自己受到的影响, 常见的有:
     * - 方向键必须能移动焦点, 控件 (如 `Slider`) 不能把方向键全部当成自己的输入吞掉;
     * - 持有焦点的组件被移除 / 重建后焦点会丢失, 需要主动把焦点交给替代目标;
     * - 对话框窗口不会自动分配初始焦点, 需要显式请求, 否则整个对话框无法操作;
     * - 滚动由焦点移动驱动, 不可聚焦的区域 (如页首头图) 需要额外手段才能露出.
     */
    val focusDrivenNavigation: Boolean = false,
    /**
     * 是否显示界面内的返回按钮.
     *
     * 有硬件返回键的设备 (遥控器) 上为 `false`: 按钮纯属多余, 且会占掉一个焦点位.
     */
    val showBackNavigationButton: Boolean = true,
    /**
     * 弹窗 / 侧栏里"取消""关闭"这类**只用来关掉当前界面**的按钮是否渲染.
     *
     * 有硬件返回键的设备 (遥控器) 上为 `false`: 返回键已经是关闭的出口, 按钮纯属多余, 而且
     * 会占掉一个焦点位 —— 方向键要多走一格才能落到真正的动作 (确定 / 发送 / 删除) 上,
     * 出口有两个也容易让人以为两者有区别.
     *
     * 只针对"关闭界面"这一种语义. 表示真实动作的"取消"不受此开关影响 (取消下载、取消收藏、
     * 取消选择模式等) —— 那些不是关闭界面, 返回键替代不了.
     */
    val showDismissButtons: Boolean = true,
    /**
     * 只承载标题 (以及返回按钮) 的顶栏是否渲染.
     *
     * [showBackNavigationButton] 为 `false` 时这种顶栏只剩一条标题占位, 在大屏上纯浪费空间;
     * 当前分类由导航栏高亮承担, 因此可以整条不渲染.
     */
    val showNavigationTopAppBar: Boolean = true,
    /**
     * 顶栏是否固定, 即不随内容滚动收起.
     *
     * 焦点驱动滚动的设备上必须固定: 顶栏不渲染时 `enterAlways` 的 `heightOffsetLimit` 得不到
     * 测量更新 (保持初始 `-MAX_VALUE`), 它的 `onPreScroll` 会无限消耗向上滚动增量, 吃掉焦点
     * `bringIntoView` 触发的滚动, 页面无法随焦点移动.
     */
    val pinTopAppBar: Boolean = false,
    /**
     * `ModalBottomSheet` 最大宽度占窗口宽度的比例; `null` 表示用 M3 默认值.
     *
     * M3 默认写死 640.dp, 在横屏大屏上 (960dp 宽) 只占 2/3 宽而高度近全屏, 显得窄长.
     */
    val sheetMaxWidthFraction: Float? = null,
    /**
     * 应用外壳是否为全屏沉浸式布局 (整屏背景 + 侧边导航).
     *
     * 为真时页面按外壳的约定渲染:
     * - 页面容器透明, 透出外壳的统一整屏背景 (页面自绘背景色会出现明显的矩形边界);
     * - 账号头像与设置入口由外壳的侧边导航统一承载, 页面顶栏不再重复渲染.
     */
    val immersiveShell: Boolean = false,
    /**
     * 播放器是否支持窗口/全屏切换.
     *
     * 恒为全屏的设备上, 与"全屏按钮"相关的设置项没有意义, 应当隐藏.
     */
    val supportsWindowedPlayback: Boolean = true,
    /**
     * 页面切换是否使用柔和 crossfade.
     *
     * 默认方案的 `emphasizedAccelerate` 淡出在全屏切换时观感像"突然变白".
     */
    val crossfadeNavigation: Boolean = false,
    /**
     * 导航根容器底色是否恒为黑色.
     *
     * 页面切换过渡的淡入淡出间隙会露出根底色, 浅色主题下白色一闪很刺眼 (尤其播放器 → 缓存页,
     * 前后都是暗色内容). 各页面都自绘不透明背景, 根底色只在过渡间隙可见, 全屏形态下黑色最自然.
     */
    val blackRootBackground: Boolean = false,
    /**
     * 贴边侧栏/抽屉是否改为居中弹窗形态.
     *
     * 大屏上贴边面板离视线中心远, 且焦点从内容跳到屏幕边缘的过程难以看清.
     */
    val panelsAsCenteredDialogs: Boolean = false,
    /**
     * 更新下载完成后是否自动安装.
     *
     * 输入成本高的设备 (遥控器) 上, 让用户下载结束后再找一次按钮不合理.
     */
    val autoInstallUpdates: Boolean = false,
    /**
     * 播放页及其内部页面是否强制深色主题.
     *
     * 背景恒为视频画面的形态下, 浅色配色的文字压在画面上不可读.
     */
    val forceDarkInPlayer: Boolean = false,
) {
    companion object {
        /**
         * 指针设备 (触屏 / 鼠标) 的默认行为.
         */
        val Default = AniUiBehavior()
    }
}

val LocalAniUiBehavior = staticCompositionLocalOf { AniUiBehavior.Default }
