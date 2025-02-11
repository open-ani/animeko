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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.IconButton
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.settings.rendering.P2p
import me.him188.ani.app.ui.wizard.HeroIcon
import me.him188.ani.app.ui.wizard.WizardLayoutParams

@Composable
private fun BitTorrentTip(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.size(8.dp),
                color = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
            ) { }
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun BitTorrentFeature(
    bitTorrentEnabled: Boolean,
    grantedNotificationPermission: Boolean,
    showPermissionError: Boolean,
    onBitTorrentEnableChanged: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenSystemNotificationSettings: () -> Unit,
    layoutParams: WizardLayoutParams,
    modifier: Modifier = Modifier,
    showGrantNotificationItem: Boolean = true
) {
    val motionScheme = LocalAniMotionScheme.current
    val platform = LocalPlatform.current
    
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
                    .padding(horizontal = 4.dp)
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
            modifier = Modifier.padding(bottom = layoutParams.horizontalPadding),
        ) {
            /*SwitchItem(
                checked = bitTorrentEnabled,
                onCheckedChange = onBitTorrentEnableChanged,
                title = { Text("启用 BitTorrent 功能") },
            )*/

            if (showGrantNotificationItem) {
                TextItem(
                    title = { Text(if (grantedNotificationPermission) "已授权通知权限" else "请求通知权限") },
                    description = { Text("显示 BT 引擎的运行状态、下载进度等信息") },
                    action = {
                        if (!grantedNotificationPermission) {
                            IconButton(onRequestNotificationPermission) {
                                Icon(Icons.Rounded.ArrowOutward, "请求通知权限")
                            }
                        }
                    },
                    onClick = if (!grantedNotificationPermission) onRequestNotificationPermission else null,
                )
                AnimatedVisibility(
                    showPermissionError,
                    enter = motionScheme.animatedVisibility.standardEnter, // don't animate layout
                    exit = motionScheme.animatedVisibility.columnExit,
                ) {
                    TextItem(
                        icon = { Icon(Icons.Filled.Error, null) },
                        title = {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                Text(
                                    text = "请求通知权限失败，Ani 在后台时 BT 服务可能会被系统终止。" +
                                            "若非手动拒绝授权，请点击此处打开系统设置进行授权。",
                                )
                            }
                        },
                        onClick = onOpenSystemNotificationSettings,
                    )
                }
            }
        }
    }
}


@Stable
class NotificationPermissionState(
    val showGrantNotificationItem: Boolean,
    val granted: Boolean,
    /**
     * `null` 还没请求过, `true` 成功了, `false` 拒绝了
     */
    val lastRequestResult: Boolean?,
    val placeholder: Boolean = false
) {
    companion object {
        @Stable
        val Placeholder = NotificationPermissionState(
            showGrantNotificationItem = false,
            granted = false,
            lastRequestResult = null,
            placeholder = true,
        )
    }
}