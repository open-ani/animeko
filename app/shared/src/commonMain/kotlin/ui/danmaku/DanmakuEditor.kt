/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.danmaku

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.network.protocol.DanmakuInfo
import me.him188.ani.app.data.network.protocol.DanmakuLocation
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.app.videoplayer.ui.PlayerControllerState
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults
import me.him188.ani.app.videoplayer.ui.rememberAlwaysOnRequester
import org.openani.mediamp.MediampPlayer


@Composable
fun PlayerDanmakuEditor(
    danmakuEditorState: DanmakuEditorState,
    danmakuTextPlaceholder: String,
    playerState: MediampPlayer,
    videoScaffoldConfig: VideoScaffoldConfig,
    playerControllerState: PlayerControllerState,
    modifier: Modifier = Modifier,
) {
    val sending by danmakuEditorState.isSending.collectAsStateWithLifecycle()
    PlayerDanmakuEditor(
        text = danmakuEditorState.text,
        onTextChange = { danmakuEditorState.text = it },
        isSending = { sending },
        onSend = {
            danmakuEditorState.post(it)
        },
        danmakuTextPlaceholder, playerState, videoScaffoldConfig, playerControllerState, modifier,
    )
}


@Composable
fun PlayerDanmakuEditor(
    text: String,
    onTextChange: (String) -> Unit,
    isSending: () -> Boolean,
    onSend: suspend (DanmakuInfo) -> Unit,
    danmakuTextPlaceholder: String,
    playerState: MediampPlayer,
    videoScaffoldConfig: VideoScaffoldConfig,
    playerControllerState: PlayerControllerState,
    modifier: Modifier = Modifier,
) {
    val danmakuEditorRequester = rememberAlwaysOnRequester(playerControllerState, "danmakuEditor")

    val focusManager = LocalFocusManager.current

    /**
     * 是否设置了暂停
     */
    var didSetPaused by rememberSaveable { mutableStateOf(false) }
    Row(modifier = modifier) {
        val scope = rememberCoroutineScope()
        PlayerDanmakuEditor(
            text = text,
            onTextChange = onTextChange,
            isSending = isSending,
            placeholderText = danmakuTextPlaceholder,
            onSend = { text ->
                onTextChange("")
                scope.launch {
                    onSend(
                        DanmakuInfo(
                            playerState.getCurrentPositionMillis(),
                            text = text,
                            color = Color.White.toArgb(),
                            location = DanmakuLocation.NORMAL,
                        ),
                    )
                    focusManager.clearFocus()
                }
            },
            modifier = Modifier.onFocusChanged {
                if (it.isFocused) {
                    if (videoScaffoldConfig.pauseVideoOnEditDanmaku && playerState.playbackState.value.isPlaying) {
                        didSetPaused = true
                        playerState.pause()
                    }
                    danmakuEditorRequester.request()
                } else {
                    if (didSetPaused) {
                        didSetPaused = false
                        playerState.resume()
                    }
                    danmakuEditorRequester.cancelRequest()
                }
            }.weight(1f),
        )
    }
}

@Composable
fun PlayerDanmakuEditor(
    text: String,
    onTextChange: (String) -> Unit,
    isSending: () -> Boolean,
    placeholderText: String,
    onSend: (text: String) -> Unit,
    modifier: Modifier = Modifier,
    colors: TextFieldColors = PlayerControllerDefaults.inVideoDanmakuTextFieldColors(),
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    PlayerControllerDefaults.DanmakuTextField(
        text,
        onValueChange = onTextChange,
        modifier = modifier,
        onSend = {
            if (text.isEmpty()) return@DanmakuTextField
            onSend(text)
        },
        isSending = isSending,
        placeholder = {
            Text(
                placeholderText,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = style,
            )
        },
        colors = colors,
        style = style,
    )
}

@Composable
fun DummyDanmakuEditor(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.height(36.dp)
                .clip(shape)
                .clickable(onClick = onClick)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProvideContentColor(MaterialTheme.colorScheme.onSurfaceVariant) {
                    Text(
                        "发送弹幕",
                        style = MaterialTheme.typography.labelLarge,
                    )

                    Icon(Icons.AutoMirrored.Rounded.Send, null)
                }
            }
        }
    }
}
