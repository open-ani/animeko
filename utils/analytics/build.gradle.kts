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
    kotlin("plugin.serialization")
    id("com.android.library")
    `ani-mpp-lib-targets`
}


android {
    namespace = "me.him188.ani.utils.analytics"
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.kotlinx.coroutines.core)
                api(projects.utils.logging)
                api(libs.firebase.analytics)
            }
        }
        val skikoMain by getting {
            dependencies {
                api(projects.utils.ktorClient)
                api(projects.utils.serialization)
                api(projects.utils.coroutines)
            }
        }
        val androidMain by getting {
            dependencies {
                /*
                
      > Could not find com.google.firebase:firebase-common:.
        Required by:
            project :app:android > project :app:shared > project :app:shared:app-platform > project :utils:analytics > dev.gitlive:firebase-analytics:2.3.0 > dev.gitlive:firebase-analytics-android:2.3.0 > dev.gitlive:firebase-app:2.3.0 > dev.gitlive:firebase-app-android:2.3.0
                 */
                api(libs.google.firebase.common)
                api(libs.google.firebase.analytics)
            }
        }
    }
}
