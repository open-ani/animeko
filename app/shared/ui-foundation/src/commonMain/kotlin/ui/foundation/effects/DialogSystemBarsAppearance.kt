/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.effects

import androidx.compose.runtime.Composable

/**
 * 在铺满整屏的 Dialog (Android 上 `decorFitsSystemWindows = false`) 的内容里调用: 设置 Dialog 窗口的系统栏图标颜色.
 * 这种 Dialog 画到系统栏下面, 系统栏图标颜色取 Dialog 窗口自己的设置, 默认是浅色图标, 画在浅色面板上看不清.
 *
 * @param lightBars true 时状态栏与导航栏用深色图标 (配浅色背景).
 */
@Composable
expect fun DialogSystemBarsAppearance(lightBars: Boolean)
