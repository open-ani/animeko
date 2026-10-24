/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.time

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
actual fun rememberLocationTimeZoneCapability(): LocationTimeZoneCapability {
    val pendingResult = remember { mutableStateOf<((granted: Boolean) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val callback = pendingResult.value ?: return@rememberLauncherForActivityResult
        pendingResult.value = null
        callback(granted)
    }
    return remember(launcher) {
        object : LocationTimeZoneCapability {
            override val supported: Boolean get() = true

            override fun requestPermission(onResult: (granted: Boolean) -> Unit) {
                // 已授权时 ActivityResultContracts.RequestPermission 直接返回 granted, 不会弹窗
                pendingResult.value = onResult
                launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    }
}
