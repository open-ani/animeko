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
    `android-library`
    kotlin("plugin.serialization")
    `ani-mpp-lib-targets`
    idea
}

android {
    namespace = "me.him188.ani.app.datasource.api"
}

kotlin {
    sourceSets.commonMain {
        dependencies {
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.protobuf)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            api(projects.utils.ktorClient)
            api(projects.utils.serialization)
            implementation(projects.utils.platform)
            api(libs.ktor.client.auth)
            implementation(libs.ktor.client.logging)
            implementation(projects.utils.logging)
        }
    }

    sourceSets.commonTest {
        dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(projects.utils.testing)
        }
    }

    sourceSets.getByName("jvmMain") {
        dependencies {
            api(libs.jsoup)
        }
    }
}

idea {
    module.generatedSourceDirs.add(file("src/commonTest/kotlin/title/generated"))
}

junitPlatform {
    this.instrumentationTests.enabled = true
}