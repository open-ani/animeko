/*
 * Copyright (C) 2024 OpenAni and contributors.
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
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.atomicfu")
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")

    sourceSets.commonMain.dependencies {
        implementation(libs.atomicfu)
        implementation(libs.kotlinx.collections.immutable)
        implementation(libs.androidx.collection)
    }

    sourceSets.getByName("jvmMain").dependencies {
        api(libs.jetbrains.annotations)
    }

    sourceSets.nativeMain.dependencies {
        implementation(libs.kotlinx.datetime)
    }
}

android {
    namespace = "me.him188.ani.utils.platform"
}
