/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.torrent.peer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.data.models.preference.TorrentPeerConfig
import me.him188.ani.app.ui.foundation.rememberBackgroundScope
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.settings.mediasource.rss.SaveableStorage

@Preview
@Composable
fun PreviewPeerFilterEditPane() {
    val config = remember { mutableStateOf(TorrentPeerConfig.Default) }
    val scope = rememberBackgroundScope()
    val state = remember {
        PeerFilterSettingsState(
            MutableStateFlow(emptyList()),
            SaveableStorage(config, { config.value = it }, stateOf(false)),
            scope.backgroundScope,
            { },
            { _, _ -> },
        )
    }
    PeerFilterEditPane(
        state = state,
        showIpBlockingItem = true,
        onClickIpBlockSettings = { },
    )
}