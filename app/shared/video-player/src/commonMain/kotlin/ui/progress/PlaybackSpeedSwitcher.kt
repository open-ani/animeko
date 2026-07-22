/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.dialogs.PlatformPopupProperties
import me.him188.ani.app.videoplayer.ui.PlaybackSpeedControllerState
import kotlin.math.roundToInt

@Composable
fun PlaybackSpeedSwitcher(
    playbackSpeedControllerState: PlaybackSpeedControllerState,
    modifier: Modifier = Modifier,
    onExpandedChanged: (expanded: Boolean) -> Unit = {},
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        var expanded by rememberSaveable { mutableStateOf(false) }
        var openCustomDialogAfterDismiss by rememberSaveable { mutableStateOf(false) }
        val currentSpeed = playbackSpeedControllerState.currentSpeed
        val customSpeedInput = playbackSpeedControllerState.customSpeedInput
        val showCustomDialog = playbackSpeedControllerState.isCustomSpeedDialogVisible
        val isValidCustomSpeed = remember(customSpeedInput) {
            customSpeedInput.parseCustomPlaybackSpeed() != null
        }

        LaunchedEffect(expanded, showCustomDialog) {
            onExpandedChanged(expanded || showCustomDialog)
        }

        LaunchedEffect(expanded, openCustomDialogAfterDismiss, currentSpeed) {
            if (!expanded && openCustomDialogAfterDismiss) {
                playbackSpeedControllerState.openCustomSpeedDialog()
                openCustomDialogAfterDismiss = false
            }
        }

        TextButton(
            onClick = { expanded = true },
            colors = ButtonDefaults.textButtonColors(
                contentColor = LocalContentColor.current,
            ),
            modifier = Modifier.testTag(TAG_SPEED_SWITCHER_TEXT_BUTTON),
        ) {
            Text("${currentSpeed.formatPlaybackSpeed()}x")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PlatformPopupProperties(
                clippingEnabled = false,
            ),
            modifier = Modifier.testTag(TAG_SPEED_SWITCHER_DROPDOWN_MENU),
        ) {
            playbackSpeedControllerState.speedList.forEach { speedValue ->
                DropdownMenuItem(
                    text = {
                        val color = if (currentSpeed == speedValue) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            LocalContentColor.current
                        }
                        CompositionLocalProvider(LocalContentColor provides color) {
                            Text("${speedValue.formatPlaybackSpeed()}x")
                        }
                    },
                    onClick = {
                        expanded = false
                        playbackSpeedControllerState.setSpeed(speedValue)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Custom") },
                onClick = {
                    openCustomDialogAfterDismiss = true
                    expanded = false
                },
            )
        }

        if (showCustomDialog) {
            AlertDialog(
                onDismissRequest = { playbackSpeedControllerState.closeCustomSpeedDialog() },
                title = { Text("Custom playback speed") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customSpeedInput,
                            onValueChange = playbackSpeedControllerState::updateCustomSpeedInput,
                            singleLine = true,
                            label = { Text("Speed") },
                            suffix = { Text("x") },
                            isError = customSpeedInput.isNotBlank() && !isValidCustomSpeed,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done,
                            ),
                        )
                        Text(
                            "Enter a value from 0.1 to 5.0 with at most one decimal place.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = isValidCustomSpeed,
                        onClick = {
                            customSpeedInput.parseCustomPlaybackSpeed()?.let {
                                playbackSpeedControllerState.setCustomSpeed(it)
                            }
                            playbackSpeedControllerState.closeCustomSpeedDialog()
                        },
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { playbackSpeedControllerState.closeCustomSpeedDialog() }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

private fun Float.formatPlaybackSpeed(): String {
    return if (this % 1f == 0f) {
        roundToInt().toString()
    } else {
        toString().trimEnd('0').trimEnd('.')
    }
}

private fun String.parseCustomPlaybackSpeed(): Float? {
    val trimmed = trim()
    if (!trimmed.matches(Regex("""\d+(\.\d)?"""))) return null
    val value = trimmed.toFloatOrNull() ?: return null
    return value.takeIf {
        it in PlaybackSpeedControllerState.MIN_CUSTOM_SPEED..PlaybackSpeedControllerState.MAX_CUSTOM_SPEED
    }
}
