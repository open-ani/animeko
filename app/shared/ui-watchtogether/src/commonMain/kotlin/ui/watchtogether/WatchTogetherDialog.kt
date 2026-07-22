/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.watchtogether

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import me.him188.ani.app.data.network.WatchTogetherJoinFailure
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.watch_together_cancel
import me.him188.ani.app.ui.lang.watch_together_chip_following
import me.him188.ani.app.ui.lang.watch_together_chip_free
import me.him188.ani.app.ui.lang.watch_together_collapse
import me.him188.ani.app.ui.lang.watch_together_confirm_disband
import me.him188.ani.app.ui.lang.watch_together_connection_connected
import me.him188.ani.app.ui.lang.watch_together_connection_degraded
import me.him188.ani.app.ui.lang.watch_together_connection_reconnecting
import me.him188.ani.app.ui.lang.watch_together_disable
import me.him188.ani.app.ui.lang.watch_together_disband
import me.him188.ani.app.ui.lang.watch_together_disconnected
import me.him188.ani.app.ui.lang.watch_together_episode_label
import me.him188.ani.app.ui.lang.watch_together_error_invalid_name
import me.him188.ani.app.ui.lang.watch_together_error_invalid_password
import me.him188.ani.app.ui.lang.watch_together_error_rate_limited
import me.him188.ani.app.ui.lang.watch_together_error_room_closed
import me.him188.ani.app.ui.lang.watch_together_error_room_full
import me.him188.ani.app.ui.lang.watch_together_error_temporary
import me.him188.ani.app.ui.lang.watch_together_error_wrong_password
import me.him188.ani.app.ui.lang.watch_together_follow_host
import me.him188.ani.app.ui.lang.watch_together_follow_host_desc
import me.him188.ani.app.ui.lang.watch_together_host
import me.him188.ani.app.ui.lang.watch_together_host_desc
import me.him188.ani.app.ui.lang.watch_together_host_empty_self
import me.him188.ani.app.ui.lang.watch_together_host_idle
import me.him188.ani.app.ui.lang.watch_together_idle
import me.him188.ani.app.ui.lang.watch_together_join
import me.him188.ani.app.ui.lang.watch_together_join_failed
import me.him188.ani.app.ui.lang.watch_together_join_helper
import me.him188.ani.app.ui.lang.watch_together_join_subtitle
import me.him188.ani.app.ui.lang.watch_together_joining
import me.him188.ani.app.ui.lang.watch_together_leave
import me.him188.ani.app.ui.lang.watch_together_login
import me.him188.ani.app.ui.lang.watch_together_login_description
import me.him188.ani.app.ui.lang.watch_together_login_required
import me.him188.ani.app.ui.lang.watch_together_member_offline
import me.him188.ani.app.ui.lang.watch_together_member_watching
import me.him188.ani.app.ui.lang.watch_together_members_count
import me.him188.ani.app.ui.lang.watch_together_members_label
import me.him188.ani.app.ui.lang.watch_together_minimize
import me.him188.ani.app.ui.lang.watch_together_password
import me.him188.ani.app.ui.lang.watch_together_room_name
import me.him188.ani.app.ui.lang.watch_together_show_password
import me.him188.ani.app.ui.lang.watch_together_state_buffering
import me.him188.ani.app.ui.lang.watch_together_state_loading
import me.him188.ani.app.ui.lang.watch_together_state_paused
import me.him188.ani.app.ui.lang.watch_together_state_playing
import me.him188.ani.app.ui.lang.watch_together_title
import me.him188.ani.app.ui.lang.watch_together_watching
import me.him188.ani.app.ui.lang.watch_together_you
import me.him188.ani.app.ui.lang.watch_together_you_are_host
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
    Dialog(onDismissRequest = onDismissRequest) {
        WatchTogetherDialogContent(state, onIntent, onLogin, onDismissRequest)
    }
}

/** Content of [WatchTogetherDialog], extracted so previews can render it without a dialog window. */
@Composable
internal fun WatchTogetherDialogContent(
    state: WatchTogetherUiState,
    onIntent: (WatchTogetherIntent) -> Unit,
    onLogin: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var roomName by rememberSaveable { mutableStateOf(state.joinForm.lastRoomName) }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmDisband by remember { mutableStateOf(false) }

    Box(modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .heightIn(max = 720.dp)
                .testTag(WATCH_TOGETHER_DIALOG_TEST_TAG),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                when {
                    state.requiresLogin -> LoginRequiredContent(onLogin, onDismissRequest)
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
                        onDisable = { onIntent(WatchTogetherIntent.DisableFeature) },
                        onDismiss = onDismissRequest,
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
private fun LoginRequiredContent(onLogin: () -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Rounded.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = stringResource(Lang.watch_together_login_required),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = stringResource(Lang.watch_together_login_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Lang.watch_together_login))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 6.dp)) {
            Text(stringResource(Lang.watch_together_cancel))
        }
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
    onDisable: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Rounded.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = stringResource(Lang.watch_together_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = stringResource(Lang.watch_together_join_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )
    }
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
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag(WATCH_TOGETHER_PASSWORD_TEST_TAG),
    )
    Text(
        text = stringResource(Lang.watch_together_join_helper),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 14.dp),
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
            modifier = Modifier.padding(top = 8.dp, start = 14.dp),
        )
    }
    Button(
        onClick = onJoin,
        enabled = !joining && roomName.isNotBlank() && password.isNotBlank(),
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp).testTag(WATCH_TOGETHER_JOIN_TEST_TAG),
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDisable) {
            Text(stringResource(Lang.watch_together_disable), color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = onDismiss) {
            Text(stringResource(Lang.watch_together_minimize))
        }
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(
                room.roomName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ConnectionChip(room.connection, Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                Icons.Rounded.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                stringResource(Lang.watch_together_members_count, room.members.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (room.playback != null) {
        NowPlayingCard(room.playback, Modifier.padding(top = 16.dp))
    } else {
        HostIdleCard(state.isSelfHost, Modifier.padding(top = 16.dp))
    }

    Text(
        stringResource(Lang.watch_together_members_label, room.members.size),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier,
        ) {
            room.members.forEachIndexed { index, presentation ->
                MemberRow(
                    presentation,
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                )
                if (index != room.members.size - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
                }
            }
        }

    }

    if (state.isSelfHost) {
        HostIdentityRow(Modifier.padding(top = 12.dp))
    } else {
        FollowSwitchRow(
            following = state.following,
            onFollowingChange = onFollowingChange,
            modifier = Modifier.padding(top = 12.dp),
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onLeave) {
            Text(
                stringResource(
                    if (state.isSelfHost) Lang.watch_together_disband else Lang.watch_together_leave,
                ),
                color = MaterialTheme.colorScheme.error,
            )
        }
        TextButton(onClick = onDisable) {
            Text(stringResource(Lang.watch_together_disable), color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = onCollapse) {
            Text(stringResource(Lang.watch_together_collapse))
        }
    }
}

@Composable
private fun ConnectionChip(connection: WatchTogetherConnectionPresentation, modifier: Modifier = Modifier) {
    val good = connection == WatchTogetherConnectionPresentation.CONNECTED
    val container = if (good) watchTogetherGoodContainerColor() else MaterialTheme.colorScheme.errorContainer
    val content = if (good) watchTogetherGoodColor() else MaterialTheme.colorScheme.onErrorContainer
    Surface(shape = RoundedCornerShape(999.dp), color = container, contentColor = content, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(content))
            Text(
                text = stringResource(
                    when (connection) {
                        WatchTogetherConnectionPresentation.CONNECTED -> Lang.watch_together_connection_connected
                        WatchTogetherConnectionPresentation.RECONNECTING -> Lang.watch_together_connection_reconnecting
                        WatchTogetherConnectionPresentation.DEGRADED -> Lang.watch_together_connection_degraded
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun NowPlayingCard(playback: WatchTogetherPlaybackPresentation, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 64.dp, height = 88.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primary,
                            ),
                        ),
                    ),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    playback.subjectName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(Lang.watch_together_episode_label, playback.episodeSort, playback.episodeName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
                val (stateIcon, stateText) = playback.stateIconAndText()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(
                        stateIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        stateText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (!playback.loading) Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    val progress = if (playback.durationMillis > 0L) {
                        (playback.positionMillis.toFloat() / playback.durationMillis).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                        )
                    }
                    Text(
                        "${playback.positionMillis.formatDuration()} / ${playback.durationMillis.formatDuration()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchTogetherPlaybackPresentation.stateIconAndText(): Pair<ImageVector, String> = when {
    loading -> Icons.Rounded.Downloading to stringResource(Lang.watch_together_state_loading)
    buffering -> Icons.Rounded.HourglassEmpty to stringResource(Lang.watch_together_state_buffering)
    paused -> Icons.Rounded.Pause to stringResource(Lang.watch_together_state_paused)
    else -> Icons.Rounded.PlayArrow to stringResource(Lang.watch_together_state_playing)
}

@Composable
private fun HostIdleCard(isSelfHost: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Tv,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp),
            )
            Text(
                stringResource(
                    if (isSelfHost) Lang.watch_together_host_empty_self else Lang.watch_together_host_idle,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MemberRow(
    member: WatchTogetherMemberPresentation,
    modifier: Modifier = Modifier,
) {
    val disconnected = member.state == WatchTogetherMemberPresence.DISCONNECTED
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarImage(
            member.avatarUrl,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .alpha(if (disconnected) 0.45f else 1f),
        )
        Column(Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(if (disconnected) 0.6f else 1f),
            ) {
                Text(
                    member.nickname,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (member.isSelf) {
                    Text(
                        stringResource(Lang.watch_together_you),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    member.isHost -> MemberChip(
                        text = stringResource(Lang.watch_together_host),
                        icon = Icons.Rounded.Star,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )

                    member.following -> MemberChip(
                        text = stringResource(Lang.watch_together_chip_following),
                        icon = Icons.Rounded.Check,
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )

                    else -> MemberChip(
                        text = stringResource(Lang.watch_together_chip_free),
                        icon = null,
                        container = Color.Transparent,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                        outlined = true,
                    )
                }
            }
            Text(
                text = member.statusText(),
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    disconnected -> MaterialTheme.colorScheme.error
                    member.state == WatchTogetherMemberPresence.WATCHING -> watchTogetherGoodColor()
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun MemberChip(
    text: String,
    icon: ImageVector?,
    container: Color,
    content: Color,
    outlined: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = container,
        contentColor = content,
        modifier = if (outlined) {
            Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
        } else {
            Modifier
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(11.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FollowSwitchRow(
    following: Boolean,
    onFollowingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Lang.watch_together_follow_host),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(Lang.watch_together_follow_host_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            Switch(
                checked = following,
                onCheckedChange = onFollowingChange,
                modifier = Modifier.testTag(WATCH_TOGETHER_FOLLOW_TEST_TAG),
            )
        }
    }
}

@Composable
private fun HostIdentityRow(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().alpha(0.85f),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                stringResource(Lang.watch_together_you_are_host),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(Lang.watch_together_host_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

@Composable
private fun WatchTogetherMemberPresentation.statusText(): String = when (state) {
    WatchTogetherMemberPresence.DISCONNECTED -> {
        val minutes = disconnectedMinutes
        if (minutes != null && minutes > 0) {
            stringResource(Lang.watch_together_member_offline, minutes)
        } else {
            stringResource(Lang.watch_together_disconnected)
        }
    }

    WatchTogetherMemberPresence.IDLE -> stringResource(Lang.watch_together_idle)
    WatchTogetherMemberPresence.WATCHING -> {
        val watching = watching
        when {
            watching == null -> stringResource(Lang.watch_together_watching)
            watching.loading -> stringResource(
                Lang.watch_together_member_watching,
                watching.episodeSort,
                stringResource(Lang.watch_together_state_loading),
            )

            else -> stringResource(
                Lang.watch_together_member_watching,
                watching.episodeSort,
                watching.positionMillis.formatDuration(),
            )
        }
    }
}

/**
 * The design's "good" (green) role — Material 3 has no success color, so these mirror the
 * mockup values for light/dark themes.
 */
@Composable
internal fun watchTogetherGoodColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFA5D399) else Color(0xFF2E6B27)

@Composable
internal fun watchTogetherGoodContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF1F3A1B) else Color(0xFFDCEDD8)

private fun Long.formatDuration(): String {
    val totalSeconds = (coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}
