/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.app

import androidx.compose.runtime.Composable

/**
 * 当前设备是否有可用于播放器快捷键的实体键盘.
 *
 * 桌面端总是 `true`. Android 检查已连接的字母键盘 (包括蓝牙 / USB 键盘和带键盘的平板), 并随插拔更新.
 * iOS 为 `false`.
 */
@Composable
internal expect fun rememberHasPhysicalKeyboard(): Boolean
