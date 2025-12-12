/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.features.AspectRatioMode
import org.openani.mediamp.features.VideoAspectRatio

@Stable
class VideoAspectRatioControllerState(
    private val videoAspectRatio: VideoAspectRatio,
    scope: CoroutineScope,
) {
    var currentMode by mutableStateOf(videoAspectRatio.mode.value)
    val currentIndex by derivedStateOf { Entries.indexOf(currentMode) }

    init {
        scope.launch {
            videoAspectRatio.mode.collect {
                currentMode = it
            }
        }
    }

    fun setMode(mode: AspectRatioMode) {
        videoAspectRatio.setMode(mode)
    }

    companion object {
        val Entries: List<AspectRatioMode> = AspectRatioMode.entries
    }
}

@Composable
fun renderAspectRatioMode(mode: AspectRatioMode): String {
    return when (mode) {
        AspectRatioMode.FIT -> "适应"
        AspectRatioMode.STRETCH -> "填充"
        AspectRatioMode.CROP -> "裁切"
    }
}

@OptIn(InternalForInheritanceMediampApi::class)
object NoOpVideoAspectRatio : VideoAspectRatio {
    override val mode: StateFlow<AspectRatioMode> = MutableStateFlow(AspectRatioMode.FIT)
    override fun setMode(mode: AspectRatioMode) {

    }
}