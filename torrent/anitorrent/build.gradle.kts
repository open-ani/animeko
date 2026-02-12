/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    `ani-mpp-lib-targets`
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.kotlinx.atomicfu)
}

kotlin {
    androidLibrary {
        namespace = "me.him188.ani.torrent.anitorrent"
    }
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.collections.immutable)
        api(projects.utils.io)
        api(projects.utils.platform)
        api(projects.torrent.torrentApi)
        api(projects.utils.coroutines)
    }
    sourceSets.getByName("jvmMain").dependencies {
        api(anitorrentLibs.anitorrent.native)
    }
    sourceSets.getByName("desktopMain").dependencies {
        val triple = getAnitorrentTriple()
        if (triple != null) {
            api(
                anitorrentLibs.anitorrent.native.desktop.asProvider().map { notation ->
                    "$notation:${triple}"
                },
            )
        }
    }
}

fun getAnitorrentTriple(): String? {
    return when (getOs()) {
        Os.MacOS -> {
            when (getArch()) {
                Arch.X86_64 -> "macos-x64"
                Arch.AARCH64 -> "macos-aarch64"
            }
        }

        Os.Windows -> {
            when (getArch()) {
                Arch.X86_64 -> "windows-x64"
                else -> error("Unsupported architecture: ${getArch()}")
            }
        }

        Os.Linux -> {
            when (getArch()) {
                Arch.X86_64 -> "linux-x64"
                else -> error("Unsupported architecture: ${getArch()}")
            }
        }

        Os.Unknown -> error("Unsupported OS: ${getOs()}")
    }
}