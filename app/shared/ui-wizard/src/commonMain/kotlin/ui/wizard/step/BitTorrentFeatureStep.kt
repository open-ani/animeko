/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard.step

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.rendering.P2p
import me.him188.ani.app.ui.wizard.HeroIcon
import me.him188.ani.app.ui.wizard.WizardLayoutParams

@Composable
internal fun BitTorrentFeatureStep(
    bitTorrentEnabled: Boolean,
    onBitTorrentEnableChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    bitTorrentCheckFeatureItem: (@Composable (WizardLayoutParams) -> Unit) = { lp ->
        Box(Modifier.padding(horizontal = lp.horizontalPadding)) {
            BitTorrentFeatureSwitchItem(
                checked = bitTorrentEnabled,
                onCheckedChange = onBitTorrentEnableChanged
            )
        }
    },
    requestNotificationPermission: (@Composable (WizardLayoutParams) -> Unit)? = null,
    layoutParams: WizardLayoutParams = WizardLayoutParams.calculate(currentWindowAdaptiveInfo1().windowSizeClass)
) {
    SettingsTab(modifier = modifier) {
        HeroIcon(layoutParams) {
            Icon(
                imageVector = Icons.Default.P2p,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = layoutParams.horizontalPadding)
                    .padding(horizontal = layoutParams.descHorizontalPadding)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    Text(
                        "Ani 可以通过 BitTorrent P2P 网络搜索、在线观看和缓存番剧。" +
                                "你将从其他 Ani 用户和全球的 BT 用户下载并缓存内容，同时你的缓存也将分享给他们。",
                    )

                    /*when (platform) {
                        // 启用 BitTorrent 功能，App 将会启动前台服务来保持运行 torrent 引擎，这可能会增加耗电。
                        is Platform.Android -> Text(
                            "App 将会启动前台服务来保持运行 BT 引擎，这可能会增加耗电。" +
                                    "App 还会创建一个常驻的通知显示 BT 引擎的运行状态。",
                        )

                        else -> {}
                    }*/

                    // Text("你也可以在 设置 - BitTorrent 中开启或关闭 BitTorrent 功能。")

                    Text("BT 源的下载速度取决于你的运营商网络质量。")
                }
            }
        }
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            bitTorrentCheckFeatureItem.invoke(layoutParams)
            requestNotificationPermission?.invoke(layoutParams)
        }
    }
}

@Composable
internal fun BitTorrentFeatureSwitchItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Box(
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { onCheckedChange(!checked) }
            )
        ) {
            Column(
                Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "启用 BitTorrent 功能",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Switch(
                        checked = checked,
                        onCheckedChange = onCheckedChange,
                        interactionSource = interactionSource
                    )
                }
            }
        }
    }
}

@Composable
internal fun RequestNotificationPermission(
    granted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) { 
        Column(
            Modifier
                .padding(all = 24.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) { 
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (granted) Icons.Outlined.NotificationsActive else Icons.Outlined.Notifications,
                        contentDescription = "Notification icon",
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "允许通知",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }
                Box(modifier = Modifier.padding(start = 48.dp)) {
                    Text(
                        text = "显示 BT 下载进度和速度等信息",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (granted) {
                OutlinedButton(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    content = { Text("已授权") }
                )
            } else {
                Button(
                    onClick = onRequestNotificationPermission,
                    modifier = Modifier.fillMaxWidth(),
                    content = { Text("授予权限") }
                )
            }
        }
    }
}


@Stable
class GrantNotificationPermissionState(
    val showGrantNotificationItem: Boolean,
    val granted: Boolean,
    /**
     * `null` 还没请求过, `true` 成功了, `false` 拒绝了
     */
    val lastRequestResult: Boolean?,
    val isPlaceholder: Boolean = false
) {
    companion object {
        @Stable
        val Placeholder = GrantNotificationPermissionState(
            showGrantNotificationItem = false,
            granted = false,
            lastRequestResult = null,
            isPlaceholder = true,
        )
    }
}