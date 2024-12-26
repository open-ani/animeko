/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    `ani-mpp-lib-targets`
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.atomicfu")
}

android {
    namespace = "me.him188.ani.torrent.anitorrent"
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.coroutines.core)
        api(projects.utils.io)
        api(projects.utils.platform)
        api(projects.torrent.torrentApi)
        api(projects.utils.coroutines)
    }
    sourceSets.getByName("jvmMain").dependencies {
        api(anitorrentLibs.anitorrent.native)
    }
    sourceSets.getByName("desktopMain").dependencies {
        val anitorrentVersion = anitorrentLibs.versions.anitorrent.get()
        val triple = getAnitorrentTriple()
        if (triple != null) {
            api("org.openani.anitorrent:anitorrent-native-desktop:$anitorrentVersion:${triple}")
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

        Os.Linux -> null
        Os.Unknown -> error("Unsupported OS: ${getOs()}")
    }
}