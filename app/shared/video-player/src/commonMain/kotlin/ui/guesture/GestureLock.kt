/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.guesture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.theme.aniColorTheme
import me.him188.ani.app.ui.foundation.theme.slightlyWeaken
import me.him188.ani.app.videoplayer.ui.ControllerVisibility
import me.him188.ani.app.videoplayer.ui.PlayerControllerState
import me.him188.ani.app.videoplayer.ui.progress.PlayerProgressSliderState
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.PlaybackSpeed
import kotlin.time.Duration.Companion.seconds

const val TAG_GESTURE_LOCK = "GestureLock"

@Composable
fun GestureLock(
    isLocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
//    val background = aniColorTheme(isDark = true).onSurface
//    SmallFloatingActionButton(
//        onClick = onClick,
//        modifier = modifier,
//        containerColor = background,
//    ) {
//        CompositionLocalProvider(LocalContentColor provides aniColorTheme(isDark = true).contentColorFor(background)) {
//            if (isLocked) {
//                Icon(Icons.Outlined.LockOpen, contentDescription = "Lock screen")
//            } else {
//                Icon(Icons.Outlined.Lock, contentDescription = "Unlock screen")
//            }
//        }
//    }
    Surface(
        modifier.testTag(TAG_GESTURE_LOCK),
        shape = RoundedCornerShape(16.dp),
        color = aniColorTheme(isDark = true).background.copy(0.05f),
        border = BorderStroke(0.5.dp, aniColorTheme(isDark = false).outline.slightlyWeaken()),
    ) {
        IconButton(onClick) {
            val color = if (isLocked) {
                aniColorTheme(isDark = true).primary
            } else {
                Color.White
            }
            CompositionLocalProvider(LocalContentColor provides color) {
                if (isLocked) {
                    Icon(Icons.Outlined.Lock, contentDescription = "UnLock screen")
                } else {
                    Icon(Icons.Outlined.LockOpen, contentDescription = "Lock screen")
                }
            }
        }
    }
//    Surface(
//        modifier,
//        shape = MaterialTheme.shapes.small,
//        shadowElevation = 1.dp,
//    ) {
//        IconButton(
//            onClick = onClick,
//        ) {
//            if (isLocked) {
//                Icon(Icons.Rounded.Lock, contentDescription = "Lock screen")
//            } else {
//                Icon(Icons.Rounded.LockOpen, contentDescription = "Unlock screen")
//            }
//        }
//    }
}

/**
 * Handles click events and auto-hide controller.
 *
 * @see LockableVideoGestureHost
 */
@Composable
fun LockedScreenGestureHost(
    controllerVisibility: () -> ControllerVisibility,
    setFullVisible: (visible: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clickable(
                remember { MutableInteractionSource() },
                indication = null,
                onClick = { setFullVisible(true) },
            ).fillMaxSize(),
    )

    if (controllerVisibility() == ControllerVisibility.Visible) {
        LaunchedEffect(true) {
            delay(2.seconds)
            setFullVisible(false)
        }
    }
    return
}


@Composable
fun LockableVideoGestureHost(
    controllerState: PlayerControllerState,
    seekerState: SwipeSeekerState,
    progressSliderState: PlayerProgressSliderState,
    playerState: MediampPlayer,
    locked: Boolean,
    enableSwipeToSeek: Boolean,
    audioController: LevelController,
    brightnessController: LevelController,
    modifier: Modifier = Modifier,
    onTogglePauseResume: () -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    onExitFullscreen: () -> Unit = {},
    family: GestureFamily = LocalPlatform.current.mouseFamily,
    gestureIndicatorState: GestureIndicatorState = rememberGestureIndicatorState(),
    fastSkipState: FastSkipState = rememberPlayerFastSkipState(
        playerState = playerState.features.getOrFail(PlaybackSpeed),
        gestureIndicatorState,
    ),
) {
    if (locked) {
        LockedScreenGestureHost(
            { controllerState.visibility },
            controllerState.setFullVisible,
            modifier.testTag("LockedScreenGestureHost"),
        )
    } else {
        PlayerGestureHost(
            controllerState,
            seekerState,
            progressSliderState,
            gestureIndicatorState,
            fastSkipState,
            playerState,
            enableSwipeToSeek,
            audioController,
            brightnessController,
            modifier,
            onTogglePauseResume = onTogglePauseResume,
            onToggleFullscreen = onToggleFullscreen,
            onExitFullscreen = onExitFullscreen,
            family = family,
        )
    }
}
