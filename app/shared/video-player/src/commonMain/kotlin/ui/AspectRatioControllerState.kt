/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.openani.mediamp.features.AspectRatioMode

/**
 * 画面比例控制器状态
 */
@Stable
class AspectRatioControllerState(
    initialMode: AspectRatioMode = AspectRatioMode.FIT
) {
    var currentMode by mutableStateOf(initialMode)
        private set

    fun setMode(mode: AspectRatioMode) {
        currentMode = mode
    }
}