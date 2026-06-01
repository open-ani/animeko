/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import me.him188.ani.app.ui.foundation.LocalUiScaleApplier
import me.him188.ani.app.ui.foundation.theme.LocalThemeSettings

/**
 * 离开设置页时, 把界面缩放对齐到窗口层, 使弹窗与菜单也跟随.
 *
 * 用户在设置里拖滑块时, 只有主窗口能即时跟随 (Compose 层的 `LocalDensity`); 弹窗是独立 window,
 * 必须重建 Activity 才会变 —— 详见 [me.him188.ani.app.ui.foundation.UiScaleApplier].
 * 重建挑在**离开设置页**这一刻做: 页面本来就要整体换掉, 重建带来的闪烁与焦点变化被这次切换掩盖,
 * 而在设置页内调整的全程都不会被打断.
 *
 * 值没变时 [apply][me.him188.ani.app.ui.foundation.UiScaleApplier.apply] 是空操作, 所以只看不改的
 * 用户不会白白吃一次重建.
 *
 * 挂在设置页根部而不是缩放滑块旁边: 滑块所在的分组会随分栏切换 (缩放本身就可能让窗口跨过分栏断点)
 * 被销毁重建, 挂在那里会在用户还在调的时候就触发重建.
 */
@Composable
fun UiScaleSyncEffect() {
    val applier = LocalUiScaleApplier.current
    // onDispose 里读的必须是最新值: DisposableEffect 不因缩放变化重启, 直接捕获会拿到进入设置页时的旧值
    val targetScale by rememberUpdatedState(LocalThemeSettings.current.effectiveUiScale)
    DisposableEffect(applier) {
        onDispose {
            applier.apply(targetScale)
        }
    }
}
