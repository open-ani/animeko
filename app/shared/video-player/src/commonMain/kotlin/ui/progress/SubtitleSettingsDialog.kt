/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import me.him188.ani.app.data.models.preference.SubtitleStyle
import me.him188.ani.app.data.models.preference.SubtitleTextConversion
import me.him188.ani.app.domain.player.SubtitleTextConverter
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_close
import me.him188.ani.app.ui.lang.subtitle_style_reset
import me.him188.ani.app.ui.lang.subtitle_style_reset_all
import me.him188.ani.app.ui.lang.video_player_subtitle_conversion
import me.him188.ani.app.ui.lang.video_player_subtitle_conversion_description
import me.him188.ani.app.ui.lang.video_player_subtitle_font_size
import me.him188.ani.app.ui.lang.video_player_subtitle_font_size_hint
import me.him188.ani.app.ui.lang.video_player_subtitle_font_size_input
import me.him188.ani.app.ui.lang.video_player_subtitle_original
import me.him188.ani.app.ui.lang.video_player_subtitle_preview
import me.him188.ani.app.ui.lang.video_player_subtitle_settings
import me.him188.ani.app.ui.lang.video_player_subtitle_simplified
import me.him188.ani.app.ui.lang.video_player_subtitle_traditional
import me.him188.ani.app.videoplayer.subtitle.SubtitleAppearance
import me.him188.ani.app.videoplayer.subtitle.SubtitleStyledText
import org.jetbrains.compose.resources.stringResource

@Composable
fun SubtitleSettingsDialog(
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    onDismissRequest: () -> Unit,
    textConversion: SubtitleTextConversion = SubtitleTextConversion.ORIGINAL,
    onTextConversionChange: (SubtitleTextConversion) -> Unit = {},
    style: SubtitleStyle = SubtitleStyle.Default,
    onStyleChange: (SubtitleStyle) -> Unit = {},
    delayMillis: Long = 0L,
    onDelayChange: ((Long) -> Unit)? = null,
) {
    var scale by remember(fontScale) { mutableFloatStateOf(SubtitleAppearance(fontScale).effectiveFontScale) }
    var input by remember(fontScale) { mutableStateOf(formatSubtitleFontSize(SubtitleAppearance(fontScale).fontSize)) }
    val size = input.toFloatOrNull()
    val validSize = size != null && size.isFinite() && size in SubtitleAppearance.MinFontSize..SubtitleAppearance.MaxFontSize
    val converter = remember { SubtitleTextConverter() }
    val originalPreview = stringResource(Lang.video_player_subtitle_preview)
    val preview by produceState(originalPreview, originalPreview, textConversion) {
        value = converter.convert(originalPreview, textConversion)
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Lang.video_player_subtitle_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().height(132.dp).background(Color(0xFF303030)).padding(8.dp).testTag("SubtitleSizePreview"),
                    contentAlignment = BiasAlignment(0f, style.normalized().verticalPosition * 2 - 1)) {
                    SubtitleStyledText(preview, (22 * scale).sp, SubtitleAppearance(scale, style))
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Lang.video_player_subtitle_font_size, "${(scale * 100).roundToInt()}% · ${(scale * 100).roundToInt() / 100f}×"), Modifier.testTag("SubtitleSizeLabel"))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { value ->
                            input = value
                            value.toFloatOrNull()?.takeIf {
                                it.isFinite() && it in SubtitleAppearance.MinFontSize..SubtitleAppearance.MaxFontSize
                            }?.let { scale = it / SubtitleAppearance.DefaultFontSize; onFontScaleChange(scale) }
                        },
                        label = { Text(stringResource(Lang.video_player_subtitle_font_size_input)) },
                        supportingText = { Text(stringResource(Lang.video_player_subtitle_font_size_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = !validSize,
                        modifier = Modifier.fillMaxWidth().testTag("SubtitleFontSizeInput"),
                    )
                    Slider(
                        value = scale,
                        onValueChange = {
                            scale = (it * 100).roundToInt() / 100f
                            input = formatSubtitleFontSize(scale * SubtitleAppearance.DefaultFontSize)
                            onFontScaleChange(scale)
                        },
                        onValueChangeFinished = { onFontScaleChange(scale) },
                        valueRange = SubtitleAppearance.MinFontSize / SubtitleAppearance.DefaultFontSize..SubtitleAppearance.MaxFontSize / SubtitleAppearance.DefaultFontSize,
                        modifier = Modifier.testTag("SubtitleFontScale"),
                    )
                    TextButton({ scale = 1f; input = "38"; onFontScaleChange(1f) }, Modifier.testTag("ResetSubtitleFont")) {
                        Text(stringResource(Lang.subtitle_style_reset))
                    }
                    Text(stringResource(Lang.video_player_subtitle_conversion))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (option in SubtitleTextConversion.entries) {
                            FilterChip(
                                selected = option == textConversion,
                                onClick = { onTextConversionChange(option) },
                                label = { Text(stringResource(when (option) {
                                    SubtitleTextConversion.ORIGINAL -> Lang.video_player_subtitle_original
                                    SubtitleTextConversion.SIMPLIFIED -> Lang.video_player_subtitle_simplified
                                    SubtitleTextConversion.TRADITIONAL -> Lang.video_player_subtitle_traditional
                                })) },
                                modifier = Modifier.testTag("SubtitleConversion-${option.name}"),
                            )
                        }
                    }
                    Text(stringResource(Lang.video_player_subtitle_conversion_description), style = MaterialTheme.typography.bodySmall)
                    SubtitleAdvancedSettings(style, onStyleChange, delayMillis, onDelayChange)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onFontScaleChange(scale); onDismissRequest() }, enabled = validSize) {
                Text(stringResource(Lang.subject_episode_close))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                scale = 1f; input = "38"; onFontScaleChange(1f)
                onStyleChange(SubtitleStyle.Default)
                onTextConversionChange(SubtitleTextConversion.ORIGINAL)
                onDelayChange?.invoke(0)
            }, modifier = Modifier.testTag("ResetSubtitleSize")) {
                Text(stringResource(Lang.subtitle_style_reset_all))
            }
        },
    )
}

private fun formatSubtitleFontSize(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else ((value * 10).roundToInt() / 10f).toString()
