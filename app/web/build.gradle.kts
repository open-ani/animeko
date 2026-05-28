@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

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
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    wasmJs {
        outputModuleName = "animeko-web"
        browser {
            commonWebpackConfig {
                outputFileName = "animeko-web.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(projects.app.shared)
            implementation(projects.app.shared.application)
            implementation(libs.compose.components.resources)
        }
    }
}
