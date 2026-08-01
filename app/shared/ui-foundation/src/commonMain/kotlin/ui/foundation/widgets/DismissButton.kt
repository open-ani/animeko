/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.widgets

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import me.him188.ani.app.ui.foundation.LocalAniUiBehavior

/**
 * 对话框里那颗**只用来关掉对话框**的按钮 ("取消" / "关闭" / "继续编辑" 等).
 *
 * 有硬件返回键的形态 (遥控器) 上返回 `null`, 即整颗按钮不渲染: 返回键已经是关闭的出口, 按钮
 * 纯属多余, 还会占掉一个焦点位 —— 方向键要多走一格才能落到真正的动作上
 * (见 [AniUiBehavior.showDismissButtons][me.him188.ani.app.ui.foundation.AniUiBehavior.showDismissButtons]).
 *
 * 用法: `AlertDialog(dismissButton = dismissDialogButton(cancelText, onDismissRequest), ...)`.
 *
 * **只用于"关闭界面"这一种语义**. 表示真实动作的"取消"照常渲染 (取消下载、取消收藏、退出多选),
 * 那些返回键替代不了.
 */
@Composable
fun dismissDialogButton(text: String, onClick: () -> Unit): (@Composable () -> Unit)? =
    if (LocalAniUiBehavior.current.showDismissButtons) {
        { TextButton(onClick) { Text(text) } }
    } else {
        null
    }

/**
 * [dismissDialogButton] 的直接渲染版: 该藏的时候什么都不画.
 *
 * 给不接受 `null` 的按钮槽用 —— 典型是纯提示对话框, 唯一的按钮 ("关闭"/"取消") 只能放在
 * `AlertDialog(confirmButton = ...)` 里, 而那个参数是必填的.
 */
@Composable
fun DismissDialogButton(text: String, onClick: () -> Unit) {
    if (LocalAniUiBehavior.current.showDismissButtons) {
        TextButton(onClick) { Text(text) }
    }
}
