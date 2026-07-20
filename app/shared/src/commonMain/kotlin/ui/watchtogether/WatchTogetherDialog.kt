/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.watchtogether

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import me.him188.ani.app.data.network.WatchTogetherJoinFailure
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource

internal const val WATCH_TOGETHER_DIALOG_TEST_TAG = "watch_together_dialog"
internal const val WATCH_TOGETHER_ROOM_NAME_TEST_TAG = "watch_together_room_name"
internal const val WATCH_TOGETHER_PASSWORD_TEST_TAG = "watch_together_password"
internal const val WATCH_TOGETHER_JOIN_TEST_TAG = "watch_together_join"
internal const val WATCH_TOGETHER_FOLLOW_TEST_TAG = "watch_together_follow"

@Composable
internal fun WatchTogetherDialog(
    state: WatchTogetherUiState,
    onIntent: (WatchTogetherIntent) -> Unit,
    onLogin: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    var roomName by rememberSaveable { mutableStateOf(state.joinForm.lastRoomName) }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmDisband by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .heightIn(max = 720.dp)
                .testTag(WATCH_TOGETHER_DIALOG_TEST_TAG),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DialogHeader(onDismissRequest)
                when {
                    state.requiresLogin -> LoginRequiredContent(onLogin)
                    state.phase == WatchTogetherPhase.IN_ROOM && state.room != null -> RoomContent(
                        state = state,
                        onFollowingChange = {
                            onIntent(WatchTogetherIntent.SetFollowing(it))
                        },
                        onLeave = {
                            if (state.isSelfHost) confirmDisband = true
                            else onIntent(WatchTogetherIntent.LeaveRoom)
                        },
                        onDisable = { onIntent(WatchTogetherIntent.DisableFeature) },
                        onCollapse = onDismissRequest,
                    )

                    else -> JoinRoomContent(
                        roomName = roomName,
                        onRoomNameChange = { roomName = it },
                        password = password,
                        onPasswordChange = { password = it },
                        passwordVisible = passwordVisible,
                        onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
                        joining = state.phase == WatchTogetherPhase.JOINING,
                        errorMessage = state.joinForm.errorMessage,
                        onJoin = {
                            onIntent(WatchTogetherIntent.JoinRoom(roomName, password))
                        },
                    )
                }
            }
        }
    }

    if (confirmDisband) {
        AlertDialog(
            onDismissRequest = { confirmDisband = false },
            text = { Text(stringResource(Lang.watch_together_confirm_disband)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDisband = false
                        onIntent(WatchTogetherIntent.LeaveRoom)
                    },
                ) {
                    Text(
                        stringResource(Lang.watch_together_disband),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisband = false }) {
                    Text(stringResource(Lang.watch_together_cancel))
                }
            },
        )
    }
}

@Composable
private fun DialogHeader(onDismissRequest: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(Lang.watch_together_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onDismissRequest) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(Lang.watch_together_collapse))
        }
    }
}

@Composable
private fun LoginRequiredContent(onLogin: () -> Unit) {
    Text(
        text = stringResource(Lang.watch_together_login_required),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = stringResource(Lang.watch_together_login_description),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Lang.watch_together_login))
    }
}

@Composable
private fun JoinRoomContent(
    roomName: String,
    onRoomNameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    joining: Boolean,
    errorMessage: String?,
    onJoin: () -> Unit,
) {
    OutlinedTextField(
        value = roomName,
        onValueChange = onRoomNameChange,
        label = { Text(stringResource(Lang.watch_together_room_name)) },
        singleLine = true,
        enabled = !joining,
        modifier = Modifier.fillMaxWidth().testTag(WATCH_TOGETHER_ROOM_NAME_TEST_TAG),
    )
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(Lang.watch_together_password)) },
        singleLine = true,
        enabled = !joining,
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = onTogglePasswordVisibility) {
                Icon(
                    if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = stringResource(Lang.watch_together_show_password),
                )
            }
        },
        modifier = Modifier.fillMaxWidth().testTag(WATCH_TOGETHER_PASSWORD_TEST_TAG),
    )
    Text(
        text = stringResource(Lang.watch_together_join_helper),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (errorMessage != null) {
        val localizedError = when (errorMessage) {
            WatchTogetherJoinFailure.WRONG_PASSWORD.name -> stringResource(Lang.watch_together_error_wrong_password)
            WatchTogetherJoinFailure.ROOM_FULL.name -> stringResource(Lang.watch_together_error_room_full)
            WatchTogetherJoinFailure.ROOM_CLOSED.name -> stringResource(Lang.watch_together_error_room_closed)
            WatchTogetherJoinFailure.INVALID_NAME.name -> stringResource(Lang.watch_together_error_invalid_name)
            WatchTogetherJoinFailure.INVALID_PASSWORD.name -> stringResource(Lang.watch_together_error_invalid_password)
            WatchTogetherJoinFailure.RATE_LIMITED.name -> stringResource(Lang.watch_together_error_rate_limited)
            WatchTogetherJoinFailure.TEMPORARY.name -> stringResource(Lang.watch_together_error_temporary)
            else -> errorMessage
        }
        Text(
            text = stringResource(Lang.watch_together_join_failed, localizedError),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Button(
        onClick = onJoin,
        enabled = !joining && roomName.isNotBlank() && password.isNotBlank(),
        modifier = Modifier.fillMaxWidth().testTag(WATCH_TOGETHER_JOIN_TEST_TAG),
    ) {
        if (joining) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.size(8.dp))
        }
        Text(
            stringResource(
                if (joining) Lang.watch_together_joining else Lang.watch_together_join,
            ),
        )
    }
}

@Composable
private fun RoomContent(
    state: WatchTogetherUiState,
    onFollowingChange: (Boolean) -> Unit,
    onLeave: () -> Unit,
    onDisable: () -> Unit,
    onCollapse: () -> Unit,
) {
    val room = checkNotNull(state.room)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(room.roomName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        StatusChip(room.connection)
    }

    HostPlaybackCard(room.playback)

    Text(
        stringResource(Lang.watch_together_members_count, room.members.size),
        style = MaterialTheme.typography.titleMedium,
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        room.members.forEach { MemberRow(it) }
    }

    HorizontalDivider()
    if (state.isSelfHost) {
        Text(
            stringResource(Lang.watch_together_you_are_host),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            stringResource(Lang.settings_watch_together_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Lang.watch_together_follow_host), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(
                        if (state.following) Lang.watch_together_following else Lang.watch_together_free_watching,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.following,
                onCheckedChange = onFollowingChange,
                modifier = Modifier.testTag(WATCH_TOGETHER_FOLLOW_TEST_TAG),
            )
        }
    }

    OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(
                if (state.isSelfHost) Lang.watch_together_disband else Lang.watch_together_leave,
            ),
            color = MaterialTheme.colorScheme.error,
        )
    }
    TextButton(onClick = onDisable, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Lang.watch_together_disable))
    }
    TextButton(onClick = onCollapse, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Lang.watch_together_collapse))
    }
}

@Composable
private fun StatusChip(connection: WatchTogetherConnectionPresentation) {
    val isError = connection != WatchTogetherConnectionPresentation.CONNECTED
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
    ) {
        Text(
            text = stringResource(
                when (connection) {
                    WatchTogetherConnectionPresentation.CONNECTED -> Lang.watch_together_connection_connected
                    WatchTogetherConnectionPresentation.RECONNECTING -> Lang.watch_together_connection_reconnecting
                    WatchTogetherConnectionPresentation.DEGRADED -> Lang.watch_together_connection_degraded
                },
            ),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun HostPlaybackCard(playback: WatchTogetherPlaybackPresentation?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(Lang.watch_together_now_playing), style = MaterialTheme.typography.labelLarge)
            if (playback == null) {
                Text(
                    stringResource(Lang.watch_together_host_idle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(playback.subjectName, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOf(playback.episodeSort, playback.episodeName)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val progress = if (playback.durationMillis > 0L) {
                    playback.positionMillis.toFloat() / playback.durationMillis
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${playback.positionMillis.formatDuration()} / ${playback.durationMillis.formatDuration()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MemberRow(member: WatchTogetherMemberPresentation) {
    Row(
        modifier = Modifier.fillMaxWidth().alpha(
            if (member.state == WatchTogetherMemberPresence.DISCONNECTED) 0.55f else 1f,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarImage(
            member.avatarUrl,
            modifier = Modifier.size(40.dp).clip(CircleShape),
        )
        Column(Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(member.nickname, style = MaterialTheme.typography.titleSmall)
                if (member.isHost) {
                    MemberChip(stringResource(Lang.watch_together_host))
                } else {
                    MemberChip(
                        stringResource(
                            if (member.following) {
                                Lang.watch_together_following
                            } else {
                                Lang.watch_together_free_watching
                            },
                        ),
                    )
                }
            }
            Text(
                text = member.statusText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MemberChip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun WatchTogetherMemberPresentation.statusText(): String = when (state) {
    WatchTogetherMemberPresence.DISCONNECTED -> stringResource(Lang.watch_together_disconnected)
    WatchTogetherMemberPresence.IDLE -> stringResource(Lang.watch_together_idle)
    WatchTogetherMemberPresence.WATCHING -> {
        val watching = watching
        if (watching == null) {
            stringResource(Lang.watch_together_watching)
        } else {
            listOf(
                stringResource(Lang.watch_together_watching),
                watching.episodeSort,
                watching.positionMillis.formatDuration(),
            ).filter { it.isNotBlank() }.joinToString(" · ")
        }
    }
}

private fun Long.formatDuration(): String {
    val totalSeconds = (coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
