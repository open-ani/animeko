/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.episode.controls

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.video_player_speed
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionStepper
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TvSpeedControl(
    speed: Float,
    minSpeed: Float,
    maxSpeed: Float,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TvOptionStepper(
        label = stringResource(Lang.video_player_speed),
        valueDescription = formatSpeedLabel(speed),
        canDecrease = speed > minSpeed,
        canIncrease = speed < maxSpeed,
        onStep = onStep,
        modifier = modifier.testTag("tv-speed-control"),
    )
}
