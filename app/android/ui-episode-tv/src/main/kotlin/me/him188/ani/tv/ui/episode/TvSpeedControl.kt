/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

private object TvSpeedControlDefaults {
    val ArrowSize = 40.dp
    val IconSize = 24.dp
    val Padding = 16.dp
    const val PulseDurationMillis = 180
}

/** One focus target; the arrows only echo directional input. Speed changes travel upward as intents. */
@Composable
internal fun TvSpeedControl(
    speed: Float,
    minSpeed: Float,
    maxSpeed: Float,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var direction by remember { mutableIntStateOf(0) }
    var pressGeneration by remember { mutableIntStateOf(0) }
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(pressGeneration) {
        if (pressGeneration == 0) return@LaunchedEffect
        pulse.snapTo(1f)
        pulse.animateTo(0f, tween(TvSpeedControlDefaults.PulseDurationMillis))
    }
    Surface(
        onClick = {},
        modifier = modifier.fillMaxWidth()
            .testTag("tv-speed-control")
            .semantics {
                contentDescription = "播放速度"
                stateDescription = formatSpeedLabel(speed)
            }
            .onFocusChanged { if (!it.isFocused) direction = 0 }
            .onPreviewKeyEvent { event ->
                val step = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) {
                    direction = step
                    pressGeneration++
                    onStep(step)
                }
                true
            },
        colors = tvPlayerOptionColors(filled = true),
        shape = ClickableSurfaceDefaults.shape(TvPlayerSurfaceDefaults.ItemShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(TvSpeedControlDefaults.Padding),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpeedArrow(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                speed > minSpeed,
                if (direction < 0) pulse.value else 0f
            )
            Text(
                formatSpeedLabel(speed),
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineMedium,
            )
            SpeedArrow(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                speed < maxSpeed,
                if (direction > 0) pulse.value else 0f
            )
        }
    }
}

@Composable
private fun SpeedArrow(icon: ImageVector, enabled: Boolean, pulse: Float) {
    val color = LocalContentColor.current
    Box(
        Modifier.size(TvSpeedControlDefaults.ArrowSize)
            .clearAndSetSemantics {}
            .graphicsLayer { scaleX = 1f - pulse * .1f; scaleY = scaleX }
            .background(color.copy(alpha = .08f + pulse * .22f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            null,
            Modifier.size(TvSpeedControlDefaults.IconSize),
            tint = color.copy(alpha = if (enabled) 1f else .3f)
        )
    }
}
