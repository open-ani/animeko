/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.danmaku

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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.app.ui.foundation.theme.aniDarkColorTheme
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults

@Composable
fun DanmakuEditor(
    text: String,
    onTextChange: (String) -> Unit,
    isSending: () -> Boolean,
    placeholderText: String,
    onSend: (text: String) -> Unit,
    modifier: Modifier = Modifier,
    colors: TextFieldColors = PlayerControllerDefaults.inVideoDanmakuTextFieldColors(),
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val textUpdated by rememberUpdatedState(text)
    MaterialTheme(aniDarkColorTheme()) {
        PlayerControllerDefaults.DanmakuTextField(
            text,
            onValueChange = onTextChange,
            modifier = modifier,
            onSend = {
                if (textUpdated.isEmpty()) return@DanmakuTextField
                onSend(textUpdated)
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
