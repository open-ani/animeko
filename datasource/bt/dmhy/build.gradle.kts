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
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.plugin.serialization)
    `ani-mpp-lib-targets`

    // TODO AGP Migration: atomicfu plugin broken see: https://github.com/Kotlin/kotlinx-atomicfu/issues/511
    // alias(libs.plugins.kotlinx.atomicfu)
}


kotlin {
    androidLibrary {
        namespace = "me.him188.ani.datasource.dmhy"
    }
    sourceSets {
        getByName("jvmMain") {
            dependencies {
                api(projects.datasource.datasourceApi)

                api(libs.kotlinx.coroutines.core)
                api(libs.ktor.client.core)

                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.jsoup)
                implementation(libs.slf4j.api)

                implementation(projects.utils.logging)
            }
        }
    }
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE // why is there a duplicate?
}
