/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_close
import me.him188.ani.app.ui.lang.video_player_subtitle_font_size
import me.him188.ani.app.ui.lang.video_player_subtitle_preview
import me.him188.ani.app.ui.lang.video_player_subtitle_reset
import me.him188.ani.app.ui.lang.video_player_subtitle_settings
import me.him188.ani.app.videoplayer.subtitle.SubtitleAppearance
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun SubtitleSettingsDialog(fontScale: Float, onFontScaleChange: (Float) -> Unit, onDismissRequest: () -> Unit) {
    var scale by remember(fontScale) { mutableFloatStateOf(SubtitleAppearance(fontScale).effectiveFontScale) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Lang.video_player_subtitle_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Lang.video_player_subtitle_font_size, (scale * 100).roundToInt()))
                Slider(
                    value = scale,
                    onValueChange = { scale = (it * 4).roundToInt() / 4f },
                    onValueChangeFinished = { onFontScaleChange(scale) },
                    valueRange = 0.75f..2f,
                    steps = 4,
                    modifier = Modifier.testTag("SubtitleFontScale"),
                )
                Text(
                    stringResource(Lang.video_player_subtitle_preview),
                    modifier = Modifier.fillMaxWidth().clipToBounds().background(Color.Black)
                        .padding(horizontal = 16.dp, vertical = 20.dp).testTag("SubtitleSizePreview"),
                    color = Color.White,
                    fontSize = (22 * scale).sp,
                    lineHeight = (28 * scale).sp,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onFontScaleChange(scale); onDismissRequest() }) {
                Text(stringResource(Lang.subject_episode_close))
            }
        },
        dismissButton = {
            TextButton(onClick = { scale = 1f; onFontScaleChange(1f) }, modifier = Modifier.testTag("ResetSubtitleSize")) {
                Text(stringResource(Lang.video_player_subtitle_reset))
            }
        },
    )
}
