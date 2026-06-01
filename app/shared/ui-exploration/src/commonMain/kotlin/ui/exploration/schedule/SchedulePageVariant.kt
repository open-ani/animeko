/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * 新番时间表页变体: 应用入口可提供一个替代布局 (遥控器形态的日期胶囊 + 竖版海报网格).
 *
 * PC/移动端那套 15 天并排纵向列表靠横向翻页浏览, 遥控器上没法用 —— TV 变体把两个维度拆正交
 * (日期走胶囊行, 时间走网格), 见 `TvSchedulePage`. 条目点击由变体自己走导航, 因此不需要
 * [ScheduleScreen] 那样的 onClickItem 回调.
 */
fun interface SchedulePageVariant {
    @Composable
    fun Page(presentation: SchedulePagePresentation, onRetry: () -> Unit, modifier: Modifier)
}

val LocalSchedulePageVariant = staticCompositionLocalOf<SchedulePageVariant?> { null }
