/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.layout.isInLandscapeMode
import me.him188.ani.utils.platform.isMobile

@Composable
fun rememberStatusBarHeightAsState(
    isSystemInLandscapeMode: Boolean = isInLandscapeMode(),
): State<Dp> {
    val isLandscapeModeUpdated by rememberUpdatedState(isSystemInLandscapeMode)
    var statusBarHeight by rememberSaveable { mutableStateOf(0) }
    val density by rememberUpdatedState(LocalDensity.current)
    if (LocalPlatform.current.isMobile() && !isSystemInLandscapeMode) {
        // TODO: 2024/12/28 We should actually consider insets from all sides and write a proper layout algorithm.
        val insets = WindowInsets.displayCutout // composable
        SideEffect {
            statusBarHeight = insets.getTop(density)
        }
    }

    return remember {
        derivedStateOf {
            if (isLandscapeModeUpdated) {
                with(density) {
                    statusBarHeight.toDp()
                }
            } else {
                0.dp
            }
        }
    }
}