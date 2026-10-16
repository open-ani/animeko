/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import me.him188.ani.app.domain.notification.NotificationBadgeManager
import me.him188.ani.app.ui.foundation.layout.LocalAppWindowVisible

/**
 * 把 App 的前后台状态告诉 [NotificationBadgeManager], 使它只在前台轮询未读数, 并在回到前台时立即刷新.
 *
 * Lifecycle 至少为 STARTED 且窗口可见 ([LocalAppWindowVisible]) 时为前台. 桌面端把窗口隐藏到系统托盘时 Lifecycle 仍为 STARTED,
 * 所以要同时看窗口是否可见. 隐藏的窗口不再重组, 因此两者都在协程中观察, 而不依赖重组.
 *
 * 在 UI 根组件挂载一次.
 */
@Composable
fun NotificationForegroundObserver(badgeManager: NotificationBadgeManager) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val windowVisible = LocalAppWindowVisible.current
    LaunchedEffect(badgeManager, lifecycle, windowVisible) {
        combine(
            lifecycle.currentStateFlow,
            snapshotFlow { windowVisible.value },
        ) { state, visible ->
            state.isAtLeast(Lifecycle.State.STARTED) && visible
        }
            .distinctUntilChanged()
            .collect { badgeManager.setAppForeground(it) }
    }
}
