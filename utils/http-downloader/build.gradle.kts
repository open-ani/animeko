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

val iosArm64FfmpegRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val iosSimulatorArm64FfmpegRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
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
        "windows-x64" -> add("desktopTestRuntimeOnly", libs.mediamp.ffmpeg.runtime.windows.x64)
        "linux-x64" -> add("desktopTestRuntimeOnly", libs.mediamp.ffmpeg.runtime.linux.x64)
        "macos-x64" -> add("desktopTestRuntimeOnly", libs.mediamp.ffmpeg.runtime.macos.x64)
        "macos-arm64" -> add("desktopTestRuntimeOnly", libs.mediamp.ffmpeg.runtime.macos.arm64)
        else -> throw UnsupportedOperationException("Unknown os: $triple")
    }

    "iosArm64FfmpegRuntime"(libs.mediamp.ffmpeg.runtime.ios.arm64)
    "iosSimulatorArm64FfmpegRuntime"(libs.mediamp.ffmpeg.runtime.ios.simulator.arm64)
}

tasks.register<Sync>("preparePublishedFfmpegRuntimeIosArm64") {
    from(
        providers.provider {
            val files = iosArm64FfmpegRuntime.resolve().toList()
            check(files.size == 1) {
                "Expected ${iosArm64FfmpegRuntime.name} to resolve exactly one runtime jar, got ${files.size}: $files"
            }
            zipTree(files.single())
        },
    )
    into(layout.buildDirectory.dir("published-ffmpeg-runtime/ios-arm64"))
    includeEmptyDirs = false
}

tasks.register<Sync>("preparePublishedFfmpegRuntimeIosSimulatorArm64") {
    from(
        providers.provider {
            val files = iosSimulatorArm64FfmpegRuntime.resolve().toList()
            check(files.size == 1) {
                "Expected ${iosSimulatorArm64FfmpegRuntime.name} to resolve exactly one runtime jar, got ${files.size}: $files"
            }
            zipTree(files.single())
        },
    )
    into(layout.buildDirectory.dir("published-ffmpeg-runtime/ios-simulator-arm64"))
    includeEmptyDirs = false
}
