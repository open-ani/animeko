/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.watchtogether

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import me.him188.ani.leanback.ui.foundation.focus.TvFocusKey
import me.him188.ani.leanback.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.requestPrepared
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.focus.tvFocusHotkey
import me.him188.ani.leanback.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.leanback.ui.foundation.layout.tvPanelScrollEdges
import me.him188.ani.leanback.ui.foundation.widgets.LocalTvOptionColors
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionDivider
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionTextField

private enum class TogetherFocus : TvFocusKey { Name, Password, Submit, Playback, Follow, Leave }
private data class TogetherMemberFocus(val userId: String) : TvFocusKey

/**
 * TV room content, independent of the player that hosts it.
 * The host owns panel navigation and supplies [entryModifier] for initial and return focus.
 * Joining, membership and synchronization remain VM intents; local focus and layout stay here.
 */
@Composable
fun TvWatchTogetherPanel(
    together: TvTogetherState,
    onTogetherIntent: (TvTogetherIntent) -> Unit,
    onLogin: () -> Unit,
    confirmLeave: Boolean,
    onConfirmLeaveChange: (Boolean) -> Unit,
    listState: LazyListState,
    entryModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    var returnToLeave by rememberSaveable(together.joined, if (together.joined) together.roomName else null) {
        mutableStateOf(confirmLeave)
    }
    when {
        together.requiresLogin -> TvTogetherPanelLayout(
            listState = listState,
            modifier = modifier,
            footer = {
                TvTogetherAction("登录账号", modifier = entryModifier, icon = Icons.AutoMirrored.Rounded.Login, onClick = onLogin)
            },
        ) {
            item {
                TvTogetherIntro(
                    "和朋友一起看",
                    "登录后即可加入或创建房间，和朋友同步观看同一集番剧。",
                    Icons.Rounded.Groups,
                )
            }
        }

        !together.joined -> TvTogetherJoinForm(together, onTogetherIntent, listState, entryModifier, modifier)
        confirmLeave -> TvTogetherPanelLayout(
            listState = listState,
            modifier = modifier.testTag("tv-together-confirmation"),
            footer = {
                TvTogetherAction("留在房间", modifier = entryModifier.testTag("tv-together-stay")) {
                    onConfirmLeaveChange(false)
                }
                TvTogetherLeaveAction(together.isHost, confirm = true) { onTogetherIntent(TvTogetherIntent.Leave) }
            },
        ) {
            item {
                TvTogetherIntro(
                    if (together.isHost) "要解散这个房间吗？" else "要退出这个房间吗？",
                    if (together.isHost) "解散后，所有成员都会离开房间。" else "退出后将停止跟随房主，你可以继续独自观看。",
                    Icons.AutoMirrored.Rounded.Logout,
                )
            }
            item {
                Text(together.roomName, style = MaterialTheme.typography.titleMedium, color = LocalTvOptionColors.current.muted)
            }
        }

        else -> TvTogetherRoom(
            together = together,
            onIntent = onTogetherIntent,
            onLeave = {
                returnToLeave = true
                onConfirmLeaveChange(true)
            },
            returnToLeave = returnToLeave,
            listState = listState,
            entryModifier = entryModifier,
            modifier = modifier,
        )
    }
}

/** Fixed room heading and actions surround independently scrolling, slotted content. */
@Composable
private fun TvTogetherPanelLayout(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    footer: @Composable ColumnScope.() -> Unit,
    content: LazyListScope.() -> Unit,
) {
    // Reserve space around focus outlines, including at list edges.
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) { header() }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth()
                .tvPanelScrollEdges(listState, LocalTvOptionColors.current.container)
                .focusGroup().testTag("tv-together-content"),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvOptionDivider(Modifier.padding(bottom = 4.dp))
            footer()
        }
    }
}

@Composable
private fun TvTogetherJoinForm(
    together: TvTogetherState,
    onIntent: (TvTogetherIntent) -> Unit,
    listState: LazyListState,
    entryModifier: Modifier,
    modifier: Modifier,
) {
    val focus = rememberTvFocusScope()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    focus.Resolver()
    fun focusField(index: Int, key: TogetherFocus) {
        scope.launch {
            focus.requestPrepared {
                if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
                key
            }
        }
    }
    TvTogetherPanelLayout(
        listState,
        modifier.tvFocusNavSignal(focus),
        footer = {
            together.error?.let { TvTogetherJoinError(it) }
            TvTogetherJoinAction(
                joining = together.joining,
                retry = together.error != null,
                modifier = Modifier.tvFocusAnchor(focus, TogetherFocus.Submit)
                    .onFocusChanged { if (it.isFocused) keyboard?.hide() }
                    .tvFocusHotkey(focus, Key.DirectionUp) { focusField(2, TogetherFocus.Password) },
            ) { onIntent(if (together.joining) TvTogetherIntent.CancelJoin else TvTogetherIntent.Join) }
        },
    ) {
        item {
            TvTogetherIntro("和朋友一起看", "分享房间名称和密码，同步播放、暂停和观看进度。", Icons.Rounded.Groups)
        }
        item {
            TvOptionTextField(
                together.roomName, "房间名称", { onIntent(TvTogetherIntent.RoomName(it)) },
                entryModifier.testTag("tv-together-name")
                    .tvFocusAnchor(focus, TogetherFocus.Name)
                    .tvFocusHotkey(focus, Key.DirectionDown) { focusField(2, TogetherFocus.Password) },
                readOnly = together.joining,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusField(2, TogetherFocus.Password) }),
                textStyle = MaterialTheme.typography.bodyLarge,
                labelStyle = MaterialTheme.typography.labelLarge,
            )
        }
        item {
            TvOptionTextField(
                together.password, "房间密码", { onIntent(TvTogetherIntent.Password(it)) },
                Modifier.testTag("tv-together-password")
                    .tvFocusAnchor(focus, TogetherFocus.Password)
                    .tvFocusHotkey(focus, Key.DirectionUp) { focusField(1, TogetherFocus.Name) }
                    .tvFocusHotkey(focus, Key.DirectionDown to TogetherFocus.Submit),
                password = true,
                readOnly = together.joining,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide()
                    focus.request(TogetherFocus.Submit)
                }),
                textStyle = MaterialTheme.typography.bodyLarge,
                labelStyle = MaterialTheme.typography.labelLarge,
            )
        }
        item {
            Text(
                "输入相同的房间名称即可一起看。房间不存在时将自动创建。",
                Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalTvOptionColors.current.muted,
            )
        }
    }
}

@Composable
private fun TvTogetherRoom(
    together: TvTogetherState,
    onIntent: (TvTogetherIntent) -> Unit,
    onLeave: () -> Unit,
    returnToLeave: Boolean,
    listState: LazyListState,
    entryModifier: Modifier,
    modifier: Modifier,
) {
    val focus = rememberTvFocusScope()
    val scope = rememberCoroutineScope()
    focus.Resolver()
    val controlsKey = if (together.isHost) TogetherFocus.Leave else TogetherFocus.Follow
    val memberIds = together.members.map { it.userId }
    var focusedMember by remember { mutableStateOf<String?>(null) }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var lastMember by rememberSaveable { mutableStateOf<String?>(null) }
    val focusedBeforeUpdate = focusedMember
    val indexBeforeUpdate = focusedIndex
    val navigationGeneration = focus.userNavGeneration
    LaunchedEffect(memberIds) {
        // A departed member must not drop focus out of the sidebar. Ordinary status ticks do nothing.
        if (focusedBeforeUpdate == null || focus.userNavGeneration != navigationGeneration) return@LaunchedEffect
        val retainedIndex = memberIds.indexOf(focusedBeforeUpdate)
        if (retainedIndex >= 0) {
            focusedIndex = retainedIndex
        } else if (memberIds.isEmpty()) {
            lastMember = null
            focus.request(controlsKey)
        } else {
            focus.requestPrepared {
                val targetIndex = indexBeforeUpdate.coerceIn(memberIds.indices)
                listState.scrollToItem(targetIndex + 2)
                TogetherMemberFocus(memberIds[targetIndex])
            }
        }
    }
    fun focusContent() {
        scope.launch {
            focus.requestPrepared {
                val index = memberIds.indexOf(lastMember)
                listState.scrollToItem(if (index >= 0) index + 2 else 0)
                if (index >= 0) TogetherMemberFocus(memberIds[index]) else TogetherFocus.Playback
            }
        }
    }
    TvTogetherPanelLayout(
        listState,
        modifier.tvFocusNavSignal(focus),
        header = { TvTogetherRoomHeader(together.roomName, together.connection) },
        footer = {
            if (!together.isHost) {
                TvTogetherFollowAction(
                    following = together.following,
                    modifier = (if (!returnToLeave) entryModifier else Modifier)
                        .testTag("tv-together-follow")
                        .tvFocusAnchor(focus, TogetherFocus.Follow)
                        .tvFocusHotkey(focus, Key.DirectionUp, ::focusContent)
                        .tvFocusHotkey(focus, Key.DirectionDown to TogetherFocus.Leave),
                ) { onIntent(TvTogetherIntent.ToggleFollowing) }
            }
            TvTogetherLeaveAction(
                together.isHost,
                modifier = (if (returnToLeave) entryModifier else Modifier)
                    .tvFocusAnchor(focus, TogetherFocus.Leave)
                    .then(
                        if (together.isHost) Modifier.tvFocusHotkey(focus, Key.DirectionUp, ::focusContent)
                        else Modifier.tvFocusHotkey(focus, Key.DirectionUp to TogetherFocus.Follow),
                    ),
                onClick = onLeave,
            )
        },
    ) {
        item(key = "playback") {
            TvTogetherPlaybackCard(
                together.playback, together.isHost,
                modifier = (if (together.isHost && !returnToLeave) entryModifier else Modifier)
                    .tvFocusAnchor(focus, TogetherFocus.Playback)
                    .then(if (memberIds.isEmpty()) Modifier.tvFocusHotkey(focus, Key.DirectionDown to controlsKey) else Modifier),
            )
        }
        item(key = "members") { TvTogetherMembersHeading(together.members.size) }
        if (memberIds.isEmpty()) item(key = "empty-members") {
            Text(
                "把房间名称和密码分享给朋友，即可一起观看。",
                Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalTvOptionColors.current.muted,
            )
        }
        itemsIndexed(together.members, key = { _, member -> "member/${member.userId}" }, contentType = { _, _ -> "member" }) { index, member ->
            TvTogetherMemberRow(
                member,
                Modifier.tvFocusAnchor(focus, TogetherMemberFocus(member.userId))
                    .onFocusChanged {
                        if (it.isFocused) {
                            focusedMember = member.userId
                            focusedIndex = index
                            lastMember = member.userId
                        } else if (focusedMember == member.userId) focusedMember = null
                    }
                    .then(
                        if (index == memberIds.lastIndex) Modifier.tvFocusHotkey(focus, Key.DirectionDown to controlsKey)
                        else Modifier,
                    ),
            )
        }
    }
}
