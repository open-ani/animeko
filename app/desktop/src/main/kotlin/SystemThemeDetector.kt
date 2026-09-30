/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import com.jthemedetecor.OsThemeDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通过 jSystemThemeDetector 检测系统是否为深色模式, 并在系统主题变化时更新.
 * 结果通过 [me.him188.ani.app.ui.foundation.theme.LocalSystemDarkThemeOverride] 提供给 Compose.
 */
class SystemThemeDetector {
    private val detector = OsThemeDetector.getDetector()

    private val _isDark = MutableStateFlow(detector.isDark)
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    init {
        detector.registerListener {
            _isDark.value = it
        }
    }
}
