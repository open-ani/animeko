/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    `ani-mpp-lib-targets`
}

dependencies {
    jvmMainApi(libs.slf4j.api)
}

kotlin {
    androidLibrary {
        namespace = "me.him188.ani.utils.logging"
    }
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")

    sourceSets.nativeMain {
        dependencies {
//            implementation(libs.diamondedge.logging)
            api(libs.kotlinx.io.core)
            implementation(libs.kotlinx.io.bytestring)
        }
    }
}