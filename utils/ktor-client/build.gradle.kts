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
    `ani-mpp-lib-targets`
    kotlin("plugin.serialization")
}

android {
    namespace = "me.him188.ani.utils.ktor.client"
}

kotlin {
    sourceSets.commonMain {
        dependencies {
            api(libs.kotlinx.serialization.core)
            api(libs.ktor.client.core)
            api(libs.ktor.client.content.negotiation)
            api(libs.ktor.serialization.kotlinx.json)
            implementation(projects.utils.xml)
            api(projects.utils.logging)
            implementation(projects.utils.platform)
            api(projects.utils.io)
        }
    }

    sourceSets.getByName("jvmMain") {
        dependencies {
            api(libs.ktor.client.okhttp)
            implementation(libs.jsoup)
        }
    }

    sourceSets.appleMain {
        dependencies {
            api(libs.ktor.client.darwin)
        }
    }
}