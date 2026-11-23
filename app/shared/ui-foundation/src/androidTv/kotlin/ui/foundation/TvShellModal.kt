/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.foundation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * 主壳内容页的模态层开关. 页面打开对话框等模态层时置为 `true`, 主壳据此禁用菜单键等壳级快捷键,
 * 避免焦点被快捷键带出模态层. 页面不在主壳内时为 `null`.
 */
val LocalTvShellModalOpen: ProvidableCompositionLocal<MutableState<Boolean>?> = compositionLocalOf { null }
