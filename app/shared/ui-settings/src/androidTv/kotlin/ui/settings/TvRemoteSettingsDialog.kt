/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.settings

import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.qr_login_close
import me.him188.ani.app.ui.lang.remote_settings_tv_description
import me.him188.ani.app.ui.lang.remote_settings_tv_no_network
import me.him188.ani.app.ui.lang.remote_settings_tv_permission
import me.him188.ani.app.ui.lang.remote_settings_tv_qr_description
import me.him188.ani.app.ui.lang.remote_settings_tv_scan_hint
import me.him188.ani.app.ui.lang.remote_settings_tv_starting
import me.him188.ani.app.ui.lang.remote_settings_tv_step_network
import me.him188.ani.app.ui.lang.remote_settings_tv_step_scan
import me.him188.ani.app.ui.lang.remote_settings_tv_title
import me.him188.ani.app.ui.lang.remote_settings_tv_unavailable
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.settings.remote.RemoteSettingsHostStatus
import me.him188.ani.app.domain.settings.remote.RemoteSettingsHostState
import me.him188.ani.tv.ui.foundation.layout.TvModalOverlay
import me.him188.ani.tv.ui.foundation.widgets.TvHeroButton
import me.him188.ani.tv.ui.foundation.widgets.TvOptionDefaults
import me.him188.ani.tv.ui.foundation.widgets.TvQrCode
import me.him188.ani.tv.ui.foundation.widgets.tvOptionPanelSurface

/** 横向扫码任务布局；关闭按钮是唯一焦点，返回键由同窗口 modal 处理。 */
@Composable
internal fun TvRemoteSettingsDialog(state: RemoteSettingsHostState, onClose: () -> Unit) {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { closeFocus.requestFocus() }
    TvModalOverlay(
        onClose = onClose,
        background = { Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f))) },
    ) {
        Row(
            Modifier.align(Alignment.Center)
                .padding(horizontal = 48.dp, vertical = 32.dp)
                .widthIn(max = 800.dp)
                .tvOptionPanelSurface()
                .padding(36.dp)
                .testTag("tv-remote-settings-dialog"),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Icon(
                    Icons.Rounded.Smartphone,
                    null,
                    Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(Lang.remote_settings_tv_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TvOptionDefaults.Content,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(Lang.remote_settings_tv_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TvOptionDefaults.Muted,
                )
                Spacer(Modifier.height(24.dp))
                ConnectionStep("1", stringResource(Lang.remote_settings_tv_step_network))
                Spacer(Modifier.height(14.dp))
                ConnectionStep("2", stringResource(Lang.remote_settings_tv_step_scan))
                Spacer(Modifier.height(28.dp))
                TvHeroButton(
                    stringResource(Lang.qr_login_close),
                    Icons.Rounded.Close,
                    filled = true,
                    onClick = onClose,
                    onFocused = {},
                    modifier = Modifier.testTag("tv-remote-settings-close"),
                    focusRequester = closeFocus,
                )
            }
            Column(
                Modifier.width(248.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val link = state.link
                if (link != null) {
                    TvQrCode(
                        link.toUri(),
                        stringResource(Lang.remote_settings_tv_qr_description),
                        Modifier.size(248.dp).testTag("tv-remote-settings-qr"),
                    )
                    Text(
                        stringResource(Lang.remote_settings_tv_scan_hint),
                        style = MaterialTheme.typography.titleMedium,
                        color = TvOptionDefaults.Content,
                    )
                    Text(
                        "${link.ip}:${link.port} · ${link.appVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TvOptionDefaults.Muted,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Box(
                        Modifier.size(248.dp)
                            .background(TvOptionDefaults.Raised, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.WifiOff,
                            null,
                            Modifier.size(64.dp),
                            tint = TvOptionDefaults.Muted,
                        )
                    }
                    Text(
                        stringResource(when (state.status) {
                            RemoteSettingsHostStatus.STARTING -> Lang.remote_settings_tv_starting
                            RemoteSettingsHostStatus.PERMISSION_REQUIRED -> Lang.remote_settings_tv_permission
                            RemoteSettingsHostStatus.NO_NETWORK -> Lang.remote_settings_tv_no_network
                            RemoteSettingsHostStatus.UNAVAILABLE -> Lang.remote_settings_tv_unavailable
                            RemoteSettingsHostStatus.READY -> Lang.remote_settings_tv_scan_hint
                        }),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TvOptionDefaults.Content,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("tv-remote-settings-unavailable"),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionStep(number: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).background(TvOptionDefaults.Raised, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TvOptionDefaults.Content)
    }
}
