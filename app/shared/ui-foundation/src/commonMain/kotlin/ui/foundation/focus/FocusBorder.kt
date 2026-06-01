/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.focus

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ifThen

/**
 * 聚焦时沿卡片轮廓画一圈主题色描边.
 *
 * 用来代替默认 indication (涟漪) 的焦点状态层作为**示焦**手段: 那是一块半透明高亮, 压在封面图上
 * 几乎看不出是哪一张拿了焦点 (遥控器导航时每按一下都要找焦点在哪); 描边一眼可见, 且与 TV 各页
 * 卡片 (选集卡、角色卡容器、播放器面板条目) 的示焦形态统一. 按压反馈仍由各自的 indication 负责,
 * 触摸/鼠标端的手感不变.
 *
 * 用法约束:
 *  - 必须挂在**焦点目标之前** (同一条 modifier 链上, `clickable`/`focusable` 之前), 否则观察不到焦点;
 *  - 必须挂在**定尺寸之后** (`width`/`size` 之后), 否则描边按父约束的尺寸画;
 *  - 若链上还有 `clip`, 挂在 `clip` 之前, 免得描边被卡片自己的圆角裁掉一半.
 *
 * 取 [Shape] 与卡片自身圆角一致即可 (描边画在轮廓内侧).
 */
@Composable
fun Modifier.focusBorder(
    shape: Shape,
    width: Dp = FOCUS_BORDER_WIDTH,
    color: Color = MaterialTheme.colorScheme.primary,
): Modifier {
    // hasFocus 而非 isFocused: 焦点目标有时在下面的子树里 (卡容器包着可聚焦的内容)
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.hasFocus }
        .ifThen(focused) { border(width, color, shape) }
}

/** 示焦描边宽度; 与 TV 各页卡片的聚焦框同一档. */
val FOCUS_BORDER_WIDTH = 2.dp

/**
 * 卡内文字避让 [focusBorder] 描边的内缩量.
 *
 * 描边画在卡片轮廓**内侧**, 而横滑卡的标题/说明一般直接顶满卡宽、末行贴着卡底 —— 不留这一点
 * 内缩, 聚焦时描边就压在字上 (真机: "文字被焦点框挡住"). 取略大于 [FOCUS_BORDER_WIDTH] 即可,
 * 大了会显得文字与封面左缘对不齐.
 */
val FOCUS_BORDER_TEXT_INSET = 4.dp
