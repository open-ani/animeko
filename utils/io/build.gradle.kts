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
    id("ani.kmp-library")

    // alias(libs.plugins.kotlinx.atomicfu)
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    android {
        namespace = "me.him188.ani.utils.io"
    }
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions.freeCompilerArgs.add("-Xdont-warn-on-error-suppression")

    sourceSets.commonMain.dependencies {
        api(projects.utils.platform)
        api(libs.kotlinx.io.core)
        implementation(projects.utils.logging)
        implementation(libs.atomicfu)
//        implementation(libs.okio) // 仅用于读文件
    }

    sourceSets.nativeMain.dependencies {
    }
}
