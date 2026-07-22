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
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview

@Composable
@Preview
private fun PreviewWatchTogetherDialogJoinForm() = ProvideCompositionLocalsForPreview {
    PreviewDialogContent(
        WatchTogetherUiState.Initial.copy(
            featureEnabled = true,
            joinForm = WatchTogetherJoinFormState(lastRoomName = "周五夜放送"),
        ),
    )
}

@Composable
@Preview
private fun PreviewWatchTogetherDialogJoinError() = ProvideCompositionLocalsForPreview {
    PreviewDialogContent(
        WatchTogetherUiState.Initial.copy(
            featureEnabled = true,
            joinForm = WatchTogetherJoinFormState(
                lastRoomName = "周五夜放送",
                errorMessage = "WRONG_PASSWORD",
            ),
        ),
    )
}

@Composable
@Preview
private fun PreviewWatchTogetherDialogJoining() = ProvideCompositionLocalsForPreview {
    PreviewDialogContent(
        WatchTogetherUiState.Initial.copy(
            featureEnabled = true,
            phase = WatchTogetherPhase.JOINING,
            joinForm = WatchTogetherJoinFormState(lastRoomName = "周五夜放送"),
        ),
    )
}

@Composable
@Preview
private fun PreviewWatchTogetherDialogMemberView() = ProvideCompositionLocalsForPreview {
    PreviewDialogContent(
        previewInRoomState(
            isSelfHost = false,
            playback = previewPlayback(),
        ),
    )
}

@Composable
@Preview
private fun PreviewWatchTogetherDialogHostBuffering() = ProvideCompositionLocalsForPreview {
    PreviewDialogContent(
        previewInRoomState(
            isSelfHost = false,
            playback = previewPlayback().copy(paused = true, buffering = true),
            connection = WatchTogetherConnectionPresentation.DEGRADED,
        ),
    )
}

@Composable
@Preview
private fun PreviewWatchTogetherDialogHostViewIdle() = ProvideCompositionLocalsForPreview {
    PreviewDialogContent(
        previewInRoomState(
            isSelfHost = true,
            playback = null,
        ),
    )
}

@Composable
@Preview
private fun PreviewWatchTogetherDialogLoginRequired() = ProvideCompositionLocalsForPreview {
    PreviewDialogContent(
        WatchTogetherUiState.Initial.copy(
            featureEnabled = true,
            requiresLogin = true,
        ),
    )
}

@Composable
@Preview
private fun PreviewWatchTogetherBubbles() = ProvideCompositionLocalsForPreview {
    Column(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 未入房
        WatchTogetherBubble(
            WatchTogetherUiState.Initial.copy(featureEnabled = true),
            onClick = {},
        )
        // 加入中
        WatchTogetherBubble(
            WatchTogetherUiState.Initial.copy(
                featureEnabled = true,
                phase = WatchTogetherPhase.JOINING,
            ),
            onClick = {},
        )
        // 房间内 (SSE 已连接)
        WatchTogetherBubble(previewInRoomState(isSelfHost = false, playback = previewPlayback()), onClick = {})
        // 重连中
        WatchTogetherBubble(
            previewInRoomState(
                isSelfHost = false,
                playback = previewPlayback(),
                connection = WatchTogetherConnectionPresentation.RECONNECTING,
            ),
            onClick = {},
        )
        // 降级轮询
        WatchTogetherBubble(
            previewInRoomState(
                isSelfHost = false,
                playback = previewPlayback(),
                connection = WatchTogetherConnectionPresentation.DEGRADED,
            ),
            onClick = {},
        )
    }
}

@Composable
private fun PreviewDialogContent(state: WatchTogetherUiState) {
    WatchTogetherDialogContent(
        state = state,
        onIntent = {},
        onLogin = {},
        onDismissRequest = {},
        modifier = Modifier.padding(16.dp),
    )
}

private fun previewPlayback() = WatchTogetherPlaybackPresentation(
    subjectName = "葬送的芙莉莲",
    episodeSort = "12",
    episodeName = "真正的勇者",
    positionMillis = 754_000L,
    durationMillis = 1_440_000L,
    paused = false,
)

private fun previewInRoomState(
    isSelfHost: Boolean,
    playback: WatchTogetherPlaybackPresentation?,
    connection: WatchTogetherConnectionPresentation = WatchTogetherConnectionPresentation.CONNECTED,
) = WatchTogetherUiState(
    featureEnabled = true,
    phase = WatchTogetherPhase.IN_ROOM,
    joinForm = WatchTogetherJoinFormState(lastRoomName = "周五夜放送"),
    room = WatchTogetherRoomCardState(
        roomName = "周五夜放送",
        connection = connection,
        playback = playback,
        members = listOf(
            WatchTogetherMemberPresentation(
                userId = "host",
                nickname = "Alice",
                avatarUrl = null,
                isHost = true,
                isSelf = isSelfHost,
                following = false,
                state = if (playback != null) {
                    WatchTogetherMemberPresence.WATCHING
                } else {
                    WatchTogetherMemberPresence.IDLE
                },
                watching = playback,
            ),
            WatchTogetherMemberPresentation(
                userId = "member-1",
                nickname = "Bob",
                avatarUrl = null,
                isHost = false,
                isSelf = !isSelfHost,
                following = true,
                state = if (playback != null) {
                    WatchTogetherMemberPresence.WATCHING
                } else {
                    WatchTogetherMemberPresence.IDLE
                },
                watching = playback,
            ),
            WatchTogetherMemberPresentation(
                userId = "member-2",
                nickname = "自由观影的朋友",
                avatarUrl = null,
                isHost = false,
                following = false,
                state = WatchTogetherMemberPresence.IDLE,
                watching = null,
            ),
            WatchTogetherMemberPresentation(
                userId = "member-3",
                nickname = "掉线的朋友",
                avatarUrl = null,
                isHost = false,
                following = true,
                state = WatchTogetherMemberPresence.DISCONNECTED,
                watching = null,
                disconnectedMinutes = 3,
            ),
        ),
    ),
    following = !isSelfHost,
    isSelfHost = isSelfHost,
    requiresLogin = false,
    inPlayer = false,
)
