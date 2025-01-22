/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard.step

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import me.him188.ani.app.ui.foundation.layout.cardVerticalPadding
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.settings.rendering.P2p

@Stable
class NotificationPermissionState(
    val showGrantNotificationItem: Boolean,
    val granted: State<Boolean>,
    /**
     * `null` 还没请求过, `true` 成功了, `false` 拒绝了
     */
    val lastRequestResult: State<Boolean?>
)

@Composable
fun BitTorrentFeature(
    bitTorrentEnabled: Boolean,
    grantedNotificationPermission: Boolean,
    lastRequestNotificationPermissionResult: Boolean? = null,
    onBitTorrentEnableChanged: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
    showGrantNotificationItem: Boolean = true,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass
) {
    SettingsTab(modifier = modifier) {
        Row(
            modifier = Modifier
                .padding(
                    horizontal = windowSizeClass.paneHorizontalPadding,
                    vertical = 16.dp,
                )
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            ProvideContentColor(MaterialTheme.colorScheme.primary) {
                Icon(
                    imageVector = Icons.Default.P2p,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                )
            }
        }
        Column {
            Column(
                modifier = Modifier
                    .padding(horizontal = windowSizeClass.paneHorizontalPadding)
                    .padding(4.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    Text(
                        "Ani 可以通过 BitTorrent P2P 网络搜索、在线观看和缓存番剧。" +
                                "你将从其他 Ani 用户和全球的 BT 用户下载并缓存内容，同时你的缓存也将分享给他们。",
                    )
                    Text(
                        "启用 BitTorrent 功能，Ani 将会启动前台服务来保持运行 torrent 引擎，这可能会增加耗电。" +
                                "Ani 还会创建一个常驻的通知显示 BT 引擎的运行状态。",
                    )
                    Text("你也可以在 设置 - BitTorrent 中开启或关闭。")
                }
            }
        }
        Column(modifier = Modifier.padding(bottom = windowSizeClass.cardVerticalPadding)) {
            TextItem(
                title = { Text("启用 BitTorrent 功能") },
                action = {
                    Switch(
                        checked = bitTorrentEnabled,
                        onCheckedChange = onBitTorrentEnableChanged,
                    )
                },
                onClick = { onBitTorrentEnableChanged(!bitTorrentEnabled) },
            )

            if (showGrantNotificationItem) {
                TextItem(
                    title = {
                        Text(
                            text = if (grantedNotificationPermission) "已授权通知权限" else "请求通知权限",
                            color = if (grantedNotificationPermission) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    description = {
                        Text(
                            text = "Ani 需要通知权限来显示 BT 引擎的运行状态、下载进度等信息",
                            color = if (grantedNotificationPermission) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    action = {
                        if (!grantedNotificationPermission) {
                            Column {
                                Spacer(Modifier.height(16.dp))
                                Icon(Icons.Rounded.ArrowOutward, null)
                            }
                        }
                    },
                    modifier = Modifier.clickable {
                        if (!grantedNotificationPermission) onRequestNotificationPermission()
                    },
                )
                AnimatedVisibility(lastRequestNotificationPermissionResult == false) {
                    Item(
                        leadingContent = { Icon(Icons.Filled.Error, null) },
                        headlineContent = {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                Text(text = "请求通知权限失败，在 Ani 位于后台时 BT 服务可能会被系统终止。")
                            }
                        },
                    )
                }
            }
        }
    }
}