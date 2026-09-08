/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.app.ui.watchtogether.stateIconAndText
import me.him188.ani.app.ui.watchtogether.watchTogetherStatusText
import me.him188.ani.leanback.ui.foundation.focus.TvFocusKey
import me.him188.ani.leanback.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.focus.tvFocusHotkey
import me.him188.ani.leanback.ui.foundation.focus.tvFocusNavSignal


private enum class TogetherFieldFocus : TvFocusKey { Name, Password, Submit }

@Composable
internal fun TvPlayerTogetherPanel(
    together: TvTogetherState,
    onTogetherIntent: (TvTogetherIntent) -> Unit,
    onLogin: () -> Unit,
    confirmLeave: Boolean,
    onConfirmLeaveChange: (Boolean) -> Unit,
    listState: LazyListState,
    entryModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val fields = rememberTvFocusScope()
    fields.Resolver()
    val colors = LocalTvPlayerSurfaceColors.current
    TvPlayerOptionPanelLayout(TvPlayerPanel.Together, listState, modifier, Modifier.tvFocusNavSignal(fields)) {
        if (together.requiresLogin) item {
            TvOptionRow(
                "登录账号",
                supportingText = "登录后即可加入或创建一起看房间",
                modifier = entryModifier,
            ) { onLogin() }
        }
        else if (!together.joined) {
            item {
                TvOptionTextField(
                    together.roomName, "房间名称", { onTogetherIntent(TvTogetherIntent.RoomName(it)) },
                    entryModifier
                        .tvFocusAnchor(fields, TogetherFieldFocus.Name)
                        .tvFocusHotkey(fields, Key.DirectionDown to TogetherFieldFocus.Password),
                )
            }
            item {
                TvOptionTextField(
                    together.password, "房间密码", { onTogetherIntent(TvTogetherIntent.Password(it)) },
                    Modifier
                        .tvFocusAnchor(fields, TogetherFieldFocus.Password)
                        .tvFocusHotkey(
                            fields,
                            Key.DirectionUp to TogetherFieldFocus.Name,
                            Key.DirectionDown to TogetherFieldFocus.Submit,
                        ),
                    password = true,
                )
            }
            item {
                TvOptionRow(
                    when {
                        together.joining -> "正在加入… · 取消"
                        together.error != null -> "重新加入"
                        else -> "加入 / 创建房间"
                    },
                    modifier = Modifier.tvFocusAnchor(fields, TogetherFieldFocus.Submit),
                    icon = Icons.Rounded.Groups,
                    filled = true,
                    supportingText = together.error,
                ) { onTogetherIntent(if (together.joining) TvTogetherIntent.CancelJoin else TvTogetherIntent.Join) }
            }
            item {
                Text(
                    "输入相同的房间名称即可一起看。\n房间不存在时将自动创建。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.padding(8.dp),
                )
            }
        } else if (confirmLeave) {
            item {
                TvOptionRow(
                    if (together.isHost) "确定解散房间" else "确定退出房间",
                    modifier = entryModifier,
                ) { onTogetherIntent(TvTogetherIntent.Leave) }
            }
            item { TvOptionRow("留在房间") { onConfirmLeaveChange(false) } }
        } else {
            item {
                Text(
                    "${together.roomName} · ${together.connection}",
                    color = colors.content,
                    modifier = Modifier.padding(8.dp),
                )
            }
            together.playback?.let { playback ->
                item {
                    Text(
                        "${playback.subjectName} · 第 ${playback.episodeSort} 集 · ${playback.stateIconAndText().second} · " +
                                "${formatTime(playback.positionMillis)} / ${formatTime(playback.durationMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            if (!together.isHost) item {
                TvOptionRow(
                    "跟随房主",
                    checked = together.following,
                    modifier = entryModifier,
                ) { onTogetherIntent(TvTogetherIntent.ToggleFollowing) }
            }
            item {
                TvOptionRow(
                    if (together.isHost) "解散房间" else "退出房间",
                    modifier = if (together.isHost) entryModifier else Modifier,
                ) { onConfirmLeaveChange(true) }
            }
            items(together.members, key = { it.userId }) { member ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        member.nickname + if (member.isSelf) "（我）" else "",
                        color = colors.content, style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        (if (member.isHost) "房主" else if (member.following) "跟随房主" else "自由观看") +
                                " · " + member.watchTogetherStatusText(),
                        color = colors.muted, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
