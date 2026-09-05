/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.foundation.focus

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/*
 * 焦点驱动滚动 (BringIntoView) 的策略原语 (atv-architecture.md §14.4-9).
 *
 * Compose 在 Android TV 上的平台默认 LocalBringIntoViewSpec 是 **pivot 30%**: 可滚动容器里的节点
 * 一聚焦, 前缘就被滚到容器 30% 处, 与是否已可见无关 —— 它会把整屏 hero 滚掉半屏、把锚定行滚到
 * 错位, 且总能压过手写的 animateScrollTo (两条动画争同一 ScrollState, 后启动的赢, 而 BIV 由
 * focusable 节点在焦点回调之后的协程里启动). 所以每个可滚动容器都要显式给定策略, 把页面想要的
 * 位置编码进 calculateScrollDistance; 嵌套容器 (列里的横向行) 要各自再提供, 否则继承外层的.
 */

/**
 * 锚定式策略: 聚焦项总是滚到容器**前缘** (再留 [leadingReservePx], 如行头高度 / 行首 contentPadding),
 * 而不是默认的"只要露出来就不动"或平台的 30%. 预留量用 lambda: 可随聚焦目标动态变化
 * (焦点回调同步写入, 滚动计算在其后的协程里读取).
 */
@OptIn(ExperimentalFoundationApi::class)
class TvAnchoredBringIntoViewSpec(private val leadingReservePx: () -> Float = { 0f }) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        offset - leadingReservePx()
}
