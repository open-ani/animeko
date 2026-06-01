/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.dialogs

import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

@Composable
internal actual fun DialogWindowDimAmount(dimAmount: Float) {
    val view = LocalView.current
    // 弹窗内容视图的某级祖先实现了 DialogWindowProvider (compose 的 DialogLayout).
    // 沿链向上找而不是写死 view.parent: 层级由 compose 内部决定, 找不到就放弃 (保持系统默认压暗),
    // 不能让一层视觉调整把弹窗弄崩
    val window = remember(view) {
        var node: View? = view
        while (node != null) {
            (node as? DialogWindowProvider)?.let { return@remember it.window }
            node = node.parent as? View
        }
        null
    }
    SideEffect {
        window ?: return@SideEffect
        // dim 生效的前提是窗口带 FLAG_DIM_BEHIND (compose 的弹窗主题已经带了, 这里只是保底)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        // 不用 Window.setDimAmount: 它是 API 33 才有的, 而 TV 盒子普遍停在更早的版本.
        // 改 attributes 各版本通用 —— 赋值这一步会触发 WindowManager 重新应用参数
        window.attributes = window.attributes.also { it.dimAmount = dimAmount }
    }
}
