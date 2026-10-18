/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.layout

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * App 的窗口是否对用户可见. 桌面端把窗口隐藏到系统托盘时为 `false`. 其他平台没有提供, 总是 `true`.
 *
 * 窗口隐藏到托盘时 Lifecycle 仍为 STARTED, 所以需要判断 App 是否在前台时, 还要读取它.
 *
 * 隐藏的窗口不再绘制, 窗口内的组合也不再重组, 所以不能靠重组得知它变为 `false`.
 * 应在协程中用 `snapshotFlow` 观察 [State.value].
 */
val LocalAppWindowVisible: ProvidableCompositionLocal<State<Boolean>> = staticCompositionLocalOf { AlwaysVisible }

private object AlwaysVisible : State<Boolean> {
    override val value: Boolean get() = true
}
