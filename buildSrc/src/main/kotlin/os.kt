/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import java.util.Locale

// get current os
enum class Os {
    Windows,
    MacOS,
    Linux,
    Unknown
}

fun getOs(): Os {
    val os = System.getProperty("os.name").lowercase(Locale.getDefault())
    return when {
        os.contains("win") -> Os.Windows
        os.contains("mac") -> Os.MacOS
        os.contains("nux") -> Os.Linux
        else -> Os.Unknown
    }
}

enum class Arch {
    X86_64,
    AARCH64,
}

fun getArch(): Arch {
    val arch = System.getProperty("os.arch").lowercase(Locale.getDefault())
    return when {
        arch.contains("x86_64") || arch.contains("amd64") -> Arch.X86_64
        arch.contains("aarch64") || arch.contains("arm") -> Arch.AARCH64
        else -> throw UnsupportedOperationException("Unknown architecture: $arch")
    }
}

fun getOsTriple(): String {
    return when (getOs()) {
        Os.Windows -> "windows-x64"
        Os.MacOS -> if (getArch() == Arch.AARCH64) "macos-arm64" else "macos-x64"
        Os.Linux -> "linux-x64"
        Os.Unknown -> throw UnsupportedOperationException("Unknown OS")
    }
}
