/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.settings.remote

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * 与局域网内其他设备通信 (包括电视接受手机的连接) 所需的运行时权限, 自 SDK 37 起需要申请.
 */
object LocalNetworkPermission {
    const val NAME = "android.permission.ACCESS_LOCAL_NETWORK"

    val isRuntimePermission: Boolean get() = Build.VERSION.SDK_INT >= 37

    fun isGranted(context: Context): Boolean =
        !isRuntimePermission || context.checkSelfPermission(NAME) == PackageManager.PERMISSION_GRANTED
}
