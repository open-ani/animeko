/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinReg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow

@Composable
fun WindowsAccentColorEffect() {
    val hKey = remember { WinReg.HKEYByReference() }
    val platformWindow = LocalPlatformWindow.current
    LaunchedEffect(hKey, platformWindow) {
        Advapi32.INSTANCE.RegOpenKeyEx(
            WinReg.HKEY_CURRENT_USER,
            "SOFTWARE\\Microsoft\\Windows\\DWM",
            0,
            WinNT.KEY_READ,
            hKey,
        )
        platformWindow._accentColor.tryEmit(currentAccentColor())
        withContext(Dispatchers.IO) {
            while (true) {
                Advapi32.INSTANCE.RegNotifyChangeKeyValue(
                    hKey.value,
                    false,
                    WinNT.REG_NOTIFY_CHANGE_LAST_SET,
                    null,
                    false,
                )
                platformWindow._accentColor.tryEmit(currentAccentColor())
            }
        }
    }
    DisposableEffect(hKey) {
        onDispose {
            Advapi32Util.registryCloseKey(hKey.value)
        }
    }
}

private fun currentAccentColor(): Color {
    val value = Advapi32Util.registryGetIntValue(
        WinReg.HKEY_CURRENT_USER,
        "SOFTWARE\\Microsoft\\Windows\\DWM",
        "AccentColor",
    ).toLong()
    val alpha = (value and 0xFF000000)
    val green = (value and 0xFF).shl(16)
    val blue = (value and 0xFF00)
    val red = (value and 0xFF0000).shr(16)
    return Color((alpha or green or blue or red).toInt())
}