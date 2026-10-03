/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 平台自行检测到的系统深色模式, `null` 表示未提供, 使用 Compose 的 [isSystemInDarkTheme].
 *
 * 桌面端用 jSystemThemeDetector 检测系统主题并在此提供, 覆盖 Compose 在部分平台 (如 Linux) 上不可用的检测.
 */
val LocalSystemDarkThemeOverride: ProvidableCompositionLocal<Boolean?> = staticCompositionLocalOf { null }

/**
 * 系统当前是否为深色模式. 优先使用 [LocalSystemDarkThemeOverride], 否则使用 Compose 的 [isSystemInDarkTheme].
 *
 * 所有根据 [me.him188.ani.app.data.models.preference.DarkMode.AUTO] 判定主题的地方都应使用此函数.
 */
@Composable
fun isSystemInDarkThemeDetected(): Boolean = LocalSystemDarkThemeOverride.current ?: isSystemInDarkTheme()
