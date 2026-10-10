/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.app.domain.settings.remote.RemoteSettingsHostState
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.remote_settings_tv_no_network
import me.him188.ani.app.ui.lang.remote_settings_tv_permission
import me.him188.ani.app.ui.lang.remote_settings_tv_qr_description
import me.him188.ani.app.ui.lang.remote_settings_tv_scan_hint
import me.him188.ani.app.ui.lang.remote_settings_tv_starting
import me.him188.ani.app.ui.lang.remote_settings_tv_step_network
import me.him188.ani.app.ui.lang.remote_settings_tv_step_scan
import me.him188.ani.app.ui.lang.remote_settings_tv_unavailable
import me.him188.ani.tv.ui.foundation.widgets.TvQrCode
import org.jetbrains.compose.resources.stringResource

private val QrSize = 200.dp
private val QrShape = RoundedCornerShape(8.dp)

/**
 * 设置页“手机配置”分区的详情: 左侧是连接步骤, 右侧是二维码; 服务未就绪时二维码位置显示状态.
 *
 * 没有可聚焦的内容, 焦点留在分区列表上.
 */
@Composable
internal fun TvRemoteSettingsPane(state: RemoteSettingsHostState, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(top = 12.dp).testTag("tv-remote-settings"),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ConnectionStep(1, stringResource(Lang.remote_settings_tv_step_network))
            ConnectionStep(2, stringResource(Lang.remote_settings_tv_step_scan))
            ConnectionStep(3, stringResource(Lang.remote_settings_tv_scan_hint))
        }
        Column(
            Modifier.width(QrSize),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state is RemoteSettingsHostState.Ready) {
                TvQrCode(
                    state.link.toUri(),
                    stringResource(Lang.remote_settings_tv_qr_description),
                    Modifier.size(QrSize).testTag("tv-remote-settings-qr"),
                )
                Text(
                    "${state.link.ip}:${state.link.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                val (icon, message) =
                    when (state) {
                        RemoteSettingsHostState.PermissionRequired ->
                            Icons.Rounded.Lock to Lang.remote_settings_tv_permission
                        RemoteSettingsHostState.NoNetwork ->
                            Icons.Rounded.WifiOff to Lang.remote_settings_tv_no_network
                        RemoteSettingsHostState.Unavailable ->
                            Icons.Rounded.ErrorOutline to Lang.remote_settings_tv_unavailable
                        else -> Icons.Rounded.HourglassEmpty to Lang.remote_settings_tv_starting
                    }
                Box(
                    Modifier.size(QrSize)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f), QrShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        null,
                        Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("tv-remote-settings-status"),
                )
            }
        }
    }
}

@Composable
private fun ConnectionStep(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text,
            // 与 28 dp 的序号在首行垂直对齐.
            Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
