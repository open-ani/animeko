/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import me.him188.ani.app.videoplayer.ui.guesture.GestureIndicator
import me.him188.ani.app.videoplayer.ui.guesture.rememberGestureIndicatorState


@Composable
private fun SeekPositionIndicator(
    deltaDuration: Int,
) {
    GestureIndicator(
        state = rememberGestureIndicatorState().apply {
            LaunchedEffect(key1 = true) {
                showSeeking(deltaDuration)
            }
        },
    )
}

@PreviewLightDark
@Composable
private fun PreviewSeekPositionIndicatorForward() {
    Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Transparent),
        ) {

        }
        SeekPositionIndicator(deltaDuration = 10)
    }
}

@PreviewLightDark
@Composable
private fun PreviewSeekPositionIndicatorBackward() {
    Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Transparent),
        ) {

        }
        SeekPositionIndicator(deltaDuration = -10)
    }
}

@PreviewLightDark
@Composable
private fun PreviewSeekPositionIndicatorBackwardMinutes() {
    Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Transparent),
        ) {

        }
        SeekPositionIndicator(deltaDuration = -90)
    }
}


@Preview
@Composable
private fun PreviewPaused() {
    GestureIndicator(
        state = rememberGestureIndicatorState().apply {
            LaunchedEffect(key1 = true) {
                showPausedLong()
            }
        },
    )
}

@Preview
@Composable
private fun PreviewVolume() {
    GestureIndicator(
        state = rememberGestureIndicatorState().apply {
            LaunchedEffect(key1 = true) {
                showVolumeRange(0.6f)
            }
        },
    )
}