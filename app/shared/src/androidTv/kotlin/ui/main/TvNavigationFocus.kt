/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntryDecorator
import me.him188.ani.tv.ui.foundation.focus.TvFocusBoundary

/** 使用 NavEntry 的内容身份指定导航目标；动画期间的其他条目只参与绘制。 */
@Composable
internal fun <T : Any> rememberTvNavigationFocusDecorator(activeContentKey: Any): NavEntryDecorator<T> {
    val currentKey by rememberUpdatedState(activeContentKey)
    return remember {
        NavEntryDecorator { entry ->
            TvFocusBoundary(entry.contentKey == currentKey, Modifier.fillMaxSize()) {
                entry.Content()
            }
        }
    }
}
