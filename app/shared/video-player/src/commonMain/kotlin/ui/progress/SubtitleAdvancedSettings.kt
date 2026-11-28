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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import me.him188.ani.app.data.models.preference.SubtitleStyle
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_close
import me.him188.ani.app.ui.lang.subtitle_style_background
import me.him188.ani.app.ui.lang.subtitle_style_background_color
import me.him188.ani.app.ui.lang.subtitle_style_color_hint
import me.him188.ani.app.ui.lang.subtitle_style_delay
import me.him188.ani.app.ui.lang.subtitle_style_delay_hint
import me.him188.ani.app.ui.lang.subtitle_style_limits
import me.him188.ani.app.ui.lang.subtitle_style_opacity
import me.him188.ani.app.ui.lang.subtitle_style_outline
import me.him188.ani.app.ui.lang.subtitle_style_outline_color
import me.him188.ani.app.ui.lang.subtitle_style_outline_width
import me.him188.ani.app.ui.lang.subtitle_style_position
import me.him188.ani.app.ui.lang.subtitle_style_reset
import me.him188.ani.app.ui.lang.subtitle_style_text_color
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SubtitleAdvancedSettings(
    style: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    delayMillis: Long,
    onDelayChange: ((Long) -> Unit)?,
) {
    val current = style.normalized()
    fun change(next: SubtitleStyle) = onStyleChange(next.copy(overrideEmbeddedStyle = true).normalized())
    HorizontalDivider()
    if (onDelayChange != null) {
        var delayInput by remember { mutableStateOf((delayMillis / 1000.0).toString()) }
        LaunchedEffect(delayMillis) {
            if (delayInput.toDoubleOrNull()?.takeIf { it.isFinite() }?.times(1000)?.roundToInt()?.toLong() != delayMillis) {
                delayInput = (delayMillis / 1000.0).toString()
            }
        }
        val seconds = delayInput.toDoubleOrNull()
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onDelayChange((delayMillis - 100).coerceAtLeast(-600_000)) }, modifier = Modifier.testTag("SubtitleDelayEarlier")) { Text("−") }
            OutlinedTextField(
                delayInput,
                { input ->
                    delayInput = input
                    input.toDoubleOrNull()?.takeIf { it.isFinite() && it in -600.0..600.0 }
                        ?.let { onDelayChange((it * 1000).roundToInt().toLong()) }
                },
                label = { Text(stringResource(Lang.subtitle_style_delay)) },
                isError = seconds == null || !seconds.isFinite() || seconds !in -600.0..600.0,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), singleLine = true,
                modifier = Modifier.weight(1f).testTag("SubtitleDelayInput"),
            )
            TextButton(onClick = { onDelayChange((delayMillis + 100).coerceAtMost(600_000)) }, modifier = Modifier.testTag("SubtitleDelayLater")) { Text("+") }
        }
        Text(stringResource(Lang.subtitle_style_delay_hint), style = MaterialTheme.typography.bodySmall)
        ResetSubtitleSetting { onDelayChange(0) }
    }
    SubtitleStyleSlider(stringResource(Lang.subtitle_style_position), "${(current.verticalPosition * 100).roundToInt()}%",
        current.verticalPosition, 0f..1f, "SubtitlePosition", { change(current.copy(verticalPosition = it)) }, { change(current.copy(verticalPosition = 1f)) })
    SubtitleColorSetting(stringResource(Lang.subtitle_style_text_color), current.textColor, "SubtitleTextColor",
        { change(current.copy(textColor = it)) }, { change(current.copy(textColor = SubtitleStyle.Default.textColor)) })
    SubtitleColorSetting(stringResource(Lang.subtitle_style_outline_color), current.outlineColor, "SubtitleOutlineColor",
        { change(current.copy(outlineColor = it)) }, { change(current.copy(outlineColor = SubtitleStyle.Default.outlineColor)) })
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Lang.subtitle_style_outline), Modifier.weight(1f))
        Switch(current.outlineWidth > 0, { change(current.copy(outlineWidth = if (it) 2f else 0f)) }, Modifier.testTag("SubtitleOutlineEnabled"))
    }
    if (current.outlineWidth > 0) {
        SubtitleStyleSlider(stringResource(Lang.subtitle_style_outline_width), "${(current.outlineWidth * 10).roundToInt() / 10f}",
            current.outlineWidth, 0f..8f, "SubtitleOutlineWidth", { change(current.copy(outlineWidth = it)) }, { change(current.copy(outlineWidth = 2f)) })
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Lang.subtitle_style_background), Modifier.weight(1f))
        Switch(current.backgroundEnabled, { change(current.copy(backgroundEnabled = it)) }, Modifier.testTag("SubtitleBackgroundEnabled"))
        ResetSubtitleSetting { change(current.copy(backgroundEnabled = false, backgroundColor = 0xFF000000, backgroundOpacity = 0.6f)) }
    }
    if (current.backgroundEnabled) {
        SubtitleColorSetting(stringResource(Lang.subtitle_style_background_color), current.backgroundColor, "SubtitleBackgroundColor",
            { change(current.copy(backgroundColor = it)) }, { change(current.copy(backgroundColor = 0xFF000000)) })
        SubtitleStyleSlider(stringResource(Lang.subtitle_style_opacity), "${(current.backgroundOpacity * 100).roundToInt()}%",
            current.backgroundOpacity, 0f..1f, "SubtitleBackgroundOpacity", { change(current.copy(backgroundOpacity = it)) }, { change(current.copy(backgroundOpacity = 0.6f)) })
    }
    Text(stringResource(Lang.subtitle_style_limits), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ResetSubtitleSetting(onClick: () -> Unit) {
    TextButton(onClick) { Text(stringResource(Lang.subtitle_style_reset)) }
}

@Composable
private fun SubtitleStyleSlider(
    label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, tag: String,
    onChange: (Float) -> Unit, onReset: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("$label · $valueText", Modifier.weight(1f))
        ResetSubtitleSetting(onReset)
    }
    Slider(value, onChange, valueRange = range, modifier = Modifier.testTag(tag))
}

@Composable
private fun SubtitleColorSetting(label: String, color: Long, tag: String, onChange: (Long) -> Unit, onReset: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton({ open = true }, Modifier.weight(1f).testTag(tag)) {
            Box(Modifier.size(20.dp).background(Color(color)))
            Text("  $label")
        }
        ResetSubtitleSetting(onReset)
    }
    if (open) {
        var hex by remember { mutableStateOf(color.toString(16).takeLast(6).uppercase()) }
        AlertDialog(
            onDismissRequest = { open = false }, title = { Text(label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (preset in listOf(0xFFFFFFFF, 0xFF000000, 0xFFFFFF00, 0xFFFFD54F, 0xFF00FFFF, 0xFF00FF00, 0xFFFF8080)) {
                            Button({ hex = preset.toString(16).takeLast(6).uppercase(); onChange(preset) }, Modifier.testTag("SubtitleColor-${preset.toString(16)}")) {
                                Box(Modifier.size(24.dp).background(Color(preset)))
                            }
                        }
                    }
                    OutlinedTextField(hex, { input ->
                        hex = input.removePrefix("#").uppercase()
                        if (hex.length == 6) hex.toLongOrNull(16)?.takeIf { it in 0..0xFFFFFF }?.let { onChange(it or 0xFF000000) }
                    }, label = { Text(stringResource(Lang.subtitle_style_color_hint)) }, singleLine = true,
                        isError = hex.length != 6 || hex.toLongOrNull(16) == null,
                        modifier = Modifier.testTag("SubtitleColorHex"))
                }
            },
            confirmButton = { TextButton({ open = false }, Modifier.testTag("SubtitleColorClose")) { Text(stringResource(Lang.subject_episode_close)) } },
        )
    }
}
