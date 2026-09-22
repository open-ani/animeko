/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.qrlogin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.him188.ani.app.data.repository.user.QrLoginRepository
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.qr_login_camera_permission
import me.him188.ani.app.ui.lang.qr_login_camera_permission_grant
import me.him188.ani.app.ui.lang.qr_login_close
import me.him188.ani.app.ui.lang.qr_login_scan_hint
import me.him188.ani.app.ui.lang.qr_login_scan_invalid
import me.him188.ani.app.ui.lang.qr_login_scan_torch
import me.him188.ani.app.ui.lang.qr_login_title
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

/**
 * 扫描待登录设备上的二维码. 识别到扫码登录的二维码后调用一次 [onScanned]; 其他二维码只提示, 继续扫描.
 */
@Composable
fun QrLoginScanScreen(
    onScanned: (requestId: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var torchEnabled by rememberSaveable { mutableStateOf(false) }
    var handled by remember { mutableStateOf(false) }
    // 最近一次扫到的无关二维码. 用于提示, 一段时间后自动消失
    var invalidContent by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(invalidContent) {
        if (invalidContent != null) {
            delay(3.seconds)
            invalidContent = null
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        QrCodeScanner(
            onScanned = { content ->
                if (handled) return@QrCodeScanner
                val requestId = QrLoginRepository.parseRequestId(content)
                if (requestId != null) {
                    handled = true
                    onScanned(requestId)
                } else {
                    invalidContent = content
                }
            },
            torchEnabled = torchEnabled,
            permissionDeniedContent = { requestPermission ->
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(Lang.qr_login_camera_permission),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Button(requestPermission) { Text(stringResource(Lang.qr_login_camera_permission_grant)) }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        ScanFrame(Modifier.fillMaxSize())

        TopAppBar(
            title = { Text(stringResource(Lang.qr_login_title)) },
            navigationIcon = {
                IconButton(onNavigateBack) { Icon(Icons.Rounded.Close, stringResource(Lang.qr_login_close)) }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
            ),
            windowInsets = AniWindowInsets.forTopAppBar(),
        )

        Column(
            Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(AniWindowInsets.forPageContent())
                .padding(horizontal = 32.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(if (invalidContent != null) Lang.qr_login_scan_invalid else Lang.qr_login_scan_hint),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            FilledTonalIconToggleButton(torchEnabled, { torchEnabled = it }) {
                Icon(
                    if (torchEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                    stringResource(Lang.qr_login_scan_torch),
                )
            }
        }
    }
}

/**
 * 压暗取景框以外的区域, 并描出取景框. 只是视觉引导: 识别范围是整个画面.
 */
@Composable
private fun ScanFrame(modifier: Modifier = Modifier) {
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val side = size.minDimension * 0.68f
        val topLeft = Offset((size.width - side) / 2, (size.height - side) / 2.4f)
        val radius = CornerRadius(28.dp.toPx())
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(Color.Transparent, topLeft, Size(side, side), radius, blendMode = BlendMode.Clear)
        drawRoundRect(Color.White, topLeft, Size(side, side), radius, style = Stroke(3.dp.toPx()))
    }
}
