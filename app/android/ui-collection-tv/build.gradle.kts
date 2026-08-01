/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

plugins {
    alias(libs.plugins.android.library) // AGP 9 内置 Kotlin 支持
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.jetbrains.compose)
}

android {
    namespace = "me.him188.ani.tv.ui.collection"
    compileSdk = getIntProperty("android.compile.sdk")
    defaultConfig {
        minSdk = getIntProperty("android.min.sdk")
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    api(projects.app.android.uiFoundationTv)
    implementation(projects.app.shared.appData)
    api(projects.app.shared.uiFoundation) // 仅白名单基建: AsyncImage / AbstractViewModel (§4.2)
    implementation(libs.koin.core)
    implementation(libs.paging.compose.android)
}
