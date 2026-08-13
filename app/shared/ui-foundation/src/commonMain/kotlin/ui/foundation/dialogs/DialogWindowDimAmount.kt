/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.dialogs

import androidx.compose.runtime.Composable

/**
 * 把当前弹窗**窗口外**那层系统压暗调成 [dimAmount] (0 = 不压暗, 1 = 全黑). 在
 * [androidx.compose.ui.window.Dialog] 的内容里调用.
 *
 * 为什么要有这个: Android 上 `Dialog` 是独立窗口, 弹窗四周那层黑是 WindowManager 的 dim,
 * 不在任何 composable 的画布里 —— 应用侧既画不出它也盖不掉它, 只能问窗口本身要. 而 compose
 * 的 `DialogWindowTheme` 沿用了系统对话框那档偏重的默认值 (0.6), 大屏上盖掉大半个屏幕时太黑.
 *
 * 非 Android 平台无操作: skiko 的弹窗遮罩是自己画的 (`DialogProperties.scrimColor`), 不走窗口 dim.
 */
@Composable
internal expect fun DialogWindowDimAmount(dimAmount: Float)
