/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")

    `ani-mpp-lib-targets`
}

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.ui.test.ExperimentalTestApi")
    }
    sourceSets.commonMain.dependencies {
        api(projects.utils.platform)
        api(projects.utils.testing)
        api(projects.utils.io)
        api(libs.kotlinx.io.core)
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.coroutines.test)
        @OptIn(ExperimentalComposeLibrary::class)
        api(compose.uiTest)
        api(kotlin("test"))

        api(compose.runtime)
        implementation(libs.compose.lifecycle.runtime.compose)
        implementation(libs.compose.lifecycle.runtime)
    }
    sourceSets.desktopMain.dependencies {
        runtimeOnly(libs.kotlinx.coroutines.swing)
        api(compose.desktop.currentOs)
    }
    sourceSets.androidMain.dependencies {
        runtimeOnly(libs.kotlinx.coroutines.android)
    }
}

android {
    namespace = "me.him188.ani.utils.ui.testing"
}
