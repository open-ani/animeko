/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.foundation.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager

/**
 * 在组合存活期间保持键盘输入模式. 真 TV 永远非 touch mode; 触屏设备上跑 TV 界面时,
 * touch mode 下 clickable 节点不参与键盘焦点 (requestFocus 恒 false), 遥控器/dpad 导航整个失效,
 * 在途的 [TvFocusScope.request] 也会一直悬挂.
 *
 * 只请求一次不够: 窗口首次布局和获得窗口焦点时, ViewRootImpl 会按 WindowManager 的全局状态
 * 重置 touch mode, 可能覆盖先前的请求; 触摸也会重新进入 touch mode. 所以每次回到 touch mode 都重新请求.
 */
@Composable
fun TvKeyboardInputMode() {
    val inputModeManager = LocalInputModeManager.current
    LaunchedEffect(inputModeManager) {
        snapshotFlow { inputModeManager.inputMode }.collect {
            if (it == InputMode.Touch) inputModeManager.requestInputMode(InputMode.Keyboard)
        }
    }
}
