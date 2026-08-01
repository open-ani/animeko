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
    val abis = Build.SUPPORTED_ABIS?.toList()
        ?: return Platform.Android(Arch.ARMV8A) // unit testing
    // arch 只是首选 ABI, 且只能取 Arch 里的值 (x86 这类不认识的一律记成 ARMV8A);
    // 完整列表一并带上, 判断"装得上哪个包"必须用它, 见 Platform.Android.supportedAbis
    val arch = when (abis.firstOrNull()?.lowercase()) {
        "armeabi-v7a" -> Arch.ARMV7A
        "arm64-v8a" -> Arch.ARMV8A
        "x86_64" -> Arch.X86_64
        else -> Arch.ARMV8A
    }
    return Platform.Android(arch, abis)
}
