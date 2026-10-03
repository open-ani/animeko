/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.tv_login_qr_content_description
import me.him188.ani.app.ui.lang.tv_login_qr_expires_in
import me.him188.ani.app.ui.lang.tv_login_qr_failed
import me.him188.ani.app.ui.lang.tv_login_qr_hint
import me.him188.ani.app.ui.lang.tv_login_qr_loading
import me.him188.ani.app.ui.lang.tv_login_qr_refresh
import me.him188.ani.app.ui.lang.tv_login_qr_rejected
import me.him188.ani.app.ui.lang.tv_login_qr_scanned
import me.him188.ani.app.ui.lang.tv_login_qr_scanned_account
import me.him188.ani.app.ui.lang.tv_login_qr_scanned_hint
import me.him188.ani.app.ui.lang.tv_login_qr_success
import me.him188.ani.app.ui.lang.tv_login_qr_title
import me.him188.ani.tv.ui.foundation.widgets.TvHeroButton
import me.him188.ani.tv.ui.foundation.widgets.TvQrCode
import me.him188.ani.tv.ui.foundation.widgets.tvHeroContentColor
import me.him188.ani.tv.ui.foundation.widgets.tvHeroSecondaryContentColor
import org.jetbrains.compose.resources.stringResource

/**
 * 登录页右侧的扫码登录区域. 等待扫码时不需要任何遥控器操作; 只有 [TvQrLoginUiState.Invalid] 时出现可聚焦的刷新按钮.
 *
 * @param refreshButtonModifier 挂到刷新按钮上, 供页面接入焦点锚点
 */
@Composable
internal fun TvQrLoginPanel(
    state: TvQrLoginUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    refreshButtonModifier: Modifier = Modifier,
) {
    Column(
        modifier.testTag("tv-login-qr-panel"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(Lang.tv_login_qr_title),
            style = MaterialTheme.typography.titleLarge,
            color = tvHeroContentColor(),
        )

        Box(Modifier.size(TvQrLoginDefaults.QrSize), contentAlignment = Alignment.Center) {
            val qrContent = when (state) {
                is TvQrLoginUiState.Waiting -> state.qrContent
                is TvQrLoginUiState.Scanned -> state.qrContent
                else -> null
            }
            if (qrContent != null) {
                TvQrCode(
                    qrContent,
                    stringResource(Lang.tv_login_qr_content_description),
                    Modifier.matchParentSize().testTag("tv-login-qr"),
                )
            } else {
                Box(Modifier.matchParentSize().background(TvQrLoginDefaults.PlaceholderColor, TvQrLoginDefaults.QrShape))
            }
            when (state) {
                TvQrLoginUiState.Loading -> CircularProgressIndicator()
                is TvQrLoginUiState.Waiting -> {}
                is TvQrLoginUiState.Scanned ->
                    QrOverlay(Icons.Rounded.Smartphone, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)

                TvQrLoginUiState.Success ->
                    QrOverlay(Icons.Rounded.Check, TvQrLoginDefaults.SuccessColor, Color.Black)

                is TvQrLoginUiState.Invalid ->
                    QrOverlay(Icons.Rounded.Refresh, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        val (headline, detail) = when (state) {
            TvQrLoginUiState.Loading -> null to stringResource(Lang.tv_login_qr_loading)
            is TvQrLoginUiState.Waiting -> null to stringResource(Lang.tv_login_qr_hint)
            is TvQrLoginUiState.Scanned -> stringResource(Lang.tv_login_qr_scanned) to buildString {
                append(stringResource(Lang.tv_login_qr_scanned_hint))
                state.nickname?.let { append('\n').append(stringResource(Lang.tv_login_qr_scanned_account, it)) }
            }

            TvQrLoginUiState.Success -> stringResource(Lang.tv_login_qr_success) to null
            is TvQrLoginUiState.Invalid -> null to when (state.reason) {
                TvQrLoginUiState.Invalid.Reason.Rejected -> stringResource(Lang.tv_login_qr_rejected)
                TvQrLoginUiState.Invalid.Reason.Failed -> stringResource(Lang.tv_login_qr_failed)
            }
        }
        headline?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, color = tvHeroContentColor())
        }
        detail?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = tvHeroSecondaryContentColor(),
                textAlign = TextAlign.Center,
            )
        }

        when (state) {
            is TvQrLoginUiState.Waiting -> Text(
                stringResource(Lang.tv_login_qr_expires_in, formatRemaining(state.remainSec)),
                style = MaterialTheme.typography.bodySmall,
                color = tvHeroSecondaryContentColor(),
            )

            is TvQrLoginUiState.Invalid -> TvHeroButton(
                text = stringResource(Lang.tv_login_qr_refresh),
                icon = Icons.Rounded.Refresh,
                filled = false,
                onClick = onRefresh,
                onFocused = {},
                modifier = refreshButtonModifier.testTag("tv-login-qr-refresh"),
            )

            else -> {}
        }
    }
}

/** 盖在二维码上的状态标记: 压暗二维码 (此时不应再被扫描) 并显示一个图标. */
@Composable
private fun QrOverlay(icon: ImageVector, containerColor: Color, contentColor: Color) {
    Box(
        Modifier.size(TvQrLoginDefaults.QrSize).background(TvQrLoginDefaults.ScrimColor, TvQrLoginDefaults.QrShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(64.dp).background(containerColor, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(34.dp), tint = contentColor)
        }
    }
}

private fun formatRemaining(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
}

private object TvQrLoginDefaults {
    val QrSize = 210.dp
    val QrShape = RoundedCornerShape(8.dp)
    val ScrimColor = Color(0xDB111318)
    val PlaceholderColor = Color(0x1FFFFFFF)
    val SuccessColor = Color(0xFF9BD4A0)
}
