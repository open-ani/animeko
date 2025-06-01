/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import androidx.compose.ui.SystemTheme
import com.jthemedetecor.OsThemeDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SystemThemeDetector {
    private val isLinux = System.getProperty("os.name").lowercase().contains("linux")
    private val detector = OsThemeDetector.getDetector()

    private val _current = MutableStateFlow(
        if (isLinux) SystemTheme.Light else isDarkToTheme(detector.isDark)
    )
    val current: StateFlow<SystemTheme> = _current.asStateFlow()

    init {
        if (!isLinux) {
            detector.registerListener {
                _current.value = isDarkToTheme(it)
            }
        }
    }

    private fun isDarkToTheme(isDark: Boolean): SystemTheme {
        return if (isDark) SystemTheme.Dark else SystemTheme.Light
    }
}