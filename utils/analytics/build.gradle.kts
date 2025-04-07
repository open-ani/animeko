/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("UnstableApiUsage")

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    `ani-mpp-lib-targets`
}


android {
    namespace = "me.him188.ani.utils.analytics"
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.coroutines.core)
        api(projects.utils.logging)
    }
    sourceSets.androidMain.dependencies {
//        api(libs.countly.sdk.android)
        api(libs.posthog.android)
    }
    sourceSets.desktopMain.dependencies {
//        api(libs.countly.sdk.java)
        api(libs.posthog.java)
    }
}
