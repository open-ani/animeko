/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")

    `ani-mpp-lib-targets`
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.atomicfu")
    id("io.sentry.kotlin.multiplatform.gradle")
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.appPlatform)
        api(projects.app.shared.uiFoundation)
        api(projects.app.shared)
        api(libs.kotlinx.coroutines.core)
    }
    sourceSets.commonTest.dependencies {
        implementation(projects.utils.uiTesting)
    }
    sourceSets.androidMain.dependencies {
        implementation(libs.androidx.compose.ui.tooling.preview)
        implementation(libs.androidx.compose.ui.tooling)
    }
}

android {
    namespace = "me.him188.ani.app.application"
}

kotlin {
    if (enableIos) {
        // Sentry requires cocoapods for its dependencies
        if (getOs() == Os.MacOS) {
            extensions.configure<org.jetbrains.kotlin.gradle.plugin.cocoapods.CocoapodsExtension> {
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
        }
    }
}
