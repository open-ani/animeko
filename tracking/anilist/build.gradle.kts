/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

@file:Suppress("UnstableApiUsage")

plugins {
    id("ani.kmp-library")
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    android {
        namespace = "me.him188.ani.app.tracking.anilist"
    }

    sourceSets.commonMain.dependencies {
        api(projects.tracking.api)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.ktor.client.content.negotiation)
        implementation(libs.ktor.serialization.kotlinx.json)
    }

    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.ktor.client.mock)
        implementation(projects.utils.testing)
    }
}
