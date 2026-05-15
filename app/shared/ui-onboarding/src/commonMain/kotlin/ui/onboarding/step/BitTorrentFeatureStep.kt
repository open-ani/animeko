/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.onboarding.step

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.lang.*
import me.him188.ani.app.ui.foundation.widgets.HeroIcon
import me.him188.ani.app.ui.onboarding.WizardLayoutParams
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.rendering.P2p
import org.jetbrains.compose.resources.*

@Composable
internal fun BitTorrentFeatureStep(
    bitTorrentEnabled: Boolean,
    onBitTorrentEnableChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    bitTorrentCheckFeatureItem: (@Composable (WizardLayoutParams) -> Unit) = { lp ->
        Box(Modifier.padding(horizontal = lp.horizontalPadding)) {
            BitTorrentFeatureSwitchItem(
                checked = bitTorrentEnabled,
                onCheckedChange = onBitTorrentEnableChanged,
            )
        }
    },
    requestNotificationPermission: (@Composable (WizardLayoutParams) -> Unit)? = null,
    layoutParams: WizardLayoutParams = WizardLayoutParams.calculate(currentWindowAdaptiveInfo1().windowSizeClass)
) {
    SettingsTab(modifier = modifier) {
        HeroIcon {
            Icon(
                imageVector = Icons.Default.P2p,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .padding(horizontal = layoutParams.horizontalPadding)
                .padding(horizontal = layoutParams.descHorizontalPadding)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(Lang.onboarding_bittorrent_play_while_downloading))
            Text(stringResource(Lang.onboarding_bittorrent_notification_permission_hint))
        }
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { onCheckedChange(!checked) },
            ),
        ) {
            Column(
                Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Lang.onboarding_bittorrent_enable),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                    Switch(
                        checked = checked,
                        onCheckedChange = onCheckedChange,
                        interactionSource = interactionSource,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier
                .padding(all = 24.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        if (granted) Icons.Outlined.NotificationsActive else Icons.Outlined.Notifications,
                        contentDescription = "Notification icon",
                        modifier = Modifier.size(32.dp),
                    )
                    Text(
                        text = stringResource(Lang.onboarding_bittorrent_allow_notification),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                }
                Box(modifier = Modifier.padding(start = 48.dp)) {
                    Text(
                        text = stringResource(Lang.onboarding_bittorrent_notification_description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (granted) {
                OutlinedButton(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    content = { Text(stringResource(Lang.onboarding_bittorrent_authorized)) },
                )
            } else {
                Button(
                    onClick = onRequestNotificationPermission,
                    modifier = Modifier.fillMaxWidth(),
                    content = { Text(stringResource(Lang.onboarding_bittorrent_grant_permission)) },
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

@Preview(showBackground = true)
@Composable
fun PreviewBitTorrentFeatureStep() {
    ProvideCompositionLocalsForPreview {
        BitTorrentFeatureStep(
            bitTorrentEnabled = true,
            onBitTorrentEnableChanged = { },
            requestNotificationPermission = {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    RequestNotificationPermission(
                        granted = false,
                        onRequestNotificationPermission = { },
                    )
                }
            },
        )
    }
}
