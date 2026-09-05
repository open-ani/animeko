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
    namespace = "me.him188.ani.tv.ui.foundation"
    compileSdk = getIntProperty("android.compile.sdk")
    defaultConfig {
        minSdk = getIntProperty("android.min.sdk")
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    api(libs.androidx.tv.material)
    api(projects.app.shared.appPlatform)
    api(compose.foundation)
    implementation(projects.app.shared.uiFoundation) // 仅白名单基建: AsyncImage (§4.2)
    // materialkolor 生成 m3 ColorScheme, 由 TvColorMapping 逐字段映射到 tv-material ColorScheme.
    // 这是 TV 代码中唯一允许接触 androidx.compose.material3 类型的位置 (atv-architecture.md §4.2).
    implementation(libs.materialkolor)

    // 焦点框架单元测试 (纯逻辑: 解析轮询语义 / 焦点记忆协议状态机)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.junit5.jupiter.engine)
    testImplementation(libs.kotlinx.coroutines.test)
}
