/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import me.him188.ani.app.data.models.preference.FullscreenSwitchMode
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.ui.subject.episode.EpisodeVideoDefaults
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults
import kotlin.time.Duration.Companion.seconds

/**
 * @see VideoScaffoldConfig.fullscreenSwitchMode
 */
@Suppress("UnusedReceiverParameter")
@Composable
fun EpisodeVideoDefaults.FloatingFullscreenSwitchButton(
    mode: FullscreenSwitchMode,
    isFullscreen: Boolean,
    onClickFullScreen: () -> Unit,
) {
    when (mode) {
        FullscreenSwitchMode.ONLY_IN_CONTROLLER -> {}

        FullscreenSwitchMode.ALWAYS_SHOW_FLOATING -> {
            PlayerControllerDefaults.FullscreenIcon(
                isFullscreen,
                onClickFullscreen = onClickFullScreen,
            )
        }

        FullscreenSwitchMode.AUTO_HIDE_FLOATING -> {
            var visible by remember { mutableStateOf(true) }
            LaunchedEffect(true) {
                delay(5.seconds)
                visible = false
            }
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(snap()),
                exit = fadeOut(),
            ) {
                PlayerControllerDefaults.FullscreenIcon(
                    isFullscreen,
                    onClickFullscreen = onClickFullScreen,
                )
            }
        }
    }
}