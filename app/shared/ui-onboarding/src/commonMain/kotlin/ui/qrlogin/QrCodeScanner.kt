/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.qrlogin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 当前平台是否能用相机扫描二维码. 为 `false` 时不应展示扫码入口, [QrCodeScanner] 不会显示任何内容.
 */
expect val isQrCodeScannerSupported: Boolean

/**
 * 铺满 [modifier] 的相机预览, 持续识别画面中的二维码.
 *
 * 首次显示时会请求相机权限. 没有权限时显示 [permissionDeniedContent], 其参数用于再次请求权限.
 *
 * @param onScanned 识别到二维码时调用, 参数为二维码的文本. 同一个二维码停留在画面中时会被反复调用, 由调用方去重.
 */
@Composable
expect fun QrCodeScanner(
    onScanned: (String) -> Unit,
    torchEnabled: Boolean,
    permissionDeniedContent: @Composable (requestPermission: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
)
