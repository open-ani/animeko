/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.torrent.peer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.IconButton
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.TextItem


@Composable
fun BlackListEditPane(
    ipBlackList: List<String>,
    showTitle: Boolean,
    onAdd: (List<String>) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    var showAddBlackIpDialog by rememberSaveable { mutableStateOf(false) }

    SettingsTab(modifier) {
        AniAnimatedVisibility(visible = showTitle) {
            Group(
                title = { Text("黑名单") },
                description = { Text("黑名单中的 Peer 总是被屏蔽，无论是否匹配过滤规则") },
                content = { },
            )
        }
        TextItem(
            title = { if (!showTitle) Text("添加黑名单 IP 地址") },
            action = {
                if (showTitle) {
                    OutlinedButton({ showAddBlackIpDialog = true }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "添加黑名单 IP 地址")
                            Text("添加黑名单 IP 地址")
                        }
                    }
                } else {
                    IconButton(
                        {
                            showAddBlackIpDialog = true
                        },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加黑名单 IP 地址")
                    }
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = WindowInsets.ime.asPaddingValues(),
        ) {
            items(items = ipBlackList, key = { it }) { item ->
                ListItem(
                    headlineContent = { Text(item) },
                    trailingContent = {
                        IconButton({ onRemove(item) }) {
                            Icon(Icons.Default.Close, contentDescription = "移除此黑名单 IP")
                        }
                    },
                    colors = listItemColors,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    if (showAddBlackIpDialog) {
        AddBlockedIPDialog(
            onAdd = onAdd,
            onDismiss = { showAddBlackIpDialog = false },
        )
    }
}