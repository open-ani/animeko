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
    `ani-mpp-lib-targets`
}

android {
    namespace = "me.him188.ani.utils.logging"
}

dependencies {
    jvmMainApi(libs.slf4j.api)
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")

    sourceSets.nativeMain {
        dependencies {
            implementation(libs.oshai.kotlin.logging)
        }
    }
}