/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.settings

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** 在 SDK 37 引入, 访问局域网中的其他设备 (包括接受手机的连接) 需要该权限. */
private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
private const val ACCESS_LOCAL_NETWORK_SDK = 37

/**
 * 返回一个在未授予局域网访问权限时请求该权限的函数. 电视端的远程设置服务需要该权限才能接受手机的连接.
 */
@Composable
internal fun rememberLocalNetworkPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(context, launcher) {
        {
            if (Build.VERSION.SDK_INT >= ACCESS_LOCAL_NETWORK_SDK &&
                context.checkSelfPermission(ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(ACCESS_LOCAL_NETWORK)
            }
        }
    }
}
