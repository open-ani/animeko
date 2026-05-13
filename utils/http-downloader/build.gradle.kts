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
    alias(libs.plugins.kotlin.plugin.serialization)

    // alias(libs.plugins.kotlinx.atomicfu)
    `ani-mpp-lib-targets`
}

val mediampFfmpegAppleRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val mediampVersion = libs.versions.mediamp.get()
val mediampFfmpegIOSRuntime = libs.mediamp.ffmpeg.runtime.ios.xcframework.get()

val mediampFfmpegAppleRuntimeDirectory = layout.buildDirectory.dir("mediamp-ffmpeg/apple-runtime")
val extractMediampFfmpegAppleRuntime = tasks.register<ExtractAppleXcframeworkTask>("extractMediampFfmpegAppleRuntime") {
    archives.from(mediampFfmpegAppleRuntime)
    outputDirectory.set(mediampFfmpegAppleRuntimeDirectory)
}

kotlin {
    androidLibrary {
        namespace = "me.him188.ani.utils.http.downloader"
        packaging {
            resources {
                pickFirsts.add("META-INF/AL2.0")
                pickFirsts.add("META-INF/LGPL2.1")
                excludes.add("META-INF/DEPENDENCIES")
                excludes.add("META-INF/licenses/ASM")
            }
        }
    }
    sourceSets.commonMain.dependencies {
        api(projects.utils.coroutines)
        api(libs.kotlinx.datetime)
        api(libs.mediamp.ffmpeg)
        implementation(projects.utils.logging)
        implementation(projects.utils.ktorClient)
        api(libs.datastore.core)
        implementation(libs.androidx.room.common)
        implementation(projects.utils.serialization)
        implementation(libs.kotlinx.serialization.protobuf)
        implementation(libs.kotlinx.collections.immutable)
    }
    sourceSets.desktopMain.dependencies {
//        runtimeOnly(libs.slf4j.simple)
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.turbine)
        implementation(libs.ktor.client.mock)
        implementation(libs.ktor.server.test.host)
        runtimeOnly(libs.slf4j.simple)
    }
}

dependencies {
    when (val triple = getOsTriple()) {
        "windows-x64" -> desktopTestRuntimeOnly(libs.mediamp.ffmpeg.runtime.windows.x64)
        "linux-x64" -> desktopTestRuntimeOnly(libs.mediamp.ffmpeg.runtime.linux.x64)
        "macos-x64" -> desktopTestRuntimeOnly(libs.mediamp.ffmpeg.runtime.macos.x64)
        "macos-arm64" -> desktopTestRuntimeOnly(libs.mediamp.ffmpeg.runtime.macos.arm64)
        "windows-arm64" -> desktopTestRuntimeOnly(libs.mediamp.ffmpeg.runtime.windows.arm64)
        else -> throw UnsupportedOperationException("Unknown os: $triple")
    }

    mediampFfmpegAppleRuntime("${mediampFfmpegIOSRuntime.group}:${mediampFfmpegIOSRuntime.name}:$mediampVersion@zip")
}
