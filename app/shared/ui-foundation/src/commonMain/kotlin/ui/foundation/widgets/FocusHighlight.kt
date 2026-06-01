/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.LocalAniUiBehavior

/*
 * 焦点导航 (遥控器) 上的示焦约定, 集中在这一处.
 *
 * 手机/桌面用鼠标点, 主题色表示"我是主要动作"; 遥控器没有指针, 屏幕上必须一眼看出方向键
 * 此刻停在哪 —— 于是主题色改为表示"焦点在我身上", 未聚焦的一律压成中性色. 弹窗里同时摆着
 * 好几个按钮时 (搜索/取消、发送、确认), 全是主色实底就完全分不出焦点位置.
 *
 * 只在 [me.him188.ani.app.ui.foundation.AniUiBehavior.focusDrivenNavigation] 的形态上生效.
 */

/**
 * 弹窗动作按钮 (确认 / 发送 等): 聚焦即主题色实底, 未聚焦是中性容器 + 描边胶囊.
 *
 * 提交中不要用 `enabled = false` 挡重复点击: 禁用的按钮不可聚焦, 焦点会当场丢在弹窗里
 * (遥控器上表现为方向键全失效). 传 [loading] 换成转圈, 重复点击由 [onClick] 自己挡.
 *
 * 焦点请求器 / [androidx.compose.ui.focus.focusProperties] 这类导航配置由调用方经 [modifier] 给.
 */
@Composable
fun AniFocusActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        // 未聚焦的底比面板高一档 (与弹窗里的输入框/引用区同一档), 而不是自定 alpha:
        // 走配色表才能保证各个弹窗里的按钮长得一样
        color = if (focused) colorScheme.primary else colorScheme.surfaceContainerHighest,
        contentColor = if (focused) colorScheme.onPrimary else colorScheme.onSurface,
        border = if (focused) null else BorderStroke(1.dp, colorScheme.outlineVariant),
        interactionSource = interactionSource,
    ) {
        Box(
            Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    Modifier.size(18.dp),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = content,
                )
            }
        }
    }
}

/**
 * M3 [androidx.compose.material3.Button] 的实底取色: **聚焦才变主题色**, 未聚焦是中性容器.
 *
 * 给复用手机端版式的对话框用 (那里的按钮是现成的 `Button`, 换不成 [AniFocusActionButton]).
 * 焦点导航之外 (手机 / 桌面鼠标) 保持 M3 默认.
 */
@Composable
fun focusHighlightedButtonColors(focused: Boolean): ButtonColors =
    if (focused || !LocalAniUiBehavior.current.focusDrivenNavigation) {
        ButtonDefaults.buttonColors()
    } else {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
