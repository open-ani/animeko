/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

plugins {
    id("ani.android-library") // convention: jvmTarget/OptIns/编码 等全仓约定 (AGP 9 内置 Kotlin 支持)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.jetbrains.compose)
}

android {
    namespace = "me.him188.ani.tv.ui"
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
    implementation(projects.app.android.uiExplorationTv)
    implementation(projects.app.android.uiSubjectTv)
    implementation(projects.app.android.uiEpisodeTv)
    implementation(projects.app.android.uiCollectionTv)
    implementation(projects.app.android.uiSearchTv)
    implementation(projects.app.android.uiScheduleTv)
    implementation(projects.app.android.uiLoginTv)
    implementation(projects.app.android.uiSettingsTv)
    implementation(projects.app.shared.appData) // SelfInfo (抽屉登录态)
    implementation(projects.app.shared.appPlatform)
    implementation(libs.compose.navigation3.runtime)
    implementation(libs.compose.navigation3.ui)
    implementation(libs.compose.lifecycle.viewmodel.navigation3)
    implementation(libs.compose.lifecycle.viewmodel.compose)
    implementation(libs.koin.core)
    implementation(compose.materialIconsExtended)
    implementation(libs.androidx.activity.compose) // BackHandler

    // 约定边界守护 (atv-architecture.md §11.1)
    testImplementation(libs.konsist)
    testImplementation(libs.junit5.jupiter.engine)
    testImplementation(kotlin("test-junit5"))
}
