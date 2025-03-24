/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.atomicfu")
    `ani-mpp-lib-targets`
}

android {
    namespace = "me.him188.ani.utils.http.downloader"
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.utils.coroutines)
        api(libs.kotlinx.datetime)
        implementation(projects.utils.logging)
        implementation(projects.utils.ktorClient)
        api(libs.datastore.core)
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.turbine)
        implementation(libs.ktor.client.mock)
        implementation(libs.ktor.server.test.host)
        runtimeOnly(libs.slf4j.simple)
    }
}
