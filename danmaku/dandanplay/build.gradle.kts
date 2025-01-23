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
    `ani-mpp-lib-targets`
}

android {
    namespace = "me.him188.ani.danmaku.dandanplay"
}

kotlin {
    sourceSets.commonMain {
        dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.core)

            api(projects.danmaku.danmakuApi)
            api(projects.utils.ktorClient)
            api(projects.utils.logging)
            api(projects.datasource.datasourceApi)
        }
    }
    sourceSets.commonTest {
        dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
