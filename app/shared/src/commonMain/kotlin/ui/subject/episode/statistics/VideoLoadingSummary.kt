/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.statistics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.rememberAsyncHandler
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_close
import me.him188.ani.app.ui.lang.subject_episode_statistics_cancelled
import me.him188.ani.app.ui.lang.subject_episode_statistics_copy
import me.him188.ani.app.ui.lang.subject_episode_statistics_error
import me.him188.ani.app.ui.lang.subject_episode_statistics_error_details
import me.him188.ani.app.ui.lang.subject_episode_statistics_network_error
import me.him188.ani.app.ui.lang.subject_episode_statistics_no_matching_file
import me.him188.ani.app.ui.lang.subject_episode_statistics_resolution_timed_out
import me.him188.ani.app.ui.lang.subject_episode_statistics_unknown_error_tap
import me.him188.ani.app.ui.lang.subject_episode_statistics_unsupported_media
import org.jetbrains.compose.resources.stringResource


@Composable
fun SimpleErrorDialog(
    text: () -> String,
    onDismissRequest: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberAsyncHandler()
    val copyText = stringResource(Lang.subject_episode_statistics_copy)
    val closeText = stringResource(Lang.settings_mediasource_close)
    val errorDetailsText = stringResource(Lang.subject_episode_statistics_error_details)
    val copy: () -> Unit = {
        scope.launch {
            clipboard.setClipEntryText(text())
        }
    }
    AlertDialog(
        onDismissRequest,
        confirmButton = {
            TextButton(copy) {
                Text(copyText)
            }
        },
        dismissButton = {
            TextButton(onDismissRequest) {
                Text(closeText)
            }
        },
        title = { Text(errorDetailsText) },
        text = {
            OutlinedTextField(
                value = text(),
                onValueChange = {},
                trailingIcon = {
                    IconButton(copy) {
                        Icon(Icons.Outlined.ContentCopy, copyText)
                    }
                },
                readOnly = true,
                maxLines = 4,
            )
        },
    )
}

@Composable
fun VideoLoadingSummary(
    state: VideoLoadingState,
    color: Color = MaterialTheme.colorScheme.error,
) {
    if (state is VideoLoadingState.Failed) {
        val errorText = stringResource(Lang.subject_episode_statistics_error)
        val noMatchingFileText = stringResource(Lang.subject_episode_statistics_no_matching_file)
        val resolutionTimedOutText = stringResource(Lang.subject_episode_statistics_resolution_timed_out)
        val unsupportedMediaText = stringResource(Lang.subject_episode_statistics_unsupported_media)
        val unknownErrorTapText = stringResource(Lang.subject_episode_statistics_unknown_error_tap)
        val cancelledText = stringResource(Lang.subject_episode_statistics_cancelled)
        val networkErrorText = stringResource(Lang.subject_episode_statistics_network_error)
        ProvideContentColor(color) {
            var showErrorDialog by rememberSaveable(state) { mutableStateOf(false) }
            if (showErrorDialog) {
                val text = remember(state) {
                    when (state) {
                        is VideoLoadingState.UnknownError -> state.cause.stackTraceToString()
                        else -> state.toString()
                    }
                }
                SimpleErrorDialog({ text }) { showErrorDialog = false }
            }
            Row(
                Modifier.ifThen(state is VideoLoadingState.UnknownError) {
                    clickable { showErrorDialog = true }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.align(Alignment.Top)
                        .minimumInteractiveComponentSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        errorText,
                    )
                }

                when (state) {
                    VideoLoadingState.NoMatchingFile -> Text(noMatchingFileText)
                    VideoLoadingState.ResolutionTimedOut -> Text(resolutionTimedOutText)
                    VideoLoadingState.UnsupportedMedia -> Text(unsupportedMediaText)
                    is VideoLoadingState.UnknownError -> {
                        Text(unknownErrorTapText)
                    }

                    VideoLoadingState.Cancelled -> Text(cancelledText)
                    VideoLoadingState.NetworkError -> Text(networkErrorText)
                }
            }
        }
    }
}
