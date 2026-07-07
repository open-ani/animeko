/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.layout

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

/**
 * 条目详情页的三种自适应形态.
 *
 * 断点 (见 `docs/subject-details-rewrite/02-rewrite-plan.md` §5, 数值取自 Figma 定稿):
 * - [COMPACT] `< 600dp`: 单列, 复用现有 Header + TabRow(详情/评价/讨论), 仅"详情"tab 内容为新实现.
 * - [MEDIUM] `600 ~ 1600dp`: 双栏 (Sidebar + 内容流 + 末尾评价预览).
 * - [EXPANDED] `>= 1600dp`: 三栏 (Sidebar + 内容流 + 右栏评分/热评/制作人员).
 */
enum class SubjectDetailsPaneKind {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

/**
 * 条目详情页布局参数. 集中管理断点相关的尺寸, 避免在 composable 里散落魔数.
 */
@Immutable
data class SubjectDetailsLayoutParams(
    val kind: SubjectDetailsPaneKind,
    /** 左侧信息栏固定宽度 (双栏/三栏). */
    val sidebarWidth: Dp,
    /** 侧栏内部各块的竖向间距. */
    val sidebarItemSpacing: Dp,
    /** 三栏右栏固定宽度; 双栏/单列为 0. */
    val railWidth: Dp,
    /** 右栏内卡片间距. */
    val railItemSpacing: Dp,
    /** 各栏之间的水平间距. */
    val columnSpacing: Dp,
    /** 内容区水平留白. */
    val contentHorizontalPadding: Dp,
    /** 顶栏下方的内容顶部留白. 定稿仅 12dp (Body padTop), 且随整页滚动滚出. */
    val contentTopPadding: Dp,
    val contentBottomPadding: Dp,
    /** 中栏各区块的竖向间距. */
    val sectionSpacing: Dp,
    /** 评分行内是否右对齐展示直方图 (定宽 274). 仅宽双栏; 三栏挪到右栏卡, 窄双栏放不下. */
    val showInlineRatingHistogram: Boolean = false,
    /** 中栏制作人员网格列数 (双栏用; 三栏的制作人员在右栏为键值列表). */
    val staffGridColumns: Int = 6,
) {
    val isMultiColumn: Boolean get() = kind != SubjectDetailsPaneKind.COMPACT
    val showRail: Boolean get() = kind == SubjectDetailsPaneKind.EXPANDED

    @Stable
    companion object {
        val Compact = SubjectDetailsLayoutParams(
            kind = SubjectDetailsPaneKind.COMPACT,
            sidebarWidth = 0.dp,
            sidebarItemSpacing = 0.dp,
            railWidth = 0.dp,
            railItemSpacing = 0.dp,
            columnSpacing = 0.dp,
            contentHorizontalPadding = 16.dp,
            contentTopPadding = 16.dp,
            contentBottomPadding = 16.dp,
            sectionSpacing = 20.dp,
        )

        // 双栏 1400 画板 (1505:335) Body: padTop 12 / padBottom 40 / padLR 40 / 列距 48
        val Medium = SubjectDetailsLayoutParams(
            kind = SubjectDetailsPaneKind.MEDIUM,
            sidebarWidth = 340.dp,
            sidebarItemSpacing = 20.dp,
            railWidth = 0.dp,
            railItemSpacing = 0.dp,
            columnSpacing = 48.dp,
            contentHorizontalPadding = 40.dp,
            contentTopPadding = 12.dp,
            contentBottomPadding = 40.dp,
            sectionSpacing = 28.dp,
            showInlineRatingHistogram = true,
        )

        /**
         * 窄双栏 (600 ~ [MEDIUM_WIDE_WIDTH_DP]): Figma 双栏画板按 1400 绘制, 其 340 侧栏 + 留白
         * 在 600dp 下只给中栏剩不足 200dp. 此档收缩侧栏与留白, 保证中栏始终 >= ~250dp 可用;
         * 中栏放不下的次要内容 (行内直方图) 隐藏, 制作人员降为 3 列.
         */
        val MediumNarrow = SubjectDetailsLayoutParams(
            kind = SubjectDetailsPaneKind.MEDIUM,
            sidebarWidth = 280.dp,
            sidebarItemSpacing = 20.dp,
            railWidth = 0.dp,
            railItemSpacing = 0.dp,
            columnSpacing = 24.dp,
            contentHorizontalPadding = 24.dp,
            contentTopPadding = 12.dp,
            contentBottomPadding = 24.dp,
            sectionSpacing = 24.dp,
            staffGridColumns = 3,
        )

        // 三栏 1600 画板 (1515:336) Body: padTop 12 / padBottom 48 / padLR 48
        val Expanded = SubjectDetailsLayoutParams(
            kind = SubjectDetailsPaneKind.EXPANDED,
            sidebarWidth = 340.dp,
            sidebarItemSpacing = 20.dp,
            railWidth = 372.dp,
            railItemSpacing = 20.dp,
            columnSpacing = 48.dp,
            contentHorizontalPadding = 48.dp,
            contentTopPadding = 12.dp,
            contentBottomPadding = 48.dp,
            sectionSpacing = 28.dp,
        )

        /** 三栏(extraLarge)断点, 单位 dp. */
        val EXPANDED_WIDTH_DP = WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND

        /** 双栏(medium)断点, 单位 dp. */
        val MEDIUM_WIDTH_DP = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND

        /** [Medium] 与 [MediumNarrow] 的分界, 单位 dp. 非 Figma 定稿值, 见 [MediumNarrow]. */
        const val MEDIUM_WIDE_WIDTH_DP = 1000

        /**
         * 按可用宽度选择形态.
         *
         * [availableWidth] 必须是本页面实际可用的宽度 (通常来自 `BoxWithConstraints.maxWidth`),
         * 而不是窗口宽度: 本页面会被内嵌到播放页的 ModalBottomSheet (最大 640dp) 和搜索页的
         * list-detail 详情栏中, 两者都远窄于窗口. 也不要用 [WindowSizeClass] 判断 1600 断点,
         * 桌面端它由已废弃的 `WindowSizeClass.compute()` 生成, 分档上限为 expanded(840).
         */
        fun calculate(availableWidth: Dp): SubjectDetailsLayoutParams = when {
            availableWidth >= EXPANDED_WIDTH_DP.dp -> Expanded
            availableWidth >= MEDIUM_WIDE_WIDTH_DP.dp -> Medium
            availableWidth >= MEDIUM_WIDTH_DP.dp -> MediumNarrow
            else -> Compact
        }
    }
}
