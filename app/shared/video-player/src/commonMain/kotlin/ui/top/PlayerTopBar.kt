/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.top

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.navigation.LocalBackDispatcher
import me.him188.ani.utils.platform.isDesktop

/**
 * 播放器顶部导航栏
 */
@Composable
fun PlayerTopBar(
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit) = {},
    color: Color = MaterialTheme.colorScheme.onBackground,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    onButtonFocusChanged: (Boolean) -> Unit = {},
) {
    TopAppBar(
        title = {
            CompositionLocalProvider(LocalContentColor provides color) {
                if (title != null) {
                    title()
                }
            }
        },
        modifier
            .fillMaxWidth()
            .onFocusEvent { focusState ->
                // Notify parent when any button in topBar has focus
                onButtonFocusChanged(focusState.hasFocus)
            },
        navigationIcon = {
            val back = LocalBackDispatcher.current
            CompositionLocalProvider(LocalContentColor provides color) {
                val focusManager by rememberUpdatedState(LocalFocusManager.current) // workaround for #288
                var suppressInput by remember { mutableStateOf(false) }
                IconButton(
                    onClick = { back.onBackPressed() },
                    Modifier
                        .onFocusEvent {
                            if (it.isFocused) {
                                suppressInput = true
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            if (suppressInput) {
                                if (event.type == KeyEventType.KeyUp) {
                                    suppressInput = false
                                }
                                if (event.key == Key.Enter || event.key == Key.DirectionCenter || event.key == Key.NumPadEnter) {
                                    return@onPreviewKeyEvent true
                                }
                            }
                            false
                        }
                        .ifThen(needWorkaroundForFocusManager) {
                            onFocusEvent {
                                if (it.hasFocus) {
                                    focusManager.clearFocus()
                                }
                            }
                        },
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
        ),
        actions = {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                actions()
            }
        },
        windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
    )
}

// See #288
val needWorkaroundForFocusManager: Boolean
    @Composable
    get() = LocalPlatform.current.isDesktop()
