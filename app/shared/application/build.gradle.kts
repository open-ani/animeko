/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.cocoapods.CocoapodsExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.jetbrains.compose)

    `ani-mpp-lib-targets`
    alias(libs.plugins.kotlin.plugin.serialization)

    // alias(libs.plugins.kotlinx.atomicfu)
    alias(libs.plugins.sentry.kotlin.multiplatform)
}

kotlin {
    androidLibrary {
        namespace = "me.him188.ani.app.application"
    }
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.appPlatform)
        api(projects.app.shared.uiFoundation)
        api(projects.app.shared)
        api(libs.kotlinx.coroutines.core)
        implementation(libs.atomicfu)
    }
    sourceSets.commonTest.dependencies {
        implementation(projects.utils.uiTesting)
    }
    sourceSets.iosMain.dependencies {
        implementation(libs.mediamp.ffmpeg)
    }
}

kotlin {
    if (enableIos && buildIosFramework && getOs() == Os.MacOS) {
        // Sentry requires cocoapods for its dependencies
        extensions.configure<CocoapodsExtension> {
            // https://kotlinlang.org/docs/native-cocoapods.html#configure-existing-project
            framework {
                baseName = "application"
                isStatic = false
                @OptIn(ExperimentalKotlinGradlePluginApi::class)
                transitiveExport = false
                export(projects.app.shared.appPlatform)
            }
            // iOS Firebase SDKs are linked from the host Podfile
        }

        val httpDownloaderProject = project(":utils:http-downloader")
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            val capitalizedTargetName = target.name.replaceFirstChar { it.uppercase() }
            val sliceName = when (target.name) {
                "iosArm64" -> "ios-arm64"
                "iosSimulatorArm64" -> "ios-arm64-simulator"
                else -> error("Unsupported Apple target: ${target.name}")
            }
            val frameworkSearchPath = httpDownloaderProject.layout.buildDirectory.dir(
                "mediamp-ffmpeg/apple-runtime/MediampFFmpegKit.xcframework/$sliceName",
            )
            val frameworkSearchPathValue = frameworkSearchPath.get().asFile.absolutePath

            target.binaries.configureEach {
                linkerOpts("-F$frameworkSearchPathValue", "-framework", "MediampFFmpegKit")
            }

            tasks.matching { task ->
                task.name.startsWith("link") && task.name.endsWith(capitalizedTargetName)
            }.configureEach {
                dependsOn(":utils:http-downloader:extractMediampFfmpegAppleRuntime")
            }
        }
    }
}
