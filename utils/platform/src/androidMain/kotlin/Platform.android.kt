/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.platform

import android.os.Build

internal actual fun currentPlatformImpl(): Platform {
    if (Build.SUPPORTED_ABIS == null) {
        // unit testing
        return Platform.Android(Arch.ARMV8A)
    }
    return Build.SUPPORTED_ABIS.getOrNull(0)?.let { abi ->
        when (abi.lowercase()) {
            "armeabi-v7a" -> Platform.Android(Arch.ARMV7A)
            "arm64-v8a" -> Platform.Android(Arch.ARMV8A)
            "x86_64" -> Platform.Android(Arch.X86_64)
            else -> Platform.Android(Arch.ARMV8A)
        }
    } ?: Platform.Android(Arch.ARMV8A)
}
