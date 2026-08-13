/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.focus

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.LocalAniUiBehavior
import me.him188.ani.app.ui.foundation.ifThen

/**
 * 横滑卡片条. **TV (焦点驱动) 形态下是"锚位条"**: 聚焦卡一律停在行首锚位, 整行滑动 —— 与探索页
 * 卡片区、选集轮播同一套手感; 手机/桌面形态完全等价于一个普通 [LazyRow] (逐字对齐原实现).
 *
 * ## 为什么这些区块不能就用裸 LazyRow
 *
 * 那些按手机设计、后来直接搬到 TV 上的横滑区块 (角色条、人物页的出演/参演条、关联条目条) 都有
 * 同一个毛病: 焦点从上方进入或在行内移动时, **卡片会突然横向跳一小段**. 根因是 Compose 默认的
 * `BringIntoViewSpec` = "最小滚动到可见" —— 焦点落到部分露出的那张卡上, 行就只挪够它露全的那
 * 几十 dp. 手机上没人察觉 (手机不靠焦点导航, 这条路根本不走); 遥控器上每次进出行都看得见.
 * 补丁式地去调某个区块的留白/卡宽都治不了它: 只要"滚动量 = 让它刚好可见", 位移就必然是半张卡
 * 这种由布局余数决定的量.
 *
 * 本组件换成两条确定的规则:
 *  1. **滚动量恒为"到锚位的距离"** ([tvAnchorBringIntoViewSpec], 锚位 = [contentPadding] 的起始
 *     留白): 聚焦哪张, 哪张停到行首, 每帧重算目标、速度连续 (leanback 手感);
 *  2. **进行落点 = 上次聚焦那张** (首次进入 = 行首那张, 即锚位上那张): 进行时它本就在锚位上,
 *     滚动量为 0 —— 入场彻底不动. 不用官方 `focusRestorer` (它记节点引用, 卡片滚出组合后失效会
 *     退化成"第一个可聚焦项", 在锚位滚动下会把整行拽走; 我们记下标).
 *
 * 末尾几张卡够不到锚位 (滚动到底了) 时框架自然停在边界, 焦点框继续往右走 —— 这些卡各自画自己的
 * 焦点框, 不像探索页那样有个钉死的框, 所以不需要给末项留一整屏空白.
 *
 * @param itemSpacing 卡片间距.
 * @param contentPadding 行内留白; 其**起始值即锚位** (聚焦卡的停靠线).
 * @param itemContent 第二个参数必须挂到该卡的**可聚焦节点**上 (落点请求器 + 聚焦簿记都在里面).
 */
@Composable
fun TvAnchoredStrip(
    itemCount: Int,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    state: LazyListState = rememberLazyListState(),
    itemContent: @Composable (index: Int, itemModifier: Modifier) -> Unit,
) {
    if (!LocalAniUiBehavior.current.focusDrivenNavigation) {
        LazyRow(
            modifier,
            state = state,
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            contentPadding = contentPadding,
        ) {
            items(itemCount) { index -> itemContent(index, Modifier) }
        }
        return
    }

    val density = LocalDensity.current
    val startPadding = contentPadding.calculateStartPadding(LocalLayoutDirection.current)
    val bringIntoViewSpec = remember(density, startPadding) {
        tvAnchorBringIntoViewSpec(with(density) { startPadding.toPx() })
    }
    // 上次聚焦的下标 (进行落点). 热状态: 推导包进 derivedStateOf, 每张卡只订阅"我是不是落点"
    // 这个布尔 —— 直读的话每次换焦点整条卡都要陪跑重组
    var lastFocusedIndex by remember { mutableIntStateOf(-1) }
    val enterRequester = remember { FocusRequester() }
    val enterIndexState = remember(itemCount) {
        derivedStateOf {
            lastFocusedIndex.takeIf { it in 0 until itemCount }
                ?: state.firstVisibleItemIndex // 行首那张 = 锚位上那张
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
        LazyRow(
            modifier
                // 长按方向键的移动频率上限 (同探索页/选集轮播): 系统连发 ~20 次/秒, 每发都换卡
                // 的话滑动动画不断被打断, 卡片是闪过去而不是滑过去
                .tvFocusMoveRateLimit()
                // onEnter 只在**焦点组**节点上生效, 少一个 focusGroup 就完全不触发 (真机踩过)
                .focusProperties { onEnter = { runCatching { enterRequester.requestFocus() } } }
                .focusGroup(),
            state = state,
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            contentPadding = contentPadding,
        ) {
            items(itemCount) { index ->
                val isEnterTarget by remember(index) {
                    derivedStateOf { index == enterIndexState.value }
                }
                itemContent(
                    index,
                    Modifier
                        .ifThen(isEnterTarget) { focusRequester(enterRequester) }
                        .onFocusChanged { if (it.isFocused) lastFocusedIndex = index },
                )
            }
        }
    }
}
